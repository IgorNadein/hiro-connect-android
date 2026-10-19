package ru.hiro.manager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun NavGraphBuilder.gatewayMessagesScreenRoute(
    navController: NavController,
    session: HiroSession
) {
    composable("gateway-messages?address={address}",arguments=listOf(navArgument("address"){type=NavType.StringType;nullable=true;defaultValue=null})) { entry ->
        androidx.compose.runtime.key(session.apiBaseUrl,session.user.id) { GatewayMessagesScreen(navController, session, entry.arguments?.getString("address")) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GatewayMessagesScreen(navController: NavController, session: HiroSession, initialAddress: String? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val client = remember { HiroServerClient() }
    val syncStore = remember { GatewayMessageSyncStore(context) }
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<HiroSmsMessage>>(emptyList()) }
    var outbox by remember { mutableStateOf<List<HiroSmsOutboxMessage>>(emptyList()) }
    var simCards by remember { mutableStateOf<List<HiroSIMCard>>(emptyList()) }
    var sendSIM by remember { mutableStateOf<Long?>(null) }
    var simReady by remember { mutableStateOf(false) }
    var simMenu by remember { mutableStateOf(false) }
    var selectedAddress by rememberSaveable { mutableStateOf<String?>(initialAddress?.takeIf { normalizeClientNumber(it)!=null }) }
    var newAddress by remember { mutableStateOf("") }
    var draft by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val navigation = LocalWorkspaceNavigation.current
    var leaveThread by remember { mutableStateOf(false) }
    var retryId by rememberSaveable { mutableStateOf("") }
    var retryPayload by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(draft) { navigation?.dirty = draft.isNotBlank() }
    LaunchedEffect(sending) { navigation?.busy = sending }
    DisposableEffect(Unit) { onDispose { navigation?.dirty = false; navigation?.busy = false } }
    fun leave() {
        if(sending) return
        if(draft.isNotBlank()) { leaveThread = true; return }
        if(selectedAddress != null) selectedAddress = null else navController.navigateUp()
    }

    suspend fun refresh() {
        var gatewayError: String? = null
        try {
            val inventory = withContext(Dispatchers.IO) { client.simSubscriptions(session).inventory }
            simCards = inventory.items.sortedBy { it.slotIndex }
            if (simCards.none { it.id == sendSIM }) sendSIM = simCards.singleOrNull()?.id
            simReady = true
        } catch (failure: CancellationException) { throw failure }
        catch (failure: Exception) {
            simReady = false
            gatewayError = (failure as? HiroServerException)?.userMessage ?: "Не удалось прочитать SIM телефона-шлюза"
        }
        try {
            messages = withContext(Dispatchers.IO) { client.smsMessages(session).items }
            syncStore.clearUnread()
        } catch (failure: HiroServerException) {
            gatewayError = failure.userMessage
        } catch (_: Exception) {
            gatewayError = "Телефон-шлюз недоступен"
        }
        try {
            outbox = withContext(Dispatchers.IO) { client.smsOutbox(session).items }
            error = gatewayError
        } catch (failure: HiroServerException) {
            error = failure.userMessage
        } catch (_: Exception) {
            error = "Не удалось загрузить очередь SMS"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(session.accessToken, refreshKey) {
        refresh()
        while (true) {
            delay(5_000)
            refresh()
        }
    }

    BackHandler {
        leave()
    }

    val activeAddress = selectedAddress
    val displayMessages = remember(messages, outbox, simCards) {
        val phoneMessageIDs = messages.map { it.id }.toSet()
        messages.map {
            GatewaySmsItem(
                id = "phone-${it.id}",
                address = it.address.orEmpty(),
                text = it.text.orEmpty(),
                timestamp = it.timestamp ?: it.sentAt ?: 0,
                direction = it.direction.orEmpty(),
                status = localizedSmsStatus(it.direction.orEmpty(), it.status.orEmpty()),
                simLabel = smsSIMLabel(it.subscriptionId, simCards)
            )
        } + outbox
            .filter { it.localMessageId == null || it.localMessageId !in phoneMessageIDs }
            .map {
                GatewaySmsItem(
                    id = "outbox-${it.clientMessageId}",
                    address = it.address,
                    text = it.text,
                    timestamp = parseServerTime(it.createdAt),
                    direction = "outbound",
                    simLabel = smsSIMLabel(it.subscriptionId, simCards),
                    status = when (it.status) {
                        "queued" -> "в очереди"
                        "submitted" -> "передано шлюзу"
                        "failed" -> "ошибка"
                        else -> it.status
                    }
                )
            }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WorkspaceHeader(
                title=activeAddress ?: "Сообщения",
                subtitle=if(activeAddress==null)"SMS через телефон-шлюз" else "Через сервер",
                onBack=if(activeAddress!=null){{leave()}}else null,
                actions = {
                    if(activeAddress == null) {
                        IconButton(onClick={navController.navigate("chats")}){Icon(Icons.AutoMirrored.Filled.Chat,"Открыть SIP-чаты")}
                        IconButton(onClick={navController.navigate("workspace-receive")}){Icon(Icons.Default.Settings,"Выбор SIM для приёма SMS")}
                    }
                    IconButton(onClick = {
                        loading = true
                        refreshKey++
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        },
        bottomBar = {
            if (activeAddress != null) {
                Column {
                    androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 12.dp)) {
                        OutlinedButton(enabled = simReady && !sending, onClick = { simMenu = true }) {
                            Text("Отправить через: " + (simCards.find { it.id == sendSIM }?.label ?: "Выберите SIM"))
                        }
                        DropdownMenu(expanded = simMenu, onDismissRequest = { simMenu = false }) {
                            simCards.forEach { sim -> DropdownMenuItem(text = { Text(sim.label) }, onClick = { sendSIM = sim.id; simMenu = false }) }
                        }
                    }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Сообщение") },
                        shape = RoundedCornerShape(16.dp),
                        enabled = !sending,
                        maxLines = 4
                    )
                    IconButton(
                        enabled = draft.isNotBlank() && !sending && simReady && simCards.any { it.id == sendSIM },
                        onClick = {
                            val outgoing = draft
                            val outgoingSIM = sendSIM ?: return@IconButton
                            val payload = java.security.MessageDigest.getInstance("SHA-256").digest((activeAddress+"\n"+outgoingSIM+"\n"+outgoing).toByteArray()).joinToString(""){"%02x".format(it)}
                            if(retryId.isEmpty() || retryPayload != payload) { retryId = UUID.randomUUID().toString(); retryPayload = payload }
                            val messageId = retryId
                            sending = true
                            error = null
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        client.sendSms(
                                            session = session,
                                            clientMessageId = messageId,
                                            address = activeAddress,
                                            text = outgoing,
                                            subscriptionId = outgoingSIM
                                        )
                                    }
                                    draft = ""; retryId = ""; retryPayload = ""
                                    refresh()
                                } catch (failure: CancellationException) { throw failure }
                                catch (failure: HiroServerException) {
                                    draft = outgoing
                                    error = failure.userMessage
                                } catch (_: Exception) {
                                    draft = outgoing
                                    error = "Не удалось отправить SMS. Проверьте соединение"
                                } finally {
                                    sending = false
                                }
                            }
                        }
                    ) {
                        if (sending) CircularProgressIndicator() else
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                    }
                }
                }
            }
        }
    ) { padding ->
        when {
            loading && messages.isEmpty() && outbox.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }

            activeAddress == null -> ConversationList(
                padding = padding,
                messages = displayMessages,
                newAddress = newAddress,
                onNewAddressChange = { newAddress = it },
                error = error,
                onOpen = {
                    selectedAddress = it
                    sendSIM = simCards.singleOrNull()?.id
                    newAddress = ""
                }
            )

            else -> MessageThread(
                padding = padding,
                messages = displayMessages.filter { it.address == activeAddress },
                error = error
            )
        }
    }
    if(leaveThread) AlertDialog(onDismissRequest = { leaveThread=false }, title = { Text("Неотправленное сообщение") }, text = { Text("Текст не будет отправлен. Уйти без сохранения черновика?") },
        confirmButton = { TextButton(onClick={draft="";leaveThread=false;selectedAddress=null}){Text("Не сохранять")}},
        dismissButton = { TextButton(onClick={leaveThread=false}){Text("Остаться")}})
}

