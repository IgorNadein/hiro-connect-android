package ru.hiro.manager

import android.app.Application
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The normal one-account manager must retain controls throughout answering. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], qualifiers="w411dp-h891dp-xhdpi", application=Application::class,
    shadows=[NativeCallDetailsTest.AccountMetadata::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeCallControlsTest {
    @get:Rule val compose=createComposeRule()

    @After fun clearCalls() {
        BaresipService.uas.value=emptyList()
        BaresipService.calls.clear()
    }

    @Test fun answeredCallShowsControlsAndHangupWithoutAccountSpinner() {
        val ua=UserAgent(0, Account(0,"sip:operator@test.invalid"))
        var hangups=0
        val call=object:Call(1,ua,"sip:+79990000001@test.invalid","in","incoming") {
            override fun duration()=0
            override fun hangup(code:Int,reason:String) { hangups++ }
        }
        BaresipService.uas.value=listOf(ua)
        BaresipService.calls.add(call)
        BaresipService.darkTheme.value=false
        BaresipService.dynamicColors.value=false
        val model=ViewModel()
        compose.setContent { AppTheme { CallCard(LocalContext.current,model,call,null) } }
        compose.onNodeWithText("Ответить").assertIsDisplayed()
        // Simulate the engine notification, with the old selector-owned flag stale.
        compose.runOnIdle { call.status.value="answered" }
        assertFalse(call.showHangupButton.value)
        compose.onNodeWithText("Завершить разговор").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Ответить").assertDoesNotExist()
        compose.runOnIdle { call.status.value="connected" }
        compose.onNodeWithText("Завершить разговор").assertIsDisplayed().performClick()
        compose.onNodeWithText("Завершить разговор").assertIsNotEnabled()
        assertEquals(1,hangups)
    }
}
