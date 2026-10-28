package ru.hiro.manager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import java.text.DateFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class WorkspaceTab(val route: String, val label: String)
internal val workspaceTabs = listOf(WorkspaceTab("main", "Звонки"), WorkspaceTab("gateway-messages", "Сообщения"), WorkspaceTab("client-routing", "Клиенты"), WorkspaceTab("workspace-settings", "Настройки"))
internal fun workspaceTabFor(route: String?): String = when {
    route?.startsWith("workspace-call") == true || route in setOf("workspace-local-calls","workspace-team-calls","workspace-employees") -> "main"
    route?.startsWith("workspace-dialer") == true -> "main"
    route?.startsWith("client-routing") == true -> "client-routing"
    route?.startsWith("gateway-messages") == true || route?.startsWith("chat") == true -> "gateway-messages"
    route?.startsWith("workspace-") == true || route in setOf("settings", "audio", "accounts", "about") -> "workspace-settings"
    else -> "main"
}

internal class WorkspaceNavigation(val nav: NavController) {
    var dirty by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var pending by mutableStateOf<String?>(null)
    fun go(route: String) {
        if (busy) return
        if (dirty) { pending = route; return }
        nav.navigate(route) { launchSingleTop = true; popUpTo("main") { saveState = false } }
    }
}
internal val LocalWorkspaceNavigation = staticCompositionLocalOf<WorkspaceNavigation?> { null }

@Composable
internal fun HiroWorkspaceShell(nav: NavController, calls: ViewModel, content: @Composable () -> Unit) {
    val navigation = remember(nav) { WorkspaceNavigation(nav) }
    val entry by nav.currentBackStackEntryAsState()
    val live by calls.calls.collectAsState()
    val route = entry?.destination?.route
    val rootRoutes = workspaceTabs.map { it.route }
    val context = LocalContext.current
    remember { GatewayMessageSyncStore(context) }
    val unread by GatewayMessageSyncStore.unreadCount.collectAsState()
    // Avoid broken words or shrinking accessibility text in a four-column bar.
    // Page titles remain visible, and every icon retains its full TalkBack label.
    val iconOnlyNavigation = LocalConfiguration.current.screenWidthDp / LocalDensity.current.fontScale < 300f
    CompositionLocalProvider(LocalWorkspaceNavigation provides navigation) {
        Scaffold(contentWindowInsets = WindowInsets(0,0,0,0), bottomBar = {
            if ((route?.substringBefore('?') in rootRoutes || route?.startsWith("workspace-")==true) && route?.startsWith("workspace-dialer")!=true && !(route == "main" && live.isNotEmpty())) Column {
                if (live.isNotEmpty()) TextButton(onClick = { navigation.go("main") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Phone, null); Spacer(Modifier.width(8.dp)); Text("Вернуться к разговору")
                }
                Surface(color=MaterialTheme.colorScheme.surfaceContainerLowest) {
                  Column(Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant,thickness=0.5.dp)
                    Row(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=8.dp)) {
                    workspaceTabs.forEachIndexed { index, tab ->
                        val selected=workspaceTabFor(route)==tab.route
                        val tint=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Column(Modifier.weight(1f).heightIn(min=52.dp).selectable(selected=selected,enabled=!navigation.busy,role=Role.Tab,onClick={navigation.go(tab.route)}).semantics(mergeDescendants=true){if(iconOnlyNavigation)contentDescription=tab.label}.padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(46.dp,28.dp).then(if(selected)Modifier.background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(12.dp))else Modifier),contentAlignment=Alignment.Center) {
                                BadgedBox(badge={if(index==1&&unread>0)Badge{Text(if(unread>99)"99+" else "$unread")}}) {
                                    Icon(listOf(Icons.Outlined.Phone,Icons.AutoMirrored.Outlined.Chat,Icons.Outlined.People,Icons.Outlined.Settings)[index],null,tint=tint,modifier=Modifier.size(22.dp))
                                }
                            }
                            if(!iconOnlyNavigation)Text(tab.label,style=MaterialTheme.typography.labelSmall,color=tint,maxLines=1,textAlign=TextAlign.Center)
                        }
                    }
                    }
                  }
                }
            }
        }) { padding -> Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() } }
        navigation.pending?.let { destination -> AlertDialog(onDismissRequest = { navigation.pending = null },
            title = { Text("Есть несохранённые изменения") }, text = { Text("Переход не сохранит черновик. Данные и настройки на сервере не изменятся.") },
            confirmButton = { TextButton(onClick = { navigation.dirty = false; navigation.pending = null; navigation.go(destination) }) { Text("Не сохранять и перейти") } },
            dismissButton = { TextButton(onClick = { navigation.pending = null }) { Text("Остаться") } }) }
    }
}

