package ru.hiro.manager

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AudioLatencyDefaultsTest {
    @Test
    fun lowLatencyAudioUsesAdaptiveBoundedJitterBuffer() {
        val settings = File("src/main/assets/config.static").readLines()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .associate { line ->
                val parts = line.trim().split(Regex("""\s+"""), limit = 2)
                parts[0] to parts.getOrElse(1) { "" }
            }

        assertEquals("adaptive", settings["audio_jitter_buffer_type"])
        assertEquals("60-120", settings["audio_jitter_buffer_ms"])
        assertEquals("50", settings["audio_jitter_buffer_size"])
        assertEquals("20-160", settings["audio_buffer"])
    }
}
