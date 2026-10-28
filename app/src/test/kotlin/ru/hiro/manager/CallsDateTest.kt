package ru.hiro.manager

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CallsDateTest {
    @Test fun dateAndClockUseLocalTimeAcrossMidnight() {
        val zone = ZoneId.of("Europe/Moscow")
        assertEquals(LocalDate.of(2026, 10, 28), callDate("2026-10-27T22:15:00Z", zone))
        assertEquals("01:15", callClockTime("2026-10-27T22:15:00Z", zone))
        assertNull(callDate("bad", zone))
        assertEquals("—", callClockTime("bad", zone))
    }
    @Test fun labelsHandleTodayYesterdayAndYearBoundary() {
        val today = LocalDate.of(2027, 1, 1)
        assertEquals("Сегодня", callDayLabel(today, today))
        assertEquals("Вчера", callDayLabel(today.minusDays(1), today))
        assertTrue(callDayLabel(today.minusDays(2), today).contains("2026"))
        assertEquals("Дата не указана", callDayLabel(null, today))
    }
}
