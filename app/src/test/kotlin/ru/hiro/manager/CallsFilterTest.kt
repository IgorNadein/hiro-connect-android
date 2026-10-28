package ru.hiro.manager

import org.junit.Assert.*
import org.junit.Test

class CallsFilterTest {
    private val incoming = SharedCall("in", direction = "inbound", status = "answered", startedAt = "2026-01-01T00:00:00Z")
    private val missed = incoming.copy(id = "missed", status = "missed")
    private val outgoing = incoming.copy(id = "out", direction = "outbound")
    private val active = LiveTeamCall("active", direction = "inbound", state = "active")
    private val waiting = LiveTeamCall("waiting", direction = "inbound", state = "waiting")

    @Test fun filterDistinguishesHistoryDirectionAndMissed() {
        assertTrue(historyMatchesFilter(incoming, CallsFilter.INCOMING))
        assertFalse(historyMatchesFilter(outgoing, CallsFilter.INCOMING))
        assertTrue(historyMatchesFilter(outgoing, CallsFilter.OUTGOING))
        assertTrue(historyMatchesFilter(missed, CallsFilter.MISSED))
        assertFalse(historyMatchesFilter(incoming, CallsFilter.MISSED))
        assertFalse(historyMatchesFilter(incoming, CallsFilter.ACTIVE))
    }

    @Test fun liveFiltersKeepActiveAndWaitingDistinct() {
        assertTrue(liveMatchesFilter(active, CallsFilter.ACTIVE))
        assertFalse(liveMatchesFilter(waiting, CallsFilter.ACTIVE))
        assertTrue(liveMatchesFilter(waiting, CallsFilter.WAITING))
        assertFalse(liveMatchesFilter(active, CallsFilter.WAITING))
        assertFalse(liveMatchesFilter(active, CallsFilter.MISSED))
    }
}
