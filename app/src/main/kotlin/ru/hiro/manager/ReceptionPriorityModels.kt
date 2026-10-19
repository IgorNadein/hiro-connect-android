package ru.hiro.manager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Reception of one call, not queue priority, access, or SMS subscription. */
@Serializable
data class HiroReceptionRule(
    @SerialName("line_id") val lineId: String = "",
    @SerialName("gateway_type") val gatewayType: String = "",
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("sim_id") val simId: String = "",
    val direction: String = "inbound",
    val priority: Int = 100,
    @SerialName("caller_number") val callerNumber: String = ""
)
@Serializable
data class HiroReceptionPreferences(
    val revision: Long = 0,
    val rules: List<HiroReceptionRule> = emptyList(),
    @SerialName("default_priority") val defaultPriority: Int = 0,
    @SerialName("applied_to_live_calls") val appliedToLiveCalls: Boolean = false,
    @SerialName("execution_blocker") val executionBlocker: String = ""
)
@Serializable data class HiroReceptionUpdate(val rules: List<HiroReceptionRule>)
@Serializable data class HiroReceptionPreviewRequest(
    @SerialName("line_id") val lineId: String,
    @SerialName("caller_number") val callerNumber: String = ""
)
@Serializable data class HiroReceptionEffective(val priority: Int, val rule: HiroReceptionRule? = null)
@Serializable data class HiroReceptionPreview(
    val effective: HiroReceptionEffective,
    val scope: String,
    @SerialName("changes_access") val changesAccess: Boolean,
    @SerialName("applied_to_live_calls") val appliedToLiveCalls: Boolean
)

internal fun receptionScope(rule: HiroReceptionRule): String = when {
    rule.lineId.isNotEmpty() -> "line"
    rule.simId.isNotEmpty() -> "sim"
    rule.deviceId.isNotEmpty() -> "device"
    rule.gatewayType.isNotEmpty() -> "type"
    else -> "all"
}
internal fun receptionRuleKey(r: HiroReceptionRule): String =
    listOf(r.lineId,r.deviceId,r.simId,r.gatewayType,r.callerNumber).joinToString("\u001f")

internal fun receptionRuleTarget(r: HiroReceptionRule, lines: List<HiroVoiceLine>): String = when(receptionScope(r)) {
    "line" -> lines.find { it.id==r.lineId }?.name ?: "Недоступная линия · ${r.lineId}"
    "sim" -> "SIM ${r.simId} · ${lines.find { it.deviceId==r.deviceId && it.simId==r.simId }?.name ?: r.deviceId}"
    "device" -> "Телефон · ${lines.find { it.deviceId==r.deviceId }?.name ?: r.deviceId}"
    "type" -> when(r.gatewayType) { "novofon"->"Все NOVOFON"; "mango"->"Все MANGO OFFICE"; "mobile"->"Все телефоны-шлюзы"; else->"Тип шлюза · ${r.gatewayType}" }
    else -> "Все доступные линии"
}

internal fun receptionRuleSupported(r: HiroReceptionRule, lines: List<HiroVoiceLine>): Boolean {
    if(r.direction!="inbound" || r.priority !in 0..100 ||
        listOf(r.lineId,r.deviceId,r.gatewayType).count{it.isNotEmpty()}>1 ||
        r.simId.isNotEmpty() && r.deviceId.isEmpty()) return false
    if(r.callerNumber.isNotEmpty() && normalizeClientNumber(r.callerNumber)!=r.callerNumber) return false
    return receptionScope(r)=="all" || lines.any { l -> l.enabled && when(receptionScope(r)) {
        "line" -> l.id==r.lineId
        "sim" -> l.deviceId==r.deviceId && l.simVerified && l.simId==r.simId
        "device" -> l.deviceId==r.deviceId
        "type" -> l.gatewayType==r.gatewayType
        else -> false
    } }
}

/** Identity is a stable scope+caller key, never a mutable list index after reload. */
internal fun replaceReceptionRule(rules: List<HiroReceptionRule>, originalKey: String?, rule: HiroReceptionRule): List<HiroReceptionRule> {
    require(originalKey==null || rules.any { receptionRuleKey(it)==originalKey }) { "Правило уже удалено на другом устройстве. Вернитесь к списку; черновик остаётся на экране." }
    val remaining=rules.filterNot { receptionRuleKey(it)==originalKey }
    require(remaining.none { receptionRuleKey(it)==receptionRuleKey(rule) }) { "Для этой области и номера уже есть правило. Измените существующее." }
    return remaining+rule
}
