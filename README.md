# power-tv

一款本地优先的 Android 系统监控器，灵感来自 [btop](https://github.com/aristocratos/btop)。界面使用 Material 3，实时查看 CPU、GPU、温度、内存、电池和设备信息，并尽力列出系统允许访问的进程。

> MVP 分支：`mvp/1.0.00` · versionName `1.0.00` · minSdk 24 · compileSdk 37

| 概览 · 浅色 | 详情 · 浅色 |
| --- | --- |
| ![Dashboard light](docs/screenshots/dashboard-light.png) | ![Dashboard details](docs/screenshots/dashboard-details-light.png) |

| 概览 · 深色（中文） | 进程 · 深色（中文） |
| --- | --- |
| ![Dashboard dark](docs/screenshots/dashboard-dark-zh.png) | ![Processes dark](docs/screenshots/processes-dark-zh.png) |

## 功能

- **实时概览**：每秒采样，卡片式展示设备状态与最新数据。
- **CPU**：整体与每核心占用、在线状态、当前/最高频率、60 点滚动迷你图。
- **GPU**：尽力读取 Adreno / Mali / devfreq 的占用与频率，并复用温度传感器数据。
- **温度**：公开热管理 API、可读的 thermal sysfs 传感器、电池温度兜底，显示平台热状态。
- **内存**：已用/总量、可用、缓存、Swap，以及实时占比。
- **电池**：电量、充电状态、温度、电压、电流。
- **进程**：列出系统允许访问的进程，按内存或 CPU 排序，并明确解释 Android 的可见性限制。
- **设计**：Material 3 组件、浅色/深色主题、中英双语、自定义脉冲图标与启动画面。
- **隐私**：仅在本机采集数据，无网络权限，不上传任何信息。

## 数据来源与限制

| 指标 | 数据来源 | 说明 |
| --- | --- | --- |
| CPU | `/proc/stat`、`/sys/.../cpufreq` | 新版 Android 可能向普通应用隐藏 `/proc/stat`，此时显示限制说明而不是假数据。 |
| GPU | 厂商 sysfs（kgsl / mali / devfreq） | Android 没有公开 GPU 利用率 API，只有内核开放节点时可用。 |
| 温度 | `HardwarePropertiesManager`、thermal sysfs、电池广播 | 不同厂商传感器命名与权限不同，缺失的传感器会自动省略。 |
| 内存 | `/proc/meminfo`、`ActivityManager.MemoryInfo` | 权限内可稳定读取。 |
| 电池 | `ACTION_BATTERY_CHANGED`、`BatteryManager` | 通过粘性广播读取，无需注册常驻接收器。 |
| 进程 | `/proc/<pid>` | Android 10+ 普通应用通常只能看到自身进程；不做 Root、不申请特殊权限。 |

应用仅声明常规 Android 组件，不申请 `INTERNET`、定位、存储或无障碍权限。

## 工程结构

```text
app/src/main/java/com/zitzhen/powertv/
├── MainActivity.kt              # 应用壳、底部导航、生命周期采集
├── data/
│   ├── model/                   # 不可变遥测模型
│   ├── proc/                    # /proc CPU、内存、进程采样与解析
│   └── system/                  # 温度、电池、GPU、设备与聚合器
└── ui/
    ├── MonitorViewModel.kt      # StateFlow + 1 秒采样循环
    ├── DashboardBinder.kt       # 概览卡片渲染
    ├── ProcessBinder.kt         # 进程列表与排序
    ├── Formatters.kt            # 单位与数值格式化
    └── widget/SparklineView.kt  # 自绘滚动折线图
```

纯解析逻辑与 Android 文件读取分离，`/proc/stat`、`/proc/meminfo`、`/proc/<pid>/stat` 和 GPU sysfs 解析都有 JVM 单元测试。

## 构建与运行

需要 Android Studio（含 JBR 17+）与 Android SDK 37。命令行构建：

```bash
export JAVA_HOME=/opt/android-studio/jbr   # 按本机 Android Studio 路径调整
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

运行单元测试：

```bash
./gradlew testDebugUnitTest
```

## 已知边界

- Emulator / 部分新系统会禁止读取 `/proc/stat`，此时系统级 CPU 占用不可得；卡片会直接说明原因。
- GPU 计数依赖具体内核，模拟器和多数量产机可能完全不可用。
- 进程列表受 Android 沙箱限制，通常只显示 power-tv 自身。
- 温度传感器名称主要由内核提供，个别厂商名称会原样显示。
- 当前为 MVP，暂未加入历史持久化、桌面小组件、通知栏监控或 Shizuku/ADB 数据桥。

## 后续方向

1. 可选 Shizuku / ADB 数据桥，在用户明确授权后补全系统 CPU 与进程数据。
2. 可配置采样间隔、图表时间窗与温度阈值告警。
3. 桌面小组件、前台服务与通知栏实时概览。
4. 平板/横屏自适应布局与更完整的无障碍语义。

## 提交约定

每个提交只做一件事，避免混合无关改动；变更按 `feat` / `fix` / `chore` / `docs` 等前缀组织。当前开发分支为 `mvp/1.0.00`。
