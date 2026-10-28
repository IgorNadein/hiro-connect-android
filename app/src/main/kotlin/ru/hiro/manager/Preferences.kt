package ru.hiro.manager

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.core.content.edit

class Preferences(context: Context) {

    companion object {
        internal const val DISPLAY_THEME = "ru.hiro.manager.DISPLAY_THEME"
        private const val DYNAMIC_COLORS = "ru.hiro.manager.DYNAMIC_COLORS"
    }

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    val hasDisplayTheme: Boolean get() = preferences.contains(DISPLAY_THEME)

    var displayTheme: Int
        get() = preferences.getInt(DISPLAY_THEME, -1)
        set(value) = preferences.edit { putInt(DISPLAY_THEME, value) }

    var dynamicColors: Boolean?
        get() = if (preferences.contains(DYNAMIC_COLORS)) preferences.getBoolean(DYNAMIC_COLORS, true) else null
        set(value) = preferences.edit {
            if (value == null) remove(DYNAMIC_COLORS) else putBoolean(DYNAMIC_COLORS, value)
        }

    var ringtoneUri = preferences.getString("ringtone_uri", "")
        set(value) = preferences.edit { putString("ringtone_uri", value) }

}