package ru.hiro.manager

import java.util.GregorianCalendar
import androidx.annotation.Keep

// Android saves this selection across process recreation. Preserve serialized
// class/field names when the release optimizer shrinks the application.
@Keep
data class CallRow(
    val aor: String,
    val peerUri: String,
    var direction: Int,
    var startTime: GregorianCalendar?,
    var stopTime: GregorianCalendar,
    var recording: List<String>
) : java.io.Serializable {
    @Keep
    data class Details(
        var direction: Int,
        var startTime: GregorianCalendar?,
        var stopTime: GregorianCalendar,
        var recording: List<String>
    ) : java.io.Serializable {
        companion object { private const val serialVersionUID: Long = 1L }
    }
    val details = mutableListOf(Details(direction, startTime, stopTime, recording))
    companion object { private const val serialVersionUID: Long = 1L }
}
