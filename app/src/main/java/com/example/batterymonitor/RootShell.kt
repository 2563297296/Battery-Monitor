package com.example.batterymonitor

import java.io.BufferedReader

object RootShell {
    fun run(command: String): String? {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use(BufferedReader::readText).trim()
            process.waitFor()
            if (process.exitValue() == 0) output else null
        } catch (_: Exception) {
            null
        }
    }

    fun readFirst(path: String): String? {
        val safePath = path.replace("\"", "")
        return run("cat \"$safePath\" 2>/dev/null")?.lineSequence()?.firstOrNull()?.trim()
    }
}
