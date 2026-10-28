package ru.hiro.manager

import android.os.Build.VERSION
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

internal fun resolveDynamicColors(saved: Boolean?, legacy: String): Boolean = saved ?: (legacy != "no")

internal fun resolveDarkTheme(mode: Int, systemDark: Boolean): Boolean = when (mode) {
    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES -> true
    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO -> false
    else -> systemDark
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun hiroTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var themeMode by remember(preferences) {
        mutableIntStateOf(preferences.getInt(Preferences.DISPLAY_THEME, -1))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == Preferences.DISPLAY_THEME) themeMode = prefs.getInt(key, -1)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val useDynamicColors by remember { BaresipService.dynamicColors }

    val useActualDynamicColors = useDynamicColors && VERSION.SDK_INT >= 31
    val isDark = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    SideEffect {
        if (BaresipService.darkTheme.value != isDark) BaresipService.darkTheme.value = isDark
    }

    val colorScheme = when {
        useActualDynamicColors ->
            if (isDark)
                dynamicDarkColorScheme(context)
            else
                dynamicLightColorScheme(context)
        isDark -> darkColorScheme()
        else -> lightColorScheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode)
        DisposableEffect(isDark) {
            val activity = context as? ComponentActivity
            if (activity != null) {
                if (VERSION.SDK_INT < 35) {
                    val barStyle = if (isDark)
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    activity.enableEdgeToEdge(
                        statusBarStyle = barStyle,
                        navigationBarStyle = barStyle
                    )
                }
                else {
                    val controller = WindowCompat.getInsetsController(activity.window, view)
                    controller.isAppearanceLightStatusBars = !isDark
                    controller.isAppearanceLightNavigationBars = !isDark
                }
                if (VERSION.SDK_INT >= 29)
                    activity.window.isNavigationBarContrastEnforced = false
            }
            onDispose {}
        }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content
        )
    }
}
