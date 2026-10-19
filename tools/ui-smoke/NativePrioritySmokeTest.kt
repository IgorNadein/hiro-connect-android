package ru.hiro.manager

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.*
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h891dp-xhdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativePrioritySmokeTest {
    @get:Rule val compose=createComposeRule()
    private class Fake:ReceptionRepository {
        var preferences=HiroReceptionPreferences()
        var saves=0
        var previews=0
        var conflict=false
        val lines=listOf(HiroVoiceLine("office","Офис · NOVOFON","1","novofon","office",enabled=true),HiroVoiceLine("phone","Телефон офиса","2","mobile","phone",enabled=true))
        override suspend fun load()=lines to preferences
        override suspend fun save(rules:List<HiroReceptionRule>,revision:Long):HiroReceptionPreferences {
            if(conflict)throw HiroServerException("revision_conflict","Настройки изменены на другом устройстве")
            check(revision==preferences.revision)
            saves++;preferences=preferences.copy(revision=revision+1,rules=rules);return preferences
        }
        override suspend fun preview(line:String,caller:String):HiroReceptionPreview {previews++;return HiroReceptionPreview(HiroReceptionEffective(42),"own_preference_only",false,false)}
    }
    private fun show(fake:Fake=Fake(),dark:Boolean=false,fontScale:Float=1f):Fake {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val auth=HiroAuthViewModel(app)
        BaresipService.darkTheme.value=dark;BaresipService.dynamicColors.value=false
        compose.setContent{CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,fontScale)){AppTheme{
            val nav=rememberNavController()
            NavHost(nav,startDestination="priority") {
                composable("priority"){ReceptionPriorityScreen(nav,auth,repository=fake)}
            }
        }}}
        compose.waitForIdle();return fake
    }
    private fun click(text:String){compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToNode(hasText(text));compose.onNodeWithText(text).performClick();compose.waitForIdle()}
    private fun capture(name:String){
        compose.waitForIdle()
        val h=compose.onAllNodes(hasText("Мой приоритет") or hasText("Мой приоритет звонков")).fetchSemanticsNodes().map{it.boundsInRoot.toString()}
        println("HEADER $name $h ROOT "+compose.onRoot().fetchSemanticsNode().boundsInRoot)
        val f=File(System.getProperty("hiro.smoke.output"),"$name.png");f.parentFile?.mkdirs();f.outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun savePreferenceWithoutDisablingOthers() {
        val fake=show()
        compose.onNodeWithText("Пока нет правил").assertIsDisplayed()
        capture("priority-empty-light")
        click("Добавить приоритет")
        click("Со шлюзов одного типа")
        compose.onNodeWithText("0 · наравне").assertExists()
        capture("priority-edit-light")
        click("Далее: что изменится")
        click("Сохранить правило на сервере")
        assertEquals(1,fake.saves);assertEquals("novofon",fake.preferences.rules.single().gatewayType)
        assertFalse(fake.preferences.appliedToLiveCalls)
        compose.onNodeWithText("Правило сохранено на сервере.",substring=true).assertIsDisplayed()
    }
    @Test fun unknownVoiceSIMCannotBeChosen() {
        show();click("Добавить приоритет")
        compose.onNodeWithText("С определённой SIM").performScrollTo().assertIsNotEnabled()
    }
    @Test fun serverPreviewNotAFabricatedTeamSimulation() {
        val fake=show();click("Проверить моё правило");click("Рассчитать на сервере")
        compose.onNodeWithText("Мой приоритет: 42").performScrollTo().assertIsDisplayed()
        assertEquals(1,fake.previews);assertEquals(0,fake.saves)
        capture("priority-server-preview-light")
    }
    @Test fun unsavedBackKeepsTheDraftAndDoesNotWrite() {
        val fake=show();click("Добавить приоритет");click("Со всех доступных линий")
        compose.onNodeWithContentDescription("Мой приоритет · 0–100").performScrollTo().performTextReplacement("73")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Есть несохранённое правило").assertIsDisplayed()
        compose.onNodeWithText("Продолжить редактирование").performClick()
        compose.onNodeWithContentDescription("Мой приоритет · 0–100").assertTextEquals("73")
        assertEquals(0,fake.saves)
    }
    @Test fun conflictReloadPreservesDraftAndOtherRules() {
        val fake=Fake();fake.preferences=fake.preferences.copy(rules=listOf(HiroReceptionRule(gatewayType="mobile",priority=50)));fake.conflict=true
        show(fake);click("Добавить приоритет");click("Со всех доступных линий");click("Далее: что изменится");click("Сохранить правило на сервере")
        compose.onNodeWithText("Настройки изменены на другом устройстве").performScrollTo().assertIsDisplayed()
        click("Обновить список, сохранив черновик")
        fake.conflict=false
        click("Сохранить правило на сервере")
        assertEquals(2,fake.preferences.rules.size);assertTrue(fake.preferences.rules.any{it.gatewayType=="mobile"&&it.priority==50})
    }
    @Test @Config(qualifiers="w320dp-h740dp-mdpi") fun narrowDarkEditorAndMultilineHeader() {
        show(dark=true);click("Добавить приоритет");click("Со всех доступных линий")
        compose.onNodeWithText("Мой приоритет").assertIsDisplayed()
        compose.onNodeWithText("Шаг 2 из 3 · только входящие").assertIsDisplayed()
        compose.onNodeWithContentDescription("Мой приоритет · 0–100").performScrollTo().assertIsDisplayed()
        capture("priority-editor-narrow-dark")
    }
    @Test @Config(qualifiers="w320dp-h740dp-mdpi") fun largeTextHeaderDoesNotClip() {
        show(fontScale=1.5f)
        val title=compose.onNodeWithText("Мой приоритет звонков").fetchSemanticsNode().boundsInRoot
        assertTrue(title.top>=0);assertTrue(title.right<=320)
        capture("priority-large-text-light")
        click("Добавить приоритет");click("Со всех доступных линий")
        compose.onNodeWithText("Шаг 2 из 3 · только входящие").assertIsDisplayed()
    }
}
