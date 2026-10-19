package ru.hiro.manager

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the actual native presentation components with synthetic data.
 * No permission prompts, API calls, real contacts, audio or SIP engine. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h891dp-xhdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeVisualSystemTest {
    @get:Rule val compose=createComposeRule()
    private fun show(dark:Boolean=false,scale:Float=1f,content:@Composable ()->Unit) {
        BaresipService.darkTheme.value=dark;BaresipService.dynamicColors.value=false
        compose.setContent{CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,scale)){AppTheme{content()}}}
        compose.waitForIdle()
    }
    @Composable private fun shell(route:String,title:String,content:@Composable (PaddingValues)->Unit) {
        val nav=rememberNavController();val calls=remember{ViewModel()}
        HiroWorkspaceShell(nav,calls){NavHost(nav,startDestination=route){workspaceTabs.forEach{tab->composable(tab.route){Scaffold(topBar={WorkspaceHeader(title,"Тестовый сотрудник")}){p->content(p)}}}}}
    }
    private fun capture(name:String){
        compose.waitForIdle();compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        val file=File(System.getProperty("hiro.smoke.output"),"visual-$name.png");file.parentFile?.mkdirs()
        file.outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun callsHaveWorkingMissedFilterAndSafeNavigation(){
        var query by mutableStateOf("");var missed by mutableStateOf(false);var opened=""
        val rows=listOf(HiroCallLogItem("anna","Анна Смирнова","+79990000001","10:24",true,true),HiroCallLogItem("boris","Борис Волков","+79990000002","Вчера",false,false))
        show{shell("main","Звонки"){p->HiroCallsFeed(p,true,rows,query,missed,{query=it},{missed=it},{},{},{opened=it})}}
        capture("calls-light")
        compose.onNodeWithText("Пропущенные").performClick()
        compose.onNodeWithText("Борис Волков").assertDoesNotExist()
        compose.onNodeWithText("Анна Смирнова").performClick();assertEquals("anna",opened)
    }
    @Test fun directoryMakesPrivateAndSharedScopesClear(){
        var personal by mutableStateOf(false);var query by mutableStateOf("");var opened="";var adds=0
        show{shell("client-routing","Клиенты"){p->HiroDirectoryList(p,
            if(personal)listOf(HiroDirectoryPerson("+79990000003","Личный контакт","Только вам")) else listOf(HiroDirectoryPerson("+79990000001","Анна Смирнова","Кто привёл: Мария Волкова"),HiroDirectoryPerson("+79990000002","Борис Волков","Кто привёл: не указан")),
            personal,query,true,false,true,false,false,"",{personal=it},{query=it},{},{opened=it},{adds++},{},{},{})}}
        capture("clients-light")
        compose.onNodeWithText("Мои контакты").performClick()
        compose.onNodeWithText("Анна Смирнова").assertDoesNotExist()
        compose.onNodeWithText("Личный контакт").assertIsDisplayed().performClick();assertEquals("+79990000003",opened)
        compose.onNodeWithText("Добавить").performClick();assertEquals(1,adds)
    }
    @Test fun messagesSearchAndComposeRequireExplicitRecipient(){
        var query by mutableStateOf("");var opened=""
        val rows=listOf(GatewaySmsItem("1","+79990000001","Документы готовы, спасибо!",1730000000000,"inbound","получено","Рабочая SIM"),GatewaySmsItem("2","+79990000002","Удобно созвониться завтра?",1729900000000,"outbound","доставлено","Рабочая SIM"))
        show{shell("gateway-messages","Сообщения"){p->ConversationList(p,rows,query,{query=it},null,{opened=it})}}
        capture("messages-light")
        compose.onNodeWithText("Новое сообщение").performClick()
        compose.onNodeWithText("Начать диалог").assertIsNotEnabled()
        compose.onNodeWithText("Номер телефона").performTextInput("+79990000003")
        compose.onNodeWithText("Начать диалог").assertIsEnabled().performClick();assertEquals("+79990000003",opened)
    }
    @Test fun keypadButtonsAndBackspaceActuallyEditTheNumber(){
        var number by mutableStateOf("")
        var line by mutableStateOf("")
        var called = 0
        assertEquals("main",workspaceTabFor("workspace-dialer?number={number}"))
        show{
            val nav=rememberNavController();val calls=remember{ViewModel()}
            HiroWorkspaceShell(nav,calls){NavHost(nav,startDestination="workspace-dialer"){
                composable("workspace-dialer"){HiroDialer(number,{number=it},listOf("" to "Автоматически по правилам", "office" to "Офисная линия"),line,{line=it},emptyList(),"",{},number=="600","",{called++},{})}
            }}
        }
        compose.onNodeWithText("Сообщения").assertDoesNotExist()
        compose.onNodeWithText("Позвонить").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Цифра 6").performClick();compose.onNodeWithContentDescription("Цифра 0").performClick();compose.onNodeWithContentDescription("Цифра 0").performClick();assertEquals("600",number)
        capture("dialer-light")
        compose.onNodeWithText("Автоматически по правилам").performClick()
        compose.onNodeWithText("Офисная линия").performClick();assertEquals("office",line)
        assertEquals(0,called)
        compose.onNodeWithText("Позвонить").assertIsEnabled().performClick();assertEquals(1,called)
        compose.onNodeWithContentDescription("Удалить последнюю цифру").performClick();assertEquals("60",number)
    }
    @Test fun conversationControlsShowTheirActualState(){
        var muted by mutableStateOf(false);var speaker by mutableStateOf(false);var held by mutableStateOf(false)
        show{Scaffold(topBar={WorkspaceHeader("Текущий вызов","Тестовый сотрудник")}){p->Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)){
            HiroCallIdentity("Анна Смирнова","+79990000001","Идёт разговор",true);Text("02:34",fontSize=42.sp)
            HiroConversationPad(muted,speaker,held,true,true,{muted=!muted},{speaker=!speaker},{held=!held},{})
            TextButton(onClick={}){Text("Перевод и сведения о вызове")}
            Button(onClick={},modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("Завершить разговор")}
        }}}
        capture("conversation-light")
        compose.onNodeWithText("Выключить микрофон").performClick();compose.onNodeWithText("Включить микрофон").assertIsDisplayed();assertTrue(muted)
        compose.onNodeWithText("Удержать").performClick();compose.onNodeWithText("Продолжить").assertIsDisplayed();assertTrue(held)
    }
    @Test fun loginDoesNotConnectUntilUserActs(){
        var server=""
        show{HiroLoginScreen("https://example.invalid",null,false,null,{server=it},{_,_,_->},{})}
        capture("login-light");assertEquals("",server)
        compose.onNodeWithText("Продолжить").performScrollTo().performClick();assertEquals("https://example.invalid",server)
    }
    @Test @Config(qualifiers="w320dp-h740dp-mdpi") fun narrowDarkDirectoryUsesReadableWrappedRows(){
        show(dark=true,scale=1.5f){shell("client-routing","Клиенты"){p->HiroDirectoryList(p,listOf(HiroDirectoryPerson("+79990000001","Очень длинное имя клиента организации","Кто привёл: другой сотрудник")),false,"",true,false,true,false,false,"",{},{},{},{},{},{},{},{})}}
        compose.onNodeWithText("Клиенты команды").assertIsDisplayed()
        compose.onNodeWithContentDescription("Настройки").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Клиенты").performClick().assertIsSelected()
        capture("clients-narrow-dark")
    }
    @Test @Config(qualifiers="w320dp-h740dp-mdpi") fun narrowDarkLoginStillAllowsSSOWithLargeText(){
        show(dark=true,scale=1.5f){HiroLoginScreen("https://example.invalid",null,false,HiroLoginOptions(false,true),{},{_,_,_->},{})}
        compose.onNodeWithText("Войти через SSO").performScrollTo().assertIsDisplayed();capture("login-narrow-dark")
    }
    @Test @Config(qualifiers="w320dp-h740dp-mdpi") fun narrowDialerKeepsCallActionVisible(){
        show(dark=true,scale=1.5f){HiroDialer("600",{},listOf("" to "Автоматически по правилам"),"",{},emptyList(),"",{},true,"",{},{})}
        compose.onNodeWithText("Позвонить").assertIsDisplayed()
        compose.onNodeWithContentDescription("Цифра #").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Позвонить").assertIsDisplayed();capture("dialer-narrow-dark")
    }
    @Test @Config(qualifiers="w320dp-h740dp-mdpi") fun narrowConversationControlsRemainReachable(){
        show(dark=true,scale=1.5f){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
            HiroCallIdentity("Анна Смирнова","+79990000001","Идёт разговор",true)
            HiroConversationPad(false,false,false,true,true,{},{},{},{})
            Button(onClick={}){Text("Завершить разговор")}
        }}
        compose.onNodeWithText("Выключить микрофон").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Завершить разговор").performScrollTo().assertIsDisplayed();capture("conversation-narrow-dark")
    }
    @Test fun audioPreferencesKeepTechnicalControlsSeparate(){
        val model=AudioViewModel().apply{callVolume.value=6;micGain.value="1.0";audioDelay.value="100"}
        show{Scaffold(topBar={WorkspaceHeader("Гарнитура и звук","Только этот смартфон",onBack={})}){padding->AudioContent(model,padding)}}
        compose.onNodeWithText("РАЗГОВОР").assertIsDisplayed()
        compose.onNodeWithText("Задержка, модули обработки и параметры кодеков. Меняйте их, только если знаете, что требуется вашему подключению.").assertDoesNotExist()
        capture("audio-light")
        compose.onNodeWithText("Дополнительные аудиопараметры").performScrollTo().performClick()
        compose.onNodeWithText("Скрыть технические параметры").assertIsDisplayed()
    }
}
