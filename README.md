# Battery Monitor

Android 电池监测应用（Root 增强版）：

- 现代化 Material 3 UI，并内置 3 套样板风格可快速预览
- 通过 `su` 读取 `/sys/class/power_supply` 计算电池健康度（最大容量 / 设计容量）
- 展示充电功率、放电功率、电流、电压、交流/直流类型与电池技术类型
- Debug 日志开关（统一结构化日志格式）
- Root 直接接管 ADB 调试开关（启动/停止 `adbd` 并设置属性）
