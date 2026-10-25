package ru.hiro.manager

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun GatewaySIMPreferences(auth: HiroAuthViewModel) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<HiroSIMSubscriptions?>(null) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    fun update(value: HiroSIMSubscriptions) {
        state = value
        selected = if (!value.preference.configured) value.inventory.items.map { it.id }.toSet()
            else value.preference.smsSubscriptionIds.toSet()
    }
    fun work(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true; error = ""; saved = false
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: HiroServerException) { error = e.userMessage }
            catch (_: Exception) { error = "Не удалось получить SIM. Проверьте соединение с сервером" }
            finally { busy = false }
        }
    }
    LaunchedEffect(auth) { work { update(auth.authenticatedRequest { c,s -> c.simSubscriptions(s) }) } }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Мои SIM для приёма", style = MaterialTheme.typography.titleLarge)
            Text("Это SIM общего телефона-шлюза, не смартфона менеджера. Настройки личные и одинаковы на всех ваших устройствах. Одну SIM могут обслуживать несколько менеджеров.")
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state?.let { current ->
                if (current.gatewayChanged) Text("Телефон-шлюз заменён. Сохранённые назначения не переносятся автоматически.", color = MaterialTheme.colorScheme.error)
                if (current.smsReceiveSupported && current.inventory.items.isEmpty()) Text("Активных SIM нет. Проверьте SIM и разрешение «Телефон» в приложении на шлюзе.")
                current.inventory.items.sortedBy { it.slotIndex }.forEach { sim ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(sim.label, Modifier.weight(1f).padding(top = 12.dp))
                        Switch(checked = sim.id in selected && !current.gatewayChanged,
                            enabled = !busy && current.smsReceiveSupported,
                            onCheckedChange = { checked ->
                                selected = if (current.gatewayChanged) emptySet() else selected
                                state = current.copy(gatewayChanged = false)
                                selected = if (checked) selected + sim.id else selected - sim.id
                                saved = false
                            })
                    }
                }
                val activeIDs = current.inventory.items.map { it.id }.toSet()
                if (current.smsReceiveSupported && !current.gatewayChanged && (selected - activeIDs).isNotEmpty()) Text("Одна из ранее выбранных SIM не активна. При сохранении останутся только выбранные активные SIM.")
                if (current.smsReceiveSupported) Text("Переключатели управляют входящими SMS и уведомлениями. Пустой выбор отключает их приём. SIM отправки выбирается отдельно в диалоге.")
                Text(if (current.smsReceiveSupported) "Звонки по отдельным SIM пока недоступны" else "Приём через телефон-шлюз недоступен", style = MaterialTheme.typography.titleSmall)
                Text(current.voiceLimitation)
                Button(enabled = !busy && current.smsReceiveSupported, onClick = { work {
                    val ids = if (current.gatewayChanged) emptyList() else (selected intersect activeIDs).sorted()
                    update(auth.authenticatedRequest { c,s -> c.saveSIMSubscriptions(s, current.inventory.deviceId, ids, current.preference.revision) })
                    saved = true
                } }) { Text("Сохранить выбор SMS") }
                if (saved) Text("Сохранено. Новые SMS и уведомления будут приходить только с выбранных SIM.")
            }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(enabled = !busy, onClick = { work { update(auth.authenticatedRequest { c,s -> c.simSubscriptions(s) }) } }) { Text("Обновить SIM шлюза") }
        }
    }
}
