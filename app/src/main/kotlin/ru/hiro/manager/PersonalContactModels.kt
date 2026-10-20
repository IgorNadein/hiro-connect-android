package ru.hiro.manager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HiroPersonalContact(val number: String, val name: String)
@Serializable
data class HiroPersonalContacts(val revision: Long, val contacts: List<HiroPersonalContact>,
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_contacts") val sourceContacts: List<HiroPersonalContact> = emptyList())
@Serializable
data class HiroPersonalContactsUpdate(val contacts: List<HiroPersonalContact>)

internal fun canonicalPersonalContacts(items: List<HiroPersonalContact>): List<HiroPersonalContact> =
    items.mapNotNull { c -> normalizeClientNumber(c.number)?.let { HiroPersonalContact(it, c.name.take(80).replace(Regex("[\\r\\n\\u0000]"), " ").trim()) } }
        .distinctBy { it.number }.sortedBy { it.number }
