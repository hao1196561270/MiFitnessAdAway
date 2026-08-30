package io.github.hao1196561270.mifitnessadaway;

/**
 * 模块配置默认值（Q16：9 项开关，默认全开 = 去广告生效）。
 * 运行时实际值从 RemotePreferences（设置页）读取，见 Prefs / AdAwayModule。
 */
public final class Config {

    /** 目标应用包名 */
    public static final String TARGET_PACKAGE = "com.mi.health";

    /** 模块调试标签 */
    public static final String TAG = "MiFitnessAdAway";

    /** 总开关 */
    public static final boolean DEFAULT_ENABLE_ALL = true;

    /** 健康 tab：暑期焕新季 / 以旧换新 banner（banner 管线） */
    public static final boolean DEFAULT_ENABLE_HEALTH_BANNER = true;

    /** 设备 tab：REDMI Watch 6 推荐 banner（banner 管线） */
    public static final boolean DEFAULT_ENABLE_DEVICE_BANNER = true;

    /** 我的 tab：VIP 会员 / 立即开通 */
    public static final boolean DEFAULT_ENABLE_MINE_VIP = true;

    /** 我的 tab：健康问诊卡 */
    public static final boolean DEFAULT_ENABLE_MINE_DOCTOR = true;

    /** 运动 tab：轮播式推广卡片 */
    public static final boolean DEFAULT_ENABLE_SPORT_BANNER = true;

    /** 开屏广告 */
    public static final boolean DEFAULT_ENABLE_SPLASH = true;

    /** 公告 banner */
    public static final boolean DEFAULT_ENABLE_ANNOUNCE = true;

    /** 反 hook 检测拦截 */
    public static final boolean DEFAULT_ENABLE_ANTI_DETECT = true;

    /** 调试日志 */
    public static final boolean DEFAULT_DEBUG_LOG = true;
}