package ru.hiro.manager

import android.content.Context
import java.time.Instant

interface RefreshSessionStore {
    fun load(): HiroSession?
    fun saveIfCurrent(expected: HiroSession, replacement: HiroSession): Boolean
}

internal fun guardedRefresh(
    store: RefreshSessionStore,
    expected: HiroSession,
    tokenIsUsable: (HiroSession) -> Boolean,
    fetch: (HiroSession) -> HiroSession
): HiroSession {
    val latest = store.load()
    if (latest == null || latest.sessionId != expected.sessionId || latest.serverUrl != expected.serverUrl) {
        throw HiroServerException("session_changed", "Сессия изменилась")
    }
    if (latest.accessToken != expected.accessToken && tokenIsUsable(latest)) return latest
    val refreshed = fetch(latest)
    if (!store.saveIfCurrent(latest, refreshed)) {
        throw HiroServerException("session_changed", "Сессия изменилась")
    }
    return refreshed
}

object HiroSessionRefresher {
    private val lock = Any()

    fun refresh(context: Context, expected: HiroSession): HiroSession = synchronized(lock) {
        val store = HiroSessionStore(context.applicationContext)
        guardedRefresh(store, expected, ::tokenIsUsable) { HiroServerClient().refresh(it) }
    }

    private fun tokenIsUsable(session: HiroSession): Boolean {
        val expiresAt = runCatching { Instant.parse(session.expiresAt) }.getOrNull() ?: return false
        return expiresAt.isAfter(Instant.now().plusSeconds(15))
    }
}
