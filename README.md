# MiFitnessAdAway

English | [中文](README_zh.md)

Remove ads from Xiaomi Mi Fitness (Xiaomi Sports & Health, `com.mi.health/com.xiaomi.wearable` 3.0+), built as a modern **libxposed API 102** LSPosed module (requires LSPosed ≥ v2.1.1 / KernelSU).

> **v1.0.9 verified on device** (OnePlus PLQ110 / Android 16 / KernelSU / LSPosed 2.1.1): splash / home / sport / device / mine / health detail tabs cleaned, all normal features intact; trial watchfaces auto-export for third-party import.

## Features

| Removed ads | Status |
|---|---|
| Splash ads (image/video) | ✅ |
| Home health tab promotion cards | ✅ |
| Device tab promotion cards | ✅ |
| Device red dots (bottom nav "Device" tab + home "System settings" entry) | ✅ |
| Sport tab carousel cards | ✅ |
| Sport tab operation cards (below "training index") | ✅ + scroll disabled |
| Mine tab VIP membership card | ✅ |
| Mine tab doctor consultation card | ✅ |
| Health detail pages consultation cards (Sleep / Heart rate / SpO₂ / Stress) | ✅ |
| "PingAn Health" consultation cards (data-layer: bindOneBanner/bindTwoBanners) | ✅ |
| "AntBoy AI" interpretation card (top of Sleep / Heart rate pages) | ✅ |
| Sleep page research / improvement cards (sleep-breathing-apnea research, sleep-health research, 21-day improvement plan) | ✅ |
| Weight page personalized plan card ("个性化减重方案") | ✅ |
| Trial watchface auto-export (re-ID'd → Download/, third-party import) + cleanup protection | ✅ (experimental) |
| App update dialog ("Update available" prompt) | ✅ |
| VIP promo popup ("会员限时低价福利" / "抢先购买") | ⚠️ unverified (server-driven, cannot be reproduced on demand) |

The module app ships with a **settings UI** with 19 toggles (libxposed RemotePreferences, changes take effect after restarting the target app):

- Master (enable ad-removal)
- Home / device / mine VIP / mine doctor / sport carousel / sport operation / splash / announcement / app-update-dialog / weight-plan / vip-promo-popup toggles
- Health consultation card (Sleep / Heart rate / SpO₂ / Stress pages)
- Sleep research / improvement cards
- Device red dots (bottom nav + system settings entry)
- Watchface auto-export (experimental, off by default)
- Anti-hook detection (`SensorHelper.A()/D()` → 0)
- **Hide launcher icon** (applies instantly, no restart needed; the settings page stays reachable from LSPosed)
- Debug log

The settings UI follows the system dark/light theme.

## How it works

- **Data-layer interception**: banner APIs, splash cache, membership data, doctor data and PingAn-Health banner binders return empty / are skipped.
- **View-layer fallback**: the "Mine" tab is rendered by React Native (YRN) — ad cards are collapsed layer-by-layer via view-tree scan, and following content is shifted up to fill the gap.
- **Health detail pages**: the "AntBoy AI" interpretation card (AqView) and sleep research/improvement cards are hidden via view-tree scan with resource-id targeting.
- **Device red dots**: `PowerManager.isIgnoringBatteryOptimizations` is faked to true (equivalent to "battery optimization ignored") plus face-entrance red-dot getters return false, which removes the bottom-nav "Device" tab dot and the home "System settings" entry dot.
- **Sport anchor strategy**: everything below the "training index" anchor is removed as a whole, and page scrolling is disabled.
- **Watchface auto-export (experimental)**: after a trial download, the cached `resource.bin` is re-ID'd (`12→19` prefix swap, same length) and written to `Download/` under its Chinese name for third-party import; exported IDs are filtered out of the server-side cleanup list so sideloaded faces survive sync; exported cache is removed whole-directory on the next scan (snapshot-based, with handoff/push guards); every scan reports via Toast/notification.

- **App update dialog**: `AppUpgradeUtil.showUpdateDialogIfNeed` is skipped, so the "Update available" popup never shows (background version check still runs; manual update check on the Mine page is unaffected).
- **VIP promo popup**: `MembershipDialogManager.showMembershipExpiredFaceDialog` is skipped, so the full-screen membership marketing popup never appears; the caller's dismiss callback is still invoked so the birthday-medal flow it continues is left intact. Only this automatic chain is blocked — the user-initiated purchase dialog (`showMembershipDialog`, opened by tapping "开通会员") is untouched.
- **RN title cards**: Weight ("个性化减重方案"), Stress ("健康问诊") and Sleep ("健康研究/睡眠改善计划") cards are removed whole by title-text view-tree scan on the shared RN host (`YRNCFragment`), since these pages are React Native with server-driven copy and no stable data hooks.

## Requirements

- LSPosed ≥ 2.1.1 (Zygisk) / KernelSU
- `com.mi.health/com.xiaomi.wearable` 3.0+ (every hook installs independently — entry points missing on a version are skipped gracefully, the rest keep working)

## Build

Requires Gradle 9.5.1, AGP 9.2.1, JDK 17, compileSdk 37.

```powershell
gradle assembleRelease   # output: app/build/outputs/apk/release/app-release.apk
```

If `keystore/mifitnessadaway.keystore` and `keystore/signing.properties` exist locally (both git-ignored), the release is signed with the real key; otherwise it falls back to the debug key.

## Install

1. Rooted device (KernelSU or Magisk) + LSPosed v2.1.1+ (Zygisk)
2. `adb install app-release.apk`
3. Enable the module in LSPosed (static scope already includes `com.mi.health/com.xiaomi.wearable`)
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