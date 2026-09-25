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
    private var expiryJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { store.load() }
            if (saved == null) {
                _state.value = HiroAuthState.SignedOut(defaultServerUrl())
                return@launch
            }
            val expired = runCatching { !Instant.parse(saved.expiresAt).isAfter(Instant.now()) }.getOrDefault(true)
            if (expired) {
                withContext(Dispatchers.IO) { store.clearSession() }
                _state.value = HiroAuthState.SignedOut(saved.serverUrl, "Сессия завершена. Войдите снова")
                return@launch
            }
            try {
                setSignedIn(withContext(Dispatchers.IO) { client.validate(saved) })
            } catch (error: HiroServerException) {
                if (error.code == "unauthorized") {
                    withContext(Dispatchers.IO) { store.clearSession() }
                    _state.value = HiroAuthState.SignedOut(saved.serverUrl, "Сессия завершена. Войдите снова")
                } else {
                    // A temporary outage must not destroy a still-valid local session.
                    setSignedIn(saved)
                }
            }
        }
    }

    fun signIn(serverUrl: String, username: String, password: String) {
        if (_state.value is HiroAuthState.SigningIn) return
        expiryJob?.cancel()
        _state.value = HiroAuthState.SigningIn(serverUrl)
        viewModelScope.launch {
            try {
                val session = withContext(Dispatchers.IO) { client.login(serverUrl, username, password) }
                withContext(Dispatchers.IO) { store.save(session) }
                setSignedIn(session)
            } catch (error: HiroServerException) {
                _state.value = HiroAuthState.SignedOut(serverUrl, error.userMessage)
            } catch (_: Exception) {
                _state.value = HiroAuthState.SignedOut(serverUrl, "Не удалось сохранить защищённую сессию")
            }
        }
    }

    fun signOut() {
        val currentUrl = (state.value as? HiroAuthState.SignedIn)?.session?.serverUrl ?: defaultServerUrl()
        expiryJob?.cancel()
        store.clearSession()
        _state.value = HiroAuthState.SignedOut(currentUrl)
    }

    private fun setSignedIn(session: HiroSession) {
        expiryJob?.cancel()
        _state.value = HiroAuthState.SignedIn(session)
        val expiresAt = Instant.parse(session.expiresAt)
        val remainingMillis = Duration.between(Instant.now(), expiresAt).toMillis().coerceAtLeast(0)
        expiryJob = viewModelScope.launch {
            delay(remainingMillis)
            withContext(Dispatchers.IO) { store.clearSession() }
            _state.value = HiroAuthState.SignedOut(session.serverUrl, "Сессия завершена. Войдите снова")
        }
    }

    private fun defaultServerUrl(): String = store.lastServerUrl()
        ?: if (BuildConfig.DEBUG) "http://127.0.0.1:8788" else "https://"
}