@Composable
internal fun WorkspacePage(title: String, subtitle: String = "", nav: NavController? = null, action: (@Composable () -> Unit)? = null, content: LazyListScope.() -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        WorkspaceHeader(title,subtitle,onBack=nav?.let{controller->{controller.navigateUp()}},actions={action?.invoke()})
    }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content) }
}

/** Content-driven height: long titles and Android font scaling must not clip. */
@Composable
internal fun WorkspaceHeader(title:String,subtitle:String="",onBack:(()->Unit)?=null,statusInset:Boolean=true,actions:@Composable RowScope.()->Unit={}) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(Modifier.fillMaxWidth().then(if(statusInset)Modifier.statusBarsPadding()else Modifier).padding(start=if(onBack==null)21.dp else 9.dp,end=9.dp,top=18.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically) {
            if(onBack!=null)IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Назад",modifier=Modifier.size(20.dp))}
            Column(Modifier.weight(1f).padding(end=8.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(title,style=if(onBack==null)MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge.copy(fontSize=22.sp,lineHeight=28.sp))
                if(subtitle.isNotEmpty())Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            actions()
        }
    }
}

@Composable
internal fun WorkspaceSearch(value:String,onValueChange:(String)->Unit,label:String) {
    OutlinedTextField(value,onValueChange,placeholder={Text(label)},leadingIcon={Icon(Icons.Default.Search,null,modifier=Modifier.size(20.dp))},singleLine=true,
        textStyle=MaterialTheme.typography.bodyLarge,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth(),colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant,unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLowest,focusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLowest))
}

@Composable
internal fun WorkspaceGroup(content:@Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(17.dp),color=MaterialTheme.colorScheme.surfaceContainerLowest) { Column(content=content) }
}

