package com.example.batterymonitor

import android.content.Context

object AdbRootController {
    data class Result(val success: Boolean, val message: String)

    private const val PREFS = "adb_root"
    private const val SAVED_USB_CONFIG = "saved_usb_config"
    private const val SAVED_ADB_PORT = "saved_adb_port"

    fun setEnabled(context: Context, enabled: Boolean): Result {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentConfig = readUsbConfig()
            ?: return Result(false, "无法读取当前 USB 配置")
        val currentPort = RootShell.run("getprop service.adb.tcp.port")?.trim().orEmpty()
        val savedConfig = preferences.getString(SAVED_USB_CONFIG, null)
        val savedPort = preferences.getString(SAVED_ADB_PORT, null)
        val targetConfig = if (enabled) {
            addAdb(currentConfig)
        } else {
            savedConfig ?: removeAdb(currentConfig)
        }
        val targetPort = if (enabled) {
            "5555"
        } else {
            savedPort ?: "-1"
        }

        val commands = listOf(
            "setprop service.adb.tcp.port $targetPort",
            "setprop persist.sys.usb.config ${sanitizeConfig(targetConfig)}",
            "setprop sys.usb.config ${sanitizeConfig(targetConfig)}",
            "stop adbd",
            "start adbd"
        )
        val failed = commands.firstOrNull { RootShell.run(it) == null }
        return if (failed == null) {
            if (enabled) {
                if (savedConfig == null) {
                    preferences.edit()
                        .putString(SAVED_USB_CONFIG, currentConfig)
                        .putString(SAVED_ADB_PORT, currentPort.ifBlank { "-1" })
                        .apply()
                }
            } else {
                preferences.edit()
                    .remove(SAVED_USB_CONFIG)
                    .remove(SAVED_ADB_PORT)
                    .apply()
            }
            Result(true, if (enabled) "ADB 已加入当前 USB 配置" else "ADB 已关闭并恢复 USB 配置")
        } else {
            Result(false, "ADB 接管失败：$failed")
        }
    }

    private fun readUsbConfig(): String? {
        return listOf("persist.sys.usb.config", "sys.usb.config")
            .asSequence()
            .mapNotNull { RootShell.run("getprop $it")?.trim() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun addAdb(config: String): String =
        config.split(',').filter { it.isNotBlank() }.let { parts ->
            if (parts.any { it == "adb" }) config else (parts + "adb").joinToString(",")
        }

    private fun removeAdb(config: String): String =
        config.split(',').filter { it.isNotBlank() && it != "adb" }.joinToString(",").ifBlank { "none" }

    private fun sanitizeConfig(config: String): String =
        config.filter { it.isLetterOrDigit() || it == ',' || it == '_' || it == '-' }.ifBlank { "none" }
}
