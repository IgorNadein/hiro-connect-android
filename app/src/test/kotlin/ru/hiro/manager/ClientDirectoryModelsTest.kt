package ru.hiro.manager

import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class ClientDirectoryModelsTest {
    private val office=HiroVoiceLine("office","Office","1","novofon","",enabled=true)
    private val reserve=office.copy(id="reserve",name="Reserve",code="2")
    @Test fun explicitScopeWinsEvenWithSmallerNumber() {
        val rules=listOf(HiroPriorityRule(gatewayType="novofon",priority=80),HiroPriorityRule(lineId="office",priority=20))
        assertEquals(20,resolveClientPriority(rules,emptyList(),office,"inbound").rule?.priority)
        assertEquals(80,resolveClientPriority(rules,emptyList(),reserve,"inbound").rule?.priority)
        assertEquals("office",automaticClientLine(rules,emptyList(),listOf(reserve,office))?.id)
    }
    @Test fun organizationSpecificRuleBeatsClientGenericRule() {
        val result=resolveClientPriority(listOf(HiroPriorityRule(priority=100)),listOf(HiroPriorityRule(lineId="office",priority=1)),office,"inbound")
        assertEquals(1,result.rule?.priority);assertFalse(result.clientRule)
    }
    @Test fun clientOverridePrecedesExactDirectionInSameScope() {
        val result=resolveClientPriority(listOf(HiroPriorityRule(gatewayType="novofon",priority=20)),listOf(HiroPriorityRule(gatewayType="novofon",direction="inbound",priority=80)),office,"inbound")
        assertEquals(20,result.rule?.priority);assertTrue(result.clientRule)
    }
    @Test fun exactDirectionWinsWithinOneOwnerAndScope() {
        val rules=listOf(HiroPriorityRule(priority=80),HiroPriorityRule(direction="inbound",priority=20))
        assertEquals(20,resolveClientPriority(rules,emptyList(),office,"inbound").rule?.priority)
        assertEquals(80,resolveClientPriority(rules,emptyList(),office,"outbound").rule?.priority)
        assertEquals(20,basePriority(rules,"inbound"))
    }
    @Test fun simIsMatchedOnlyWhenVerified() {
        val line=office.copy(deviceId="phone",simId="sim-1")
        val rules=listOf(HiroPriorityRule(deviceId="phone",simId="sim-1",priority=90),HiroPriorityRule(deviceId="phone",priority=10))
        assertEquals(10,resolveClientPriority(rules,emptyList(),line,"inbound").rule?.priority)
        assertEquals(90,resolveClientPriority(rules,emptyList(),line.copy(simVerified=true),"inbound").rule?.priority)
    }
    @Test fun disabledLinesAreExcludedAndTieUsesNumericCode() {
        assertNull(automaticClientLine(emptyList(),emptyList(),listOf(office.copy(enabled=false))))
        assertEquals("reserve",automaticClientLine(emptyList(),emptyList(),listOf(office.copy(code="10"),reserve))?.id)
    }
    @Test fun externalOriginIsExplicitNullButManualEditorNeverSendsOrigin() {
        val external=hiroRoutingJson.encodeToString(HiroExternalClientUpdate("External",emptyList()))
        assertTrue(external.contains("\"introduced_by_user_id\":null"))
        assertFalse(hiroRoutingJson.encodeToString(HiroClientUpdate("Manual",emptyList())).contains("introduced_by_user_id"))
    }
    @Test fun navigationHasFourStableTaskBasedRoots() {
        assertEquals(listOf("Звонки","Сообщения","Клиенты","Настройки"),workspaceTabs.map{it.label})
        assertEquals("client-routing",workspaceTabFor("client-routing?personal={personal}"))
        assertEquals("gateway-messages",workspaceTabFor("gateway-messages?address={address}"))
        assertEquals("workspace-settings",workspaceTabFor("workspace-sync"))
    }
}
