package ru.hiro.manager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

internal val hiroRoutingJson = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }

@Serializable
data class HiroVoiceLine(
    val id: String, val name: String, val code: String,
    @SerialName("gateway_type") val gatewayType: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("sim_id") val simId: String = "",
    @SerialName("sim_verified") val simVerified: Boolean = false,
    val enabled: Boolean
)
@Serializable
data class HiroVoiceLines(val lines: List<HiroVoiceLine>)
@Serializable
data class HiroPriorityRule(
    @SerialName("line_id") val lineId: String = "",
    @SerialName("gateway_type") val gatewayType: String = "",
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("sim_id") val simId: String = "",
    val direction: String = "both", val priority: Int = 50
)
@Serializable
data class HiroClient(val number: String, val name: String, val rules: List<HiroPriorityRule>)
@Serializable
data class HiroClientDirectory(val revision: Long, val clients: List<HiroClient>)
@Serializable
data class HiroClientUpdate(val name: String, val rules: List<HiroPriorityRule>)
@Serializable
data class HiroExternalClientUpdate(val name: String, val rules: List<HiroPriorityRule>,
    @SerialName("introduced_by_user_id") val origin: JsonElement = JsonNull)
@Serializable
data class HiroRoutingPolicies(val revision: Long, val policies: List<HiroPriorityRule>)
@Serializable
data class HiroPolicyUpdate(val policies: List<HiroPriorityRule>)

fun normalizeClientNumber(raw: String): String? {
    val value = raw.trim().removePrefix("tel:")
    if (!value.matches(Regex("[+0-9 ()-]+"))) return null
    var n = value.replace(Regex("[ ()-]"), "")
    if (n.length == 11 && n.first() in "78") n = "+7" + n.drop(1)
    return n.takeIf { it.matches(Regex("\\+[1-9][0-9]{7,14}")) }
}
