package ru.hiro.manager

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GatewayMessageSyncStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    init {
        _unreadCount.value = preferences.getInt(UNREAD_COUNT, 0)
    }

    fun lastSeenMessageId(session: HiroSession): Long? {
        if (preferences.getString(SESSION_KEY, null) != sessionKey(session)) return null
        val value = preferences.getLong(LAST_SEEN_MESSAGE_ID, -1L)
        return value.takeIf { it >= 0L }
    }

    fun saveLastSeenMessageId(session: HiroSession, messageId: Long) {
        preferences.edit()
            .putString(SESSION_KEY, sessionKey(session))
            .putLong(LAST_SEEN_MESSAGE_ID, messageId.coerceAtLeast(0L))
            .apply()
    }

    fun addUnread(count: Int) {
        if (count <= 0) return
        val updated = (preferences.getInt(UNREAD_COUNT, 0) + count).coerceAtMost(999)
        preferences.edit().putInt(UNREAD_COUNT, updated).apply()
        _unreadCount.value = updated
    }

    fun clearUnread() {
        preferences.edit().putInt(UNREAD_COUNT, 0).apply()
        _unreadCount.value = 0
    }

    private fun sessionKey(session: HiroSession): String = "${session.serverUrl}|${session.user.id}"

    companion object {
        private const val PREFERENCES_NAME = "gateway_message_sync"
        private const val SESSION_KEY = "session_key"
        private const val LAST_SEEN_MESSAGE_ID = "last_seen_message_id"
        private const val UNREAD_COUNT = "unread_count"
        private val _unreadCount = MutableStateFlow(0)
        val unreadCount = _unreadCount.asStateFlow()
    }
}
