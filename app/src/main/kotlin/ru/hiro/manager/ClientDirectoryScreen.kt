package ru.hiro.manager

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

private data class DirectoryEntry(val number: String,val name: String,val shared: HiroClient?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClientDirectoryScreen(nav: NavController,auth: HiroAuthViewModel,user: HiroUser,initialPersonal: Boolean=false) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val navigation=LocalWorkspaceNavigation.current
    val account=remember{(auth.state.value as? HiroAuthState.SignedIn)?.session?.let(::personalContactAccountKey)}
    suspend fun <T> request(block:(HiroServerClient,HiroSession)->T): T = auth.authenticatedRequest {c,s->
        if(personalContactAccountKey(s)!=account)throw HiroServerException("account_changed","Учётная запись изменилась")
        block(c,s)
    }
    var directory by remember{mutableStateOf(HiroClientDirectory(0,emptyList()))}
    var personal by remember{mutableStateOf<List<HiroPersonalContact>>(emptyList())}
    var lines by remember{mutableStateOf<List<HiroVoiceLine>>(emptyList())}
    var policies by remember{mutableStateOf<List<HiroPriorityRule>>(emptyList())}
    var loaded by remember{mutableStateOf(false)}
    var privateReady by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var page by rememberSaveable{mutableStateOf("list")}
    val listState=rememberLazyListState()
    LaunchedEffect(page){listState.scrollToItem(0)}
    var showPersonal by rememberSaveable{mutableStateOf(initialPersonal)}
    var search by rememberSaveable{mutableStateOf("")}
    var selected by rememberSaveable{mutableStateOf("")}
    var name by rememberSaveable{mutableStateOf("")}
    var number by rememberSaveable{mutableStateOf("")}
    var draftRules by rememberSaveable(stateSaver=Saver<List<HiroPriorityRule>,String>(save={hiroRoutingJson.encodeToString(it)},restore={hiroRoutingJson.decodeFromString(it)})){mutableStateOf(emptyList())}
    var external by rememberSaveable{mutableStateOf(false)}
    var dirty by rememberSaveable{mutableStateOf(false)}
    var leave by remember{mutableStateOf<String?>(null)}
    var editRule by remember{mutableStateOf(false)}
    var previewLine by rememberSaveable{mutableStateOf("")}
    var previewDirection by rememberSaveable{mutableStateOf("inbound")}
    var score by rememberSaveable{mutableStateOf("50")}
    var imported by remember{mutableStateOf<List<HiroPersonalContact>>(emptyList())}
    var importSelection by remember{mutableStateOf<Set<String>>(emptySet())}
    val canEdit=user.role in setOf("manager","admin")
    val shared=directory.clients.find{it.number==selected}
    val privateContact=personal.find{it.number==selected}
    val displayName=shared?.name?:privateContact?.name.orEmpty()
    val syncStatus by auth.personalContactsStatus.collectAsState()

    LaunchedEffect(dirty){navigation?.dirty=dirty}
    LaunchedEffect(busy){navigation?.busy=busy}
    DisposableEffect(Unit){onDispose{navigation?.dirty=false;navigation?.busy=false}}
    fun work(action:suspend()->Unit){if(busy)return;scope.launch{busy=true;error="";try{action()}catch(e:CancellationException){throw e}catch(e:HiroServerException){error=e.userMessage}catch(_:Exception){error="Не удалось выполнить действие. Проверьте соединение и разрешения."}finally{busy=false}}}
    suspend fun reload(){
        directory=request{c,s->c.clients(s)}
        privateReady=false
        try{personal=request{c,s->c.personalContacts(s,auth.personalContactsSourceId)}.contacts;privateReady=true}
        catch(e:HiroServerException){if(e.code !in setOf("not_found","contacts_unavailable"))throw e;personal=emptyList()}
        lines=request{c,s->c.voiceLines(s)}.lines
        if(canEdit)policies=request{c,s->c.policies(s)}.policies
        loaded=true
    }
    suspend fun savePrivate(items:List<HiroPersonalContact>){request{c,s->val previous=c.personalContacts(s,"manual");c.syncPersonalContacts(s,"manual",canonicalPersonalContacts(previous.sourceContacts.filter{old->items.none{it.number==old.number}}+items),previous.revision)}}
    fun move(target:String){if(busy)return;if(dirty){leave=target;return};page=target;error=""}
    fun resetDraft(){dirty=false;navigation?.dirty=false;external=false;error=""}
    fun edit(target:String){name=displayName;number=selected;draftRules=shared?.rules?:emptyList();external=false;page=target;error=""}
    fun newContact(){selected="";name="";number="";draftRules=emptyList();external=false;page="new";error=""}
    fun validate():String? {val canonical=normalizeClientNumber(number);if(canonical==null){error="Укажите номер с кодом страны";return null};if(name.toByteArray().size>256||name.contains(Regex("[\\r\\n\\u0000]"))){error="Имя слишком длинное или содержит недопустимые символы";return null};if(canonical!=selected&&(directory.clients.any{it.number==canonical}||personal.any{it.number==canonical})){error="Этот номер уже есть. Откройте существующую карточку.";return null};return canonical}
    LaunchedEffect(account){work{reload()}}
    LaunchedEffect(syncStatus){if(loaded&&syncStatus.startsWith("Личные контакты синхронизированы")){while(busy)delay(100);work{reload()}}}
    BackHandler(enabled=page!="list"){move(if(page in setOf("incoming","outgoing","rules","preview"))"priority" else if(page=="review")"promote" else if(page in setOf("new","import"))"list" else "card")}
    fun scan(){work{imported=withContext(Dispatchers.IO){scanPersonalPhoneContacts(context)};importSelection=emptySet();page="import"}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)scan()else error="Чтение контактов не разрешено. Можно добавить контакт вручную."}

    val entries=if(showPersonal)personal.map{DirectoryEntry(it.number,it.name,directory.clients.find{c->c.number==it.number})} else directory.clients.map{DirectoryEntry(it.number,it.name,it)}
    val title=when(page){"list"->"Клиенты";"card"->if(shared!=null)"Клиент команды" else "Личный контакт";"new"->"Личный контакт";"edit"->"Редактировать контакт";"promote"->"Сделать общим клиентом";"review"->"Открыть контакт команде?";"demote"->"Убрать из общих клиентов?";"priority"->"Очередь и исходящая линия";"incoming"->"Входящие: очередь";"outgoing"->"Исходящие: линия";"rules"->"Особые правила";"preview"->"Проверка приоритета";"import"->"Выбрать из телефона";else->"Клиенты"}
    Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={WorkspaceHeader(title,if(page=="list")"Клиенты — общие, адресная книга — личная" else "",onBack=if(page=="list")null else {{move(if(page in setOf("incoming","outgoing","rules","preview"))"priority" else if(page=="review")"promote" else if(page in setOf("new","import"))"list" else "card")}},
        actions={if(page=="list")IconButton(onClick={work{reload()}},enabled=!busy){Icon(Icons.Default.Refresh,"Обновить")}})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(),state=listState,contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(error.isNotEmpty())item{WorkspaceInfo(error,true);TextButton(enabled=!busy,onClick={work{reload()}}){Text("Обновить данные, сохранив черновик")}}
            if(page=="list"){
                item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(!showPersonal,{showPersonal=false},label={Text("Клиенты команды")},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primaryContainer,selectedLabelColor=MaterialTheme.colorScheme.primary));FilterChip(showPersonal,{showPersonal=true},label={Text("Мои контакты")},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primaryContainer,selectedLabelColor=MaterialTheme.colorScheme.primary))}}
                item{Text(if(showPersonal)"Личная книга · ${user.displayName}. Другие сотрудники не видят личные записи." else "Клиенты видны всем авторизованным сотрудникам организации.",style=MaterialTheme.typography.bodySmall)}
                item{WorkspaceSearch(search,{search=it},"Имя или номер")}
                if(showPersonal)item{WorkspaceRow("Синхронизация контактов",if(auth.personalContactsSyncEnabled())"Включена · только в вашу книгу" else "Выключена · можно включить в настройках",Icons.Default.Sync){nav.navigate("workspace-sync")}}
                val filtered=entries.filter{it.name.contains(search,true)||it.number.contains(search)}.sortedWith(compareBy<DirectoryEntry>{it.name.lowercase()}.thenBy{it.number})
                if(loaded&&filtered.isEmpty())item{WorkspaceCard{Text(if(search.isBlank())"Здесь пока нет контактов" else "По этому запросу контактов нет");Text("Можно добавить запись вручную или выбрать её из телефона.")}}
                items(filtered,key={it.number}){entry->WorkspaceRow(entry.name.ifBlank{entry.number},entry.number+"\n"+(entry.shared?.let(::clientOriginLabel)?:"Личный · только вам"),if(entry.shared==null)Icons.Default.Lock else Icons.Default.People){selected=entry.number;page="card";error=""}}
                if(privateReady)item{Button(onClick={newContact()},enabled=!busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Text("Добавить личный контакт")}}
                if(privateReady)item{OutlinedButton(onClick={if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED)scan()else permission.launch(Manifest.permission.READ_CONTACTS)},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Выбрать из контактов телефона")}}
                if(!privateReady&&loaded)item{WorkspaceInfo("Личная адресная книга недоступна на этой версии сервера. Общие клиенты остаются доступны.")}
                if(user.role=="admin")item{TextButton(onClick={newContact();external=true;page="promote"}){Text("Добавить клиента из другого источника")}}
            }
            if(page=="card"){
                item{WorkspaceCard{Text(displayName.ifBlank{selected},style=MaterialTheme.typography.headlineSmall);Text(selected);Text(if(shared==null)"Личный контакт · виден только вам" else "Общий клиент · виден всей команде",color=MaterialTheme.colorScheme.primary)}}
                item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(onClick={nav.navigate("workspace-dialer?number=${Uri.encode(selected)}")},enabled=canEdit,modifier=Modifier.weight(1f)){Text("Позвонить")}
                    OutlinedButton(onClick={nav.navigate("gateway-messages?address=${Uri.encode(selected)}")},enabled=canEdit,modifier=Modifier.weight(1f)){Text("Написать")}
                }}
                if(canEdit)item{WorkspaceRow("Получать звонки этого номера раньше","Моё предпочтение, не назначение для всей команды",Icons.Default.PhoneCallback){nav.navigate("workspace-priority?caller=${Uri.encode(selected)}")}}
                if(shared!=null){
                    item{WorkspaceCard{Text("Кто привёл клиента",style=MaterialTheme.typography.titleMedium);Text(clientOriginLabel(shared));Text("Необязательная история появления. Это не ответственный менеджер и не получатель звонка.",style=MaterialTheme.typography.bodySmall)}}
                    item{WorkspaceRow("Очередь обращений и исходящая линия","Общие настройки клиента, не выбор менеджера",Icons.Default.Route){page="priority"}}
                    if(canEdit)item{TextButton(onClick={edit("edit")}){Text("Изменить данные клиента")}}
                    if(canEdit&&privateReady)item{TextButton(onClick={page="demote"}){Text("Убрать из общих клиентов")}}
                }else{
                    if(canEdit)item{Button(onClick={edit("promote")},modifier=Modifier.fillMaxWidth()){Text("Сделать общим клиентом")}}
                    if(privateReady)item{OutlinedButton(onClick={edit("edit")},modifier=Modifier.fillMaxWidth()){Text("Изменить личный контакт")}}
                    item{WorkspaceInfo("Клиентский приоритет здесь не действует. Совпадение с вашей книгой само по себе ещё не направляет звонок вам.")}
                }
            }
            if(page in setOf("new","edit","promote")){
                item{WorkspaceInfo(if(page=="promote")"Шаг 1 из 2. Эти данные станут видны команде только после подтверждения." else if(shared!=null&&page=="edit")"Изменения общего клиента увидят все сотрудники. Автор привлечения не изменится." else "Личная запись видна только вам. Сохранение не делает её общим клиентом.")}
                item{OutlinedTextField(name,{name=it;dirty=true},label={Text(if(page=="promote")"Имя для команды" else "Имя")},modifier=Modifier.fillMaxWidth())}
                item{OutlinedTextField(number,{number=it;dirty=true},label={Text("Номер с кодом страны")},enabled=selected.isEmpty(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),modifier=Modifier.fillMaxWidth())}
                if(page=="promote")item{WorkspaceCard{Text("Кто привёл");Text(if(external)"Не указан · другой источник" else user.displayName);Text("Отметка необязательна для внешнего источника. Обычное редактирование не меняет автора.",style=MaterialTheme.typography.bodySmall)}}
                item{Button(enabled=!busy&&(page!="promote"||canEdit),onClick={
                    val canonical=validate()?:return@Button
                    if(page=="promote"){dirty=true;page="review"}
                    else work{
                        if(page=="edit"&&shared!=null)directory=request{c,s->c.saveClient(s,canonical,name.trim(),shared.rules,directory.revision,false)}
                        else savePrivate(listOf(HiroPersonalContact(canonical,name.trim())))
                        selected=canonical;resetDraft();page="card";reload()
                    }
                },modifier=Modifier.fillMaxWidth()){Text(if(page=="promote")"Далее: кто увидит контакт" else "Сохранить изменения")}}
            }
            if(page=="review"){
                item{WorkspaceCard{Text(name.ifBlank{number},style=MaterialTheme.typography.titleLarge);Text(number);Text("Увидят все авторизованные сотрудники этого сервера.");Text("Кто привёл: "+if(external)"не указан" else user.displayName)}}
                item{WorkspaceInfo("Другие личные контакты не публикуются. Отметка «кто привёл» не закрепляет клиента за менеджером.")}
                item{Button(enabled=!busy&&canEdit,onClick={val canonical=validate()?:return@Button;if(directory.clients.any{it.number==canonical}){error="Контакт уже стал общим клиентом. Откройте его карточку вместо повторной публикации.";return@Button};work{
                    if(external&&user.role!="admin")throw HiroServerException("forbidden","Нужна роль администратора")
                    // Preserve a personal copy before promotion; never auto-publish a book.
                    if(!external&&privateReady)savePrivate(listOf(HiroPersonalContact(canonical,privateContact?.name?:name.trim())))
                    directory=request{c,s->c.saveClient(s,canonical,name.trim(),draftRules,directory.revision,false,external)}
                    selected=canonical;resetDraft();showPersonal=false;page="card";reload()
                }},modifier=Modifier.fillMaxWidth()){Text("Подтвердить: сделать общим клиентом")}}
                item{TextButton(onClick={page="promote"}){Text("Вернуться к данным")}}
            }
            if(page=="demote"){
                item{WorkspaceInfo("Общий клиентский статус и правила исчезнут для всей команды. Контакт сохранится в вашей личной книге. Чужие личные копии и история общения не удаляются.",true)}
                item{Button(onClick={page="card"},modifier=Modifier.fillMaxWidth()){Text("Оставить общим клиентом")}}
                item{OutlinedButton(enabled=!busy&&canEdit&&privateReady,onClick={work{
                    val existing=shared?:throw HiroServerException("not_found","Клиент уже удалён")
                    savePrivate(listOf(HiroPersonalContact(selected,privateContact?.name?:existing.name)))
                    directory=request{c,s->c.saveClient(s,selected,existing.name,existing.rules,directory.revision,true)}
                    showPersonal=true;page="card";reload()
                }},modifier=Modifier.fillMaxWidth()){Text("Убрать из клиентов для всех")}}
            }
            if(page=="priority"){
                item{WorkspaceInfo("Здесь меняется очередь самих обращений и исходящая линия для всей организации. Чтобы получать звонки этого номера раньше коллег, используйте «Мой приоритет звонков». Текущие разговоры не прерываются.")}
                item{WorkspaceRow("Входящие: порядок обслуживания",shared?.rules?.let{basePriority(it,"inbound")}?.let{"Базовый приоритет: $it"}?:"По правилам организации; клиентское преимущество сохраняется",Icons.Default.PhoneCallback){draftRules=shared?.rules?:emptyList();score=(basePriority(draftRules,"inbound")?:50).toString();page="incoming"}}
                item{WorkspaceRow("Исходящие: выбор линии",shared?.rules?.let{basePriority(it,"outbound")}?.let{"Базовый приоритет: $it"}?:"По правилам организации",Icons.Default.PhoneForwarded){draftRules=shared?.rules?:emptyList();score=(basePriority(draftRules,"outbound")?:50).toString();page="outgoing"}}
                item{WorkspaceRow("Особые правила","Линия → SIM → устройство → тип шлюза",Icons.Default.Tune){page="rules"}}
                item{WorkspaceRow("Проверить на примере","Без реального звонка и без изменения правил",Icons.Default.FactCheck){previewLine=lines.firstOrNull()?.id.orEmpty();page="preview"}}
            }
            if(page in setOf("incoming","outgoing")){
                val incoming=page=="incoming"
                item{WorkspaceInfo(if(incoming)"Клиенты обслуживаются раньше обычных обращений. Баллы влияют на порядок ожидания, но не прерывают разговор." else "Баллы участвуют в автоматическом выборе линии. Конкретное правило линии важнее типа шлюза, а ручной выбор перед звонком — выше автоматики.")}
                item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(20,50,80).forEach{value->FilterChip(score=="$value",{score="$value";dirty=true},label={Text("$value")})}}}
                item{OutlinedTextField(score,{score=it;dirty=true},label={Text("Точное значение, от 0 до 100")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())}
                item{Text("Это базовое правило всех линий. Более точное правило может заменить его даже при меньшем числе.")}
                if(!incoming)item{Text("Одинаковое базовое значение для всех линий само по себе не выделяет одну из них. Для предпочтения линии добавьте особое правило.")}
                item{Button(enabled=canEdit&&!busy&&score.toIntOrNull()?.let{it in 0..100}==true,onClick={work{
                    val existing=shared?:throw HiroServerException("not_found","Клиент уже удалён")
                    val rule=HiroPriorityRule(direction=if(incoming)"inbound" else "outbound",priority=score.toInt())
                    directory=request{c,s->c.saveClient(s,selected,existing.name,replaceRule(existing.rules,rule),directory.revision,false)}
                    resetDraft();page="priority";reload()
                }},modifier=Modifier.fillMaxWidth()){Text("Сохранить базовое правило")}}
            }
            if(page=="rules"){
                val rules=shared?.rules.orEmpty().filterNot(::isBasePriority)
                if(rules.isEmpty())item{Text("Особых правил нет. Действуют базовые правила клиента и организации.")}
                items(rules){rule->WorkspaceCard{Text(ruleDescription(rule,lines));if(canEdit)TextButton(enabled=!busy,onClick={work{val existing=shared?:return@work;directory=request{c,s->c.saveClient(s,selected,existing.name,existing.rules-rule,directory.revision,false)};reload()}}){Text("Удалить только это правило")}}}
                if(canEdit)item{Button(enabled=!busy,onClick={editRule=true},modifier=Modifier.fillMaxWidth()){Text("Добавить особое правило")}}
                item{WorkspaceInfo("Точность правила важнее числа: линия → подтверждённая SIM → устройство → тип шлюза → все линии. В одной области клиентское правило уточняет организационное.")}
            }
            if(page=="preview"){
                item{RoutingChoice("Направление",listOf("inbound" to "Входящий звонок","outbound" to "Исходящий звонок"),previewDirection){previewDirection=it}}
                item{RoutingChoice("Линия примера",lines.map{it.id to it.name},previewLine){previewLine=it}}
                val line=lines.find{it.id==previewLine}
                if(line!=null){val result=resolveClientPriority(shared?.rules.orEmpty(),policies,line,previewDirection)
                    item{WorkspaceCard{Text(line.name,style=MaterialTheme.typography.titleLarge);Text(result.rule?.let{ruleDescription(it,lines)}?:if(previewDirection=="inbound")"Нет правила: клиентские 50 баллов по умолчанию" else "Нет подходящего правила");Text(if(result.rule==null)"Правило не найдено" else if(result.clientRule)"Применено правило клиента" else "Применено правило организации")}}
                    if(previewDirection=="outbound")item{WorkspaceInfo("Автоматический выбор по включённым линиям: ${automaticClientLine(shared?.rules.orEmpty(),policies,lines)?.name?:"нет линии"}. Ручной выбор перед звонком его заменяет.")}
                }
                item{Text("Предпросмотр использует загруженные правила. Занятость каналов и состояние внешнего оператора не проверяются. Реальный звонок не выполняется.",style=MaterialTheme.typography.bodySmall)}
            }
            if(page=="import"){
                item{WorkspaceInfo("Выбранные записи будут сохранены только в вашей личной книге на сервере организации. Общие клиенты автоматически не создаются.")}
                items(imported,key={it.number}){contact->Row(Modifier.fillMaxWidth().clickable{importSelection=if(contact.number in importSelection)importSelection-contact.number else importSelection+contact.number}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(contact.number in importSelection,onCheckedChange=null);Column(Modifier.weight(1f)){Text(contact.name.ifBlank{contact.number});Text(contact.number,style=MaterialTheme.typography.bodySmall)}}}
                item{Button(enabled=importSelection.isNotEmpty()&&!busy,onClick={work{savePrivate(imported.filter{it.number in importSelection});page="list";showPersonal=true;reload()}},modifier=Modifier.fillMaxWidth()){Text("Добавить в мою книгу: ${importSelection.size}")}}
                item{Text("Номера без кода страны могут быть пропущены. Контакты Android не изменяются.",style=MaterialTheme.typography.bodySmall)}
            }
        }
    }
    if(editRule)PriorityRuleDialog(lines,busy,error,{editRule=false}){rule->work{val existing=shared?:return@work;directory=request{c,s->c.saveClient(s,selected,existing.name,replaceRule(existing.rules,rule),directory.revision,false)};editRule=false;reload()}}
    leave?.let{target->AlertDialog(onDismissRequest={leave=null},title={Text("Есть несохранённые изменения")},text={Text("Контакт не будет опубликован, а черновик не будет сохранён.")},confirmButton={TextButton(onClick={resetDraft();page=target;leave=null}){Text("Не сохранять и уйти")}},dismissButton={TextButton(onClick={leave=null}){Text("Остаться")}})}
}
