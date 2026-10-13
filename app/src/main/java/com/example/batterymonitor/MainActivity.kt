package com.example.batterymonitor

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.batterymonitor.databinding.ActivityMainBinding
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTemplateSelector()
        setupSwitches()

        binding.refreshButton.setOnClickListener { refreshData() }
        binding.adbToggleButton.setOnClickListener { toggleAdbByRoot() }

        refreshData()
    }

    private fun setupTemplateSelector() {
        val templates = listOf(
            "模板A - 极简卡片", "模板B - 科技暗色", "模板C - 图表风"
        )
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            templates
        )
        binding.templateSpinner.setAdapter(adapter)
        binding.templateSpinner.setText(templates.first(), false)
        renderTemplate(0)
        binding.templateSpinner.setOnItemClickListener { _, _, position, _ ->
            renderTemplate(position)
        }
    }

    private fun setupSwitches() {
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
            1 -> "暗色高对比 + 霓虹数据标签"
            2 -> "趋势图区域 + 信息面板"
            else -> "留白设计 + 快速状态卡"
        }
    }

    private fun refreshData() {
        lifecycleScope.launch {
            binding.progressBar.isVisible = true
            val snapshot = withContext(Dispatchers.IO) {
                BatteryDiagnostics.readSnapshot()
            }
            binding.progressBar.isVisible = false
            bindSnapshot(snapshot)
        }
    }

    private fun bindSnapshot(snapshot: BatterySnapshot) {
        binding.healthValue.text = formatPercent(snapshot.healthPercent)
        binding.powerInValue.text = formatPower(snapshot.chargingPowerW)
        binding.powerOutValue.text = formatPower(snapshot.dischargingPowerW)
        binding.currentValue.text = formatCurrent(snapshot.currentMa)
        binding.voltageValue.text = formatVoltage(snapshot.voltageV)
        binding.sourceTypeValue.text = snapshot.sourceType
        binding.batteryTypeValue.text = snapshot.batteryType
        binding.statusValue.text = snapshot.rawStatus.ifBlank { "unknown" }

        AppLogger.d(
            "MainActivity",
            "battery_snapshot",
            mapOf(
                "health" to snapshot.healthPercent,
                "charging_w" to snapshot.chargingPowerW,
                "discharging_w" to snapshot.dischargingPowerW,
                "current_ma" to snapshot.currentMa,
                "voltage_v" to snapshot.voltageV,
                "source" to snapshot.sourceType
            )
        )
    }

    private fun toggleAdbByRoot() {
        lifecycleScope.launch {
            binding.progressBar.isVisible = true
            val enabled = binding.adbRootSwitch.isChecked
            val success = withContext(Dispatchers.IO) {
                AdbRootController.setEnabled(enabled)
            }
            binding.progressBar.isVisible = false

            val message = if (success) {
                if (enabled) "ADB root调试已开启" else "ADB root调试已关闭"
            } else {
                "执行失败，请确认设备已root并授予su权限"
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            AppLogger.i("MainActivity", "adb_toggle", mapOf("enabled" to enabled, "success" to success))
        }
    }

    private fun formatPercent(value: Double?): String =
        value?.let { "%.1f%%".format(it) } ?: "N/A"

    private fun formatPower(value: Double?): String =
        value?.let { "%.2f W".format(it) } ?: "N/A"

    private fun formatCurrent(value: Double?): String =
        value?.let { "%.1f mA".format(it) } ?: "N/A"

    private fun formatVoltage(value: Double?): String =
        value?.let { "%.3f V".format(it) } ?: "N/A"
}
