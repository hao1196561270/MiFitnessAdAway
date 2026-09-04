package io.github.hao1196561270.mifitnessadaway;

import android.content.SharedPreferences;

/**
 * 设置键定义（libxposed RemotePreferences，框架自动同步到目标进程）。
 * 默认全开=true（去广告）；表盘自动导出是实验开关，默认关=false。
 */
public final class Prefs {

    public static final String GROUP = "adaway_settings";

    public static final String KEY_ENABLE_ALL = "enable_all";
    public static final String KEY_ENABLE_HEALTH_BANNER = "enable_health_banner";
    public static final String KEY_ENABLE_DEVICE_BANNER = "enable_device_banner";
    public static final String KEY_ENABLE_MINE_VIP = "enable_mine_vip";
    public static final String KEY_ENABLE_MINE_DOCTOR = "enable_mine_doctor";
    public static final String KEY_ENABLE_SPORT_BANNER = "enable_sport_banner";
    public static final String KEY_ENABLE_SPORT_CARDS = "enable_sport_cards";
    public static final String KEY_ENABLE_HEALTH_CONSULT = "enable_health_consult";
    public static final String KEY_ENABLE_SLEEP_CARDS = "enable_sleep_cards";
    public static final String KEY_ENABLE_DEVICE_RED_DOT = "enable_device_red_dot";
    public static final String KEY_ENABLE_FACE_EXPORT = "enable_face_export";
    public static final String KEY_ENABLE_SPLASH = "enable_splash";
    public static final String KEY_ENABLE_ANNOUNCE = "enable_announce";
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