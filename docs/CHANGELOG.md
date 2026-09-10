# Changelog 更新日志

## v1.1.0 (versionCode 30)

### English

**Major: settings UI redesign**
The settings page is rebuilt from a flat 19-row list into grouped cards, so a switch is now found by the page it belongs to instead of by scrolling:
`Master` (own card, with the "enabled x/11" summary) · `Splash & popups` · `Mine` · `Sport` · `Device` · `Health detail` · `Watchface` · `Other`.
Groups collapse/expand by tapping their title and the state is remembered (stored in the module's own preferences). While the master switch is off, every switch that depends on it is dimmed and not tappable, so "I enabled a page switch but ads are still there" can no longer happen. No new dependency, no resource files — still plain platform views, APK size unchanged.

**Removed: three switches that did nothing**
"Home health promotion cards", "Device promotion cards" and "Announcement banner" were declared in settings but never read by any hook (banner clearing is gated by the master switch only, because the banner getter carries no page context). Switch count: 19 → 16.

**Note: VIP promo popup still unverified**
The popup fix shipped in 1.0.9 (`MembershipDialogManager.showMembershipExpiredFaceDialog` skipped, caller's dismiss callback preserved for the birthday-medal flow) is included here as-is. It is server-driven and cannot be reproduced on demand, so it remains unverified on device.

### 中文

**重要：设置界面重构**
设置页从一列 19 行平铺列表改成分组卡片，按「它属于哪个界面」找开关，而不是一路滑：
`总开关`（单独一张卡，带「已启用 x/11」摘要）· `开屏与弹窗` · `我的页` · `运动页` · `设备页` · `健康详情页` · `表盘` · `其他`。
点分组标题即可收起/展开，折叠状态会记住（存在模块自己的 prefs）。总开关关闭时，所有依赖它的开关变灰不可点——"我开了单页开关怎么还有广告"这类困惑不会再出现。不引入任何新依赖、不加资源文件，仍是原生 View 手写，APK 体积不变。

**移除：三个无效开关**
「首页健康界面推广卡片」「设备界面推广卡片」「公告 banner」三个开关只存在于设置页，hook 侧从未读取（banner 清空只受总开关控制，因为 banner getter 没有页面上下文，做不了逐页控制）。开关数 19 → 16。

**说明：会员推广弹窗仍未验证**
1.0.9 交付的弹窗拦截（跳过 `MembershipDialogManager.showMembershipExpiredFaceDialog`，并保留调用方 dismiss 回调以不影响生日勋章流程）在本版原样包含。该弹窗由服务端下发、无法按需复现，故仍未真机验证。

---

## v1.0.9 (versionCode 29)

### English

**New: VIP promo popup removal**
The full-screen membership marketing popup ("会员限时低价福利" / "腕上时尚" / "抢先购买", reported in issue #8) is now skipped via a new toggle (18 → 19, default on). The chain is `MainActivity.dealWithMedal()` → `MembershipHelper.showVipExpiredFaceDialog()` → `MembershipDialogManager.requestMembershipExpiredFaceDialog()` → `showMembershipExpiredFaceDialog()`; blocking the last step means the popup never appears. The caller's dismiss callback is still invoked, so the birthday-medal flow it continues is left intact. Only this automatic chain is blocked — the user-initiated purchase dialog (`showMembershipDialog`, opened by tapping "开通会员") is untouched.

**Note: not verified on device**
This popup is server-driven and only appears on schedule, so the fix could not be reproduced and verified locally. It ships as-is in 1.0.9: if the popup still shows after a few days, please reopen issue #8 with a screenshot.

### 中文

**新增：会员推广弹窗去除**
全屏会员营销弹窗（"会员限时低价福利"/"腕上时尚"/"抢先购买"，issue #8 报告）新增开关去除（18 → 19 个，默认开启）。链路为 `MainActivity.dealWithMedal()` → `MembershipHelper.showVipExpiredFaceDialog()` → `MembershipDialogManager.requestMembershipExpiredFaceDialog()` → `showMembershipExpiredFaceDialog()`，拦最后一步即弹窗不出现。同时仍回调调用方的 dismiss 回调，保证它继续拉取的生日勋章流程不受影响。只拦这条自动链路——用户主动点"开通会员"的购买弹窗（`showMembershipDialog`）不受影响。

**说明：未在本机验证**
该弹窗由服务端下发、按时机触发，无法在本机复现验证，1.0.9 按现状发布。若过几天仍会弹出，请在 issue #8 附截图重开。

---

## v1.0.8 (versionCode 28)

### English

**New: International app support**
`com.xiaomi.wearable` (overseas edition) is now in scope alongside `com.mi.health`: package gates, watchface cache path and resource lookup all resolve per-package. Hooks fail soft per entry point, so unsupported screens on either app are skipped gracefully (contributed by @cloudskytian, PR #5).

**Fixed: Sleep research leftovers**
Follow-up hardening of the RN title-scan: marker fix ("21 天" has a space), whole-card locating and chain gap-fill now handle cards nested deep in containers. Native hooks kept for older versions.

**Build: CI autofix**
`gradle build` goes green again (missing notification permission for the export notifier, PR #6) and GitHub Actions now builds on every push/PR automatically (PR #7).

### 中文

**新增：国际版支持**
`com.xiaomi.wearable`（海外版）与 `com.mi.health` 同入作用域：包名门控、表盘缓存路径、资源查询全部按包动态解析。hook 逐入口失败隔离，不支持的页面优雅跳过（@cloudskytian 贡献，PR #5）。

**修复：睡眠研究卡残留**
RN 标题扫描后续打磨：标记修正（"21 天"中间有空格）、整卡定位、链式填白，专治嵌在深层容器里的卡。原生钩保留给老版本。

**构建：CI 修好**
`gradle build` 重新变绿（导出通知缺权限，PR #6），GitHub Actions 此后每次推送/PR 自动构建（PR #7）。

---

## v1.0.7 (versionCode 27)

### English

**New: Weight plan card removal**
The "个性化减重方案" whole card on the Weight page is now removed via a new toggle (17 → 18, default on). The page is React Native (`YRNCFragment`) with server-driven copy, so removal is a title-text view-tree scan on the shared RN host — no stable data hook exists.

**Improved: Stress consult card**
The Stress page consult card ("健康问诊") is covered by the existing health-consult toggle: same data-layer binder was already blocked, plus its static fallback content is now removed by the same RN title-scan (page-gated by "了解压力", no new toggle).

**Fixed: Sleep research cards on 3.59.0**
The Sleep page moved to React Native in 3.59.0, silently disabling the v1.0.2 native hooks — research/improvement cards reappeared. They are now removed by the same RN title-scan under the existing sleep-cards toggle (native hooks kept for older versions).

### 中文

**新增：体重减重方案栏去除**
体重页"个性化减重方案"整卡新增开关去除（17 → 18 个，默认开启）。该页为 RN 页（`YRNCFragment`）、文案服务端下发，走共用 RN 宿主标题文本扫描，无稳定数据接口。

**改进：压力问诊卡**
压力页问诊卡（"健康问诊"）纳入原有健康问诊开关：数据层同 binder 早已拦截，静态兜底内容改走同套 RN 标题扫描（以"了解压力"限域，不新增开关）。

**修复：3.59.0 睡眠研究卡复活**
3.59.0 睡眠页迁入 RN，原生钩静默失效，研究/改善卡重现。现改走同套 RN 标题扫描（沿用睡眠卡片开关，原生钩保留给老版本）。

---

## v1.0.6 (versionCode 26)

### English

**New: App-update popup removal**
The "Update available" dialog (`AppUpgradeUtil.showUpdateDialogIfNeed`) is now skipped via a new toggle (16 → 17, default on). Background version check still runs, and manual update check on the Mine page is unaffected.

**Change: version-agnostic support**
The 3.58.0-only wording is dropped: every hook installs independently with isolated failure, so the module runs on `com.mi.health/com.xiaomi.wearable` 3.0+ and degrades gracefully where a version's entry points differ. No version checks in code, no version lock in scope.

**Cleanup: Dead-code audit**
Full audit (unused-method scan, imports, comments): no dead code — the tree was already clean after the v1.0.4 cleanup and v1.0.5 refactor.

### 中文

**新增：应用更新弹窗去除**
"发现新版本"弹窗（`AppUpgradeUtil.showUpdateDialogIfNeed`）新增开关去除（16 → 17 个，默认开启）。后台版本检查照常跑，"我的"页手动检查更新不受影响。

**变更：版本通杀**
去掉仅 3.58.0 的说法：每个 hook 独立安装、失败隔离，`com.mi.health/com.xiaomi.wearable` 3.0+ 均可运行，某版本入口有差异时优雅降级。代码无版本判断，作用域无版本锁定。

**清理：无用代码审计**
全量审计（无用方法扫描、import、注释）：无死代码——v1.0.4 清理 + v1.0.5 重构后本就干净。

---

## v1.0.5 (versionCode 25)

### English

**New: Export explanation dialog**
Turning on "Watchface auto-export (experimental)" now pops up a short guide: what auto-export does (re-ID'd `12→19` → `Download/`, third-party import, cleanup protection, exported-cache auto-cleanup, Toast/notification results) plus step-by-step usage. It shows only when the switch is manually flipped on — reopening settings never pops it again.

**Note**
The export switch stays off by default; all existing ad-removal and export features unchanged. v1.0.3 users should still upgrade (v1.0.3 keeps every trialed face in cache; fixed since v1.0.4).

### 中文

**新增：导出说明弹窗**
打开「表盘自动导出（实验）」时弹出说明：导出的作用（换新 ID 12→19 → Download/、第三方导入、防删除保护、缓存自动清理、Toast/通知反馈）+ 分步用法。只在手动打开时弹一次，重进设置页不再打扰。

**其他**
导出开关默认关闭；原有去广告与导出功能不变。1.0.3 用户建议升级（1.0.3 缓存堆积问题自 1.0.4 起已修复）。

---

## v1.0.4 (versionCode 24)

### English

**Highly recommended for v1.0.3 users: v1.0.3 keeps every previously trialed face in cache, so stale downloads pile up and eat storage; v1.0.4 wipes them.**

**Improved: Exported cache cleanup**
Exported face cache is now removed whole-directory on the next scan after export (previously: only `resource.bin` older than 5 minutes), so only the just-downloaded face stays cached. Snapshot-based matching plus 60-second handoff and 15-minute push guards prevent deleting files mid-download or mid-transfer.

**Cleanup: Dead code removal**
Removed unused parameter plumbing in the export chain and an unused import; no behavior change besides the cleanup upgrade above.

**Code**
All existing ad-removal and export features unchanged.

### 中文

**强烈推荐 1.0.3 用户更新：1.0.3 会把之前试用过的表盘一直留在缓存里，越堆越多占空间；1.0.4 会把它们清掉。**

**改进：已导出缓存清理**
已导出表盘缓存改为导出后下次扫描即整目录删除（之前：仅删超 5 分钟的 `resource.bin`），缓存里只留本次下载的。快照匹配 + 60 秒交接保护 + 15 分钟推送保护，不误删正在下载/传输的文件。

**清理：无用代码**
清理导出链路上无用的参数传递与无用 import；除上述清理升级外无行为变化。

**其他**
原有去广告与导出功能保持不变。

---

## v1.0.3 (versionCode 23)

### English

**Update notice: this version keeps every previously trialed face in cache, which piles up over time — please upgrade to v1.0.4, which wipes them.**

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

**更新提示：此版本会把之前试用过的表盘一直留在缓存里，越用堆得越多，请升级到 v1.0.4 清理。**

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