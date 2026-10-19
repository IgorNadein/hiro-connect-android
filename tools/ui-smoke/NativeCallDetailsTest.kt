package ru.hiro.manager

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.navigation.compose.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import java.util.GregorianCalendar

/** Real history-list -> details navigation, without a SIP engine or real calls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeCallDetailsTest {
    @get:Rule val compose = createComposeRule()

    // The known-account fixture needs only its optional metadata, never JNI.
    @Implements(value = Api::class, isInAndroidSdk = false)
    class AccountMetadata {
        @Implementation fun account_extra(account: Long): String {
            check(account == 0L)
            return ""
        }
    }

    @After fun clearHistory() {
        synchronized(BaresipService.callHistory) { BaresipService.callHistory.clear() }
        BaresipService.uas.value = emptyList()
    }

    private fun showHistory(history: CallHistoryNew) {
        synchronized(BaresipService.callHistory) { BaresipService.callHistory.clear() }
        synchronized(BaresipService.callHistory) { BaresipService.callHistory.add(history) }
        BaresipService.darkTheme.value = false
        BaresipService.dynamicColors.value = false
        compose.setContent { AppTheme {
            val nav = rememberNavController()
            val model = remember { ViewModel() }
            NavHost(nav, startDestination = "main") {
                composable("main") { WorkspaceCallsContent(nav, model, PaddingValues()) }
                callDetailsScreenRoute(nav, model)
            }
        } }
        compose.onAllNodesWithText("+79990000001").onFirst().performClick()
        compose.onNodeWithText("Call Details").assertIsDisplayed()
    }

    @Test fun historyFromRemovedSipAccountStillOpens() {
        val history = CallHistoryNew("sip:removed@old.invalid", "sip:+79990000001@old.invalid", "out")
        history.startTime = GregorianCalendar().apply { add(java.util.Calendar.MINUTE, -1) }
        assertNull(Account.ofAor(history.aor))
        showHistory(history)
        compose.onNodeWithText("SIP-подключение этого звонка сейчас недоступно. Запись истории сохранена.").assertIsDisplayed()
    }

    @Test fun missedCallWithoutAccountOrRecordingStillOpens() {
        showHistory(CallHistoryNew("sip:removed@old.invalid", "sip:+79990000001@old.invalid", "in"))
    }

    @Test fun recordedHistoryCanBeOpenedAndClosedWithoutStartingPlayback() {
        val history = CallHistoryNew("sip:removed@old.invalid", "sip:+79990000001@old.invalid", "out")
        history.recording = arrayOf("/synthetic/not-real.wav", "")
        history.startTime = GregorianCalendar().apply { add(java.util.Calendar.MINUTE, -1) }
        showHistory(history)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onAllNodesWithText("+79990000001").onFirst().performClick()
        compose.onNodeWithText("Call Details").assertIsDisplayed()
    }

    @Test @Config(shadows = [AccountMetadata::class])
    fun historyFromExistingAccountStillUsesFriendlyNumber() {
        val aor = "sip:current@old.invalid"
        BaresipService.uas.value = listOf(UserAgent(0, Account(0, aor)))
        val history = CallHistoryNew(aor, "sip:+79990000001@old.invalid", "out")
        showHistory(history)
        compose.onNodeWithText("Peer +79990000001").assertIsDisplayed()
        compose.onNodeWithText("SIP-подключение этого звонка сейчас недоступно. Запись истории сохранена.").assertDoesNotExist()
    }

    @Test fun consumedSelectionSurvivesStateRestoration() {
        val model = ViewModel()
        model.selectCallRow(CallRow("sip:removed@old.invalid", "sip:+79990000001@old.invalid", CALL_MISSED_IN, null, GregorianCalendar(), emptyList()))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { AppTheme {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "call_details") { callDetailsScreenRoute(nav, model) }
        } }
        compose.onNodeWithText("Call Details").assertIsDisplayed()
        assertNull(model.consumeSelectedCallRow())
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Call Details").assertIsDisplayed()
        compose.onNodeWithText("Peer sip:+79990000001@old.invalid").assertIsDisplayed()
    }

    @Test fun missingSelectionOffersRecoveryInsteadOfCrashing() {
        val model = ViewModel()
        compose.setContent { AppTheme {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "call_details") {
                composable("main") { androidx.compose.material3.Text("Журнал") }
                callDetailsScreenRoute(nav, model)
            }
        } }
        compose.onNodeWithText("Вернуться к журналу").assertIsDisplayed().performClick()
        compose.onNodeWithText("Журнал").assertIsDisplayed()
    }
}
