# Changelog 更新日志

## v1.0.2 (versionCode 22)

> 双语更新日志 | Bilingual changelog

### New: Health detail pages consultation cards / 新增：健康详情页问诊卡片
Remove the "PingAn Health" consultation card on Sleep / Heart rate / SpO₂ pages (data-layer: `bindOneBanner` / `bindTwoBanners` skipped, official-channel style), plus the "AntBoy AI interpretation" card (AqView) on top of Sleep / Heart rate pages.
- 新增去除睡眠 / 心率 / 血氧饱和度页面的「平安健康问诊」卡（数据层跳过 `bindOneBanner` / `bindTwoBanners`，模拟官方渠道行为），并去除睡眠 / 心率页顶部「蚂蚁阿福 AI 解读」卡（AqView）。

### New: Sleep research / improvement cards / 新增：睡眠研究改善卡片
Remove the sleep-breathing-apnea research card, sleep-health research card and the 21-day improvement-plan card at the bottom of the Sleep page (view-tree scan by resource-id; normal entries like rhythm monitoring and "Learn about sleep" stay untouched).
- 新增去除睡眠页底部「睡眠呼吸暂停研究」「睡眠健康研究」「21 天改善计划」卡片（按 resource-id 视图扫描；呼吸节奏监测、「了解睡眠」等正常入口不受影响）。

### New: Device red dots / 新增：设备红点移除
Remove the red dot on the bottom-nav "Device" tab and on the home "System settings" entry (fake `PowerManager.isIgnoringBatteryOptimizations` = true + face-entrance red-dot getters return false).
- 新增去除底部导航「设备」tab 红点与首页「系统设置」入口红点（伪装已忽略电池优化 + 表盘红点 getter 返回 false）。

### New: Settings toggles / 新增：设置开关
Three new toggles in the settings UI, 12 → 15 (default on): "Health consultation card (Sleep/Heart rate/SpO₂)", "Sleep research/improvement cards", "Device red dots".
- 设置界面新增 3 个开关（默认开启），12 → 15 个：「健康问诊卡片（睡眠/心率/血氧）」「睡眠界面研究/改善卡片」「设备红点（底部tab/系统设置入口）」。

### Code / 其他
- Code cleanup; all existing ad-removal features (splash / home / device / sport / mine tabs) unchanged.
- 代码清理；原有去广告功能（开屏 / 首页 / 设备 / 运动 / 我的）保持不变。
- Docs updated (EN + 中文 README).
- 文档更新（中英双语 README）。

---

## v1.0.1 (versionCode 21)

### New: Hide launcher icon toggle / 新增：隐藏桌面图标开关
- Hide the module launcher icon instantly (no restart needed); the settings page stays reachable from LSPosed.
- 新增「隐藏桌面图标」开关：即时生效无需重启；隐藏后仍可从 LSPosed 打开设置页。
- Settings UI now has 12 toggles (master / per-page ad toggles / anti-detection / debug log / hide icon).
- 设置界面共 12 个开关（总开关 / 各页面开关 / 反检测 / 调试日志 / 隐藏图标）。
- Code cleanup; all existing ad-removal features unchanged.
- 代码清理：移除冗余代码，保持全部功能不变。
- Docs updated (EN + 中文).
- 文档更新（中英双语 README）。