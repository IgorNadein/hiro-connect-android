package ru.hiro.manager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HiroSIMCard(
    val id: Long,
    @SerialName("display_name") val displayName: String,
    @SerialName("slot_index") val slotIndex: Int
) {
    val label: String get() = "SIM ${slotIndex + 1} · ${displayName.ifBlank { "Без названия" }}"
}

@Serializable
data class HiroSIMInventory(
    @SerialName("device_id") val deviceId: String,
    val items: List<HiroSIMCard>
)

@Serializable
data class HiroSIMPreference(
    val revision: Long = 0,
    val configured: Boolean = false,
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("sms_subscription_ids") val smsSubscriptionIds: List<Long> = emptyList()
)

@Serializable
data class HiroSIMSubscriptions(
    val inventory: HiroSIMInventory,
    val preference: HiroSIMPreference,
    @SerialName("sms_receive_supported") val smsReceiveSupported: Boolean,
    @SerialName("voice_receive_supported") val voiceReceiveSupported: Boolean,
    @SerialName("voice_limitation") val voiceLimitation: String,
    @SerialName("gateway_changed") val gatewayChanged: Boolean = false
)

@Serializable
data class HiroSIMUpdate(
    @SerialName("device_id") val deviceId: String,
    @SerialName("sms_subscription_ids") val smsSubscriptionIds: List<Long>
)

internal fun smsSIMLabel(id: Long?, cards: List<HiroSIMCard>): String =
    cards.find { it.id == id }?.label ?: if (id == null) "SIM не определена" else "SIM недоступна (ID $id)"