@Composable
internal fun WorkspaceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
@Composable
internal fun WorkspaceInfo(text: String, error: Boolean = false) {
    val(background,foreground)=hiroTone(if(error)HiroTone.Error else HiroTone.Brand)
    Surface(color=background,shape=RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.Top) {
            Icon(if(error)Icons.Outlined.ErrorOutline else Icons.Outlined.Info,null,Modifier.size(17.dp),tint=foreground)
            Text(text,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=foreground)
        }
    }
}
@Composable
internal fun WorkspaceRow(title: String, subtitle: String, icon: ImageVector = Icons.Default.ChevronRight, enabled: Boolean = true, grouped:Boolean=false, click: () -> Unit) {
    Surface(shape = RoundedCornerShape(if(grouped)0.dp else 17.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = click).heightIn(min=67.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(11.dp),modifier=Modifier.size(36.dp)) {
                Box(contentAlignment=Alignment.Center){Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(19.dp))}
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); if(subtitle.isNotBlank())Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Default.ChevronRight, null,tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun WorkspaceCallsContent(nav: NavController, calls: ViewModel, padding: PaddingValues) {
    val agents by BaresipService.uas
    val live by calls.calls.collectAsState()
    var journal by remember { mutableStateOf<List<CallHistoryNew>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var missed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(live) { while(true) { journal = synchronized(BaresipService.callHistory) { BaresipService.callHistory.toList().asReversed() }; delay(1500) } }
    val rows=journal.take(100).mapIndexed{i,call->HiroCallLogItem(i.toString(),Contact.findContact(call.peerUri)?.name()?:call.peerUri.removePrefix("sip:").substringBefore('@'),call.peerUri.removePrefix("sip:").substringBefore('@'),
        hiroListTime(call.stopTime.timeInMillis),call.direction=="in",call.direction=="in"&&call.startTime==null)}
    HiroCallsFeed(padding,agents.any{it.status==BaresipService.circleGreen.getValue(BaresipService.colorblind)},rows,query,missed,{query=it},{missed=it},
        onDial={nav.navigate("workspace-dialer")},onEmployees={nav.navigate("workspace-employees")},onHelp={nav.navigate("workspace-help")},onOpen={key->
            val call=journal.getOrNull(key.toIntOrNull()?:-1)?:return@HiroCallsFeed
            val direction=if(call.direction=="in"){if(call.startTime==null)CALL_MISSED_IN else CALL_DOWN_GREEN}else{if(call.startTime==null)CALL_MISSED_OUT else CALL_UP_GREEN}
            calls.selectCallRow(CallRow(call.aor,call.peerUri,direction,call.startTime,call.stopTime,call.recording.toList()));nav.navigate("call_details")
        })
}

fun NavGraphBuilder.workspaceRoutes(nav: NavController, calls: ViewModel, auth: HiroAuthViewModel, session: HiroSession) {
    composable("workspace-employees") { WorkspaceEmployees(nav,auth,session) }
    composable("workspace-team-calls") {
        val repository=remember(auth,session.sessionId){AuthenticatedCallRepository(auth)}
        SharedCallsScreen(repository,initialTab=1,onOpen={nav.navigate("workspace-call/$it")},onDial={nav.navigate("workspace-dialer")},onLocal={nav.navigate("workspace-local-calls")},onBack={nav.navigateUp()})
    }
    composable("workspace-call/{id}",arguments=listOf(navArgument("id"){type=NavType.StringType})) { entry ->
        val repository=remember(auth,session.sessionId){AuthenticatedCallRepository(auth)}
        SharedCallDetails(repository,entry.arguments?.getString("id").orEmpty(),onBack={nav.navigateUp()},
            onCall={nav.navigate("workspace-dialer?number=${Uri.encode(it)}")},onSMS={nav.navigate("gateway-messages?address=${Uri.encode(it)}")})
    }
    composable("workspace-local-calls") {
        Scaffold(topBar={WorkspaceHeader("На этом смартфоне","Личная история, не общий журнал",onBack={nav.navigateUp()})}){padding->WorkspaceCallsContent(nav,calls,padding)}
    }
    composable("workspace-settings") { WorkspaceSettings(nav, session) }
    composable("workspace-receive") { WorkspacePage("Какие SMS получаю","Приём сообщений — отдельно от приоритета звонков",nav) { item { GatewaySIMPreferences(auth) } } }
    composable("workspace-priority?caller={caller}",arguments=listOf(navArgument("caller"){type=NavType.StringType;defaultValue=""})) { entry -> ReceptionPriorityScreen(nav,auth,entry.arguments?.getString("caller").orEmpty()) }
    composable("workspace-sync") { PersonalContactSettings(nav,auth) }
    composable("workspace-profile") { WorkspaceProfile(nav,calls,auth,session) }
    composable("workspace-appearance") { WorkspaceAppearance(nav) }
    composable("workspace-notifications") { WorkspaceNotifications(nav) }
    composable("workspace-help") { WorkspaceHelp(nav) }
    composable("workspace-organization") { WorkspaceOrganization(nav,auth,session.user) }
    composable("workspace-policies") { WorkspacePolicies(nav,auth,session.user) }
    composable("workspace-outgoing") { WorkspacePage("Исходящие звонки и SMS","Источник выбирается до отправки",nav) {
        item { WorkspaceRow("Линия следующего звонка","Явный выбор выше автоматических правил",Icons.Default.Phone) { nav.navigate("workspace-dialer") } }
        item { WorkspaceRow("SIM исходящего SMS","Выбирается отдельно в диалоге",Icons.Default.Sms) { nav.navigate("gateway-messages") } }
        item { WorkspaceInfo("Постоянное предпочтение линии пока не поддерживается приложением. Выбор в номеронабирателе действует только для следующего звонка.") }
    } }
    composable("workspace-dialer?number={number}",arguments=listOf(navArgument("number"){type=NavType.StringType;defaultValue=""})) { entry -> WorkspaceDialer(nav,calls,auth,entry.arguments?.getString("number").orEmpty()) }
}

@Composable
private fun WorkspaceSettings(nav: NavController, session: HiroSession) {
    var query by rememberSaveable { mutableStateOf("") }
    data class Setting(val section: String,val name: String,val detail: String,val route: String,val icon: ImageVector,val keywords: String="")
    val rows = listOf(
        Setting("Связь","Мой приоритет звонков","Получать раньше — без отказа от других линий","workspace-priority",Icons.Default.PhoneCallback,"номер симка SIM входящие первым правило"),
        Setting("Связь","Какие SMS получаю","Номера и SIM для входящих сообщений","workspace-receive",Icons.Default.Sms,"номер симка SIM сообщения"),
        Setting("Связь","Исходящие звонки и SMS","Линия звонка и SIM сообщения","workspace-outgoing",Icons.Default.PhoneForwarded,"номер симка SIM исходящие"),
        Setting("Связь","Гарнитура и звук","Громкость, микрофон и аудиопараметры","audio",Icons.Default.Headset,"не слышно наушники звук"),
        Setting("Связь","Уведомления и работа в фоне","Звук уведомлений и разрешения Android","workspace-notifications",Icons.Default.Notifications,"батарея фон разрешения"),
        Setting("Мои данные","Контакты и синхронизация","Личная адресная книга, не общий список","workspace-sync",Icons.Default.Contacts,"клиенты контакты адреса приватность"),
        Setting("Приложение","Оформление","Системная, светлая или тёмная тема","workspace-appearance",Icons.Default.Palette),
        Setting("Приложение","Учётная запись и безопасность","${session.user.displayName} · ${session.user.role}","workspace-profile",Icons.Default.Security,"пароль вход выход сервер"),
        Setting("Для всей организации","Настройки организации","Рабочие номера и общие правила","workspace-organization",Icons.Default.Business,"манго mango novofon новофон шлюз приоритет очередь"),
        Setting("Помощь","Помощь и проверка связи","Диагностика, разрешения и обновления","workspace-help",Icons.Default.HelpOutline,"не работает версия обновление")
    ).filter { (session.user.role == "admin" || it.section != "Для всей организации") && (it.name+it.detail+it.keywords).contains(query,true) }
    WorkspacePage("Настройки","Личные параметры и организация") {
        if(query.isBlank())item{Surface(onClick={nav.navigate("workspace-profile")},shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainerLowest){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            HiroAvatar(session.user.displayName,46.dp)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(session.user.displayName,style=MaterialTheme.typography.titleMedium);Text(if(session.user.role=="admin")"Администратор организации" else if(session.user.role=="manager")"Рабочее место менеджера" else "Просмотр данных",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(20.dp))
        }}}
        item { WorkspaceSearch(query,{query=it},"Найти настройку") }
        rows.groupBy { it.section }.forEach { (group,entries) ->
            item { HiroSection(group) }
            item { WorkspaceGroup { entries.forEachIndexed { i,row ->
                WorkspaceRow(row.name,row.detail,row.icon,grouped=true) { nav.navigate(row.route) }
                if(i<entries.lastIndex)HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant,thickness=0.5.dp)
            } } }
        }
        if(rows.isEmpty()) item { Text("Настройка не найдена. Попробуйте «SIM», «микрофон» или «контакты».") }
    }
}

