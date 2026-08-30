# MiFitnessAdAway

English | [中文](README_zh.md)

Remove ads from Xiaomi Mi Fitness (Xiaomi Sports & Health, `com.mi.health` 3.58.0), built as a modern **libxposed API 102** LSPosed module (requires LSPosed ≥ v2.1.1 / KernelSU).

> **v1.0.1 verified on device** (OnePlus PLQ110 / Android 16 / KernelSU / LSPosed 2.1.1): splash / home / sport / device / mine tabs cleaned, all normal features intact.

## Features

| Removed ads | Status |
|---|---|
| Splash ads (image/video) | ✅ |
| Home health tab promotion cards | ✅ |
| Device tab promotion cards | ✅ |
| Sport tab carousel cards | ✅ |
| Sport tab operation cards (below "training index") | ✅ + scroll disabled |
| Mine tab VIP membership card | ✅ |
| Mine tab doctor consultation card | ✅ |

The module app ships with a **settings UI** with 12 toggles (libxposed RemotePreferences, changes take effect after restarting the target app):

- Master (enable ad-removal)
- Home / device / mine VIP / mine doctor / sport carousel / sport operation / splash / announcement toggles
- Anti-hook detection (`SensorHelper.A()/D()` → 0)
- **Hide launcher icon** (applies instantly, no restart needed; the settings page stays reachable from LSPosed)
- Debug log

The settings UI follows the system dark/light theme.

## How it works

- **Data-layer interception**: banner APIs, splash cache, membership data and doctor data return empty.
- **View-layer fallback**: the "Mine" tab is rendered by React Native (YRN) — ad cards are collapsed layer-by-layer via view-tree scan, and following content is shifted up to fill the gap.
- **Sport anchor strategy**: everything below the "training index" anchor is removed as a whole, and page scrolling is disabled.

## Requirements

- LSPosed ≥ 2.1.1 (Zygisk) / KernelSU
- `com.mi.health` 3.58.0 (hook points may differ on other versions)

## Build

Requires Gradle 9.5.1, AGP 9.2.1, JDK 17, compileSdk 37.

```powershell
gradle assembleRelease   # output: app/build/outputs/apk/release/app-release.apk
```

If `keystore/mifitnessadaway.keystore` and `keystore/signing.properties` exist locally (both git-ignored), the release is signed with the real key; otherwise it falls back to the debug key.

## Install

1. Rooted device (KernelSU or Magisk) + LSPosed v2.1.1+ (Zygisk)
2. `adb install app-release.apk`
3. Enable the module in LSPosed (static scope already includes `com.mi.health`)
4. Reboot once; open the module launcher icon to adjust toggles

## Repository layout

```
app/src/main/java/io/github/hao1196561270/mifitnessadaway/
├── AdAwayModule.java     # libxposed entry (all hooks)
├── SettingsActivity.java # settings UI (dark/light adaptive, hide-icon toggle)
├── MiFitnessApp.java     # XposedService bridge (RemotePreferences)
└── Prefs.java            # preference keys
app/src/main/resources/META-INF/xposed/  # module declarations (module.prop / java_init.list / scope.list)
```

## License

Apache License 2.0