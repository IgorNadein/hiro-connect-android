package ru.hiro.manager

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class NativeSharedCallsTest {
    @get:Rule val compose=createComposeRule()
    private val first=SharedCall("a".repeat(32),"+79990000001","Анна Смирнова","inbound","answered","2026-10-20T10:20:00Z",duration=124,talk=110,line="Рабочий номер",gatewayType="mobile",operators=listOf("Мария Волкова"),recording=true,source="pbx")
    private inner class FakeRepository(val role:String="manager"):SharedCallRepository {
        var downloads=0;var joins=0;var fail=false;var conflict=false;var saved=""
        var current=first
        val capabilities get()=CallCapabilities(role!="viewer",role!="viewer",role!="viewer")
        override suspend fun history(query:String,missed:Boolean,before:String):SharedCallPage {
            if(fail)throw HiroServerException("network","Сервер недоступен")
            return SharedCallPage(if(before.isBlank())listOf(first)else listOf(first.copy(id="b".repeat(32),name="Борис Волков",number="+79990000002")),if(before.isBlank())first.id else "",capabilities)
        }
        override suspend fun live()=LiveTeamCalls(listOf(LiveTeamCall(first.id,first.number,state="active",duration=42,operators=first.operators)),ready=true,capabilities=capabilities)
        override suspend fun detail(id:String)=SharedCallDetail(current,capabilities)
        override suspend fun note(id:String,text:String,revision:Long):SharedCallDetail {if(conflict)throw HiroServerException("note_conflict","Conflict");saved=text;current=current.copy(note=text,noteRevision=revision+1);return detail(id)}
        override suspend fun recording(id:String,destination:File){downloads++;throw HiroServerException("test","No real audio in tests")}
        override suspend fun join(id:String){joins++}
    }
    private fun show(repo:FakeRepository,details:Boolean=false) {
        ApplicationProvider.getApplicationContext<Application>()
        BaresipService.darkTheme.value=false;BaresipService.dynamicColors.value=false
        compose.setContent{AppTheme{
            var selected by remember{mutableStateOf(if(details)first.id else "")}
            if(selected.isEmpty())SharedCallsScreen(repo,onOpen={selected=it},onDial={},onLocal={})
            else SharedCallDetails(repo,selected,onBack={selected=""},onCall={},onSMS={})
        }}
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }
    private fun scroll(text:String){compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text,substring=true))}
    private fun capture(name:String){
        val target=File(System.getProperty("hiro.smoke.output"),"$name.png");target.parentFile?.mkdirs()
        target.outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun historyOpensSharedCallAndDoesNotAutoplay(){
        val repo=FakeRepository();show(repo)
        scroll("Анна Смирнова")
        compose.onNodeWithText("Анна Смирнова").assertIsDisplayed().performClick()
        compose.onNodeWithText("Карточка звонка").assertIsDisplayed()
        compose.onNodeWithText("Рабочий номер",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Кто привёл",substring=true).assertDoesNotExist()
        assertEquals(0,repo.downloads);capture("shared-call-details")
    }
    @Test fun historyLoadsAnotherServerPage(){
        val repo=FakeRepository();show(repo)
        scroll("Показать ещё");compose.onNodeWithText("Показать ещё").performClick()
        scroll("Борис Волков");compose.onNodeWithText("Борис Волков").assertIsDisplayed()
        assertEquals(0,repo.downloads);capture("shared-call-history")
    }
    @Test fun liveCallsRequireExplicitJoinConfirmation(){
        val repo=FakeRepository();show(repo)
        compose.onNodeWithText("Сейчас").performClick()
        compose.onNodeWithText("Участвовать в разговоре").performScrollTo().performClick()
        assertEquals(0,repo.joins)
        compose.onNodeWithText("Запросить подключение").performClick()
        compose.onNodeWithText("Запрос отправлен.",substring=true).assertIsDisplayed()
        assertEquals(1,repo.joins);capture("team-calls")
    }
    @Test fun viewerCannotPlayAudioOrEditNotes(){
        val repo=FakeRepository("viewer");show(repo,details=true)
        compose.onNodeWithText("Прослушивание доступно",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Слушать").assertDoesNotExist()
        compose.onNodeWithText("Сохранить заметку").assertDoesNotExist()
        assertEquals(0,repo.downloads)
    }
    @Test fun noteConflictKeepsDraft(){
        val repo=FakeRepository();repo.conflict=true;show(repo,details=true)
        scroll("Итоги и следующий шаг")
        compose.onNodeWithText("Итоги и следующий шаг").performTextInput("Перезвонить завтра")
        compose.onNodeWithText("Сохранить заметку").performScrollTo().performClick()
        compose.onNodeWithText("Перезвонить завтра").assertExists()
        scroll("Другой менеджер изменил заметку.")
        compose.onNodeWithText("Другой менеджер изменил заметку.",substring=true).assertExists()
        assertEquals("",repo.saved)
    }
    @Test fun unavailableServerDoesNotLookLikeEmptyHistory(){
        val repo=FakeRepository();repo.fail=true;show(repo)
        compose.onNodeWithText("Сервер недоступен").assertIsDisplayed()
        compose.onNodeWithText("Звонков не найдено").assertDoesNotExist()
        repo.fail=false
        compose.onNodeWithText("Повторить загрузку").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600);compose.waitForIdle()
        scroll("Анна Смирнова")
        compose.onNodeWithText("Анна Смирнова").assertIsDisplayed()
    }
}
