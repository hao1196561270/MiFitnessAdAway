# MiFitnessAdAway

Remove ads from Xiaomi Mi Fitness (Xiaomi Sports & Health, `com.mi.health` 3.58.0). A modern LSPosed module built with libxposed API 102.

Remove ads from 小米运动健康 (`com.mi.health` 3.58.0) - 现代 libxposed API 102 模块。

## What it removes / 移除内容

- Splash ads / 开屏广告
- Home health tab promotion cards / 首页健康页推广卡片
- Device tab promotion cards / 设备页推广卡片
- Device red dots (bottom nav "Device" tab + home "System settings" entry) / 设备红点（底部"设备"tab + 首页"系统设置"入口）
- Sport tab carousel & operation cards (below "training index") / 运动页轮播卡与运营卡（训练指标以下）
- Mine tab VIP membership card & doctor consultation card / 我的页 VIP 会员卡与健康问诊卡
- Health detail pages consultation cards (Sleep / Heart rate / SpO₂) / 健康详情页问诊卡片（睡眠 / 心率 / 血氧）
- Sleep research / improvement cards / 睡眠研究 / 改善卡片
- Trial watchface auto-export (re-ID'd → Download/, third-party import) + cleanup protection / 试用表盘自动导出（换新 ID → Download/，第三方导入）+ 防删除保护

A built-in settings UI with 16 toggles is included (dark/light theme aware).
内置设置界面（16 个开关，跟随系统深浅色）。

## Requirements / 要求

- LSPosed ≥ 2.1.1 (Zygisk) / KernelSU
- com.mi.health 3.58.0

## Build / 构建

```
gradle assembleRelease
```

Requires Gradle 9.5.1, AGP 9.2.1, JDK 17, compileSdk 37.

## License

Apache License 2.0
