# MiFitnessAdAway

English | [中文](README_zh.md)

An ad-removal LSPosed module for Xiaomi Mi Fitness (Xiaomi Sports & Health, `com.mi.health` 3.58.0), built with the modern **libxposed API 102** (requires LSPosed ≥ v2.1.1 / KernelSU).

> **v1.0 verified on device** (OnePlus PLQ110 / Android 16 / KernelSU / LSPosed v2.1.1): splash / health / sport / device / mine tabs cleaned, all normal features intact.

## Features

The module app ships with a **settings UI** (launcher icon), 11 toggles backed by libxposed RemotePreferences (changes take effect after restarting the target app):

| Toggle | Default | Target |
|---|---|---|
| Master (enable ad-removal) | On | all ad-removal logic |
| Home health promotion cards | On | e.g. "Summer new season / trade-in" |
| Device promotion cards | On | e.g. "REDMI Watch 6" |
| Mine VIP membership card | On | "VIP Mi Fitness membership / Activate now" |
| Mine doctor consultation card | On | "online doctor consultation" |
| Sport carousel cards | On | sport tab carousel promotions |
| Sport operation cards (below training index) | On | Sport groups / activity recommendations (online races / medal races) |
| Splash ad | On | Splash image/video ads |
| Announcement banner | On | AnnouncementBanner |
| Anti-hook detection | On | `SensorHelper.A()/D()` → 0 |
| Debug log | On | Logcat output |

The settings UI follows the system dark/light theme.

## Implementation (data-layer interception + view-layer fallback)

- **Banner base**: `BannerResponseResultV1/V2.getBannerList()` → empty list (shared by home/device/sport)
- **Splash**: `SplashAdPreference.getShowSplashAdItem()` → null (also blanks locally cached ads)
- **VIP membership card**: `MembershipHelperImpl.getPaymentsPromotion/getOperationConfig/getVipInfo` → null + `IWebSyncKt.getSHOW_AIDONG_PAYMENT_ENTRANCE` → false + `MineVipView.onAttachedToWindow` → GONE
- **Sport operation area**: `SportTabV4Fragment` anchor "training index": everything below is removed as a whole + page scrolling disabled
- **RN view-tree scan fallback**: the "Mine" tab is rendered by YRN (React Native); when the data layer is unreachable, ad text rows are collapsed layer-by-layer (card removed entirely, following content shifted up to fill the gap)
- **Anti-detection**: `SensorHelper.A()/D()` → 0

## Build

Requires Gradle 9.5.1 + AGP 9.2.1 + JDK 17 + compileSdk 37.

```powershell
gradle assembleRelease   # output: app/build/outputs/apk/release/app-release.apk
```

If `keystore/mifitnessadaway.keystore` and `keystore/signing.properties` exist locally (both git-ignored), the release is signed with the real key; otherwise it automatically falls back to the debug key.

## Install

1. Rooted device (KernelSU or Magisk) + LSPosed v2.1.1+ (Zygisk)
2. `adb install app-release.apk`
3. Enable the module in LSPosed (static scope already includes `com.mi.health`)
4. Reboot once; open the module launcher icon to adjust toggles

Target app must be **com.mi.health 3.58.0** (hook points may differ on other versions).

## Repository layout

```
app/src/main/java/io/github/hao1196561270/mifitnessadaway/
├── AdAwayModule.java    # libxposed entry (all hooks)
├── SettingsActivity.java # settings UI (dark/light adaptive)
├── MiFitnessApp.java    # XposedService bridge (RemotePreferences)
├── Prefs.java           # preference keys
└── Config.java          # target package / default toggles
app/src/main/resources/META-INF/xposed/  # module declarations (module.prop / java_init.list / scope.list)
```

## License

Apache License 2.0
