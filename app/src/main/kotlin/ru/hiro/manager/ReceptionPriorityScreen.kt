package ru.hiro.manager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

internal interface ReceptionRepository {
    suspend fun load(): Pair<List<HiroVoiceLine>,HiroReceptionPreferences>
    suspend fun save(rules:List<HiroReceptionRule>,revision:Long): HiroReceptionPreferences
    suspend fun preview(line:String,caller:String): HiroReceptionPreview
}

private class AuthenticatedReceptionRepository(private val auth:HiroAuthViewModel,private val account:String?):ReceptionRepository {
    private suspend fun <T> request(action:(HiroServerClient,HiroSession)->T):T = auth.authenticatedRequest { c,s ->
        if(personalContactAccountKey(s)!=account)throw HiroServerException("account_changed","Учётная запись изменилась")
        action(c,s)
    }
    override suspend fun load()=request {c,s->c.voiceLines(s).lines to c.receptionPreferences(s)}
    override suspend fun save(rules:List<HiroReceptionRule>,revision:Long)=request{c,s->c.saveReceptionPreferences(s,rules,revision)}
    override suspend fun preview(line:String,caller:String)=request{c,s->c.previewReception(s,line,caller)}
}

@Composable
internal fun ReceptionPriorityScreen(nav:NavController,auth:HiroAuthViewModel,initialCaller:String="",repository:ReceptionRepository?=null) {
    val account=remember(auth){(auth.state.value as? HiroAuthState.SignedIn)?.session?.let(::personalContactAccountKey)}
    val repo=remember(auth,account,repository){repository?:AuthenticatedReceptionRepository(auth,account)}
    val scope=rememberCoroutineScope()
    val navigation=LocalWorkspaceNavigation.current
    var preferences by remember { mutableStateOf(HiroReceptionPreferences()) }
    var lines by remember { mutableStateOf<List<HiroVoiceLine>>(emptyList()) }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf("list") }
    var draft by rememberSaveable(stateSaver=Saver<HiroReceptionRule,String>(save={hiroRoutingJson.encodeToString(it)},restore={hiroRoutingJson.decodeFromString(it)})){mutableStateOf(HiroReceptionRule())}
    var originalKey by rememberSaveable { mutableStateOf<String?>(null) }
    var priorityText by rememberSaveable { mutableStateOf("100") }
    var callerText by rememberSaveable { mutableStateOf("") }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var discardTarget by remember { mutableStateOf<String?>(null) }
    var previewLine by rememberSaveable { mutableStateOf("") }
    var previewCaller by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<HiroReceptionPreview?>(null) }
    val listState=rememberLazyListState()
    LaunchedEffect(page,error){listState.scrollToItem(0)}

    LaunchedEffect(dirty){navigation?.dirty=dirty}
    LaunchedEffect(busy){navigation?.busy=busy}
    DisposableEffect(Unit){onDispose{navigation?.dirty=false;navigation?.busy=false}}
    fun work(action:suspend()->Unit){
        if(busy)return
        busy=true;error=""
        scope.launch{try{action()}catch(e:CancellationException){throw e}catch(e:HiroServerException){
            error=when(e.code){
                "reception_unavailable"->"Сервер ещё не настроен для личных приоритетов. Остальная связь продолжает работать."
                "invalid_reception_rule"->"Источник правила недоступен или не подтверждает голосовую SIM. Обновите список и проверьте область правила."
                else->e.userMessage
            }
        }catch(e:IllegalArgumentException){error=e.message?:"Проверьте правило"}catch(_:Exception){error="Не удалось выполнить действие. Проверьте соединение с сервером."}finally{busy=false}}
    }
    suspend fun reload(){
        val (inventory,pref)=repo.load()
        lines=inventory;preferences=pref;ready=true;preview=null
        if(lines.none{it.id==previewLine&&it.enabled})previewLine=lines.firstOrNull{it.enabled}?.id.orEmpty()
    }
    LaunchedEffect(repo){work{reload()}}
    fun move(target:String){if(busy)return;if(dirty&&target in setOf("list","preview","help")){discardTarget=target;return};page=target;error="";notice=""}
    fun back(){if(busy)return;when(page){"list"->nav.navigateUp();"review"->move("edit");"edit"->move("list");"delete"->move("edit");else->move("list")}}
    BackHandler(enabled=true){back()}
    fun reset(){dirty=false;navigation?.dirty=false;originalKey=null}
    fun create(){draft=HiroReceptionRule();originalKey=null;priorityText="100";callerText=normalizeClientNumber(initialCaller).orEmpty();page="scope";error="";notice=""}
    fun edit(rule:HiroReceptionRule){draft=rule;originalKey=receptionRuleKey(rule);priorityText=rule.priority.toString();callerText=rule.callerNumber;page="edit";error="";notice=""}
    fun validated():HiroReceptionRule? {
        val score=priorityText.toIntOrNull()
        if(score==null||score !in 0..100){error="Укажите целый приоритет от 0 до 100";return null}
        val caller=if(callerText.isBlank())"" else normalizeClientNumber(callerText)
        if(caller==null){error="Для конкретного звонящего укажите полный номер с кодом страны";return null}
        val value=draft.copy(priority=score,callerNumber=caller)
        if(!receptionRuleSupported(value,lines)){error="Этот источник больше недоступен. Выберите другую область правила.";return null}
        return value
    }
    val enabled=lines.filter{it.enabled}
    val devices=enabled.filter{it.deviceId.isNotEmpty()}.distinctBy{it.deviceId}
    val sims=enabled.filter{it.simVerified&&it.simId.isNotEmpty()&&it.deviceId.isNotEmpty()}.distinctBy{it.deviceId to it.simId}
    val title=when(page){"scope"->"Какие звонки получать первым?";"edit"->"Мой приоритет";"review"->"Что изменится";"delete"->"Удалить предпочтение?";"preview"->"Проверить моё правило";"help"->"Приоритет без отказа";else->"Мой приоритет звонков"}
    val subtitle=when(page){"scope"->"Шаг 1 из 3 · область правила";"edit"->"Шаг 2 из 3 · только входящие";"review"->"Шаг 3 из 3 · проверьте результат";"preview"->"Расчёт сервера · реальный звонок не выполняется";else->"Кому предложат входящий звонок первым"}
    Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={WorkspaceHeader(title,subtitle,onBack={back()})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(),state=listState,contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(error.isNotEmpty())item{WorkspaceInfo(error,true);TextButton(onClick={work{reload()}},enabled=!busy){Text(if(dirty)"Обновить список, сохранив черновик" else "Повторить загрузку")}}
            if(notice.isNotEmpty())item{WorkspaceInfo(notice)}
            if(ready&&!preferences.appliedToLiveCalls)item{WorkspaceInfo("Этот сервер ещё не применяет личные правила к звонкам. Сохранение работает; текущий приём звонков не меняется.")}
            if(page=="list"){
                item{WorkspaceCard{Text("Получать все доступные звонки",style=MaterialTheme.typography.titleLarge);Text("Без правил вы наравне с коллегами. Ничего не выключено.",style=MaterialTheme.typography.bodySmall)}}
                item{Text("Мои дополнительные правила",style=MaterialTheme.typography.titleLarge)}
                if(ready&&preferences.rules.isEmpty())item{WorkspaceCard{Text("Пока нет правил");Text("Можно повысить свой приоритет для одной линии, телефона или типа шлюза. Остальные звонки останутся.",style=MaterialTheme.typography.bodySmall)}}
                if(preferences.rules.isNotEmpty())item{WorkspaceGroup{preferences.rules.forEachIndexed{i,r->
                    WorkspaceRow(receptionRuleTarget(r,lines),"Приоритет ${r.priority} · "+if(r.callerNumber.isEmpty())"любой звонящий" else r.callerNumber,Icons.Default.Route,enabled=!busy,grouped=true){edit(r)}
                    if(i<preferences.rules.lastIndex)HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant,thickness=0.5.dp)
                }}}
                item{Button(onClick={create()},enabled=ready&&!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp)){Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Добавить приоритет")}}
                item{WorkspaceGroup{
                    WorkspaceRow("Проверить моё правило","Узнать свой приоритет для конкретной линии",Icons.Default.Science,enabled=ready&&!busy,grouped=true){move("preview")}
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant,thickness=0.5.dp)
                    WorkspaceRow("Как это работает","Приоритет — не отказ от звонков",Icons.Default.HelpOutline,grouped=true){move("help")}
                }}
            }
            if(page=="scope"){
                fun choose(r:HiroReceptionRule){draft=r;dirty=true;page="edit"}
                item{WorkspaceRow("С одной рабочей линии","Только выбранный номер бизнеса",Icons.Default.Phone,enabled=enabled.isNotEmpty()&&!busy){choose(draft.copy(lineId=enabled.first().id,deviceId="",simId="",gatewayType=""))}}
                item{WorkspaceRow("С одного устройства-шлюза","Не меняет остальные подключения",Icons.Default.Smartphone,enabled=devices.isNotEmpty()&&!busy){choose(draft.copy(lineId="",deviceId=devices.first().deviceId,simId="",gatewayType=""))}}
                item{WorkspaceRow("Со шлюзов одного типа","Запасное правило: например, все NOVOFON",Icons.Default.Business,enabled=enabled.isNotEmpty()&&!busy){choose(draft.copy(lineId="",deviceId="",simId="",gatewayType=enabled.first().gatewayType))}}
                item{WorkspaceRow("Со всех доступных линий","Общее личное предпочтение",Icons.Default.Route,enabled=!busy){choose(draft.copy(lineId="",deviceId="",simId="",gatewayType=""))}}
                item{WorkspaceRow("С определённой SIM",if(sims.isEmpty())"Недоступно: шлюз не определяет голосовую SIM" else "Только SIM, подтверждённые голосовым адаптером",Icons.Default.SimCard,enabled=sims.isNotEmpty()&&!busy){choose(draft.copy(lineId="",deviceId=sims.first().deviceId,simId=sims.first().simId,gatewayType=""))}}
                item{Text("Выбор здесь не отключает другие источники звонков.",style=MaterialTheme.typography.bodySmall)}
            }
            if(page=="edit"){
                val kind=receptionScope(draft)
                item{Text("Другие ваши правила сохраняются",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                item{when(kind){
                    "line"->ReceptionChoice("Рабочая линия",draft.lineId,enabled.map{it.id to it.name},!busy){draft=draft.copy(lineId=it);dirty=true}
                    "type"->ReceptionChoice("Тип шлюза",draft.gatewayType,enabled.distinctBy{it.gatewayType}.map{it.gatewayType to receptionRuleTarget(HiroReceptionRule(gatewayType=it.gatewayType),lines)},!busy){draft=draft.copy(gatewayType=it);dirty=true}
                    "device"->ReceptionChoice("Устройство-шлюз",draft.deviceId,devices.map{it.deviceId to it.name},!busy){draft=draft.copy(deviceId=it);dirty=true}
                    "sim"->ReceptionChoice("Подтверждённая SIM",draft.deviceId+"\u001f"+draft.simId,sims.map{(it.deviceId+"\u001f"+it.simId) to (it.name+" · SIM "+it.simId)},!busy){val l=sims.first{l->l.deviceId+"\u001f"+l.simId==it};draft=draft.copy(deviceId=l.deviceId,simId=l.simId);dirty=true}
                    else->WorkspaceCard{Text("Все доступные линии",style=MaterialTheme.typography.titleLarge)}
                }}
                item{ReceptionField("Для всех номеров — оставьте пустым",callerText,{callerText=it;dirty=true},KeyboardType.Phone,!busy)}
                item{ReceptionField("Мой приоритет · 0–100",priorityText,{priorityText=it;dirty=true},KeyboardType.Number,!busy)}
                item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(0 to "0 · наравне",50 to "50 · раньше",100 to "100 · первым").forEach{(n,label)->FilterChip(priorityText==n.toString(),{priorityText=n.toString();dirty=true},enabled=!busy,label={Text(label)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primaryContainer,selectedLabelColor=MaterialTheme.colorScheme.primary))}}}
                item{WorkspaceInfo("Больше число — раньше коллег с меньшим приоритетом. Одинаковое число — звонок одновременно. 0 не запрещает приём.")}
                item{Button(onClick={val valid=validated();if(valid!=null){draft=valid;page="review";error=""}},enabled=!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp)){Text("Далее: что изменится")}}
                if(originalKey!=null)item{TextButton(onClick={page="delete"},enabled=!busy){Text("Удалить только это правило")}}
            }
            if(page=="review"){
                item{WorkspaceCard{Text(receptionRuleTarget(draft,lines),style=MaterialTheme.typography.bodySmall);Text("Мой приоритет — ${draft.priority}",style=MaterialTheme.typography.titleLarge);Text(if(draft.callerNumber.isEmpty())"Все звонящие на этот источник" else "Только номер ${draft.callerNumber}")}}
                item{WorkspaceCard{Text("Если я занят или не отвечу",style=MaterialTheme.typography.titleMedium);Text("Звонок предлагается следующим подходящим менеджерам. Текущий разговор не прерывается.");Text("Другие линии, доступ и прежние правила сохраняются.",style=MaterialTheme.typography.bodySmall)}}
                item{WorkspaceInfo("Внутри ваших правил: линия → подтверждённая SIM → устройство → тип → все линии. Более точное правило заменяет общее значение только здесь. Затем сравниваются числа менеджеров.")}
                item{Button(onClick={work{
                    val next=replaceReceptionRule(preferences.rules,originalKey,draft)
                    preferences=repo.save(next,preferences.revision)
                    reset();preview=null;page="list";notice=if(preferences.appliedToLiveCalls)"Правило сохранено на сервере" else "Правило сохранено на сервере. На живых звонках распределение ещё не включено."
                }},enabled=ready&&!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp)){Text("Сохранить правило на сервере")}}
                item{TextButton(onClick={move("edit")},enabled=!busy){Text("Изменить параметры")}}
            }
            if(page=="delete"){
                item{WorkspaceCard{Text(receptionRuleTarget(draft,lines),style=MaterialTheme.typography.titleLarge);Text("Вернётся более общее правило, а если его нет — равный приоритет 0. Приём звонков не отключится.")}}
                item{Button(onClick={work{
                    require(originalKey!=null&&preferences.rules.any{receptionRuleKey(it)==originalKey}){"Правило уже удалено на другом устройстве"}
                    preferences=repo.save(preferences.rules.filterNot{receptionRuleKey(it)==originalKey},preferences.revision)
                    reset();page="list";notice="Удалено только это предпочтение. Другие правила сохранены."
                }},enabled=ready&&!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.errorContainer,contentColor=MaterialTheme.colorScheme.onErrorContainer)){Text("Удалить правило")}}
                item{OutlinedButton(onClick={move("edit")},enabled=!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp)){Text("Оставить правило")}}
            }
            if(page=="preview"){
                item{ReceptionChoice("Линия для проверки",previewLine,enabled.map{it.id to it.name},!busy){previewLine=it;preview=null}}
                item{ReceptionField("Номер звонящего · необязательно",previewCaller,{previewCaller=it;preview=null},KeyboardType.Phone,!busy)}
                item{Button(onClick={val number=if(previewCaller.isBlank())"" else normalizeClientNumber(previewCaller);if(number==null)error="Укажите полный номер с кодом страны" else work{preview=repo.preview(previewLine,number)}},enabled=ready&&!busy&&enabled.any{it.id==previewLine},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp)){Text("Рассчитать на сервере")}}
                preview?.let{result->item{WorkspaceCard{
                    Text("Мой приоритет: ${result.effective.priority}",style=MaterialTheme.typography.titleLarge)
                    Text(result.effective.rule?.let{receptionRuleTarget(it,lines)}?:"Правила нет — базовый приоритет 0")
                    Text("Это только ваше правило. Доступ к линии не меняется; другие менеджеры остаются резервом.",style=MaterialTheme.typography.bodySmall)
                    if(!result.appliedToLiveCalls)Text("Распределение на реальных звонках ещё не включено.",style=MaterialTheme.typography.bodySmall)
                }}}
                item{WorkspaceInfo("Это не проверка доступности коллег и не пробный звонок. Чужие личные контакты и SIP-направления здесь не раскрываются.")}
            }
            if(page=="help"){
                item{WorkspaceCard{Text("Без правил · все равны",style=MaterialTheme.typography.titleLarge);Text("Неизвестный номер предлагается всем доступным менеджерам. Если не-клиент есть в личных контактах нескольких менеджеров — сначала одновременно им, затем остальным.")}}
                item{WorkspaceCard{Text("Предпочтение, не отказ",style=MaterialTheme.typography.titleLarge);Text("100 для NOVOFON не отключает MANGO. При равенстве приоритетов звонят одновременно. Занятых и недоступных пропускают; при отсутствии ответа переходят к следующей группе после тайм-аута АТС.")}}
                item{WorkspaceInfo("Так работает подготовленный расчёт. Для реальных звонков нужны персональные SIP-направления и интеграция расчёта с АТС. Очередь самих звонков, исходящая линия и выбор SMS — отдельные настройки.")}
            }
        }
    }
    discardTarget?.let{target->AlertDialog(onDismissRequest={discardTarget=null},title={Text("Есть несохранённое правило")},text={Text("Черновик ещё не отправлен на сервер. Другие ваши правила не изменялись.")},confirmButton={TextButton(onClick={reset();discardTarget=null;page=target;error=""}){Text("Не сохранять и перейти")}},dismissButton={TextButton(onClick={discardTarget=null}){Text("Продолжить редактирование")}})}
}

@Composable
private fun ReceptionField(label:String,value:String,change:(String)->Unit,type:KeyboardType,enabled:Boolean) {
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
        Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value,change,singleLine=true,enabled=enabled,keyboardOptions=KeyboardOptions(keyboardType=type),modifier=Modifier.fillMaxWidth().semantics{contentDescription=label},shape=RoundedCornerShape(12.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant,unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLowest,focusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLowest))
    }
}

@Composable
private fun ReceptionChoice(label:String,selected:String,options:List<Pair<String,String>>,enabled:Boolean,change:(String)->Unit) {
    var open by remember{mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
        Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Box{
            OutlinedButton(onClick={open=true},enabled=enabled&&options.isNotEmpty(),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(14.dp),colors=ButtonDefaults.outlinedButtonColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLowest,contentColor=MaterialTheme.colorScheme.onSurface)){
                Text(options.find{it.first==selected}?.second?:"Нет доступного источника",modifier=Modifier.weight(1f));Icon(Icons.Default.ExpandMore,null)
            }
            DropdownMenu(open,{open=false}){options.forEach{(id,name)->DropdownMenuItem(text={Text(name)},onClick={change(id);open=false})}}
        }
    }
}
