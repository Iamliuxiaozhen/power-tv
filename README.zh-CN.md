# power-tv

一款本地优先的 Android 系统监控器，灵感来自 [btop](https://github.com/aristocratos/btop)。它在本机采集 CPU、GPU、温度、内存、电池和进程数据，并用 Material 3 界面展示。

| [English](README.md) | **[简体中文](README.zh-CN.md)** | [繁體中文](README.zh-TW.md) |
| :---: | :---: | :---: |

| 概览 · 浅色 | 详情 · 浅色 |
| --- | --- |
| ![概览浅色](docs/screenshots/dashboard-light.png) | ![详情浅色](docs/screenshots/dashboard-details-light.png) |

| 概览 · 深色（中文） | 进程 · 深色（中文） |
| --- | --- |
| ![概览深色](docs/screenshots/dashboard-dark-zh.png) | ![进程深色](docs/screenshots/processes-dark-zh.png) |

## 功能

- **概览**：每秒刷新，显示实时状态，并为 CPU、GPU、温度和内存保留最近 60 个采样点的迷你折线图。
- **CPU**：整体与每核心占用、在线核心、当前/最高频率和 CPU 型号。
- **GPU**：尽量从 Adreno（kgsl）、Mali 和 devfreq sysfs 节点读取占用与频率。
- **温度**：`HardwarePropertiesManager`、可读的 thermal zone、电池温度兜底和系统热状态。
- **内存**：已用/总量、可用、缓存和 Swap。
- **电池**：电量、充电状态、温度、电压和电流。
- **进程**：列出系统允许访问的进程，可按内存或 CPU 排序，并说明可见性限制。
- **设备**：厂商/型号、Android 版本、硬件、ABI、运行时间和在线核心数。
- **Shizuku（可选）**：用户授权后，通过 ADB shell 读取系统 CPU 统计和完整进程列表；未授权时自动回退。
- **界面**：Material 3、浅色/深色主题、英文、简体中文和繁体中文（香港/台湾）。
- **隐私**：所有数据只在本机处理；应用未声明 `INTERNET` 权限。

## 环境要求

- Android 7.0（API 24）或更高版本。
- Android SDK Platform 37。
- JDK 24。Gradle Wrapper 使用 Gradle 9.6.0，并会在需要时通过 Foojay resolver 获取 JDK 24。
- 可选：Shizuku 11 或更高版本。power-tv 本身不要求 Root。

## 构建与运行

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 Android Studio 打开并运行。运行 JVM 单元测试：

```bash
./gradlew testDebugUnitTest
```

## 可选：Shizuku

Shizuku 不会随应用打包，只有在你主动授权后才会使用。

1. 安装 Shizuku 11 或更高版本，并按 Shizuku 的指引启动（可通过 ADB、无线调试或 root 启动）。
2. 在 power-tv 中打开“进程”页，点击“授权”。
3. 在 Shizuku 授权弹窗中允许访问，等待状态变为已连接。

连接后，应用通过运行在 ADB shell 下的用户服务读取 `/proc/stat` 和进程数据。未连接时，界面会说明系统 CPU 占用或进程列表不可用的原因，而不是显示推测值。

## 数据来源与限制

| 指标 | 数据来源 | 说明 |
| --- | --- | --- |
| CPU | `/proc/stat`、`cpufreq` sysfs、Shizuku | 部分 Android 版本会向普通应用隐藏 `/proc/stat`；未连接 Shizuku 时总量可能不可用，但可读的频率和在线状态仍会显示。 |
| GPU | 厂商 sysfs（`kgsl`、`mali`、`devfreq`） | Android 没有公开的 GPU 利用率 API，只有内核开放节点时才有数据。 |
| 温度 | `HardwarePropertiesManager`、thermal sysfs、电池广播 | 传感器名称和可用性取决于厂商内核，缺失的传感器会被省略。 |
| 内存 | `/proc/meminfo`、`ActivityManager.MemoryInfo` | 在普通应用权限下可稳定读取。 |
| 电池 | `ACTION_BATTERY_CHANGED`、`BatteryManager` | 通过粘性广播读取。 |
| 进程 | `/proc/<pid>`、Shizuku | Android 10+ 的普通应用通常只能看到自身进程；授权 Shizuku 后可列出全部进程。应用本身不请求 Root。 |

## 工程结构

```text
app/src/main/java/com/zitzhen/powertv/
├── MainActivity.kt              # Activity、底部导航与关于对话框
├── PowerTvApp.kt                # 持有 Shizuku 连接的 Application
├── data/
│   ├── model/                   # 不可变遥测模型
│   ├── proc/                    # /proc CPU、内存和进程采样与解析
│   ├── shizuku/                 # AIDL 读取服务与 Shizuku 生命周期
│   └── system/                  # 电池、温度、GPU、设备信息与聚合
└── ui/
    ├── MonitorViewModel.kt      # 以 StateFlow 对外的每秒采样循环
    ├── DashboardBinder.kt       # 概览卡片与迷你折线图
    ├── ProcessAdapter.kt        # 进程列表适配器
    ├── ProcessBinder.kt         # 进程页状态与排序
    ├── Formatters.kt            # 单位与数值格式化
    ├── LoadPalette.kt           # 负载等级配色
    └── widget/SparklineView.kt  # 自绘滚动折线图
```

特权读取入口定义在 `app/src/main/aidl/com/zitzhen/powertv/data/shizuku/IProcReader.aidl`。`app/src/test` 下的 JVM 测试覆盖 `/proc` 解析器、Shizuku 数据表编解码和格式化逻辑。

## 已知限制

- 未连接 Shizuku 时，系统 CPU 占用和其他进程可能被隐藏；可读时仍会显示每核心频率和在线状态。
- GPU 计数依赖厂商内核，模拟器和多数量产机通常无法读取。
- 温度传感器名称由内核提供，会原样显示。
- 目前没有历史数据持久化、桌面小组件、前台服务或通知栏监控。
- power-tv 不请求 Root；Shizuku 是唯一的特权读取方式。