@Composable
internal fun PersonalContactSettings(nav: NavController?, auth: HiroAuthViewModel) {
    val context=LocalContext.current
    val status by auth.personalContactsStatus.collectAsState()
    var enabled by remember(status) { mutableStateOf(auth.personalContactsSyncEnabled()) }
    val authState by auth.state.collectAsState()
    val expected=(authState as? HiroAuthState.SignedIn)?.session?.let(::personalContactAccountKey)
    var pendingAccount by remember { mutableStateOf<String?>(null) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val actual=(auth.state.value as? HiroAuthState.SignedIn)?.session?.let(::personalContactAccountKey)
        if(pendingAccount != null && pendingAccount == actual) { auth.setPersonalContactsSyncEnabled(granted); enabled=granted }
        pendingAccount=null
    }
    WorkspacePage("Контакты и синхронизация","Ваша личная адресная книга",nav) {
        item { WorkspaceInfo("Имена и номера сохраняются на сервере вашей организации только в вашей учётной записи. Другие менеджеры не получают ваш список. Контакты на телефоне не изменяются.") }
        item { WorkspaceCard {
            Text(if(enabled) "Синхронизация включена" else "Синхронизация выключена",style=MaterialTheme.typography.titleLarge)
            Text(status)
            Button(onClick={
                if(enabled){auth.setPersonalContactsSyncEnabled(false);enabled=false}
                else if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED){auth.setPersonalContactsSyncEnabled(true);enabled=true}
                else {pendingAccount=expected;permission.launch(Manifest.permission.READ_CONTACTS)}
            },modifier=Modifier.fillMaxWidth()){Text(if(enabled) "Выключить синхронизацию" else "Разрешить синхронизацию")}
        } }
        item { Text("Общим клиентом контакт становится только после отдельного подтверждения. Выключение не удаляет уже сохранённую книгу. Синхронизация работает, пока приложение запущено; после остановки Android фоновые обновления не гарантируются.") }
        item { WorkspaceRow("Мои контакты","Открыть личную адресную книгу",Icons.Default.Contacts){nav?.navigate("client-routing?personal=true")} }
    }
}

