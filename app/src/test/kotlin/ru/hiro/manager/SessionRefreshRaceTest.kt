package ru.hiro.manager

import org.junit.Assert.*
import org.junit.Test

class SessionRefreshRaceTest {
    private fun session(id: String, token: String = "old") = HiroSession(
        serverUrl = "https://example.test", apiBaseUrl = "https://example.test/api/v1",
        accessToken = token, refreshToken = "refresh-$token", expiresAt = "2030-01-01T00:00:00Z",
        sessionId = id, refreshEndpointUrl = "https://example.test/refresh",
        logoutEndpointUrl = "https://example.test/logout", user = HiroUser(id, id, id, "manager")
    )

    private class MemoryStore(var current: HiroSession?) : RefreshSessionStore {
        override fun load() = current
        override fun saveIfCurrent(expected: HiroSession, replacement: HiroSession): Boolean {
            if (current != expected) return false
            current = replacement
            return true
        }
    }

    @Test fun logoutDuringRefreshCannotRestoreOldSession() {
        val old = session("one")
        val store = MemoryStore(old)
        val error = try {
            guardedRefresh(store, old, { false }) { store.current = null; session("one", "new") }
            null
        } catch (e: HiroServerException) { e }
        assertEquals("session_changed", error?.code)
        assertNull(store.current)
    }

    @Test fun accountSwitchDuringRefreshCannotOverwriteNewAccount() {
        val old = session("one")
        val replacement = session("two")
        val store = MemoryStore(old)
        val error = try {
            guardedRefresh(store, old, { false }) { store.current = replacement; session("one", "new") }
            null
        } catch (e: HiroServerException) { e }
        assertEquals("session_changed", error?.code)
        assertEquals(replacement, store.current)
    }

    @Test fun newerUsableRefreshIsReusedWithoutAnotherRequest() {
        val old = session("one")
        val newer = session("one", "new")
        val store = MemoryStore(newer)
        assertEquals(newer, guardedRefresh(store, old, { true }) { error("unexpected request") })
    }

    @Test fun absentOrDifferentSessionIsNotRefreshed() {
        val old = session("one")
        for (actual in listOf(null, session("two"))) {
            val store = MemoryStore(actual)
            val error = try {
                guardedRefresh(store, old, { true }) { error("unexpected request") }
                null
            } catch (e: HiroServerException) { e }
            assertEquals("session_changed", error?.code)
            assertEquals(actual, store.current)
        }
    }
}
