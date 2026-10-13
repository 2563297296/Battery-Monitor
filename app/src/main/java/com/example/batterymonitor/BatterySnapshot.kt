package com.example.batterymonitor

data class BatterySnapshot(
    val healthPercent: Double?,
    val designCapacity: Double?,
    val actualMaxCapacity: Double?,
    val capacityUnit: String,
    val powerW: Double?,
    val powerDirection: String,
    val chargingPowerW: Double?,
    val dischargingPowerW: Double?,
    val currentMa: Double?,
    val voltageV: Double?,
    val sourceType: String,
    val batteryType: String,
    val rawStatus: String
)
