package io.github.hao1196561270.mifitnessadaway;

import android.content.SharedPreferences;

/**
 * 设置键定义（libxposed RemotePreferences，框架自动同步到目标进程）。
 * 默认值均为 true = 开启去广告。
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
    public static final String KEY_ENABLE_SPLASH = "enable_splash";
    public static final String KEY_ENABLE_ANTI_DETECT = "enable_anti_detect";
    public static final String KEY_DEBUG_LOG = "debug_log";
    public static final String KEY_HIDE_ICON = "hide_icon";

    private Prefs() {
    }

    public static boolean isEnabled(SharedPreferences p, String key) {
        return p.getBoolean(key, true);
    }

    /** hook 侧读取：总开关前提下逐项生效 */
    public static boolean enabled(SharedPreferences p, String key) {
        return isEnabled(p, KEY_ENABLE_ALL) && isEnabled(p, key);
    }
}
