package ru.hiro.manager

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.*
import org.junit.Test

class ThemePreferenceTest {
    @Test fun dynamicColorsDefaultToSystemAndRespectSavedChoice() {
        assertTrue(resolveDynamicColors(null, ""))
        assertFalse(resolveDynamicColors(null, "no"))
        assertTrue(resolveDynamicColors(null, "yes"))
        assertFalse(resolveDynamicColors(false, "yes"))
        assertTrue(resolveDynamicColors(true, "no"))
    }

    @Test fun explicitThemeOverridesSystemAndSystemModeFollowsIt() {
        assertFalse(resolveDarkTheme(AppCompatDelegate.MODE_NIGHT_NO, true))
        assertTrue(resolveDarkTheme(AppCompatDelegate.MODE_NIGHT_YES, false))
        assertTrue(resolveDarkTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, true))
        assertFalse(resolveDarkTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, false))
    }
}
