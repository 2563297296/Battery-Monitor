package com.example.batterymonitor

import kotlin.math.abs

object BatteryDiagnostics {
    private const val BASE = "/sys/class/power_supply"

    fun readSnapshot(): BatterySnapshot {
        val chargeFull = readDouble("$BASE/battery/charge_full")
        val chargeDesign = readDouble("$BASE/battery/charge_full_design")
        val energyFull = readDouble("$BASE/battery/energy_full")
        val energyDesign = readDouble("$BASE/battery/energy_full_design")

        val designCapacity = when {
            chargeDesign != null -> chargeDesign / 1000.0
            energyDesign != null -> energyDesign / 1000.0
            else -> null
        }
        val actualMaxCapacity = when {
            chargeFull != null -> chargeFull / 1000.0
            energyFull != null -> energyFull / 1000.0
            else -> null
        }
        val capacityUnit = if (chargeDesign != null || chargeFull != null) "mAh" else "mWh"
        val healthPercent = when {
            chargeFull != null && chargeDesign != null && chargeDesign > 0 -> (chargeFull / chargeDesign) * 100
            energyFull != null && energyDesign != null && energyDesign > 0 -> (energyFull / energyDesign) * 100
            else -> null
        }

        val currentUa = readDouble("$BASE/battery/current_now")
        val voltageUv = readDouble("$BASE/battery/voltage_now")
        val currentMa = currentUa?.div(1000.0)
        val voltageV = voltageUv?.div(1_000_000.0)
        val powerW = if (currentUa != null && voltageUv != null) {
            (abs(currentUa) * voltageUv) / 1_000_000_000_000.0
        } else {
            null
        }

        val status = RootShell.readFirst("$BASE/battery/status").orEmpty()
        val sourceType = detectSourceType()
        val batteryType = RootShell.readFirst("$BASE/battery/technology") ?: "unknown"

        val chargingPower = if (currentUa != null && currentUa > 0) powerW else null
        val dischargingPower = if (currentUa != null && currentUa < 0) powerW else null
        val powerDirection = when {
            currentUa == null -> "unknown"
            currentUa > 0 -> "charging"
            currentUa < 0 -> "discharging"
            else -> when (status.lowercase()) {
                "charging", "full" -> "charging"
                else -> "idle"
            }
        }

        return BatterySnapshot(
            healthPercent = healthPercent,
            designCapacity = designCapacity,
            actualMaxCapacity = actualMaxCapacity,
            capacityUnit = capacityUnit,
            powerW = powerW,
            powerDirection = powerDirection,
            chargingPowerW = chargingPower,
            dischargingPowerW = dischargingPower,
            currentMa = currentMa,
            voltageV = voltageV,
            sourceType = sourceType,
            batteryType = batteryType,
            rawStatus = status
        )
    }

    private fun detectSourceType(): String {
        val acOnline = RootShell.readFirst("$BASE/ac/online") == "1"
        val usbOnline = RootShell.readFirst("$BASE/usb/online") == "1"
        val wirelessOnline = RootShell.readFirst("$BASE/wireless/online") == "1"
        val usbType = RootShell.readFirst("$BASE/usb/type") ?: ""

        return when {
            acOnline -> "AC"
            wirelessOnline -> "Wireless (DC)"
            usbOnline || usbType.isNotBlank() -> "DC/USB"
            else -> "Battery"
        }
    }

    private fun readDouble(path: String): Double? {
        return RootShell.readFirst(path)?.toDoubleOrNull()
    }
}
