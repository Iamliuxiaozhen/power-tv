# power-tv

A local-first Android system monitor inspired by [btop](https://github.com/aristocratos/btop). It samples CPU, GPU, thermal, memory, battery and process data on-device and presents it with Material 3.

| **[English](README.md)** | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) |
| :---: | :---: | :---: |

## Screenshots

| Dashboard home · light | Details · light |
| --- | --- |
| ![Dashboard home light](docs/screenshots/dashboard-light.png) | ![Dashboard details light](docs/screenshots/dashboard-details-light.png) |

| Dashboard · dark (Chinese) | Processes · dark (Chinese) |
| --- | --- |
| ![Dashboard dark](docs/screenshots/dashboard-dark-zh.png) | ![Processes dark](docs/screenshots/processes-dark-zh.png) |

### Splash screen

<p align="center">
  <img src="docs/screenshots/splash-light.png" width="280" alt="power-tv splash screen">
</p>

## Features

- **Dashboard** — one-second refresh with live status and 60-sample sparklines for CPU, GPU, thermal and memory.
- **CPU** — total and per-core load, online cores, current/max frequency and CPU model.
- **GPU** — best-effort utilization and frequency from Adreno (kgsl), Mali and devfreq sysfs nodes.
- **Thermal** — `HardwarePropertiesManager`, readable thermal zones, battery-temperature fallback and platform thermal status.
- **Memory** — used/total, available, cached and swap.
- **Battery** — level, charging state, temperature, voltage and current.
- **Processes** — readable processes, sortable by memory or CPU, with explicit visibility limits.
- **Device** — manufacturer/model, Android version, hardware, ABI, uptime and online core count.
- **Optional Shizuku** — user-granted ADB-shell access to system CPU stats and the full process list, with automatic fallback.
- **UI** — Material 3, light/dark theme, English, Simplified Chinese and Traditional Chinese (Hong Kong/Taiwan).
- **Privacy** — all data stays on the device; the app declares no `INTERNET` permission.

## Requirements

- Android 7.0 (API 24) or newer.
- Android SDK Platform 37.
- JDK 24. The Gradle wrapper uses Gradle 9.6.0 and provisions JDK 24 through the Foojay resolver when needed.
- Optional: Shizuku 11 or newer. power-tv itself does not require root.

## Build and run

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You can also open the project in Android Studio and run it from the IDE. Run the JVM unit tests with:

```bash
./gradlew testDebugUnitTest
```

## Optional: Shizuku

Shizuku is not bundled with power-tv and is only used after you grant access.

1. Install Shizuku 11 or newer and start it using its own instructions (ADB, wireless debugging or root).
2. Open the Processes tab in power-tv and tap **Grant**.
3. Allow access in the Shizuku dialog and wait for the status to become connected.

When connected, the app reads `/proc/stat` and process data through a user service running under the ADB shell UID. Without Shizuku, the UI explains why system CPU load or other processes are unavailable instead of showing guessed values.

## Data sources and limits

| Metric | Source | Notes |
| --- | --- | --- |
| CPU | `/proc/stat`, `cpufreq` sysfs, Shizuku | Some Android builds hide `/proc/stat` from normal apps. Without Shizuku the total may be unavailable, while readable frequencies and online states are still shown. |
| GPU | Vendor sysfs (`kgsl`, `mali`, `devfreq`) | Android has no public GPU utilization API; values exist only when the kernel exposes the nodes. |
| Thermal | `HardwarePropertiesManager`, thermal sysfs, battery broadcast | Sensor names and availability depend on the vendor kernel; missing sensors are omitted. |
| Memory | `/proc/meminfo`, `ActivityManager.MemoryInfo` | Stable under normal app permissions. |
| Battery | `ACTION_BATTERY_CHANGED`, `BatteryManager` | Read through the sticky broadcast. |
| Processes | `/proc/<pid>`, Shizuku | On Android 10+ normal apps usually see only themselves; Shizuku unlocks the full list. The app does not request root. |

## Project layout

```text
app/src/main/java/com/zitzhen/powertv/
├── MainActivity.kt              # Activity, bottom navigation, About dialog
├── PowerTvApp.kt                # Application owner of the Shizuku connection
├── data/
│   ├── model/                   # Immutable telemetry models
│   ├── proc/                    # /proc CPU, memory and process sampling/parsing
│   ├── shizuku/                 # AIDL reader service and Shizuku lifecycle
│   └── system/                  # Battery, thermal, GPU, device info and aggregation
└── ui/
    ├── MonitorViewModel.kt      # One-second sampling loop exposed as StateFlow
    ├── DashboardBinder.kt       # Dashboard cards and sparklines
    ├── ProcessAdapter.kt        # Process list adapter
    ├── ProcessBinder.kt         # Process screen state and sorting
    ├── Formatters.kt            # Unit and value formatting
    ├── LoadPalette.kt           # Load-level colors
    └── widget/SparklineView.kt  # Custom scrolling sparkline
```

The privileged path is defined by `app/src/main/aidl/com/zitzhen/powertv/data/shizuku/IProcReader.aidl`. JVM tests under `app/src/test` cover the `/proc` parsers, the Shizuku table codec and the formatters.

## Known limitations

- Without Shizuku, system CPU load and other processes may be hidden. Per-core frequency and online state are still shown when readable.
- GPU counters are vendor-specific and often unavailable, especially on emulators.
- Thermal sensor names come from the kernel and are displayed as-is.
- There is no history persistence, home-screen widget, foreground service or notification monitoring.
- power-tv does not request root; Shizuku is the only privileged path.

