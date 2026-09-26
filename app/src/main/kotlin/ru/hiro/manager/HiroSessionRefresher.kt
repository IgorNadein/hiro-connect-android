package ru.hiro.manager

import android.content.Context
import java.time.Instant

object HiroSessionRefresher {
    private val lock = Any()

    fun refresh(context: Context, expected: HiroSession): HiroSession = synchronized(lock) {
        val store = HiroSessionStore(context.applicationContext)
        val latest = store.load()
        if (latest != null && latest.sessionId == expected.sessionId &&
            latest.accessToken != expected.accessToken && tokenIsUsable(latest)
        ) {
            return@synchronized latest
        }
        val candidate = latest?.takeIf { it.sessionId == expected.sessionId } ?: expected
        HiroServerClient().refresh(candidate).also(store::save)
    }

    private fun tokenIsUsable(session: HiroSession): Boolean {
        val expiresAt = runCatching { Instant.parse(session.expiresAt) }.getOrNull() ?: return false
        return expiresAt.isAfter(Instant.now().plusSeconds(15))
    }
}
