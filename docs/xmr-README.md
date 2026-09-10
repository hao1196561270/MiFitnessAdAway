# MiFitnessAdAway

Remove ads from Xiaomi Mi Fitness (Xiaomi Sports & Health, `com.mi.health/com.xiaomi.wearable` 3.0+). A modern LSPosed module built with libxposed API 102.

Remove ads from 小米运动健康 (`com.mi.health/com.xiaomi.wearable` 3.0+) - 现代 libxposed API 102 模块。

## What it removes / 移除内容

- Splash ads / 开屏广告
- Home health tab promotion cards / 首页健康页推广卡片
- Device tab promotion cards / 设备页推广卡片
- Device red dots (bottom nav "Device" tab + home "System settings" entry) / 设备红点（底部"设备"tab + 首页"系统设置"入口）
- Sport tab carousel & operation cards (below "training index") / 运动页轮播卡与运营卡（训练指标以下）
- Mine tab VIP membership card & doctor consultation card / 我的页 VIP 会员卡与健康问诊卡
- Health detail pages consultation cards (Sleep / Heart rate / SpO₂ / Stress) / 健康详情页问诊卡片（睡眠 / 心率 / 血氧 / 压力）
- Sleep research / improvement cards / 睡眠研究 / 改善卡片
- Weight page personalized plan card / 体重页个性化减重方案栏
- Trial watchface auto-export (re-ID'd → Download/, third-party import) + cleanup protection / 试用表盘自动导出（换新 ID → Download/，第三方导入）+ 防删除保护
- App update dialog ("Update available" prompt) / 应用更新弹窗（"发现新版本"提示）
- VIP promo popup ("会员限时低价福利" / "抢先购买", shipped unverified) / 会员推广弹窗（"会员限时低价福利"/"抢先购买"，未验证发布）

A built-in grouped-card settings UI with 16 toggles is included (dark/light theme aware). Groups follow the page each switch belongs to, collapse by tapping a group title, and a top card shows the enabled-count summary together with the master switch; dependent switches dim while the master is off.
内置分组卡片式设置界面（16 个开关，跟随系统深浅色）。分组按开关所属界面划分，点标题可收起/展开，顶部卡片为启用数摘要 + 总开关；总开关关闭时依赖它的开关自动置灰。

## Requirements / 要求

- LSPosed ≥ 2.1.1 (Zygisk) / KernelSU
- com.mi.health/com.xiaomi.wearable 3.0+ (each hook installs independently; missing entry points on a version are skipped gracefully)

## Build / 构建

```
gradle assembleRelease
```

Requires Gradle 9.5.1, AGP 9.2.1, JDK 17, compileSdk 37.

## License

Apache License 2.0
