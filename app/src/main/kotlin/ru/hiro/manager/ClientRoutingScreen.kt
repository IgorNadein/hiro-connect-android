package ru.hiro.manager

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.composable

fun NavGraphBuilder.clientRoutingScreenRoute(nav: NavController, calls: ViewModel, auth: HiroAuthViewModel, user: HiroUser) {
    composable("client-routing?personal={personal}", arguments = listOf(navArgument("personal") { type = NavType.StringType; defaultValue = "false" })) { entry ->
        val state by auth.state.collectAsState()
        val server = (state as? HiroAuthState.SignedIn)?.session?.apiBaseUrl
        key(user.id, server) { ClientDirectoryScreen(nav, auth, user, entry.arguments?.getString("personal") == "true") }
    }
}

internal fun replaceRule(rules: List<HiroPriorityRule>, new: HiroPriorityRule) = rules.filterNot {
    it.lineId == new.lineId && it.deviceId == new.deviceId && it.simId == new.simId && it.gatewayType == new.gatewayType && it.direction == new.direction
} + new

internal fun ruleDescription(rule: HiroPriorityRule, lines: List<HiroVoiceLine>): String {
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
internal fun PriorityRuleDialog(lines: List<HiroVoiceLine>, busy: Boolean, error: String, onCancel: () -> Unit, onSave: (HiroPriorityRule) -> Unit) {
    val active = lines.filter { it.enabled }
    val choices = listOf("all" to "Все линии") + active.map { "line:${it.id}" to "Линия: ${it.name}" } +
        active.filter { it.deviceId.isNotEmpty() }.distinctBy { it.deviceId }.map { "device:${it.deviceId}" to "Устройство: ${it.deviceId}" } +
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
internal fun RoutingChoice(label: String, choices: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label: ${choices.find { it.first == selected }?.second ?: "Выберите"}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(key); open = false }) }
        }
    }
}
