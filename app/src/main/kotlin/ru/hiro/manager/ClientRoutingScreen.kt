package ru.hiro.manager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LocalClient(val number: String, val name: String)

private fun scanPhoneContacts(ctx: Context): List<LocalClient> {
    val contacts = linkedMapOf<String, LocalClient>()
    ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        null, null, null)?.use { cursor ->
        while (cursor.moveToNext() && contacts.size < 10000) {
            val number = normalizeClientNumber(cursor.getString(1) ?: "") ?: continue
            contacts.putIfAbsent(number, LocalClient(number, cursor.getString(0) ?: ""))
        }
    }
    return contacts.values.toList()
}

fun NavGraphBuilder.clientRoutingScreenRoute(nav: NavController, calls: ViewModel, auth: HiroAuthViewModel, user: HiroUser) {
    composable("client-routing") { ClientRoutingScreen(nav, calls, auth, user) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientRoutingScreen(nav: NavController, calls: ViewModel, auth: HiroAuthViewModel, user: HiroUser) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<HiroVoiceLine>>(emptyList()) }
    var directory by remember { mutableStateOf(HiroClientDirectory(0, emptyList())) }
    var policies by remember { mutableStateOf(HiroRoutingPolicies(0, emptyList())) }
    var local by remember { mutableStateOf<List<LocalClient>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var clientsOnly by rememberSaveable { mutableStateOf(false) }
    var number by rememberSaveable { mutableStateOf("") }
    var lineID by rememberSaveable { mutableStateOf("") }
    var aor by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<LocalClient?>(null) }
    var draft by remember { mutableStateOf<List<HiroPriorityRule>>(emptyList()) }
    var isClient by remember { mutableStateOf(true) }
    var newRule by remember { mutableStateOf(false) }
    var organizationRule by remember { mutableStateOf(false) }
    val canEdit = user.role in setOf("manager", "admin")
    val agents = BaresipService.uas.value
    val sipAccounts = agents.filter { !it.account.isMobile && it.uap != 0L }

    fun work(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true; message = ""
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: HiroServerException) { message = e.userMessage }
            catch (_: Exception) { message = "Не удалось выполнить действие. Проверьте разрешения и соединение" }
            finally { busy = false }
        }
    }
    suspend fun reload() {
        lines = auth.authenticatedRequest { c, s -> c.voiceLines(s) }.lines
        if (canEdit) {
            directory = auth.authenticatedRequest { c, s -> c.clients(s) }
            policies = auth.authenticatedRequest { c, s -> c.policies(s) }
        }
        loaded = true
    }
    fun scan() = work {
        local = withContext(Dispatchers.IO) { scanPhoneContacts(ctx) }
        message = "Найдено номеров: ${local.size}. На сервер отправляются только отмеченные клиенты"
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scan() else message = "Чтение контактов не разрешено. Номер можно добавить вручную"
    }
    LaunchedEffect(user.id) { work { reload() } }
    val clientsByNumber = remember(directory) { directory.clients.associateBy { it.number } }
    val candidates = remember(local, directory, query, clientsOnly) {
        (directory.clients.map { LocalClient(it.number, it.name) } + local).distinctBy { it.number }
            .filter { (!clientsOnly || it.number in clientsByNumber) && (query.isBlank() || it.name.contains(query, true) || it.number.contains(query)) }
            .sortedWith(compareBy<LocalClient> { it.number !in clientsByNumber }.thenBy { it.name.lowercase() }.thenBy { it.number })
    }
    fun edit(c: LocalClient) { editing = c; draft = clientsByNumber[c.number]?.rules ?: emptyList(); isClient = true }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.clients_and_routes)) },
        navigationIcon = { IconButton(onClick = { nav.navigateUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Явная линия → SIM → устройство → тип шлюза → общее правило. Клиенты обслуживаются раньше обычных звонков; текущие разговоры не прерываются.")
                Text("Приоритет не устраняет занятость SIM у оператора. SIM выбирается только для подтверждённых голосовых линий.")
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { work { reload() } }, enabled = !busy) { Text("Обновить с сервера") }
            }
            if (canEdit) item { key(user.id) { GatewaySIMPreferences(auth) } }
            item {
                Text("Исходящий звонок", style = MaterialTheme.typography.titleLarge)
                RoutingChoice("Линия", listOf("" to "Автоматически по правилам") + lines.filter { it.enabled }.map { it.id to "${it.name} · ${it.gatewayType}" }, lineID) { lineID = it }
                RoutingChoice("SIP-аккаунт нашей АТС", sipAccounts.map { it.account.aor to it.account.aor }, aor) { aor = it }
                OutlinedTextField(number, { number = it }, label = { Text("Номер с кодом страны") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(enabled = loaded && !busy && user.role != "viewer" && normalizeClientNumber(number) != null && sipAccounts.any { it.account.aor == aor } && (lineID.isEmpty() || lines.any { it.id == lineID && it.enabled }), onClick = {
                    val ua = UserAgent.ofAor(aor)
                    val destination = normalizeClientNumber(number)
                    val line = lines.find { it.id == lineID && it.enabled }
                    if (ua != null && destination != null) {
                        if (ua.status != BaresipService.circleGreen.getValue(BaresipService.colorblind)) message = "SIP-аккаунт не зарегистрирован"
                        else {
                            val extension = "*88${line?.code ?: "0"}*$destination"
                            handleIntent(ctx, calls, Intent(ctx, MainActivity::class.java).putExtra("uap", ua.uap)
                                .putExtra("peer", "sip:$extension@${Utils.aorDomain(aor)}"), "call")
                        }
                    }
                }) { Text("Позвонить") }
            }
            if (user.role == "admin") item {
                HorizontalDivider()
                Text("Общие правила организации", style = MaterialTheme.typography.titleLarge)
                Text("Для входящих и автоматического выбора исходящей линии. Правило клиента уточняет правило той же области.")
                policies.policies.forEachIndexed { index, rule ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(ruleDescription(rule, lines), Modifier.weight(1f))
                        TextButton(enabled = !busy, onClick = { work {
                            policies = auth.authenticatedRequest { c, s -> c.savePolicies(s, policies.policies.filterIndexed { i, _ -> i != index }, policies.revision) }
                            reload()
                        } }) { Text("Удалить") }
                    }
                }
                TextButton(enabled = loaded && !busy, onClick = { organizationRule = true; newRule = true }) { Text("Добавить правило организации") }
            }
            if (canEdit) {
                item {
                    HorizontalDivider()
                    Text("Контакты и клиенты", style = MaterialTheme.typography.titleLarge)
                    Button(enabled = !busy && loaded, onClick = {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) scan()
                        else permission.launch(Manifest.permission.READ_CONTACTS)
                    }) { Text("Сканировать контакты телефона") }
                    Text("Чтение — только по вашему действию. Контакты Android не изменяются; адресная книга целиком не загружается.")
                    OutlinedTextField(query, { query = it }, label = { Text("Имя или номер") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row { Checkbox(clientsOnly, { clientsOnly = it }); Text("Только клиенты", Modifier.padding(top = 12.dp)) }
                    TextButton(enabled = normalizeClientNumber(number) != null && loaded && !busy, onClick = { edit(LocalClient(normalizeClientNumber(number)!!, "")) }) { Text("Добавить введённый выше номер как клиента") }
                }
                items(candidates, key = { it.number }) { c ->
                    ListItem(modifier = Modifier.clickable(enabled = !busy) { edit(c) }, headlineContent = { Text(c.name.ifBlank { c.number }) },
                        supportingContent = { Text(c.number) }, trailingContent = { Text(if (c.number in clientsByNumber) "Клиент" else "Отметить") })
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    editing?.let { candidate ->
        var name by remember(candidate.number) { mutableStateOf(candidate.name) }
        AlertDialog(onDismissRequest = { if (!busy) editing = null }, title = { Text(candidate.number) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { if (it.length <= 100) name = it }, label = { Text("Имя клиента") })
                Row { Switch(isClient, { isClient = it }); Text("Клиент", Modifier.padding(12.dp)) }
                Text("Клиент — общий для организации. По умолчанию выше обычных звонков, внутри класса приоритет 50.")
                draft.forEachIndexed { index, rule ->
                    Text(ruleDescription(rule, lines))
                    TextButton(enabled = !busy, onClick = { draft = draft.filterIndexed { i, _ -> i != index } }) { Text("Убрать правило") }
                }
                TextButton(enabled = isClient && !busy, onClick = { organizationRule = false; newRule = true }) { Text("Добавить правило клиента") }
                if (message.isNotEmpty()) Text(message)
            }
        }, confirmButton = { TextButton(enabled = !busy, onClick = { work {
            directory = auth.authenticatedRequest { c, s -> c.saveClient(s, candidate.number, name, draft, directory.revision, !isClient) }
            editing = null; reload()
        } }) { Text("Сохранить") } }, dismissButton = { TextButton(enabled = !busy, onClick = { editing = null }) { Text("Отмена") } })
    }
    if (newRule) PriorityRuleDialog(lines, busy, message, onCancel = { if (!busy) newRule = false }) { rule ->
        if (organizationRule) work {
            policies = auth.authenticatedRequest { c, s -> c.savePolicies(s, replaceRule(policies.policies, rule), policies.revision) }
            newRule = false; reload()
        } else { draft = replaceRule(draft, rule); newRule = false }
    }
}

private fun replaceRule(rules: List<HiroPriorityRule>, new: HiroPriorityRule) = rules.filterNot {
    it.lineId == new.lineId && it.deviceId == new.deviceId && it.simId == new.simId && it.gatewayType == new.gatewayType && it.direction == new.direction
} + new

private fun ruleDescription(rule: HiroPriorityRule, lines: List<HiroVoiceLine>): String {
    val target = when {
        rule.lineId.isNotEmpty() -> "Линия: " + (lines.find { it.id == rule.lineId }?.name ?: rule.lineId)
        rule.simId.isNotEmpty() -> "${rule.deviceId} / SIM ${rule.simId}"
        rule.deviceId.isNotEmpty() -> "Устройство: ${rule.deviceId}"
        rule.gatewayType.isNotEmpty() -> "Тип: ${rule.gatewayType}"
        else -> "Все линии"
    }
    val direction = when (rule.direction) { "inbound" -> "входящие"; "outbound" -> "исходящие"; else -> "оба направления" }
    return "$target · $direction · ${rule.priority}"
}

@Composable
private fun PriorityRuleDialog(lines: List<HiroVoiceLine>, busy: Boolean, error: String, onCancel: () -> Unit, onSave: (HiroPriorityRule) -> Unit) {
    val active = lines.filter { it.enabled }
    val choices = listOf("all" to "Все линии") + active.map { "line:${it.id}" to "Линия: ${it.name}" } +
        active.distinctBy { it.deviceId }.map { "device:${it.deviceId}" to "Устройство: ${it.deviceId}" } +
        active.filter { it.simId.isNotEmpty() && it.simVerified }.distinctBy { it.deviceId to it.simId }.map { "sim:${it.id}" to "${it.deviceId} / SIM ${it.simId}" } +
        active.distinctBy { it.gatewayType }.map { "type:${it.gatewayType}" to "Тип шлюза: ${it.gatewayType} (запасной)" }
    var target by remember { mutableStateOf("all") }
    var direction by remember { mutableStateOf("both") }
    var priority by remember { mutableFloatStateOf(50f) }
    AlertDialog(onDismissRequest = onCancel, title = { Text("Правило приоритета") }, text = {
        Column {
            RoutingChoice("Область", choices, target) { target = it }
            RoutingChoice("Направление", listOf("both" to "Оба", "inbound" to "Входящие", "outbound" to "Исходящие"), direction) { direction = it }
            Text("Приоритет: ${priority.toInt()} (больше — раньше)")
            Slider(priority, { priority = it }, valueRange = 0f..100f, steps = 99)
            Text("Явное правило выше типа шлюза, даже при меньшем числе. Для одинаковой области точное направление выше общего.")
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        val parts = target.split(':', limit = 2); val value = parts.getOrElse(1) { "" }
        val simLine = active.find { it.id == value }
        onSave(HiroPriorityRule(lineId = if (parts[0] == "line") value else "", gatewayType = if (parts[0] == "type") value else "",
            deviceId = when(parts[0]) { "device" -> value; "sim" -> simLine?.deviceId ?: ""; else -> "" },
            simId = if (parts[0] == "sim") simLine?.simId ?: "" else "", direction = direction, priority = priority.toInt()))
    }) { Text("Применить") } }, dismissButton = { TextButton(onClick = onCancel) { Text("Отмена") } })
}

@Composable
private fun RoutingChoice(label: String, choices: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label: ${choices.find { it.first == selected }?.second ?: "Выберите"}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(key); open = false }) }
        }
    }
}
