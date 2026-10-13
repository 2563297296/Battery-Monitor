package com.example.batterymonitor

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.batterymonitor.databinding.ActivityMainBinding
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val preferences by lazy { getSharedPreferences("monitor_settings", MODE_PRIVATE) }
    private var refreshJob: Job? = null
    private var refreshInProgress = false

    private val displayModes = listOf("简洁模式", "完整模式", "容量信息模式")
    private val refreshIntervals = listOf(
        RefreshOption("0.5 秒", 500L),
        RefreshOption("1 秒", 1_000L),
        RefreshOption("2 秒", 2_000L),
        RefreshOption("5 秒", 5_000L)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDisplayOptions()
        setupRefreshOptions()
        setupAdbControls()
        setupDebugSwitch()

        binding.refreshButton.setOnClickListener { refreshData() }
        refreshData()
    }

    override fun onStart() {
        super.onStart()
        if (isAutoRefreshEnabled()) startAutoRefresh()
    }

    override fun onStop() {
        refreshJob?.cancel()
        refreshJob = null
        super.onStop()
    }

    private fun setupDisplayOptions() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayModes)
        binding.displayModeSpinner.setAdapter(adapter)
        val selected = preferences.getInt(KEY_DISPLAY_MODE, 0).coerceIn(0, displayModes.lastIndex)
        binding.displayModeSpinner.setText(displayModes[selected], false)
        renderDisplayMode(selected)
        renderTemplate(selected)
        binding.displayModeSpinner.setOnItemClickListener { _, _, position, _ ->
            preferences.edit().putInt(KEY_DISPLAY_MODE, position).apply()
            renderDisplayMode(position)
            renderTemplate(position)
        }
    }

    private fun setupRefreshOptions() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            refreshIntervals.map { it.label }
        )
        binding.refreshIntervalSpinner.setAdapter(adapter)
        val savedInterval = preferences.getLong(KEY_REFRESH_INTERVAL, 500L)
        val intervalIndex = refreshIntervals.indexOfFirst { it.millis == savedInterval }.coerceAtLeast(0)
        binding.refreshIntervalSpinner.setText(refreshIntervals[intervalIndex].label, false)
        binding.refreshIntervalSpinner.setOnItemClickListener { _, _, position, _ ->
            preferences.edit().putLong(KEY_REFRESH_INTERVAL, refreshIntervals[position].millis).apply()
            if (binding.autoRefreshSwitch.isChecked) startAutoRefresh()
        }

        binding.autoRefreshSwitch.isChecked = preferences.getBoolean(KEY_AUTO_REFRESH, true)
        binding.autoRefreshSwitch.setOnCheckedChangeListener { _, checked ->
            preferences.edit().putBoolean(KEY_AUTO_REFRESH, checked).apply()
            if (checked) startAutoRefresh() else {
                refreshJob?.cancel()
                refreshJob = null
            }
        }
    }

    private fun setupAdbControls() {
        binding.adbToggleButton.setOnClickListener { toggleAdbByRoot() }
    }

    private fun setupDebugSwitch() {
        binding.debugSwitch.setOnCheckedChangeListener { _, checked ->
            AppLogger.debugEnabled = checked
            AppLogger.i("MainActivity", "debug_switch", mapOf("enabled" to checked))
        }
    }

    private fun renderTemplate(position: Int) {
        val colors = listOf(
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary),
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondary),
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorTertiary)
        )
        binding.templatePreview.setCardBackgroundColor(colors[position.coerceIn(0, 2)])
        binding.templatePreviewText.text = when (position) {
            1 -> "完整参数 + 状态信息"
            2 -> "容量优先 + 健康度对比"
            else -> "只保留最常用的实时数据"
        }
    }

    private fun renderDisplayMode(position: Int) {
        val simple = position == 0
        val full = position == 1
        binding.capacityCard.isVisible = !simple
        binding.sourceTypeValue.isVisible = full
        binding.batteryTypeValue.isVisible = full
        binding.currentValue.isVisible = !(!full && position == 2)
        binding.voltageValue.isVisible = !(!full && position == 2)
        binding.powerValue.isVisible = full || simple
        binding.statusValue.isVisible = true
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                refreshData()
                delay(selectedRefreshInterval())
            }
        }
    }

    private fun refreshData() {
        if (refreshInProgress) return
        refreshInProgress = true
        lifecycleScope.launch {
            binding.progressBar.isVisible = true
            try {
                val snapshot = runCatching {
                    withContext(Dispatchers.IO) {
                    BatteryDiagnostics.readSnapshot()
                    }
                }.getOrElse { error ->
                    AppLogger.e("MainActivity", "battery_snapshot_failed", error)
                    BatterySnapshot(
                        healthPercent = null,
                        designCapacity = null,
                        actualMaxCapacity = null,
                        capacityUnit = "mAh",
                        powerW = null,
                        powerDirection = "unknown",
                        chargingPowerW = null,
                        dischargingPowerW = null,
                        currentMa = null,
                        voltageV = null,
                        sourceType = "N/A",
                        batteryType = "N/A",
                        rawStatus = "N/A"
                    )
                }
                bindSnapshot(snapshot)
            } finally {
                binding.progressBar.isVisible = false
                refreshInProgress = false
            }
        }
    }

    private fun bindSnapshot(snapshot: BatterySnapshot) {
        binding.healthValue.text = "健康度（实际最大容量 / 设计容量）: ${formatPercent(snapshot.healthPercent)}"
        binding.designCapacityValue.text =
            "设计容量（出厂标称）: ${formatCapacity(snapshot.designCapacity, snapshot.capacityUnit)}"
        binding.actualMaxCapacityValue.text =
            "实际最大容量（当前满充上限）: ${formatCapacity(snapshot.actualMaxCapacity, snapshot.capacityUnit)}"
        binding.powerValue.text =
            "实时功率（电压 × 电流）: ${formatPower(snapshot.powerW, snapshot.powerDirection)}"
        binding.currentValue.text =
            "电流（正值充电，负值放电）: ${formatCurrent(snapshot.currentMa)}"
        binding.voltageValue.text = "电压（电池端电压）: ${formatVoltage(snapshot.voltageV)}"
        binding.sourceTypeValue.text = "供电来源（AC / USB / 电池）: ${snapshot.sourceType}"
        binding.batteryTypeValue.text = "电池类型（化学技术）: ${snapshot.batteryType}"
        binding.statusValue.text = "电池状态（系统状态）: ${snapshot.rawStatus.ifBlank { "unknown" }}"

        AppLogger.d(
            "MainActivity",
            "battery_snapshot",
            mapOf(
                "health" to snapshot.healthPercent,
                "power_w" to snapshot.powerW,
                "current_ma" to snapshot.currentMa,
                "voltage_v" to snapshot.voltageV,
                "design_capacity" to snapshot.designCapacity,
                "actual_capacity" to snapshot.actualMaxCapacity
            )
        )
    }

    private fun toggleAdbByRoot() {
        binding.adbToggleButton.isEnabled = false
        lifecycleScope.launch {
            binding.progressBar.isVisible = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    AdbRootController.setEnabled(this@MainActivity, binding.adbRootSwitch.isChecked)
                }
            }.getOrElse { error ->
                AppLogger.e("MainActivity", "adb_toggle_failed", error)
                AdbRootController.Result(false, "ADB 操作异常：${error.message ?: "未知错误"}")
            }
            binding.progressBar.isVisible = false
            binding.adbToggleButton.isEnabled = true
            binding.adbStatusText.text = result.message
            Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
            AppLogger.i(
                "MainActivity",
                "adb_toggle",
                mapOf("enabled" to binding.adbRootSwitch.isChecked, "success" to result.success)
            )
        }
    }

    private fun isAutoRefreshEnabled(): Boolean =
        preferences.getBoolean(KEY_AUTO_REFRESH, true)

    private fun selectedRefreshInterval(): Long =
        preferences.getLong(KEY_REFRESH_INTERVAL, 500L).coerceIn(500L, 5_000L)

    private fun formatPercent(value: Double?): String =
        value?.let { "%.1f%%".format(it) } ?: "N/A"

    private fun formatCapacity(value: Double?, unit: String): String =
        value?.let { "%.0f $unit".format(it) } ?: "N/A"

    private fun formatPower(value: Double?, direction: String): String {
        if (value == null) return "N/A"
        val label = when (direction) {
            "charging" -> "充电"
            "discharging" -> "放电"
            else -> "待机"
        }
        return "%.2f W（$label）".format(value)
    }

    private fun formatCurrent(value: Double?): String =
        value?.let { "%.1f mA".format(it) } ?: "N/A"

    private fun formatVoltage(value: Double?): String =
        value?.let { "%.3f V".format(it) } ?: "N/A"

    private data class RefreshOption(val label: String, val millis: Long)

    companion object {
        private const val KEY_DISPLAY_MODE = "display_mode"
        private const val KEY_AUTO_REFRESH = "auto_refresh"
        private const val KEY_REFRESH_INTERVAL = "refresh_interval"
    }
}
