package com.example.batterymonitor

import java.io.BufferedReader
import java.util.concurrent.TimeUnit

object RootShell {
    fun run(command: String): String? {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use(BufferedReader::readText).trim()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() == 0) {
                output
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun readFirst(path: String): String? {
        val safePath = path.replace("\"", "")
        return run("cat \"$safePath\" 2>/dev/null")?.lineSequence()?.firstOrNull()?.trim()
    }
}
