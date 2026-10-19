package ru.hiro.manager

import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class GatewaySIMModelsTest {
    @Test fun readsBothGatewaySIMsNotOperatorPhoneSIMs() {
        val response = hiroRoutingJson.decodeFromString<HiroSIMSubscriptions>("""
            {"inventory":{"device_id":"gateway-a","items":[
              {"id":3,"slot_index":0,"display_name":"Carrier A"},
              {"id":4,"slot_index":1,"display_name":"Carrier B"}]},
             "preference":{"revision":2,"configured":true,"device_id":"gateway-a","sms_subscription_ids":[4]},
             "sms_receive_supported":true,"voice_receive_supported":false,"voice_limitation":"Not supported"}
        """.trimIndent())
        assertEquals(2, response.inventory.items.size)
        assertEquals(listOf(4L), response.preference.smsSubscriptionIds)
        assertEquals("SIM 2 · Carrier B", smsSIMLabel(4, response.inventory.items))
        assertFalse(response.voiceReceiveSupported)
    }
    @Test fun emptySelectionIsSerializedExplicitly() {
        assertEquals("{\"device_id\":\"gateway-a\",\"sms_subscription_ids\":[]}",
            hiroRoutingJson.encodeToString(HiroSIMUpdate("gateway-a", emptyList())))
    }
    @Test fun unknownSIMDoesNotBecomeFirstCard() {
        assertEquals("SIM не определена", smsSIMLabel(null, listOf(HiroSIMCard(3,"Carrier A",0))))
        assertEquals("SIM недоступна (ID 4)", smsSIMLabel(4, listOf(HiroSIMCard(3,"Carrier A",0))))
    }
}
