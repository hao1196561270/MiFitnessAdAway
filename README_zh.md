# MiFitnessAdAway

[English](README.md) | 中文

小米运动健康（`com.mi.health` 3.58.0）去广告 LSPosed 模块，基于现代 libxposed API 102 开发（适配 LSPosed ≥ v2.1.1 / KernelSU）。

> **v1.0.3 已在真机验证**（一加 PLQ110 / Android 16 / KernelSU / LSPosed 2.1.1）：开屏 / 健康 / 运动 / 设备 / 我的 及健康详情页广告全部清除，正常功能完好；试用表盘自动导出供第三方导入。

## 功能

| 移除的广告 | 状态 |
|---|---|
| 开屏广告（图/视频） | ✅ |
| 首页健康页推广卡片 | ✅ |
| 设备页推广卡片 | ✅ |
| 设备红点（底部"设备"tab + 首页"系统设置"入口） | ✅ |
| 运动页轮播卡片 | ✅ |
| 运动页运营卡片（训练指标以下） | ✅ 并禁用多余滚动 |
| 我的页 VIP 会员卡 | ✅ |
| 我的页健康问诊卡 | ✅ |
| 健康详情页问诊卡片（睡眠 / 心率 / 血氧饱和度） | ✅ |
| 平安健康问诊卡（数据层：bindOneBanner/bindTwoBanners） | ✅ |
| 蚂蚁阿福 AI 解读卡（睡眠 / 心率页顶部） | ✅ |
| 睡眠页研究 / 改善卡片（睡眠呼吸暂停研究、睡眠健康研究、21 天改善计划） | ✅ |
| 试用表盘自动导出（换新 ID → Download/，第三方导入）+ 防删除保护 | ✅（实验）|

模块自带**设置界面**，共 16 个开关（libxposed RemotePreferences，修改后重启应用生效）：

- 总开关（启用去广告）
- 首页 / 设备 / 我的VIP / 我的问诊 / 运动轮播 / 运动运营 / 开屏 / 公告 各页面开关
- 健康问诊卡片（睡眠 / 心率 / 血氧页面）
- 睡眠研究 / 改善卡片
- 设备红点（底部 tab + 系统设置入口）
- 试用表盘自动导出（实验，默认关闭）
- 反 hook 检测（`SensorHelper.A()/D()` → 0）
- **隐藏桌面图标**（即时生效无需重启；隐藏后仍可从 LSPosed 打开设置页）
- 调试日志

设置界面颜色跟随系统深色/浅色模式。

## 实现原理

- **数据层拦截**：banner 接口 / 开屏缓存 / 会员数据 / 问诊数据 / 平安健康 banner 绑定方法直接返回空或跳过
- **视图层兜底**：「我的」页为 React Native（YRN）渲染 —— 通过视图树逐层上卷隐藏广告卡片，后续内容自动上移填补
- **健康详情页**：蚂蚁阿福 AI 解读卡（AqView）与睡眠研究 / 改善卡按 resource-id 定位，视图树扫描隐藏
- **设备红点**：伪装 `PowerManager.isIgnoringBatteryOptimizations` 返回 true（等效"已忽略电池优化"），并让表盘红点 getter 返回 false，从而消除底部"设备"tab 红点与首页"系统设置"入口红点
- **运动页锚点策略**：「训练指标」以下运营区整体移除，并禁用页面滚动
- **试用表盘自动导出（实验）**：试用下载后，缓存的 `resource.bin` 按"12→19"规则换新 ID（等长），以中文名写入 `Download/` 供第三方软件导入；导出 ID 从服务端清理名单中摘除，同步不再误删；超 5 分钟的已导出缓存自动清理；每次扫描经 Toast/通知告知结果

## 要求

- LSPosed ≥ 2.1.1（Zygisk）/ KernelSU
- `com.mi.health` 3.58.0（其他版本 hook 点可能偏移）

## 构建

需要 Gradle 9.5.1、AGP 9.2.1、JDK 17、compileSdk 37。

```powershell
gradle assembleRelease   # 产物 app/build/outputs/apk/release/app-release.apk
```

若本地存在 `keystore/mifitnessadaway.keystore` 与 `keystore/signing.properties`（均不入库），Release 使用正式签名；否则自动回退 debug 签名。

## 安装

1. 真机 root（KernelSU 或 Magisk）+ LSPosed v2.1.1+（Zygisk）
2. `adb install app-release.apk`
3. LSPosed 中启用模块（作用域已含 `com.mi.health`）
4. 重启一次；打开桌面图标可调整开关

## 仓库结构

```
app/src/main/java/io/github/hao1196561270/mifitnessadaway/
├── AdAwayModule.java     # libxposed 入口（全部 hook）
├── SettingsActivity.java # 设置界面（深浅色自适应、隐藏图标开关）
├── MiFitnessApp.java     # XposedService 桥接（RemotePreferences）
└── Prefs.java            # 设置键定义
app/src/main/resources/META-INF/xposed/  # 模块声明（module.prop / java_init.list / scope.list）
```

## License

Apache License 2.0