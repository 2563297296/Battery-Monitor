package com.example.batterymonitor

object AdbRootController {
    fun setEnabled(enabled: Boolean): Boolean {
        val commands = if (enabled) {
            listOf(
                "setprop service.adb.tcp.port 5555",
                "setprop persist.sys.usb.config adb",
                "stop adbd",
                "start adbd"
            )
        } else {
            listOf(
                "setprop service.adb.tcp.port -1",
                "setprop persist.sys.usb.config none",
                "stop adbd",
                "start adbd"
            )
        }

        return commands.all { RootShell.run(it) != null }
    }
}
