package ru.hiro.manager

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.GregorianCalendar

class CallRowSnapshotTest {
    @Test fun savedSelectionRoundTripsAllDetailsWithoutAnAccount() {
        val start = GregorianCalendar().apply { timeInMillis = 1700000000000L }
        val stop = GregorianCalendar().apply { timeInMillis = 1700000060000L }
        val row = CallRow("sip:removed@old.invalid", "tel:+79990000001", 1, start, stop, listOf("/synthetic/recording.wav", ""))
        row.details.add(CallRow.Details(2, null, stop, emptyList()))
        val bytes = ByteArrayOutputStream().also { stream -> ObjectOutputStream(stream).use { it.writeObject(row) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as CallRow }
        assertEquals(row, restored)
        assertEquals(row.details, restored.details)
        assertNotSame(row, restored)
        assertNotSame(row.startTime, restored.startTime)
        assertNull(restored.details[1].startTime)
    }

    @Test fun explicitDeletionIsRetainedInSavedSelection() {
        val row = CallRow("sip:removed@old.invalid", "tel:+79990000001", 1, null, GregorianCalendar(), emptyList())
        row.details.clear()
        val bytes = ByteArrayOutputStream().also { stream -> ObjectOutputStream(stream).use { it.writeObject(row) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as CallRow }
        assertTrue(restored.details.isEmpty())
    }
}