@Composable
private fun WorkspaceProfile(nav: NavController,calls: ViewModel,auth: HiroAuthViewModel,session: HiroSession) {
    var confirm by remember { mutableStateOf(false) }
    val live by calls.calls.collectAsState()
    WorkspacePage("Учётная запись","${session.user.displayName} · ${session.user.role}",nav) {
        item { WorkspaceCard { Text("Сервер организации",style=MaterialTheme.typography.titleMedium);Text(session.serverUrl); Text("Ваша роль: ${session.user.role}") } }
        item { WorkspaceInfo("Личные контакты принадлежат вашей учётной записи. Общие клиенты видны всем авторизованным сотрудникам сервера.") }
        item { if(live.isNotEmpty()) Text("Завершите разговор перед выходом.");OutlinedButton(onClick={confirm=true},enabled=live.isEmpty(),modifier=Modifier.fillMaxWidth()){Text("Выйти из учётной записи")} }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Выйти на этом смартфоне?")},text={Text("Рабочая учётная запись будет закрыта. Сохранённые на сервере контакты не удалятся.")},confirmButton={TextButton(onClick={confirm=false;auth.signOut()}){Text("Выйти")}},dismissButton={TextButton(onClick={confirm=false}){Text("Остаться")}})
}

@Composable
private fun WorkspaceAppearance(nav: NavController) {
    val context=LocalContext.current
    var selected by remember { mutableIntStateOf(Preferences(context).displayTheme) }
    WorkspacePage("Оформление","Только этот смартфон",nav) {
        item { WorkspaceCard { listOf(-1 to "Как в системе",1 to "Светлая тема",2 to "Тёмная тема").forEach { (mode,title) ->
            Row(Modifier.fillMaxWidth().clickable { selected=mode;Preferences(context).displayTheme=mode;BaresipService.darkTheme.value=Utils.isThemeDark(context);AppCompatDelegate.setDefaultNightMode(mode) }.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) { RadioButton(selected==mode,onClick=null);Spacer(Modifier.width(12.dp));Text(title) }
        } } }
        item { Text("Размер текста и доступность задаются в Android. Светлая тема не зависит от системной тёмной темы.") }
    }
}

@Composable
private fun WorkspaceNotifications(nav: NavController) {
    val context=LocalContext.current
    WorkspacePage("Уведомления и работа в фоне","Разрешения этого смартфона",nav) {
        item { WorkspaceRow("Звук и уведомления","Каналы звонков и SMS в Android",Icons.Default.Notifications){context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))} }
        item { WorkspaceRow("Разрешения приложения","Микрофон, контакты и работа в фоне",Icons.Default.Security){context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))} }
        item { WorkspaceInfo("Выбор принимаемых SIM — отдельная настройка. Отключение звука уведомлений не отключает приём сообщений.") }
        item { WorkspaceRow("Приём звонков и SMS","Выбрать SIM общего шлюза",Icons.Default.Sms){nav.navigate("workspace-receive")} }
    }
}

