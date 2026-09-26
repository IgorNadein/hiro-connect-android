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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun NavGraphBuilder.gatewayMessagesScreenRoute(
    navController: NavController,
    session: HiroSession
) {
    composable("gateway-messages") {
        GatewayMessagesScreen(navController, session)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GatewayMessagesScreen(navController: NavController, session: HiroSession) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val client = remember { HiroServerClient() }
    val syncStore = remember { GatewayMessageSyncStore(context) }
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<HiroSmsMessage>>(emptyList()) }
    var outbox by remember { mutableStateOf<List<HiroSmsOutboxMessage>>(emptyList()) }
    var selectedAddress by remember { mutableStateOf<String?>(null) }
    var newAddress by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    suspend fun refresh() {
        var gatewayError: String? = null
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
        if (selectedAddress != null) {
            selectedAddress = null
            draft = ""
        } else {
            navController.navigateUp()
        }
    }

    val activeAddress = selectedAddress
    val displayMessages = remember(messages, outbox) {
        val phoneMessageIDs = messages.map { it.id }.toSet()
        messages.map {
            GatewaySmsItem(
                id = "phone-${it.id}",
                address = it.address.orEmpty(),
                text = it.text.orEmpty(),
                timestamp = it.timestamp ?: it.sentAt ?: 0,
                direction = it.direction.orEmpty(),
                status = localizedSmsStatus(it.direction.orEmpty(), it.status.orEmpty())
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
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeAddress ?: "Сообщения",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (activeAddress == null) "SMS через телефон-шлюз" else "Через сервер",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeAddress != null) selectedAddress = null else navController.navigateUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        loading = true
                        refreshKey++
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        },
        bottomBar = {
            if (activeAddress != null) {
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
                        label = { Text("Сообщение") },
                        maxLines = 4
                    )
                    IconButton(
                        enabled = draft.isNotBlank() && !sending,
                        onClick = {
                            val outgoing = draft
                            draft = ""
                            sending = true
                            error = null
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        client.sendSms(
                                            session = session,
                                            clientMessageId = UUID.randomUUID().toString(),
                                            address = activeAddress,
                                            text = outgoing
                                        )
                                    }
                                    refresh()
                                } catch (failure: HiroServerException) {
                                    draft = outgoing
                                    error = failure.userMessage
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
}

@Composable
private fun ConversationList(
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

    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newAddress,
                onValueChange = onNewAddressChange,
                modifier = Modifier.weight(1f),
                label = { Text("Номер телефона") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            Button(enabled = newAddress.isNotBlank(), onClick = { onOpen(newAddress.trim()) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Новое")
            }
        }
        if (conversations.isEmpty() && error == null) {
            Text(
                "Сообщений пока нет. Введите номер, чтобы начать диалог.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(conversations, key = { it.address }) { message ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(message.address) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(message.address, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(formatSmsTime(message.timestamp), style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                message.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageThread(
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
                        modifier = Modifier.fillMaxWidth(0.82f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (outgoing) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(message.text)
                            Text(
                                listOf(formatSmsTime(message.timestamp), message.status)
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

private data class GatewaySmsItem(
    val id: String,
    val address: String,
    val text: String,
    val timestamp: Long,
    val direction: String,
    val status: String
)
