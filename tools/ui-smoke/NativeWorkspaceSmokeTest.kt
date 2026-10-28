package ru.hiro.manager

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h891dp-xhdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeWorkspaceSmokeTest {
    @get:Rule val compose=createComposeRule()
    private fun show(role:String="manager",dark:Boolean=false) {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val auth=HiroAuthViewModel(app)
        val calls=ViewModel()
        val session=HiroSession("https://example.invalid","https://example.invalid/api/v1","unused","unused","2030-01-01T00:00:00Z","smoke","https://example.invalid/refresh","https://example.invalid/logout",HiroUser("smoke","smoke","Тестовый сотрудник",role))
        Preferences(app).displayTheme = if (dark) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        BaresipService.darkTheme.value=dark
        BaresipService.dynamicColors.value=false
        compose.setContent { AppTheme {
            val nav=rememberNavController()
            HiroWorkspaceShell(nav,calls) {
                NavHost(nav,startDestination="workspace-settings") {
                    composable("main") { WorkspacePage("Звонки") {} }
                    workspaceRoutes(nav,calls,auth,session)
                }
            }
        } }
    }
    private fun capture(name:String) {
        val file=File(System.getProperty("hiro.smoke.output"),"$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun managerSettingsSearchAndPrivateContacts() {
        show()
        compose.onNodeWithText("Звонки").assertIsDisplayed()
        compose.onNodeWithText("Сообщения").assertIsDisplayed()
        compose.onNodeWithText("Клиенты").assertIsDisplayed()
        capture("settings-manager-light")
        compose.onNodeWithText("Найти настройку").performTextInput("контакты")
        compose.onNodeWithText("Контакты и синхронизация").assertIsDisplayed().performClick()
        compose.onNodeWithText("Ваша личная адресная книга").assertIsDisplayed()
        compose.onNodeWithText("Имена и номера сохраняются",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Разрешить синхронизацию").assertIsDisplayed()
        capture("personal-sync-light")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Контакты и синхронизация").assertIsDisplayed()
        compose.onNodeWithText("Ваша личная адресная книга").assertDoesNotExist()
    }
    @Test fun appearancePersistsSystemColorsChoice() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Preferences(app).dynamicColors = null
        show()
        compose.onNodeWithText("Найти настройку").performTextInput("оформление")
        compose.onNodeWithText("Оформление").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Цвета системы").assertIsOff().performClick()
        compose.onNodeWithContentDescription("Цвета системы").assertIsOn()
        org.junit.Assert.assertEquals(true, Preferences(app).dynamicColors)
        compose.onNodeWithContentDescription("Цвета системы").performClick()
        org.junit.Assert.assertEquals(false, Preferences(app).dynamicColors)
        Preferences(app).dynamicColors = null
    }

    @Test fun adminOrganizationAppearsInSearchAndDarkTheme() {
        show(role="admin",dark=true)
        compose.onNodeWithText("Найти настройку").performTextInput("организации")
        compose.onNodeWithText("Настройки организации").assertIsDisplayed()
        capture("settings-admin-dark")
    }
    @Test fun managerCannotSeeAdminOrganization() {
        show()
        compose.onNodeWithText("Найти настройку").performTextInput("организации")
        compose.onNodeWithText("Настройки организации").assertDoesNotExist()
        compose.onNodeWithText("Настройка не найдена.",substring=true).assertIsDisplayed()
    }
    @Test @Config(qualifiers="w320dp-h740dp-mdpi") fun narrowPhoneContactsHeader() {
        show()
        compose.onNodeWithText("Найти настройку").performTextInput("контакты")
        compose.onNodeWithText("Контакты и синхронизация").performClick()
        compose.onNodeWithText("Ваша личная адресная книга").assertIsDisplayed()
        capture("personal-sync-narrow")
    }
}