@Composable
private fun WorkspaceHelp(nav: NavController) {
    val context=LocalContext.current
    val agents by BaresipService.uas
    WorkspacePage("Помощь и проверка связи","Состояние этого смартфона",nav) {
        item { WorkspaceCard { Text("Зарегистрированных SIP-подключений: ${agents.count { it.status == BaresipService.circleGreen.getValue(BaresipService.colorblind) }}");Text("Микрофон: "+if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) "разрешён" else "не разрешён");Text("Android ${Build.VERSION.RELEASE} · HiRo ${BuildConfig.VERSION_NAME}") } }
        item { WorkspaceRow("Проверка голосового соединения","Набрать 600 вручную — локальное эхо",Icons.Default.Phone){nav.navigate("workspace-dialer?number=600")} }
        item { WorkspaceRow("Гарнитура и звук","Громкость, микрофон и аудиомодули",Icons.Default.Headset){nav.navigate("audio")} }
        item { WorkspaceRow("Разрешения Android","Если микрофон или уведомления недоступны",Icons.Default.Security){nav.navigate("workspace-notifications")} }
        item { WorkspaceRow("О приложении и обновления","Подписанные обновления с GitHub",Icons.Default.SystemUpdate){nav.navigate("about")} }
        item { WorkspaceRow("Дополнительные параметры","Технические настройки этого устройства",Icons.Default.Tune){nav.navigate("settings")} }
    }
}

