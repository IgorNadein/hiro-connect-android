package ru.hiro.manager

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class HiroRoutingTest {
    @Test fun normalizeAliasesAndRejectAmbiguousNumbers() {
        listOf("+7 (999) 000-00-01", "89990000001", "79990000001", "tel:+79990000001").forEach {
            assertEquals("+79990000001", normalizeClientNumber(it))
        }
        listOf("", "9990000001", "+012345678", "++79990000001", "+79990000001#12", "+79990000001&evil").forEach {
            assertNull(normalizeClientNumber(it))
        }
    }

    @Test fun defaultsAreSentToStrictServer() {
        val body = hiroRoutingJson.encodeToString(HiroClientUpdate("Example", listOf(HiroPriorityRule())))
        val rule = hiroRoutingJson.parseToJsonElement(body).jsonObject.getValue("rules").jsonArray[0].jsonObject
        assertEquals("both", rule.getValue("direction").jsonPrimitive.content)
        assertEquals("50", rule.getValue("priority").jsonPrimitive.content)
        assertEquals("", rule.getValue("line_id").jsonPrimitive.content)
    }

    @Test fun explicitZeroAndSIMScopeSurviveRoundTrip() {
        val original = HiroPriorityRule(deviceId="gateway", simId="sim1", direction="outbound", priority=0)
        assertEquals(original, hiroRoutingJson.decodeFromString<HiroPriorityRule>(hiroRoutingJson.encodeToString(original)))
    }

    @Test fun serverInventoryAndDirectoryDecode() {
        val lines = hiroRoutingJson.decodeFromString<HiroVoiceLines>("""{"lines":[{"id":"primary","name":"Phone","gateway_type":"mobile","device_id":"primary","sim_id":"","sim_verified":false,"enabled":true,"code":"1"}],"dial_prefix":"*88"}""")
        assertFalse(lines.lines.single().simVerified)
        val directory = hiroRoutingJson.decodeFromString<HiroClientDirectory>("""{"revision":3,"clients":[{"number":"+79990000001","name":"Example","rules":[],"updated_by":"operator","updated_at":"2026-10-14T12:00:00Z"}]}""")
        assertEquals(3L, directory.revision)
        assertTrue(directory.clients.single().rules.isEmpty())
    }
}
