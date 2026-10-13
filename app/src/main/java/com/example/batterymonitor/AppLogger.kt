package com.example.batterymonitor

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    @Volatile
    var debugEnabled: Boolean = false

    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun i(tag: String, event: String, fields: Map<String, Any?> = emptyMap()) {
        emit("INFO", tag, event, fields)
    }

    fun d(tag: String, event: String, fields: Map<String, Any?> = emptyMap()) {
        if (!debugEnabled) return
        emit("DEBUG", tag, event, fields)
    }

    fun e(tag: String, event: String, throwable: Throwable? = null, fields: Map<String, Any?> = emptyMap()) {
        emit("ERROR", tag, event, fields + ("error" to throwable?.message))
    }

    private fun emit(level: String, tag: String, event: String, fields: Map<String, Any?>) {
        val timestamp = formatter.format(Date())
        val payload = fields.entries.joinToString(" ") { "${it.key}=${it.value ?: "null"}" }
        Log.println(Log.INFO, tag, "[$timestamp][$level][$event] $payload")
    }
}