@Composable
private fun WorkspaceOrganization(nav: NavController,auth: HiroAuthViewModel,user: HiroUser) {
    var lines by remember { mutableStateOf<List<HiroVoiceLine>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(user.id) { if(user.role=="admin") try {lines=auth.authenticatedRequest{c,s->c.voiceLines(s)}.lines} catch(e:CancellationException){throw e} catch(e:Exception){error=(e as? HiroServerException)?.userMessage?:"Не удалось загрузить линии"} }
    WorkspacePage("Настройки организации","Общие параметры · администратор",nav) {
        if(user.role!="admin") item { WorkspaceInfo("Доступно только администратору",true) }
        else {
            item { WorkspaceRow("Распределение обращений","Общие правила входящих и исходящих",Icons.Default.Route){nav.navigate("workspace-policies")} }
            item { Text("Рабочие номера и подключения",style=MaterialTheme.typography.titleLarge) }
            if(error.isNotEmpty()) item {WorkspaceInfo(error,true)}
            items(lines) { line -> WorkspaceCard { Text(line.name,style=MaterialTheme.typography.titleMedium); Text("${line.gatewayType} · ${if(line.enabled) "включена" else "выключена"}");if(line.simId.isNotEmpty())Text("SIM: ${line.simId} · ${if(line.simVerified) "подтверждена" else "не подтверждена"}") } }
            item { WorkspaceInfo("Техническое подключение провайдеров, права сотрудников и ИИ настраиваются на сервере. Эта версия показывает голосовые линии, но не создаёт провайдерские подключения.") }
        }
    }
}

@Composable
private fun WorkspacePolicies(nav: NavController,auth: HiroAuthViewModel,user: HiroUser) {
    val scope=rememberCoroutineScope();var policies by remember{mutableStateOf(HiroRoutingPolicies(0,emptyList()))};var lines by remember{mutableStateOf<List<HiroVoiceLine>>(emptyList())};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var edit by remember{mutableStateOf(false)}
    fun work(action:suspend()->Unit){if(busy)return;scope.launch{busy=true;error="";try{action()}catch(e:CancellationException){throw e}catch(e:Exception){error=(e as? HiroServerException)?.userMessage?:"Не удалось сохранить правила"}finally{busy=false}}}
    LaunchedEffect(user.id){if(user.role=="admin")work{lines=auth.authenticatedRequest{c,s->c.voiceLines(s)}.lines;policies=auth.authenticatedRequest{c,s->c.policies(s)}}}
    WorkspacePage("Распределение обращений","Для всех сотрудников организации",nav){
        if(user.role!="admin")item{WorkspaceInfo("Доступно только администратору",true)} else {
            item{WorkspaceInfo("Линия → подтверждённая SIM → устройство → тип шлюза → все линии. Более точное правило важнее числа. Текущий разговор не прерывается.")}
            if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())};if(error.isNotEmpty())item{WorkspaceInfo(error,true)}
            items(policies.policies){rule->WorkspaceCard{Text(ruleDescription(rule,lines));TextButton(enabled=!busy,onClick={work{policies=auth.authenticatedRequest{c,s->c.savePolicies(s,policies.policies-rule,policies.revision)}}}){Text("Удалить правило")}}}
            item{Button(onClick={edit=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Добавить общее правило")}}
        }
    }
    if(edit)PriorityRuleDialog(lines,busy,error,{edit=false}){rule->work{policies=auth.authenticatedRequest{c,s->c.savePolicies(s,replaceRule(policies.policies,rule),policies.revision)};edit=false}}
}

@Composable
private fun WorkspaceEmployees(nav: NavController, auth: HiroAuthViewModel, session: HiroSession) {
    var employees by remember { mutableStateOf<List<HiroEmployee>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(session.sessionId, reload) {
        busy = true
        error = ""
        try {
            employees = auth.authenticatedRequest { client, current -> client.employees(current) }.items
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = (e as? HiroServerException)?.userMessage ?: "Не удалось загрузить список сотрудников"
        } finally {
            busy = false
        }
    }
    val filtered = employees.filter {
        it.displayName.contains(query, true) || it.username.contains(query, true) || it.extension.contains(query)
    }
    WorkspacePage("Сотрудники", "Внутренние номера вашей организации", nav, action = {
        IconButton(onClick = { reload++ }, enabled = !busy) { Icon(Icons.Outlined.Refresh, "Обновить") }
    }) {
        item { WorkspaceSearch(query, { query = it }, "Имя или внутренний номер") }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (error.isNotEmpty()) item { WorkspaceInfo(error, true) }
        val own = employees.find { it.id == session.user.id }
        if (own != null) item { WorkspaceInfo("Ваш внутренний номер: ${own.extension}") }
        if (!busy && error.isEmpty() && filtered.isEmpty()) item {
            HiroEmpty(Icons.Outlined.Badge, "Сотрудники не найдены", "На сервере пока нет других пользователей с доступом к телефонии.")
        }
        items(filtered, key = { it.id }) { employee ->
            Surface(
                onClick = { nav.navigate("workspace-dialer?number=${Uri.encode(employee.extension)}") },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest
            ) {
                HiroPersonRow(
                    employee.displayName,
                    employee.extension,
                    when {
                        employee.id == session.user.id -> "Это вы · ${employee.role}"
                        employee.available -> "Внутренний номер · доступен"
                        else -> "Внутренний номер · сейчас не подключён"
                    }
                ) { nav.navigate("workspace-dialer?number=${Uri.encode(employee.extension)}") }
            }
        }
    }
}

@Composable
private fun WorkspaceDialer(nav: NavController,calls: ViewModel,auth: HiroAuthViewModel,initial: String) {
    val context=LocalContext.current
    val agents by BaresipService.uas
    val activeManagedProfileId=BaresipService.activeManagedProfileId
    val sip=agents.filter{
        !it.account.isMobile&&it.uap!=0L&&
            isCurrentTelephonyProfile(it.account.managedProfile,activeManagedProfileId)
    }
    var number by rememberSaveable{mutableStateOf(initial)}
    var line by rememberSaveable{mutableStateOf("")}
    var aor by rememberSaveable{mutableStateOf("")}
    var lines by remember{mutableStateOf<List<HiroVoiceLine>>(emptyList())}
    var employees by remember{mutableStateOf<List<HiroEmployee>>(emptyList())}
    var error by remember{mutableStateOf("")}
    var directoryError by remember{mutableStateOf("")}
    var ready by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){try{lines=auth.authenticatedRequest{c,s->c.voiceLines(s)}.lines;ready=true}catch(e:CancellationException){throw e}catch(e:Exception){error=(e as? HiroServerException)?.userMessage?:"Не удалось загрузить линии"}}
    LaunchedEffect(Unit){try{employees=auth.authenticatedRequest{c,s->c.employees(s)}.items}catch(e:CancellationException){throw e}catch(e:Exception){directoryError=(e as? HiroServerException)?.userMessage?:"Не удалось загрузить сотрудников"}}
    LaunchedEffect(sip.map{it.account.aor}){if(sip.none{it.account.aor==aor})aor=sip.firstOrNull()?.account?.aor.orEmpty()}
    val authState by auth.state.collectAsState()
    val user=(authState as? HiroAuthState.SignedIn)?.session?.user
    val entered=number.trim()
    val internal=employees.any{it.extension==entered}
    val canCall=user?.role in setOf("manager","admin") && (internal||entered=="600"||(ready&&normalizeClientNumber(entered)!=null)) && sip.any{it.account.aor==aor}
    fun placeCall(){
        val ua=sip.find{it.account.aor==aor}?:return
        if(ua.status!=BaresipService.circleGreen.getValue(BaresipService.colorblind)){error="Рабочее SIP-подключение не зарегистрировано";return}
        val selected=lines.find{it.id==line&&it.enabled}
        if(!internal&&entered!="600"&&line.isNotEmpty()&&selected==null){error="Выбранная линия недоступна";return}
        val extension=when{internal->entered;entered=="600"->"600";else->"*88${selected?.code?:"0"}*${normalizeClientNumber(entered)}"}
        calls.updateSelectedAor(aor)
        handleIntent(context,calls,Intent(context,MainActivity::class.java).putExtra("uap",ua.uap).putExtra("peer","sip:$extension@${Utils.aorDomain(aor)}"),"call")
        nav.navigate("main"){popUpTo("main")}
    }
    HiroDialer(
        number = number,
        onNumber = { number = it },
        lineOptions = listOf("" to "Автоматически по правилам") +
            lines.filter { it.enabled }.map { it.id to "${it.name} · ${it.gatewayType}" },
        line = line,
        onLine = { line = it },
        sipOptions = sip.map { it.account.aor to it.account.aor },
        aor = aor,
        onAor = { aor = it },
        canCall = canCall,
        error = listOf(error,directoryError).filter{it.isNotEmpty()}.joinToString("\n"),
        employees = employees,
        currentUserId = user?.id.orEmpty(),
        onEmployee = { number = it.extension },
        onCall = { placeCall() },
        onBack = { nav.navigateUp() }
    )
}