@Composable
internal fun ConversationList(
    padding: PaddingValues,
    messages: List<GatewaySmsItem>,
    newAddress: String,
    onNewAddressChange: (String) -> Unit,
    error: String?,
    onOpen: (String) -> Unit
) {
    val conversations = messages
        .filter { it.address.isNotBlank() }
        .groupBy { it.address }
        .mapValues { (_, items) -> items.maxByOrNull { it.timestamp }!! }
        .values
        .sortedByDescending { it.timestamp }

    var composing by rememberSaveable { mutableStateOf(false) }
    var recipient by rememberSaveable { mutableStateOf("") }
    val filtered=conversations.filter{it.address.contains(newAddress,true)||it.text.contains(newAddress,true)}
    LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item{WorkspaceSearch(newAddress,onNewAddressChange,"Поиск в сообщениях")}
        if(!composing)item{Button(onClick={recipient="";composing=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(13.dp)){
            Icon(Icons.Outlined.Edit,null,Modifier.size(19.dp));Spacer(Modifier.width(8.dp));Text("Новое сообщение")
        }} else item{WorkspaceCard{
            Text("Кому написать?",style=MaterialTheme.typography.titleLarge)
            Text("Номер с кодом страны. SIM отправки вы выберете в диалоге.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(recipient,{recipient=it},modifier=Modifier.fillMaxWidth(),label={Text("Номер телефона")},singleLine=true,shape=RoundedCornerShape(13.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                TextButton(onClick={composing=false},modifier=Modifier.weight(1f)){Text("Отмена")}
                Button(enabled=normalizeClientNumber(recipient)!=null,onClick={normalizeClientNumber(recipient)?.let{composing=false;onOpen(it)}},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("Начать диалог")}
            }
        }}
        if(error!=null)item{WorkspaceInfo(error,true)}
        item{HiroSection("Диалоги",if(filtered.isEmpty())"" else "${filtered.size}")}
        if(filtered.isEmpty()&&error==null)item{HiroEmpty(Icons.Outlined.Forum,if(newAddress.isBlank())"Сообщений пока нет" else "Диалог не найден",if(newAddress.isBlank())"Начните диалог по рабочей SIM. Ответы появятся здесь." else "Попробуйте другой номер или текст сообщения.")}
        items(filtered,key={it.address}){message->Surface(color=MaterialTheme.colorScheme.surfaceContainerLowest,shape=RoundedCornerShape(16.dp)){
            HiroPersonRow(message.address,message.text,message.simLabel,hiroListTime(message.timestamp),Icons.Outlined.ChatBubbleOutline){onOpen(message.address)}
        }}
    }
}

