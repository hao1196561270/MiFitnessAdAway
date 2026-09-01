package io.github.hao1196561270.mifitnessadaway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定义设置页的开关项目及其显示分组。
 */
public final class SettingsItems {

    /** 保存单个设置项的标题、键名和分组。 */
    public static final class Entry {
        public final String title;
        public final String key;
        public final String group;

        public Entry(String title, String key, String group) {
            this.title = title;
            this.key = key;
            this.group = group;
        }
    }

    private SettingsItems() {
    }

    /** 按显示顺序返回全部设置项。 */
    public static Entry[] entries() {
        return new Entry[] {
                new Entry("启用去广告", Prefs.KEY_ENABLE_ALL, "总开关"),
                new Entry("设备红点（底部 tab / 系统设置入口）", Prefs.KEY_ENABLE_DEVICE_RED_DOT, "页面广告"),
                new Entry("我的界面 VIP 会员卡", Prefs.KEY_ENABLE_MINE_VIP, "页面广告"),
                new Entry("我的界面健康问诊卡", Prefs.KEY_ENABLE_MINE_DOCTOR, "页面广告"),
                new Entry("运动界面轮播卡片", Prefs.KEY_ENABLE_SPORT_BANNER, "页面广告"),
                new Entry("运动界面运营卡片（训练指标以下）", Prefs.KEY_ENABLE_SPORT_CARDS, "页面广告"),
                new Entry("健康问诊卡片（睡眠 / 心率 / 血氧）", Prefs.KEY_ENABLE_HEALTH_CONSULT, "页面广告"),
                new Entry("睡眠界面研究 / 改善卡片", Prefs.KEY_ENABLE_SLEEP_CARDS, "页面广告"),
                new Entry("开屏广告", Prefs.KEY_ENABLE_SPLASH, "页面广告"),
                new Entry("反 hook 检测", Prefs.KEY_ENABLE_ANTI_DETECT, "系统与调试"),
                new Entry("调试日志", Prefs.KEY_DEBUG_LOG, "系统与调试"),
                new Entry("隐藏桌面图标", Prefs.KEY_HIDE_ICON, "系统与调试")
        };
    }

    /** 按显示顺序整理设置项分组。 */
    public static Map<String, List<Entry>> groupedEntries() {
        Map<String, List<Entry>> groups = new LinkedHashMap<>();
        for (Entry entry : entries()) {
            groups.computeIfAbsent(entry.group, ignored -> new ArrayList<>()).add(entry);
        }
        return groups;
    }
}
