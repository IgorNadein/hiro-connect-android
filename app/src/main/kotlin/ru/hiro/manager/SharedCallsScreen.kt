package ru.hiro.manager

import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import java.io.File

internal fun callError(error:Throwable)=if(error is HiroServerException)error.userMessage else "Не удалось загрузить данные. Проверьте соединение."

internal enum class CallsFilter(val label: String) {
    ALL("Все"), INCOMING("Входящие"), OUTGOING("Исходящие"),
    MISSED("Пропущенные"), ACTIVE("Сейчас"), WAITING("В очереди")
}

internal fun historyMatchesFilter(call: SharedCall, filter: CallsFilter): Boolean = when (filter) {
    CallsFilter.ALL -> true
    CallsFilter.INCOMING -> call.direction == "inbound"
    CallsFilter.OUTGOING -> call.direction == "outbound"
    CallsFilter.MISSED -> call.direction == "inbound" && call.status in setOf("missed", "busy")
    CallsFilter.ACTIVE, CallsFilter.WAITING -> false
}

internal fun liveMatchesFilter(call: LiveTeamCall, filter: CallsFilter): Boolean = when (filter) {
    CallsFilter.ALL -> true
    CallsFilter.INCOMING -> call.direction == "inbound"
    CallsFilter.OUTGOING -> call.direction == "outbound"
    CallsFilter.MISSED -> false
    CallsFilter.ACTIVE -> call.state == "active"
    CallsFilter.WAITING -> call.state == "waiting"
}

