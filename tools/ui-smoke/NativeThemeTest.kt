package ru.hiro.manager

import android.app.Application
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NativeThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun systemThemeRespondsToChangesAndExplicitChoiceIsSaved() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = Preferences(app)
        prefs.displayTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        BaresipService.dynamicColors.value = false
        val configuration = mutableStateOf(Configuration(app.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        })
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration.value) {
                AppTheme { }
            }
        }
        compose.runOnIdle { assertEquals(false, BaresipService.darkTheme.value) }
        compose.runOnIdle {
            configuration.value = Configuration(configuration.value).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
        }
        compose.runOnIdle { assertEquals(true, BaresipService.darkTheme.value) }
        compose.runOnIdle { prefs.displayTheme = AppCompatDelegate.MODE_NIGHT_NO }
        compose.runOnIdle {
            assertEquals(false, BaresipService.darkTheme.value)
            assertEquals(AppCompatDelegate.MODE_NIGHT_NO, prefs.displayTheme)
            assertEquals(AppCompatDelegate.MODE_NIGHT_NO, Preferences(app).displayTheme)
        }
        compose.runOnIdle { prefs.displayTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM }
        compose.runOnIdle { assertEquals(true, BaresipService.darkTheme.value) }
    }
}
