package ru.hiro.manager

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json

class SharedCallModelsTest {
    @Test fun recordsDecodeWithoutInventingArchiveIdentity(){
        val call=Json.decodeFromString<SharedCall>("""{"id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","started_at":"2026-10-20T10:00:00Z","source":"recording_archive"}""")
        assertEquals("Архивная запись",callTitle(call));assertFalse(callCanDial(call.number));assertFalse(call.recording)
    }
    @Test fun capabilitiesFailClosedOnOlderResponses(){
        val page=Json.decodeFromString<SharedCallPage>("""{"items":[]}""")
        assertFalse(page.capabilities.recordings);assertFalse(page.capabilities.notes);assertFalse(page.capabilities.join)
    }
    @Test fun dialDestinationsCannotContainSipOrDialplanSyntax(){
        assertTrue(callCanDial("+79990000001"));assertFalse(callCanDial("*88*123"));assertFalse(callCanDial("PJSIP/other"));assertFalse(callCanDial("123,ChanSpy"));assertFalse(callCanDial(""))
    }
    @Test fun durationAndUnknownDirectionAreHonest(){
        assertEquals("0:00",callDuration(-1));assertEquals("2:04",callDuration(124))
    }
}
