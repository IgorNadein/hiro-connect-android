package ru.hiro.manager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface HiroAuthState {
    data object Checking : HiroAuthState
    data class SignedOut(
        val serverUrl: String,
        val error: String? = null,
        val options: HiroLoginOptions? = null
    ) : HiroAuthState
    data class SigningIn(val serverUrl: String) : HiroAuthState
    data class SignedIn(val session: HiroSession) : HiroAuthState
}

class HiroAuthViewModel(application: Application) : AndroidViewModel(application) {

    private val store = HiroSessionStore(application)
    private val client = HiroServerClient()
    private val _state = MutableStateFlow<HiroAuthState>(HiroAuthState.Checking)
    val state: StateFlow<HiroAuthState> = _state.asStateFlow()
    private val _browserRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val browserRequests: SharedFlow<String> = _browserRequests.asSharedFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { store.load() }
            if (saved == null) {
                _state.value = HiroAuthState.SignedOut(defaultServerUrl())
                return@launch
            }
            restore(saved)
        }
    }

    fun signIn(serverUrl: String, username: String, password: String) {
        if (_state.value is HiroAuthState.SigningIn) return
        val options = (_state.value as? HiroAuthState.SignedOut)?.options
        refreshJob?.cancel()
        _state.value = HiroAuthState.SigningIn(serverUrl)
        viewModelScope.launch {
            try {
                val session = withContext(Dispatchers.IO) { client.login(serverUrl, username, password) }
                persistAndSignIn(session)
            } catch (error: HiroServerException) {
                _state.value = HiroAuthState.SignedOut(serverUrl, error.userMessage, options)
            } catch (_: Exception) {
                _state.value = HiroAuthState.SignedOut(serverUrl, "Не удалось сохранить защищённую сессию", options)
            }
        }
    }

    fun selectServer(serverUrl: String) {
        if (_state.value is HiroAuthState.SigningIn) return
        refreshJob?.cancel()
        _state.value = HiroAuthState.SigningIn(serverUrl)
        viewModelScope.launch {
            try {
                val (normalizedUrl, options) = withContext(Dispatchers.IO) { client.loginOptions(serverUrl) }
                if (options.oidcEnabled && !options.passwordEnabled) {
                    beginOIDC(normalizedUrl, options)
                } else {
                    _state.value = HiroAuthState.SignedOut(normalizedUrl, options = options)
                }
            } catch (error: HiroServerException) {
                _state.value = HiroAuthState.SignedOut(serverUrl, error.userMessage)
            } catch (_: Exception) {
                _state.value = HiroAuthState.SignedOut(serverUrl, "Не удалось проверить способы входа")
            }
        }
    }

    fun signInWithOIDC(serverUrl: String) {
        if (_state.value is HiroAuthState.SigningIn) return
        val options = (_state.value as? HiroAuthState.SignedOut)?.options
        refreshJob?.cancel()
        _state.value = HiroAuthState.SigningIn(serverUrl)
        viewModelScope.launch {
            beginOIDC(serverUrl, options ?: HiroLoginOptions(passwordEnabled = false, oidcEnabled = true))
        }
    }

    private suspend fun beginOIDC(serverUrl: String, options: HiroLoginOptions) {
        try {
            val pending = withContext(Dispatchers.IO) { client.prepareOIDC(serverUrl) }
            withContext(Dispatchers.IO) { store.savePendingOIDC(pending) }
            _browserRequests.emit(pending.startUrl)
            // The external browser may be cancelled, so keep the login screen retryable.
            _state.value = HiroAuthState.SignedOut(pending.serverUrl, options = options)
        } catch (error: HiroServerException) {
            _state.value = HiroAuthState.SignedOut(serverUrl, error.userMessage, options)
        } catch (_: Exception) {
            _state.value = HiroAuthState.SignedOut(serverUrl, "Не удалось начать защищённый вход", options)
        }
    }

    fun finishOIDC(ticket: String) {
        if (ticket.isBlank()) return
        viewModelScope.launch {
            val pending = withContext(Dispatchers.IO) { store.loadPendingOIDC() }
            if (pending == null) {
                _state.value = HiroAuthState.SignedOut(
                    defaultServerUrl(), "Вход устарел. Начните его заново",
                    HiroLoginOptions(passwordEnabled = false, oidcEnabled = true)
                )
                return@launch
            }
            _state.value = HiroAuthState.SigningIn(pending.serverUrl)
            try {
                val session = withContext(Dispatchers.IO) { client.redeemOIDC(pending, ticket) }
                withContext(Dispatchers.IO) { store.clearPendingOIDC() }
                persistAndSignIn(session)
            } catch (error: HiroServerException) {
                withContext(Dispatchers.IO) { store.clearPendingOIDC() }
                _state.value = HiroAuthState.SignedOut(
                    pending.serverUrl, error.userMessage,
                    HiroLoginOptions(passwordEnabled = false, oidcEnabled = true)
                )
            } catch (_: Exception) {
                withContext(Dispatchers.IO) { store.clearPendingOIDC() }
                _state.value = HiroAuthState.SignedOut(
                    pending.serverUrl, "Не удалось завершить защищённый вход",
                    HiroLoginOptions(passwordEnabled = false, oidcEnabled = true)
                )
            }
        }
    }

    fun signOut() {
        val session = (state.value as? HiroAuthState.SignedIn)?.session
        val currentUrl = session?.serverUrl ?: defaultServerUrl()
        refreshJob?.cancel()
        store.clearSession()
        _state.value = HiroAuthState.SignedOut(currentUrl)
        if (session != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { client.logout(session) }
            }
        }
    }

    private suspend fun restore(saved: HiroSession) {
        if (accessTokenIsUsable(saved)) {
            try {
                val validated = withContext(Dispatchers.IO) { client.validate(saved) }
                persistAndSignIn(validated)
                return
            } catch (error: HiroServerException) {
                if (error.code != "unauthorized") {
                    // Offline is not logout: retain the encrypted device session and retry later.
                    setSignedIn(saved)
                    return
                }
            }
        }
        refreshOrRetainOffline(saved)
    }

    private suspend fun refreshOrRetainOffline(session: HiroSession) {
        try {
            val refreshed = withContext(Dispatchers.IO) { client.refresh(session) }
            persistAndSignIn(refreshed)
        } catch (error: HiroServerException) {
            if (error.code == "unauthorized") {
                revokeLocalSession(session.serverUrl)
            } else {
                // The server may be unreachable for hours or months. The device stays signed in.
                setSignedIn(session, retrySoon = true)
            }
        }
    }

    private suspend fun persistAndSignIn(session: HiroSession) {
        withContext(Dispatchers.IO) { store.save(session) }
        setSignedIn(session)
    }

    private fun setSignedIn(session: HiroSession, retrySoon: Boolean = false) {
        _state.value = HiroAuthState.SignedIn(session)
        scheduleRefresh(session, retrySoon)
    }

    private fun scheduleRefresh(session: HiroSession, retrySoon: Boolean = false) {
        refreshJob?.cancel()
        val initialDelay = if (retrySoon) {
            RETRY_MIN_MILLIS
        } else {
            val expiresAt = runCatching { Instant.parse(session.expiresAt) }.getOrNull()
            if (expiresAt == null) 0L else Duration.between(Instant.now(), expiresAt)
                .minusSeconds(REFRESH_EARLY_SECONDS)
                .toMillis()
                .coerceAtLeast(0L)
        }
        refreshJob = viewModelScope.launch {
            delay(initialDelay)
            var retryDelay = RETRY_MIN_MILLIS
            while (isActive) {
                val current = (_state.value as? HiroAuthState.SignedIn)?.session ?: return@launch
                try {
                    val refreshed = withContext(Dispatchers.IO) { client.refresh(current) }
                    withContext(Dispatchers.IO) { store.save(refreshed) }
                    _state.value = HiroAuthState.SignedIn(refreshed)
                    scheduleRefresh(refreshed)
                    return@launch
                } catch (error: HiroServerException) {
                    if (error.code == "unauthorized") {
                        revokeLocalSession(current.serverUrl)
                        return@launch
                    }
                    // Network, TLS and temporary server failures never destroy the local session.
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(RETRY_MAX_MILLIS)
                }
            }
        }
    }

    private fun revokeLocalSession(serverUrl: String) {
        refreshJob?.cancel()
        store.clearSession()
        _state.value = HiroAuthState.SignedOut(serverUrl, "Сессия отозвана на сервере")
    }

    private fun accessTokenIsUsable(session: HiroSession): Boolean {
        val expiresAt = runCatching { Instant.parse(session.expiresAt) }.getOrNull() ?: return false
        return expiresAt.isAfter(Instant.now().plusSeconds(15))
    }

    private fun defaultServerUrl(): String = store.lastServerUrl()
        ?: ""

    companion object {
        private const val REFRESH_EARLY_SECONDS = 60L
        private const val RETRY_MIN_MILLIS = 60_000L
        private const val RETRY_MAX_MILLIS = 15 * 60_000L
    }
}
