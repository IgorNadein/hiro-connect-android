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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface HiroAuthState {
    data object Checking : HiroAuthState
    data class SignedOut(val serverUrl: String, val error: String? = null) : HiroAuthState
    data class SigningIn(val serverUrl: String) : HiroAuthState
    data class SignedIn(val session: HiroSession) : HiroAuthState
}

class HiroAuthViewModel(application: Application) : AndroidViewModel(application) {

    private val store = HiroSessionStore(application)
    private val client = HiroServerClient()
    private val _state = MutableStateFlow<HiroAuthState>(HiroAuthState.Checking)
    val state: StateFlow<HiroAuthState> = _state.asStateFlow()
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
        refreshJob?.cancel()
        _state.value = HiroAuthState.SigningIn(serverUrl)
        viewModelScope.launch {
            try {
                val session = withContext(Dispatchers.IO) { client.login(serverUrl, username, password) }
                persistAndSignIn(session)
            } catch (error: HiroServerException) {
                _state.value = HiroAuthState.SignedOut(serverUrl, error.userMessage)
            } catch (_: Exception) {
                _state.value = HiroAuthState.SignedOut(serverUrl, "Не удалось сохранить защищённую сессию")
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
        ?: if (BuildConfig.DEBUG) "http://127.0.0.1:8788" else "https://"

    companion object {
        private const val REFRESH_EARLY_SECONDS = 60L
        private const val RETRY_MIN_MILLIS = 60_000L
        private const val RETRY_MAX_MILLIS = 15 * 60_000L
    }
}