@Composable
internal fun MessageThread(
    padding: PaddingValues,
    messages: List<GatewaySmsItem>,
    error: String?
) {
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = true
        ) {
            items(messages.sortedByDescending { it.timestamp }, key = { it.id }) { message ->
                val outgoing = message.direction == "outbound"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        modifier = Modifier.widthIn(max=320.dp).fillMaxWidth(0.88f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (outgoing) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLowest
                        ),
                        shape = RoundedCornerShape(topStart=18.dp,topEnd=18.dp,bottomEnd=if(outgoing)4.dp else 18.dp,bottomStart=if(outgoing)18.dp else 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(message.text)
                            Text(
                                listOf(formatSmsTime(message.timestamp), message.status, message.simLabel)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (message.status == "ошибка") MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSmsTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0) return ""
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
}

private fun parseServerTime(value: String): Long =
    runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

private fun localizedSmsStatus(direction: String, status: String): String = when {
    direction == "inbound" -> "получено"
    status == "delivered" -> "доставлено"
    status == "sent" -> "отправлено"
    status == "queued" || status == "pending" -> "отправляется"
    status == "failed" -> "ошибка"
    status == "received" -> "получено"
    else -> status
}

internal data class GatewaySmsItem(
    val id: String,
    val address: String,
    val text: String,
    val timestamp: Long,
    val direction: String,
    val status: String,
    val simLabel: String
)
