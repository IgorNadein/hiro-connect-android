package ru.hiro.manager

import android.content.Context
import java.io.File

internal object SharedCallAudioCache {
    fun clear(context:Context) {
        // Only files created by this feature, never the application's cache root.
        context.cacheDir.listFiles()?.filter{it.isFile&&it.name.startsWith("hiro-call-")&&it.name.endsWith(".wav")}?.forEach{it.delete()}
    }
}
