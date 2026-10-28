package ru.hiro.manager

import android.os.Build.VERSION
import android.content.SharedPreferences
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import androidx.compose.ui.graphics.Color
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

internal val LocalHiroDarkTheme = staticCompositionLocalOf { false }

internal fun resolveDynamicColors(saved: Boolean?, legacy: String): Boolean = saved ?: (legacy != "no")

internal fun resolveDarkTheme(mode: Int, systemDark: Boolean): Boolean = when (mode) {
    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES -> true
    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO -> false
    else -> systemDark
}

internal val accentOptions = listOf(
    "default" to "Стандартный", "blue" to "Синий", "red" to "Красный",
    "green" to "Бирюзовый", "yellow" to "Золотой", "mono" to "Серый"
)
internal fun accentSeed(id: String): Color? = when (id) {
    "blue" -> Color(0xFF3B5BDB)
    "red" -> Color(0xFFB3261E)
    "green" -> Color(0xFF00696E)
    "yellow" -> Color(0xFFF4BE48)
    "mono" -> Color(0xFF5E5E5E)
    else -> null
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
    var accent by remember(preferences) { mutableStateOf(preferences.getString(Preferences.ACCENT_COLOR, "default") ?: "default") }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == Preferences.DISPLAY_THEME) themeMode = prefs.getInt(key, -1)
            if (key == Preferences.ACCENT_COLOR) accent = prefs.getString(key, "default") ?: "default"
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

    val seed = accentSeed(accent)
    val colorScheme = when {
        useActualDynamicColors ->
            if (isDark)
                dynamicDarkColorScheme(context)
            else
                dynamicLightColorScheme(context)
        seed != null -> dynamicColorScheme(seedColor = seed, isDark = isDark,
            style = if (accent == "mono") PaletteStyle.Monochrome else PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2025)
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

    CompositionLocalProvider(LocalHiroDarkTheme provides isDark) {
      MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content
        )
      }
    }
}
