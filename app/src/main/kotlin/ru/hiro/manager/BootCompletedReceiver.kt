package ru.hiro.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

import java.nio.charset.StandardCharsets

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        Log.i(TAG, "BootCompletedReceiver received intent ${intent.action}")

        val configPath = context.filesDir.absolutePath + "/config"
        val config = Utils.getFileContents(configPath)
        val autoStart = config?.let {
            val values = Utils.getNameValue(String(it, StandardCharsets.ISO_8859_1), "auto_start")
            values.isNotEmpty() && values[0] == "yes"
        } ?: false
        val hasServerSession = HiroSessionStore(context).load() != null
        if (autoStart || hasServerSession) {
            Log.i(TAG, "Start background service upon boot completed")
            val baresipService = Intent(context, BaresipService::class.java).apply {
                action = "Start"
                putExtra("onStartup", true)
            }
            ContextCompat.startForegroundService(context, baresipService)
        }

    }

}
