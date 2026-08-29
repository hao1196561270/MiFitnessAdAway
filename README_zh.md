# MiFitnessAdAway

[English](README.md) | 中文

小米运动健康（`com.mi.health` 3.58.0）去广告 LSPosed 模块（libxposed API 102，适配 LSPosed ≥ v2.1.1 / KernelSU）。

> **v1.0 已在真机完整验证**（一加 PLQ110 / Android 16 / KernelSU / LSPosed v2.1.1）：开屏 / 健康 / 运动 / 设备 / 我的 五处广告全部清除，正常功能完好。

## 功能

模块 App 自带**设置界面**（桌面图标打开），11 个开关（libxposed RemotePreferences，改后重启应用生效）：

| 开关 | 默认 | 目标 |
|---|---|---|
| 总开关（启用去广告） | 开 | 全部去广告逻辑 |
| 首页健康界面推广卡片 | 开 | 「暑期焕新季/以旧换新」等 |
| 设备界面推广卡片 | 开 | 「REDMI Watch 6」等 |
| 我的界面 VIP 会员卡 | 开 | 「VIP 小米运动健康会员/立即开通」 |
| 我的界面健康问诊卡 | 开 | 「公立医生在线问诊」 |
| 运动界面轮播卡片 | 开 | 运动页轮播推广 |
| 运动界面运营卡片（训练指标以下） | 开 | 运动团 / 活动推荐（线上赛/奖牌赛） |
| 开屏广告 | 开 | Splash 广告图/视频 |
| 公告 banner | 开 | AnnouncementBanner |
| 反 hook 检测 | 开 | SensorHelper.A/.D → 0 |
| 调试日志 | 开 | Logcat 输出 |

设置界面颜色跟随系统深色/浅色模式。

## 功能实现（数据层拦截 + 视图层兜底）

- **banner 底座**：`BannerResponseResultV1/V2.getBannerList()` → 空列表（首页/设备/运动共用）
- **开屏**：`SplashAdPreference.getShowSplashAdItem()` → null（本地缓存广告）
- **VIP 会员卡**：`MembershipHelperImpl.getPaymentsPromotion/getOperationConfig/getVipInfo` → null + `IWebSyncKt.getSHOW_AIDONG_PAYMENT_ENTRANCE` → false + `MineVipView.onAttachedToWindow` → GONE
- **运动运营区**：`SportTabV4Fragment` 锚点「训练指标」以下整体移除 + 禁用页面滚动
- **RN 视图扫描兜底**：「我的」页（YRN）为 JS 渲染，数据层不可达时按文案逐层上卷隐藏卡片并上移后续内容（VIP 卡/问诊卡）
- **反检测**：`SensorHelper.A()/D()` → 0

## 构建

需要 Gradle 9.5.1 + AGP 9.2.1 + JDK 17 + compileSdk 37。

```powershell
gradle assembleRelease   # 产物 app/build/outputs/apk/release/app-release.apk
```

若本机存在 `keystore/mifitnessadaway.keystore` 与 `keystore/signing.properties`（均不入库），Release 使用正式签名；否则自动回退 debug 签名。

## 安装

1. 真机 root（KernelSU 或 Magisk）+ LSPosed v2.1.1+（Zygisk）
2. `adb install app-release.apk`
3. LSPosed 中启用模块（`staticScope=true` + `scope.list` 已含 `com.mi.health`）
4. 重启一次，打开模块桌面图标可调开关

目标 App 版本需为 **com.mi.health 3.58.0**（其他版本 hook 点可能偏移）。

## 仓库结构

```
app/src/main/java/com/mifitness/adaway/
├── AdAwayModule.java    # libxposed 入口（全部 hook）
├── SettingsActivity.java # 设置界面（深浅色自适应）
├── MiFitnessApp.java    # XposedService 桥接（RemotePreferences）
├── Prefs.java           # 设置键定义
└── Config.java          # 目标包名/默认开关常量
app/src/main/resources/META-INF/xposed/  # 模块声明（module.prop / java_init.list / scope.list）
```

## License

Apache License 2.0
