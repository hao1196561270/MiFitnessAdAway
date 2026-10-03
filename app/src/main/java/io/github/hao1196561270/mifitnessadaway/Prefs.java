package io.github.hao1196561270.mifitnessadaway;

import android.content.SharedPreferences;

/**
 * 设置键定义（libxposed RemotePreferences，框架自动同步到目标进程）。
 * 默认全开=true（去广告）；表盘自动导出是实验开关，默认关=false。
 */
public final class Prefs {

    public static final String GROUP = "adaway_settings";

    public static final String KEY_ENABLE_ALL = "enable_all";
    public static final String KEY_ENABLE_MINE_VIP = "enable_mine_vip";
    public static final String KEY_ENABLE_MINE_DOCTOR = "enable_mine_doctor";
    public static final String KEY_ENABLE_SPORT_BANNER = "enable_sport_banner";
    public static final String KEY_ENABLE_SPORT_CARDS = "enable_sport_cards";
    public static final String KEY_ENABLE_HEALTH_CONSULT = "enable_health_consult";
    public static final String KEY_ENABLE_SLEEP_CARDS = "enable_sleep_cards";
    public static final String KEY_ENABLE_DEVICE_RED_DOT = "enable_device_red_dot";
    public static final String KEY_ENABLE_FACE_EXPORT = "enable_face_export";
    /**
     * 导出位置：相对公共存储的路径，允许多级与中文，如 "Download/表盘导出"、
     * "MiFitnessExport/2026/表盘"；默认 "Download"。写入走 MediaStore Files 集合
     * （免权限），非法字符与绝对路径在读取时剔除。
     */
    public static final String KEY_EXPORT_PATH = "export_path";
    /**
     * 「重导」信号：设置页换位置并选择重导时 +1；目标进程发现它变化就清掉
     * 已导出标记，让已有表盘重新落一份到新位置（标记存在目标 App 自己的
     * prefs 里，模块 App 写不到，只能靠这个信号跨进程通知）。
     */
    public static final String KEY_EXPORT_RESET_TOKEN = "export_reset_token";
    public static final String KEY_ENABLE_SPLASH = "enable_splash";
    public static final String KEY_ENABLE_APP_UPDATE = "enable_app_update";
    public static final String KEY_ENABLE_WEIGHT_PLAN = "enable_weight_plan";
    public static final String KEY_ENABLE_VIP_POPUP = "enable_vip_popup";
    public static final String KEY_ENABLE_DND_SYNC = "enable_dnd_sync";
    /**
     * 大陆地区添加 Health Connect 导出。独立于去广告总开关，默认开。
     * 海外地区原本就有入口，钩子看到已有条目就不再插入。
     */
    public static final String KEY_ENABLE_HEALTH_CONNECT = "enable_health_connect";
    public static final String KEY_ENABLE_ANTI_DETECT = "enable_anti_detect";
    public static final String KEY_DEBUG_LOG = "debug_log";
    public static final String KEY_HIDE_ICON = "hide_icon";

    private Prefs() {
    }

    public static boolean isEnabled(SharedPreferences p, String key) {
        return p.getBoolean(key, true);
    }

    /** 带默认值的读取（用于默认关闭的实验性开关） */
    public static boolean isEnabled(SharedPreferences p, String key, boolean def) {
        return p.getBoolean(key, def);
    }

    /** hook 侧读取：总开关前提下逐项生效 */
    public static boolean enabled(SharedPreferences p, String key) {
        return isEnabled(p, KEY_ENABLE_ALL) && isEnabled(p, key);
    }
}
