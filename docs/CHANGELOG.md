# Changelog 更新日志

## v1.0.4 (versionCode 24)

### English

**Improved: Exported cache cleanup**
Exported face cache is now removed whole-directory on the next scan after export (previously: only `resource.bin` older than 5 minutes), so only the just-downloaded face stays cached. Snapshot-based matching plus 60-second handoff and 15-minute push guards prevent deleting files mid-download or mid-transfer.

**Cleanup: Dead code removal**
Removed unused parameter plumbing in the export chain and an unused import; no behavior change besides the cleanup upgrade above.

**Code**
All existing ad-removal and export features unchanged.

### 中文

**改进：已导出缓存清理**
已导出表盘缓存改为导出后下次扫描即整目录删除（之前：仅删超 5 分钟的 `resource.bin`），缓存里只留本次下载的。快照匹配 + 60 秒交接保护 + 15 分钟推送保护，不误删正在下载/传输的文件。

**清理：无用代码**
清理导出链路上无用的参数传递与无用 import；除上述清理升级外无行为变化。

**其他**
原有去广告与导出功能保持不变。

---

## v1.0.3 (versionCode 23)

### English

**New: Trial watchface auto-export**
After a trial download finishes, the cached `resource.bin` is automatically re-ID'd (`12→19` prefix swap, same length) and written to `Download/` under its Chinese face name (e.g. `蜘蛛侠超感大眼_190917425583.bin`), ready for third-party import (verified with AstroBox on Xiaomi Smart Band 10 Pro). Every scan reports via Toast/notification; already-exported faces are never exported twice.

**New: Cleanup protection**
Exported IDs (19-prefix range) are filtered out of the server-side unavailable-face cleanup list before deletion runs; if the list becomes empty the cleanup is skipped entirely, so sideloaded faces survive app sync instead of being removed. Normal cleanup for other faces is untouched.

**New: Exported cache auto-cleanup**
Exported `resource.bin` files older than 5 minutes are auto-removed (descriptions/previews kept, app UI unaffected); if any push happened within the last 15 minutes the whole cleanup is skipped to never delete a file mid-transfer.

**New: Settings toggle**
One new toggle in the settings UI, 15 → 16 (default off): "Watchface auto-export (experimental)".

**Code**
All existing ad-removal features unchanged.

### 中文

**新增：试用表盘自动导出**
试用下载完成后，缓存的 `resource.bin` 自动按"12→19"规则换新 ID（等长），以表盘中文名写入 `Download/`（如 `蜘蛛侠超感大眼_190917425583.bin`），供第三方软件导入（已在小米手环 10 Pro + AstroBox 真机验证）；每次扫描经 Toast/通知告知结果，已导出的不再重复导出。

**新增：防删除保护**
导出 ID（19 号段）在删除前从服务端清理名单中摘除，名单空了整单跳过，第三方刷入的表盘同步不再被删；其他表盘的正常清理不受影响。

**新增：已导出缓存自动清理**
超 5 分钟的已导出 `resource.bin` 自动删除（描述/预览保留，App 显示不受影响）；15 分钟内有过推送则整单跳过，防删正在传的文件。

**新增：设置开关**
设置界面新增 1 个开关（默认关闭），15 → 16 个：「表盘自动导出（实验）」。

**其他**
原有去广告功能保持不变。

---

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