@Composable
private fun CompactCallRow(title: String, subtitle: String, trailing: String, icon: ImageVector, description: String,
    tint: Color = MaterialTheme.colorScheme.primary, onClick: (() -> Unit)? = null) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (trailing.isNotBlank()) Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SharedCallsScreen(repository: SharedCallRepository, padding: PaddingValues = PaddingValues(), initialTab: Int = 0,
    onOpen: (String) -> Unit, onDial: () -> Unit, onLocal: () -> Unit,
    subtitle: String = "", onBack: (() -> Unit)? = null) {
    var filter by rememberSaveable { mutableStateOf(if (initialTab == 1) CallsFilter.ACTIVE else if (initialTab == 2) CallsFilter.WAITING else CallsFilter.ALL) }
    var filterDialog by remember { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var history by remember { mutableStateOf<SharedCallPage?>(null) }
    var live by remember { mutableStateOf<LiveTeamCalls?>(null) }
    var historyError by remember { mutableStateOf("") }
    var liveError by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var joiningBusy by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var finishedRefresh by remember { mutableIntStateOf(0) }
    var generation by remember { mutableIntStateOf(0) }
    var joining by remember { mutableStateOf<LiveTeamCall?>(null) }
    var callback by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(searchVisible) {
        if (searchVisible) {
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboard?.show()
        } else keyboard?.hide()
    }
    val missed = filter == CallsFilter.MISSED
    LaunchedEffect(repository, query, missed, retry, finishedRefresh) {
        generation++
        historyError = ""; busy = true; history = null
        delay(250)
        try { history = repository.history(query, missed) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { historyError = callError(e) }
        finally { busy = false }
    }
    LaunchedEffect(repository, lifecycle, retry) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previousIds: Set<String>? = null
            while (true) {
                try {
                    val updated = repository.live()
                    if (updated.ready) {
                        val currentIds = updated.items.map { it.id }.toSet()
                        if (previousIds?.any { it !in currentIds } == true) finishedRefresh++
                        previousIds = currentIds
                    }
                    live = updated; liveError = ""
                }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { liveError = callError(e); live = null }
                delay(3000)
            }
        }
    }
    val liveRows = live?.takeIf { it.ready }?.items.orEmpty().filter { call ->
        liveMatchesFilter(call, filter) && (query.isBlank() || call.number.contains(query, true) || call.operators.any { it.contains(query, true) })
    }.sortedBy { if (it.state == "active") 0 else 1 }
    val liveIds = liveRows.map { it.id }.toSet()
    val historyRows = history?.items.orEmpty().filter { it.id !in liveIds && historyMatchesFilter(it, filter) }
    Scaffold(modifier = Modifier.fillMaxSize().padding(padding),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { Column {
            WorkspaceHeader("Звонки", subtitle, onBack = onBack, statusInset = onBack != null, actions = {
            IconButton(onClick = { filterDialog = true }) { Icon(Icons.Outlined.FilterList, if (filter == CallsFilter.ALL) "Фильтр звонков" else "Фильтр: ${filter.label}") }
            IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) query = "" }) { Icon(Icons.Outlined.Search, if (searchVisible) "Закрыть поиск" else "Поиск звонков") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Меню звонков") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Обновить") }, onClick = { menu = false; retry++ })
                    DropdownMenuItem(text = { Text("История на этом смартфоне") }, onClick = { menu = false; onLocal() })
                }
            }
            })
            if (searchVisible) {
                WorkspaceSearch(query, { query = it }, "Имя, номер или менеджер",
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp).focusRequester(focusRequester))
            }
        } },
        floatingActionButton = { FloatingActionButton(onClick = onDial) { Icon(Icons.Outlined.Dialpad, "Набрать номер") } }
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (historyError.isNotBlank()) item { WorkspaceInfo("История: $historyError", true); TextButton(onClick = { retry++ }) { Text("Повторить загрузку") } }
            if (liveError.isNotBlank()) item { WorkspaceInfo("Текущие звонки: $liveError", true) }
            live?.takeIf { !it.ready }?.let { item { WorkspaceInfo(liveUnavailableText(it.problem), true) } }
            if (callback.isNotBlank()) item { WorkspaceInfo(callback) }
            if (busy || joiningBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(liveRows, key = { "live-" + it.id }) { call ->
                val active = call.state == "active"
                CompactCallRow(call.number.ifBlank { "Номер не передан" }, call.operators.joinToString(" · "), callDuration(call.duration),
                    if (active) Icons.Outlined.PhoneInTalk else Icons.Outlined.Schedule, if (active) "Идёт разговор" else "В очереди",
                    tint = hiroTone(if (active) HiroTone.Good else HiroTone.Warning).second,
                    onClick = if (active && live?.capabilities?.join == true) ({ joining = call }) else null)
            }
            historyRows.groupBy { callDate(it.startedAt) }.forEach { (day, calls) ->
                item(key = "date-" + (day?.toString() ?: "unknown")) {
                    Text(callDayLabel(day), modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(calls, key = { "history-" + it.id }) { call ->
                    val missed = call.status in setOf("missed", "busy") && call.direction == "inbound"
                    val icon = when { missed -> Icons.Outlined.CallMissed
                        call.direction == "outbound" -> Icons.Outlined.CallMade
                        call.direction == "inbound" -> Icons.Outlined.CallReceived
                        else -> Icons.Outlined.Phone }
                    CompactCallRow(callTitle(call), call.operators.joinToString(" · "), callClockTime(call.startedAt), icon, callStatus(call),
                        tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) { onOpen(call.id) }
                }
            }
            val page = history
            if (page != null && filter !in setOf(CallsFilter.ACTIVE, CallsFilter.WAITING)) {
                journalProblemText(page.problem)?.let { problem -> item { WorkspaceInfo(problem, true) } }
                if (page.nextCursor.isNotEmpty()) item { OutlinedButton(enabled = !busy, onClick = {
                    val requestedGeneration = generation
                    scope.launch {
                        busy = true; historyError = ""
                        try {
                            val next = repository.history(query, missed, page.nextCursor)
                            if (generation == requestedGeneration) history = next.copy(items = (page.items + next.items).distinctBy { it.id })
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { if (generation == requestedGeneration) historyError = callError(e) }
                        finally { if (generation == requestedGeneration) busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Показать ещё") } }
            }
            if (!busy && historyError.isBlank() && liveError.isBlank() && history != null && live != null && liveRows.isEmpty() && historyRows.isEmpty() && history?.nextCursor.isNullOrEmpty())
                item { HiroEmpty(Icons.Outlined.Phone, "Звонков не найдено", "Измените поиск или фильтр.") }
        }
    }
    if (filterDialog) AlertDialog(onDismissRequest = { filterDialog = false }, title = { Text("Фильтр звонков") },
        text = { Column { CallsFilter.entries.forEach { option ->
            Row(Modifier.fillMaxWidth().clickable { filter = option; filterDialog = false }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = filter == option, onClick = { filter = option; filterDialog = false })
                Spacer(Modifier.width(8.dp)); Text(option.label)
            }
        } } }, confirmButton = { TextButton(onClick = { filterDialog = false }) { Text("Закрыть") } })
    joining?.let { call -> AlertDialog(onDismissRequest = { joining = null }, title = { Text("Подключиться к разговору?") },
        text = { Text("АТС позвонит в ваше рабочее приложение. Ответьте на входящий вызов: вас будут слышать клиент и другие участники. Разговор должен ещё продолжаться.") },
        confirmButton = { TextButton(onClick = { joining = null; scope.launch { joiningBusy = true; callback = ""; try {
            repository.join(call.id); callback = "Запрос отправлен. Ответьте на входящий вызов АТС для участия в разговоре."
        } catch (e: CancellationException) { throw e } catch (e: Exception) { callback = callError(e) } finally { joiningBusy = false } } }) { Text("Запросить подключение") } },
        dismissButton = { TextButton(onClick = { joining = null }) { Text("Отмена") } }) }
}

@Composable
internal fun SharedCallDetails(repository:SharedCallRepository,id:String,onBack:()->Unit,onCall:(String)->Unit,onSMS:(String)->Unit) {
    var detail by remember(id,repository){mutableStateOf<SharedCallDetail?>(null)}
    var error by remember{mutableStateOf("")}
    var reload by remember{mutableIntStateOf(0)}
    var busy by remember{mutableStateOf(false)}
    var draft by rememberSaveable(id){mutableStateOf("")}
    var baseNote by rememberSaveable(id){mutableStateOf("")}
    var revision by rememberSaveable(id){mutableLongStateOf(-1)}
    var leave by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val navigation=LocalWorkspaceNavigation.current
    val dirty=revision>=0&&draft!=baseNote
    SideEffect{navigation?.dirty=dirty;navigation?.busy=busy}
    DisposableEffect(Unit){onDispose{navigation?.dirty=false;navigation?.busy=false}}
    fun back(){if(busy)return;if(dirty)leave=true else onBack()}
    BackHandler{back()}
    LaunchedEffect(repository,id,reload){
        busy=true;error=""
        try{
            val result=repository.detail(id);detail=result
            if(revision<0){draft=result.call.note;baseNote=result.call.note}
            else if(dirty&&result.call.note!=baseNote)error="Заметка на сервере изменилась. Черновик сохранён на экране; сравните его с актуальной заметкой перед сохранением."
            baseNote=result.call.note;revision=result.call.noteRevision
        }catch(e:CancellationException){throw e}catch(e:Exception){error=callError(e)}finally{busy=false}
    }
    Scaffold(topBar={WorkspaceHeader("Карточка звонка","Общий журнал",onBack={back()})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(error.isNotBlank())item{WorkspaceInfo(error,true)}
            item{TextButton(enabled=!busy,onClick={reload++}){Text("Обновить карточку")}}
            detail?.let{info->val call=info.call
                item{HiroContactHero(callTitle(call),call.number,callStatus(call),true)}
                item{WorkspaceCard{
                    Text(callTime(call.startedAt),style=MaterialTheme.typography.titleMedium)
                    if(call.source=="recording_archive")Text("Старая серверная запись без журнала АТС. Номер и участники неизвестны.")
                    else {Text("Длительность · "+callDuration(call.duration));Text("Время соединения · "+callDuration(call.talk))}
                    if(call.line.isNotBlank())Text("Линия · "+call.line)
                    if(call.gatewayType.isNotBlank())Text("Шлюз · "+callGatewayLabel(call.gatewayType))
                    if(call.operators.isNotEmpty())Text("Участники · "+call.operators.joinToString(" · "))
                }}
                if(callCanDial(call.number)&&info.capabilities.notes)item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(onClick={if(dirty)error="Сохраните заметку перед переходом" else onCall(call.number)},modifier=Modifier.weight(1f)){Text("Позвонить")}
                    OutlinedButton(onClick={if(dirty)error="Сохраните заметку перед переходом" else onSMS(call.number)},modifier=Modifier.weight(1f)){Text("Написать")}
                }}
                if(call.recording&&info.capabilities.recordings)item{SharedRecordingPlayer(repository,id)}
                else item{WorkspaceInfo(if(!call.recording)"Серверная запись этого разговора отсутствует." else "Прослушивание доступно менеджерам и администраторам.")}
                item{WorkspaceCard{
                    Text("Заметка команды",style=MaterialTheme.typography.titleLarge)
                    if(info.capabilities.notes){
                        if(dirty&&call.note!=draft)Text("На сервере: "+call.note.ifBlank{"Пустая заметка"})
                        OutlinedTextField(draft,{if(it.toByteArray().size<=8192)draft=it},label={Text("Итоги и следующий шаг")},enabled=!busy,modifier=Modifier.fillMaxWidth(),minLines=3)
                        Button(enabled=dirty&&!busy,onClick={scope.launch{busy=true;error="";try{
                            val saved=repository.note(id,draft,revision);detail=saved;draft=saved.call.note;baseNote=draft;revision=saved.call.noteRevision
                        }catch(e:CancellationException){throw e}catch(e:Exception){error=if(e is HiroServerException&&e.code=="note_conflict")"Другой менеджер изменил заметку. Черновик не потерян. Обновите карточку и сравните изменения." else callError(e)}finally{busy=false}}},modifier=Modifier.fillMaxWidth()){Text("Сохранить заметку")}
                    }else Text(call.note.ifBlank{"Заметок пока нет"})
                }}
                item{WorkspaceInfo("Автоматическое резюме ИИ не подменяет запись звонка. Для этой карточки оно пока не подключено.")}
            }
        }
    }
    if(leave)AlertDialog(onDismissRequest={leave=false},title={Text("Не сохранять заметку?")},text={Text("Черновик будет потерян. Серверная заметка не изменится.")},confirmButton={TextButton(onClick={navigation?.dirty=false;leave=false;onBack()}){Text("Выйти без сохранения")}},dismissButton={TextButton(onClick={leave=false}){Text("Остаться")}})
}

@Composable
private fun SharedRecordingPlayer(repository:SharedCallRepository,id:String) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var player by remember(repository,id){mutableStateOf<MediaPlayer?>(null)}
    var file by remember(repository,id){mutableStateOf<File?>(null)}
    var loading by remember{mutableStateOf(false)}
    var playing by remember{mutableStateOf(false)}
    var position by remember{mutableIntStateOf(0)}
    var duration by remember{mutableIntStateOf(0)}
    var error by remember{mutableStateOf("")}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(repository,id){onDispose{player?.release();file?.delete()}}
    LaunchedEffect(player){while(true){player?.let{position=runCatching{it.currentPosition}.getOrDefault(0)};delay(250)}}
    LaunchedEffect(lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){try{kotlinx.coroutines.awaitCancellation()}finally{runCatching{player?.pause()};playing=false}}}
    WorkspaceCard{
        Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.GraphicEq,null);Spacer(Modifier.width(8.dp));Text("Запись разговора",style=MaterialTheme.typography.titleLarge)}
        Text("Доступ для менеджеров и администраторов. Прослушивание учитывается сервером.",style=MaterialTheme.typography.bodySmall)
        if(error.isNotBlank())WorkspaceInfo(error,true)
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        Slider(value=position.toFloat().coerceIn(0f,duration.coerceAtLeast(1).toFloat()),onValueChange={position=it.toInt();player?.seekTo(position)},valueRange=0f..duration.coerceAtLeast(1).toFloat(),enabled=player!=null)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(callDuration(position/1000L)+" / "+callDuration(duration/1000L),Modifier.weight(1f))
            FilledTonalButton(enabled=!loading,onClick={
                if(player!=null){if(playing)player?.pause()else player?.start();playing=!playing}
                else scope.launch{
                    loading=true;error=""
                    val owned=try{File.createTempFile("hiro-call-",".wav",context.cacheDir)}catch(_:Exception){loading=false;error="Недостаточно места для записи";return@launch};file=owned
                    var prepared:MediaPlayer?=null
                    try{
                        repository.recording(id,owned)
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        prepared=MediaPlayer().apply{
                            setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                            setDataSource(owned.absolutePath);prepare()
                            setOnCompletionListener{playing=false;position=duration}
                            setOnErrorListener{_,_,_->playing=false;error="Не удалось воспроизвести запись";true}
                        }
                        duration=prepared.duration;player=prepared;prepared=null;player?.start();playing=true
                    }catch(e:CancellationException){owned.delete();throw e}
                    catch(e:Exception){owned.delete();error=if(e is HiroServerException)callError(e) else "Не удалось воспроизвести запись"}
                    finally{prepared?.release();loading=false}
                }
            }){Icon(if(playing)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,null);Spacer(Modifier.width(6.dp));Text(if(playing)"Пауза" else "Слушать")}
        }
    }
}
