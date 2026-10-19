package ru.hiro.manager

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString

class ReceptionPriorityModelsTest {
    private val line=HiroVoiceLine("office","Офис","1","novofon","phone",enabled=true)
    @Test fun emptyPreferenceIsNeutralAndNeverImplicitlyLive() {
        val p=hiroRoutingJson.decodeFromString<HiroReceptionPreferences>("{\"revision\":0,\"rules\":[]}")
        assertEquals(0,p.defaultPriority);assertTrue(p.rules.isEmpty());assertFalse(p.appliedToLiveCalls)
    }
    @Test fun wireFormatIsFlatAndNeverContainsAnOwnerOrDestination() {
        val json=hiroRoutingJson.encodeToString(HiroReceptionUpdate(listOf(HiroReceptionRule(lineId="office",callerNumber="+79990000001"))))
        assertTrue(json.contains("\"line_id\":\"office\""));assertTrue(json.contains("\"direction\":\"inbound\""));assertFalse(json.contains("user_id"));assertFalse(json.contains("sip:"))
        val roundTrip=hiroRoutingJson.decodeFromString<HiroReceptionUpdate>(json)
        assertEquals(100,roundTrip.rules.single().priority)
    }
    @Test fun zeroIsValidAndOtherRulesSurviveAnEditOrDeletion() {
        val typed=HiroReceptionRule(gatewayType="novofon",priority=100)
        val exact=HiroReceptionRule(lineId="office",priority=0)
        assertTrue(receptionRuleSupported(exact,listOf(line)))
        val added=replaceReceptionRule(listOf(typed),null,exact)
        assertEquals(listOf(typed,exact),added)
        val updated=replaceReceptionRule(added,receptionRuleKey(exact),exact.copy(priority=50))
        assertEquals(typed,updated.first());assertEquals(50,updated.last().priority)
        assertEquals(listOf(typed),updated.filterNot{receptionRuleKey(it)==receptionRuleKey(exact)})
    }
    @Test fun scopeCapabilitiesRejectUnverifiedSIMAndInvalidFields() {
        val sim=HiroReceptionRule(deviceId="phone",simId="sim1")
        assertFalse(receptionRuleSupported(sim,listOf(line)))
        assertTrue(receptionRuleSupported(sim,listOf(line.copy(simId="sim1",simVerified=true))))
        assertFalse(receptionRuleSupported(HiroReceptionRule(lineId="office",gatewayType="novofon"),listOf(line)))
        assertFalse(receptionRuleSupported(HiroReceptionRule(lineId="office",priority=101),listOf(line)))
        assertFalse(receptionRuleSupported(HiroReceptionRule(lineId="office"),listOf(line.copy(enabled=false))))
        assertFalse(receptionRuleSupported(HiroReceptionRule(direction="outbound"),listOf(line)))
    }
    @Test fun callerKeysAreIndependentAndCanonical() {
        val all=HiroReceptionRule(lineId="office")
        val caller=all.copy(callerNumber="+79990000001")
        assertNotEquals(receptionRuleKey(all),receptionRuleKey(caller))
        assertEquals(2,replaceReceptionRule(listOf(all),null,caller).size)
        assertFalse(receptionRuleSupported(caller.copy(callerNumber="89990000001"),listOf(line)))
    }
    @Test(expected=IllegalArgumentException::class) fun duplicateScopeCannotSilentlyReplaceAnotherRule() {
        val r=HiroReceptionRule(lineId="office")
        replaceReceptionRule(listOf(r),null,r.copy(priority=50))
    }
    @Test(expected=IllegalArgumentException::class) fun reloadCannotResurrectAnExternallyDeletedRule() {
        val r=HiroReceptionRule(lineId="office")
        replaceReceptionRule(emptyList(),receptionRuleKey(r),r)
    }
}
