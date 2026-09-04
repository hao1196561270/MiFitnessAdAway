package io.github.hao1196561270.mifitnessadaway;

import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;
import java.util.Collections;

import io.github.libxposed.api.XposedModule;

/**
 * MiFitnessAdAway - libxposed API 102 入口。
 *
 * 生命周期（libxposed 现代 API）：
 * - onModuleLoaded: 模块加载进目标进程（每个进程一次）
 * - onPackageLoaded: 目标包加载（classloader 就绪前）
 * - onPackageReady: 目标包 classloader 就绪（在此安装 hooks 最安全）
 *
 * 开关（Q15-A / Q16）：设置页通过 libxposed RemotePreferences 写入，
 * 框架自动同步到本进程；hook 在此常驻注册，intercept 内动态读取开关，
 * 关闭时放行原逻辑 → 设置页改完立即生效，无需重启。
 */
public class AdAwayModule extends XposedModule {

    public static final String TAG = "MiFitnessAdAway";
    private static final String TARGET_PACKAGE = "com.mi.health";

    private SharedPreferences mPrefs;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        mPrefs = getRemotePreferences(Prefs.GROUP);
        log(Log.INFO, TAG, "module loaded, process=" + param.getProcessName() +
                ", api=" + getApiVersion() + ", framework=" + getFrameworkName());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            detach();
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        try {
            installHooks(param.getClassLoader());
            log(Log.INFO, TAG, "all hooks installed");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook install failed", t);
        }
    }

    private boolean debugLog() {
        return mPrefs != null && Prefs.isEnabled(mPrefs, Prefs.KEY_DEBUG_LOG);
    }

    private void installHooks(ClassLoader cl) throws Throwable {
        // banner 数据 getter（首页/设备/我的/运动/公告共用底座，受总开关控制）
        hookBannerListGetter(cl, "com.fitness.banner.export.compare.BannerResponseResultV1", "getBannerList");
        hookBannerListGetter(cl, "com.fitness.banner.export.compare.BannerResponseResultV2", "getBannerList");

        if (Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_SPLASH)) {
            hookSplashAdPreference(cl);
        }
        if (Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_MINE_VIP)) {
            hookMemberVip(cl);
        }
        if (Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_MINE_DOCTOR)) {
            hookMineDoctor(cl);
        }
        // RN「我的」页视图树扫描兜底（VIP 卡/问诊卡为 JS 渲染，数据层不可达时隐藏文本节点）
        if (Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_MINE_VIP) || Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_MINE_DOCTOR)) {
            hookMineFragmentV4ViewScan(cl);
        }
        if (Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_SPORT_BANNER)) {
            hookSportBanner(cl);
        }
        // 运动页「训练指标以下」运营卡片（运动团/活动推荐，RN 渲染）视图树扫描
        if (Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_SPORT_CARDS)) {
            hookSportTabViewScan(cl);
        }
        // 健康页问诊卡片（睡眠/心率/血氧三页）：数据层 = PingAnHealth bindOneBanner/bindTwoBanners
        if (Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_HEALTH_CONSULT)) {
            hookPingAnConsult(cl);
            // 睡/心率页顶部「蚂蚁阿福 AI 解读」卡无数据接口，视图层隐藏（Q6-B）
            hookAqViewHide(cl);
        }
        // 睡眠页底部研究/改善运营卡（Q1-A 开关，Q2-A 视图层精准法）
        if (Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_SLEEP_CARDS)) {
            hookSleepCards(cl);
        }
        // 设备页红点：主页「系统设置」入口 + 底部导航「设备」tab 红点（伪装忽略电池优化方案）
        if (Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_DEVICE_RED_DOT)) {
            hookDeviceRedDots(cl);
        }
        // 表盘自动导出：推送前把 resource.bin 换新 ID 写一份到 Download/（实验开关门控）
        if (Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_FACE_EXPORT)) {
            hookFaceExport(cl);
        }
        if (Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_ANTI_DETECT)) {
            hookSensorHelper(cl);
        }
    }

    // ===================== banner 数据 getter（总开关） =====================

    /**
     * 让 BannerResponseResult.getBannerList() 在总开关开启时返回空列表。
     * 注意：getter 无页面上下文，故受总开关 ESCAPE，不由单项开关控制；
     * 单项页面开关在下层各自 hook 处生效。
     */
    private void hookBannerListGetter(ClassLoader cl, String clsName, String methodName) throws Throwable {
        try {
            Class<?> clazz = Class.forName(clsName, true, cl);
            Method m = clazz.getDeclaredMethod(methodName);
            m.setAccessible(true);
            hook(m).intercept(chain -> {
                if (!Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_ALL)) {
                    return chain.proceed();
                }
                if (debugLog()) {
                    log(Log.INFO, TAG, "banner list cleared: " + clsName);
                }
                return Collections.emptyList();
            });
            log(Log.INFO, TAG, "hooked: " + clsName + "." + methodName);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: " + clsName + "." + methodName, t);
        }
    }

    // ===================== 开屏广告缓存（本地） =====================

    /**
     * 开屏广告数据存于本地 SharedPreferences（SplashAdPreference.SPLASH_AD_LIST JSON）。
     * SplashActivity / SplashAdActivity 通过 getShowSplashAdItem() 读取缓存并展示，
     * 网络 banner 清空不影响本地缓存，因此必须让该读取方法返回 null。
     * （SplashAdActivity.delChangeFiles 对 null 有判空，安全）
     */
    private void hookSplashAdPreference(ClassLoader cl) throws Throwable {
        try {
            Class<?> clazz = Class.forName("com.xiaomi.fitness.login.ad.SplashAdPreference", true, cl);
            Method m = clazz.getDeclaredMethod("getShowSplashAdItem");
            m.setAccessible(true);
            hook(m).intercept(chain -> {
                if (!Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_SPLASH)) {
                    return chain.proceed();
                }
                if (debugLog()) {
                    log(Log.INFO, TAG, "splash ad emptied");
                }
                return null;
            });
            log(Log.INFO, TAG, "hooked: SplashAdPreference.getShowSplashAdItem");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: SplashAdPreference.getShowSplashAdItem", t);
        }
    }

    // ===================== 我的 tab：VIP 会员卡 =====================

    /**
     * VIP 会员卡双保险：
     * 1) 数据层：MembershipHelperImpl.getPaymentsPromotion/getOperationConfig/getVipInfo
     *    在开启时返回 null（注意 MembershipHelper 是接口，必须 hook 实现类）。
     * 2) 源头开关：IWebSyncKt.getSHOW_AIDONG_PAYMENT_ENTRANCE → false，
     *    MineV4ViewModel.freshItemList() 不 addVipItem()，RN 不渲染会员卡。
     * 3) UI 兜底：MineVipView.onAttachedToWindow 后 GONE。
     */
    private void hookMemberVip(ClassLoader cl) throws Throwable {
        String helper = "com.xiaomi.fitness.membership.impl.MembershipHelperImpl";
        Class<?> cont = Class.forName("kotlin.coroutines.Continuation", true, cl);

        hookSuspendReturnNull(cl, helper, "getPaymentsPromotion",
                Prefs.KEY_ENABLE_MINE_VIP, cont);
        hookSuspendReturnNull(cl, helper, "getOperationConfig",
                Prefs.KEY_ENABLE_MINE_VIP, cont);
        hookSuspendReturnNull(cl, helper, "getVipInfo",
                Prefs.KEY_ENABLE_MINE_VIP, boolean.class, cont);

        hookAidongVipEntrance(cl);
        hookMineVipGone(cl);
    }

    /**
     * 「我的」页支付宝碰一碰会员广告卡（已还原，不再 hook）：
     * 该卡由 RN 渲染，Java 侧 hook 无法根治，且此前的处理引入了布局问题。
     */

    /**
     * 「我的」tab VIP 会员卡（RN 渲染）的源头开关。
     * MineV4ViewModel.freshItemList() 中：
     *   if (whetherToShowAidongPaymentEntrance()) addVipItem();
     * whetherToShowAidongPaymentEntrance() 依赖
     * IWebSyncKt.getSHOW_AIDONG_PAYMENT_ENTRANCE()（静态字段）。
     */
    private void hookAidongVipEntrance(ClassLoader cl) throws Throwable {
        try {
            Class<?> clazz = Class.forName("com.xiaomi.fitness.mine.export.IWebSyncKt", true, cl);
            Method m = clazz.getDeclaredMethod("getSHOW_AIDONG_PAYMENT_ENTRANCE");
            m.setAccessible(true);
            hook(m).intercept(chain -> {
                if (!Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_MINE_VIP)) {
                    return chain.proceed();
                }
                if (debugLog()) {
                    log(Log.INFO, TAG, "aidong vip entrance disabled");
                }
                return false;
            });
            log(Log.INFO, TAG, "hooked: IWebSyncKt.getSHOW_AIDONG_PAYMENT_ENTRANCE");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: IWebSyncKt.getSHOW_AIDONG_PAYMENT_ENTRANCE", t);
        }

        // 防 JS 把开关写回 true
        try {
            Class<?> clazz = Class.forName("com.xiaomi.fitness.mine.export.IWebSyncKt", true, cl);
            Method m = clazz.getDeclaredMethod("setSHOW_AIDONG_PAYMENT_ENTRANCE", boolean.class);
            m.setAccessible(true);
            hook(m).intercept(chain -> null);
            log(Log.INFO, TAG, "hooked: IWebSyncKt.setSHOW_AIDONG_PAYMENT_ENTRANCE");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: IWebSyncKt.setSHOW_AIDONG_PAYMENT_ENTRANCE", t);
        }

        // RN 头部会员卡：阻止 ViewModel LiveData 被填充（m519getVipInfo / m518getVipConfig）
        try {
            Class<?> clazz = Class.forName("com.xiaomi.fitness.mine.v4.MineV4ViewModel", true, cl);
            for (String name : new String[]{"m519getVipInfo", "m518getVipConfig"}) {
                Method m = clazz.getDeclaredMethod(name);
                m.setAccessible(true);
                hook(m).intercept(chain -> {
                    if (!Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_MINE_VIP)) {
                        return chain.proceed();
                    }
                    if (debugLog()) {
                        log(Log.INFO, TAG, "vip liveData fill blocked: " + name);
                    }
                    return null;
                });
                log(Log.INFO, TAG, "hooked: MineV4ViewModel." + name);
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: MineV4ViewModel vip fill", t);
        }

        // RN 头部会员卡：getVipConfig()/getVipInfo() getter 返回空 LiveData，
        // 切断 JS 观察到的数据（含缓存），使 JS 拿到 null 无 VIP 内容可渲染
        try {
            Class<?> clazz = Class.forName("com.xiaomi.fitness.mine.v4.MineV4ViewModel", true, cl);
            String[] getters = {"getVipConfig", "getVipInfo"};
            for (String name : getters) {
                Method m = clazz.getDeclaredMethod(name);
                m.setAccessible(true);
                hook(m).intercept(chain -> {
                    if (!Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_MINE_VIP)) {
                        return chain.proceed();
                    }
                    if (debugLog()) {
                        log(Log.INFO, TAG, "vip liveData getter emptied: " + name);
                    }
                    return new androidx.lifecycle.MutableLiveData<>();
                });
                log(Log.INFO, TAG, "hooked: MineV4ViewModel." + name + " (empty LiveData)");
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: MineV4ViewModel vip getters", t);
        }
    }

    private void hookSuspendReturnNull(ClassLoader cl, String clsName, String methodName,
                                       String prefsKey, Class<?>... paramTypes) throws Throwable {
        try {
            Class<?> clazz = Class.forName(clsName, true, cl);
            Method m = clazz.getDeclaredMethod(methodName, paramTypes);
            m.setAccessible(true);
            hook(m).intercept(chain -> {
                if (!Prefs.enabled(mPrefs, prefsKey)) {
                    return chain.proceed();
                }
                if (debugLog()) {
                    log(Log.INFO, TAG, "emptied: " + clsName + "." + methodName);
                }
                return null;
            });
            log(Log.INFO, TAG, "hooked: " + clsName + "." + methodName);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: " + clsName + "." + methodName, t);
        }
    }

    /**
     * UI 兜底：MineVipView.onAttachedToWindow 后 GONE。
     * （还原为原始单保险；此前尝试构造器 GONE + setVip* 拦截等多重防线，
     *  因 RN 渲染链路在 Java 侧不可根治且引入了布局问题，已全部移除。）
     */
    private void hookMineVipGone(ClassLoader cl) throws Throwable {
        try {
            Class<?> clazz = Class.forName("com.xiaomi.fitness.mine.vip.MineVipView", true, cl);
            Method m = clazz.getDeclaredMethod("onAttachedToWindow");
            m.setAccessible(true);
            hook(m).intercept(chain -> {
                chain.proceed();
                if (!Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_MINE_VIP)) {
                    return null;
                }
                try {
                    ((View) chain.getThisObject()).setVisibility(View.GONE);
                    if (debugLog()) {
                        log(Log.INFO, TAG, "MineVipView hidden");
                    }
                } catch (Throwable t) {
                    log(Log.ERROR, TAG, "MineVipView gone failed", t);
                }
                return null;
            });
            log(Log.INFO, TAG, "hooked: MineVipView.onAttachedToWindow");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: MineVipView.onAttachedToWindow", t);
        }
    }

    // ===================== 我的 tab：健康问诊卡 =====================

    /**
     * 健康问诊数据链：MineFragmentV4.onHiddenChanged → MineV4ViewModel.getDoctorDataResult()
     * → DoctorDataRepository.getDoctorDataResult() → DoctorDataRequest.getDoctorDataResult()（suspend）
     * → DoctorService 网络请求 → doctorDataResultLiveData。
     * hook DoctorDataRequest 具体实现类（非抽象），返回 null 则上层 LiveData 收不到数据，
     * RN 问诊卡无数据可渲染。
     */
    private void hookMineDoctor(ClassLoader cl) throws Throwable {
        Class<?> cont = Class.forName("kotlin.coroutines.Continuation", true, cl);
        hookSuspendReturnNull(cl, "com.xiaomi.fitness.mine.doctor.DoctorDataRequest",
                "getDoctorDataResult", Prefs.KEY_ENABLE_MINE_DOCTOR, cont);

        // 双保险：MineV4ViewModel.getDoctorDataResult() no-op（不再 launch 协程）
        try {
            Class<?> clazz = Class.forName("com.xiaomi.fitness.mine.v4.MineV4ViewModel", true, cl);
            Method m = clazz.getDeclaredMethod("getDoctorDataResult");
            m.setAccessible(true);
            hook(m).intercept(chain -> {
                if (!Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_MINE_DOCTOR)) {
                    return chain.proceed();
                }
                return null;
            });
            log(Log.INFO, TAG, "hooked: MineV4ViewModel.getDoctorDataResult");

            // 三保险：getDoctorDataResultLiveData() getter 返回空 LiveData（切断 JS 观察源）
            Method m2 = clazz.getDeclaredMethod("getDoctorDataResultLiveData");
            m2.setAccessible(true);
            hook(m2).intercept(chain -> {
                if (!Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_MINE_DOCTOR)) {
                    return chain.proceed();
                }
                if (debugLog()) {
                    log(Log.INFO, TAG, "doctor liveData getter emptied");
                }
                return new androidx.lifecycle.MutableLiveData<>();
            });
            log(Log.INFO, TAG, "hooked: MineV4ViewModel.getDoctorDataResultLiveData");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: MineV4ViewModel doctor", t);
        }
    }

    // ===================== RN「我的」页视图树扫描兜底 =====================

    /**
     * 「我的」页是 RN（YRN）渲染，VIP 卡/问诊卡由 JS 模板绘制，Java 数据层 hook
     * 无法阻止模板本身。此方案 hook MineFragmentV4.onViewCreated（RN 页挂载），
     * 延迟扫描其视图树：找到命中广告文案的 TextView，隐藏其整行容器。
     *
     * 命中关键词按开关拆分：VIP 卡文案（开-即/会员/问诊/表盘 等命中任一即隐藏所在行）。
     */
    private void hookMineFragmentV4ViewScan(ClassLoader cl) throws Throwable {
        try {
            Class<?> clazz = Class.forName("com.xiaomi.fitness.mine.v4.MineFragmentV4", true, cl);
            Method m = clazz.getDeclaredMethod("onViewCreated", android.view.View.class, android.os.Bundle.class);
            m.setAccessible(true);
            hook(m).intercept(chain -> {
                Object result = chain.proceed();
                // RN 视图树异步构建，延迟多次扫描
                final android.view.View root = (android.view.View) chain.getArg(0);
                if (root != null) {
                    scheduleViewScan(root, 0);
                }
                // 表盘导出备份触发：开"我的"页即扫一次缓存（schedule 内自带开关门控）
                scheduleExportScan();
                return result;
            });
            log(Log.INFO, TAG, "hooked: MineFragmentV4.onViewCreated (view scan)");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: MineFragmentV4.onViewCreated", t);
        }
    }

    /**
     * 运动页（SportTabV4Fragment，YRN RN 页面）「训练指标」以下运营卡片：
     * 运动团 / 活动推荐（线上赛/奖牌赛等）由 JS 渲染，复用逐层上卷扫描。
     */
    private void hookSportTabViewScan(ClassLoader cl) throws Throwable {
        try {
            Class<?> clazz = Class.forName("com.xiaomi.fitness.sport.sporttab.SportTabV4Fragment", true, cl);
            Method m = clazz.getDeclaredMethod("onViewCreated", android.view.View.class, android.os.Bundle.class);
            m.setAccessible(true);
            hook(m).intercept(chain -> {
                Object result = chain.proceed();
                final android.view.View root = (android.view.View) chain.getArg(0);
                if (root != null) {
                    scheduleSportViewScan(root, 0);
                }
                return result;
            });
            log(Log.INFO, TAG, "hooked: SportTabV4Fragment.onViewCreated (view scan)");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: SportTabV4Fragment.onViewCreated", t);
        }
    }

    private void scheduleSportViewScan(final android.view.View root, final int round) {
        if (round > 8) {
            return;
        }
        mSportScrollRoot = root;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    scanAndHideSport(root);
                    scheduleSportViewScan(root, round + 1);
                } catch (Throwable t) {
                    log(Log.ERROR, TAG, "sport view scan error", t);
                }
            }
        }, 800);
    }

    private void scheduleViewScan(final android.view.View root, final int round) {
        if (round > 8) {
            return;
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    scanAndHide(root);
                    scheduleViewScan(root, round + 1);
                } catch (Throwable t) {
                    log(Log.ERROR, TAG, "view scan error", t);
                }
            }
        }, 800);
    }

    /** 已处理的卡片容器（identityHashCode 去重，防止重复上移） */
    private final java.util.Set<String> handledCards = new java.util.HashSet<>();

    /** aqContainer 资源 id（首次扫描时按包名解析，避免异常） */
    private int mAqContainerResId;

    private void scanAndHide(android.view.View v) {
        scanAndHideInternal(v);
    }

    private void scanAndHideSport(android.view.View v) {
        scanAndHideSportInternal(v);
    }

    /**
     * 运动页锚点策略：找到「训练指标」文本，其所在行之后的所有兄弟（运动团/活动推荐
     * 运营区）整体 GONE，并清空兄弟占位；训练指标以上的正常内容（运动记录/体能状态）保留。
     */
    private void scanAndHideSportInternal(android.view.View v) {
        if (v == null) {
            return;
        }
        if (v instanceof android.widget.TextView) {
            String text = ((android.widget.TextView) v).getText().toString();
            if (text != null && text.contains("训练指标")) {
                hideBelowAnchor(v);
            }
            return;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                scanAndHideSportInternal(vg.getChildAt(i));
            }
        }
    }

    /** 找到锚点行容器，GONE 其所在内容列中位于其后的所有兄弟 */
    private void hideBelowAnchor(android.view.View anchorText) {
        try {
            // 找锚点所在行容器（高度 ≤ 220 的最近父）
            android.view.View row = anchorText;
            android.view.ViewParent pp = anchorText.getParent();
            if (pp instanceof android.view.ViewGroup) {
                android.view.ViewGroup pvg = (android.view.ViewGroup) pp;
                if (pvg.getHeight() <= 220) {
                    row = pvg;
                }
            }
            // 逐层上溯，记录路径；内容列 = 第一个高度 >= 1200 的容器
            android.view.ViewGroup contentCol = null;
            android.view.View childInCol = row;
            android.view.ViewParent p = row.getParent();
            while (p instanceof android.view.ViewGroup) {
                android.view.ViewGroup pv = (android.view.ViewGroup) p;
                if (pv.getHeight() >= 1200) {
                    contentCol = pv;
                    break;
                }
                childInCol = pv; // 记录当前层子节点（相对上一层的）
                p = pv.getParent();
            }
            if (contentCol == null) {
                // 找不到内容列，退化：隐藏行并上移
                String key = Integer.toHexString(System.identityHashCode(row));
                if (!handledCards.contains(key)) {
                    handledCards.add(key);
                    row.setVisibility(android.view.View.GONE);
                    shiftSiblingsAfter(row);
                }
                return;
            }
            // 若锚点行就是内容列的直接子，用之；否则用最近的非内容列祖先
            android.view.View target = row;
            android.view.ViewParent tp = row.getParent();
            while (tp != contentCol && tp instanceof android.view.ViewGroup) {
                target = (android.view.View) tp;
                tp = tp.getParent();
            }
            // 从 target（含）之后的所有兄弟全部 GONE
            boolean after = false;
            int gone = 0;
            for (int i = 0; i < contentCol.getChildCount(); i++) {
                android.view.View child = contentCol.getChildAt(i);
                if (child == target) {
                    after = true;
                    continue;
                }
                if (after && child.getVisibility() != android.view.View.GONE) {
                    child.setVisibility(android.view.View.GONE);
                    gone++;
                }
            }
            log(Log.INFO, TAG, "sport anchor: hidden below 训练指标, removed=" + gone);

            // 内容移除后禁用页面滚动（RN 布局高度未重算，避免下滑到空白区）
            disableSportScroll(mSportScrollRoot);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hideBelowAnchor error", t);
        }
    }

    /** 运动页根视图（scan 时缓存，用于找 ReactScrollView） */
    private android.view.View mSportScrollRoot;

    /** 遍历视图树找 ReactScrollView 并禁用滚动 */
    private void disableSportScroll(android.view.View root) {
        try {
            if (root == null) {
                return;
            }
            if (root.getClass().getName().contains("ReactScrollView")
                    || root.getClass().getName().contains("ReactHorizontalScrollView")) {
                try {
                    java.lang.reflect.Method m = root.getClass().getMethod("setScrollEnabled", boolean.class);
                    m.invoke(root, false);
                    log(Log.INFO, TAG, "scroll disabled: " + root.getClass().getSimpleName());
                } catch (Throwable t) {
                    log(Log.ERROR, TAG, "setScrollEnabled fail", t);
                }
                return;
            }
            if (root instanceof android.view.ViewGroup) {
                android.view.ViewGroup vg = (android.view.ViewGroup) root;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    disableSportScroll(vg.getChildAt(i));
                }
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "disableSportScroll error", t);
        }
    }

    private void scanAndHideInternal(android.view.View v) {
        if (v == null) {
            return;
        }
        if (v instanceof android.widget.TextView) {
            String text = ((android.widget.TextView) v).getText().toString();
            if (text != null && text.length() > 0 && isAdText(text)) {
                hideCardContaining(v, text);
            }
            return; // TextView 无子视图
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                scanAndHideInternal(vg.getChildAt(i));
            }
        }
    }

    /**
     * 逐层上卷隐藏广告卡片：
     * 1. 从命中文案的 TextView 出发，先 GONE 其行容器；
     * 2. 若行容器的父容器不含「正常文案」（广告文本+图标等中性节点不算），则 GONE 父容器，
     *    继续上卷——这样问诊卡的外层框体也会被整体移除；
     * 3. 每 GONE 一层，把该层之后的所有兄弟节点按序号上移填补空白。
     * 同一层用 identityHashCode 去重。
     */
    private void hideCardContaining(android.view.View textView, String text) {
        try {
            // 起始节点：文案所属行容器（高度 ≤ 220 的最近父）
            android.view.View row = textView;
            android.view.ViewParent pp = textView.getParent();
            if (pp instanceof android.view.ViewGroup) {
                android.view.ViewGroup pvg = (android.view.ViewGroup) pp;
                if (pvg.getHeight() <= 220) {
                    row = pvg;
                }
            }
            collapseUp(row, text);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hideCardContaining error", t);
        }
    }

    /** 逐层上卷：GONE 当前节点 → 上移兄弟 → 若父容器无正常文案则继续上卷 */
    private void collapseUp(android.view.View node, String text) {
        String key = Integer.toHexString(System.identityHashCode(node));
        if (handledCards.contains(key)) {
            return;
        }
        if (node.getVisibility() == android.view.View.GONE) {
            handledCards.add(key);
            return;
        }
        handledCards.add(key);
        node.setVisibility(android.view.View.GONE);
        log(Log.INFO, TAG, "hidden: " + text.substring(0, Math.min(20, text.length()))
                + " layer=" + node.getClass().getSimpleName() + " (h=" + node.getHeight() + ")");
        shiftSiblingsAfter(node);

        android.view.ViewParent p = node.getParent();
        if (p instanceof android.view.ViewGroup) {
            android.view.ViewGroup parent = (android.view.ViewGroup) p;
            // 父容器已无正常文案 → 继续上卷（整卡移除）
            if (!containsNormalText(parent)) {
                collapseUp(parent, text);
            }
        }
    }

    /** 该视图（含后代）是否包含「正常文案」（非广告的文本） */
    private boolean containsNormalText(android.view.View v) {
        if (v.getVisibility() == android.view.View.GONE) {
            return false; // 已隐藏的不算
        }
        if (v instanceof android.widget.TextView) {
            String t = ((android.widget.TextView) v).getText().toString();
            if (t == null || t.length() == 0) {
                return false;
            }
            return !isAdText(t);
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (containsNormalText(vg.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 把同父容器中位于 goneView 之后的兄弟上移（按序号，不依赖 RN 布局坐标） */
    private void shiftSiblingsAfter(android.view.View goneView) {
        try {
            android.view.ViewParent p = goneView.getParent();
            if (!(p instanceof android.view.ViewGroup)) {
                return;
            }
            android.view.ViewGroup vg = (android.view.ViewGroup) p;
            int h = goneView.getHeight();
            boolean after = false;
            for (int i = 0; i < vg.getChildCount(); i++) {
                android.view.View child = vg.getChildAt(i);
                if (child == goneView) {
                    after = true;
                    continue;
                }
                if (after && child.getVisibility() != android.view.View.GONE) {
                    child.setTranslationY(child.getTranslationY() - h);
                    if (debugLog()) {
                        log(Log.INFO, TAG, "shifted up by " + h + ": " + child.getClass().getSimpleName());
                    }
                }
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "shiftSiblingsAfter error", t);
        }
    }

    /** 广告文案关键词（VIP 卡 + 问诊卡） */
    private boolean isAdText(String text) {
        if (Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_MINE_VIP)) {
            if (text.contains("立即开通") || text.contains("小米运动健康会员")
                    || text.contains("全量表盘") || text.contains("专业健康管家")
                    || text.contains("总有一款让您满心欢喜")) {
                return true;
            }
        }
        if (Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_MINE_DOCTOR)) {
            if (text.contains("健康问诊") || text.contains("公立医生在线问诊")
                    || text.contains("开通会员立享公立医生")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 运动轮播数据链：SportTabModel.loadBannerList → IBanner.getBannerListAsyncV1（回调式）
     * → BannerImpl 内部 p1.getBannerList()（BannerResponseResultV1 已被总开关 hook 清空）。
     * 因此运动轮播已被 BannerHook 覆盖；此处按运动开关单独 hook 回调入口，
     * 使关闭运动开关时仅运动页恢复显示（其余 banner 页仍清空）。
     */
    private void hookSportBanner(ClassLoader cl) throws Throwable {
        try {
            Class<?> clazz = Class.forName("com.fitness.banner.export.BannerImpl", true, cl);
            Class<?> fn = Class.forName("kotlin.jvm.functions.Function1", true, cl);

            // 旧版运动页：getBannerListAsyncV1(BannerRequestParam, onSuccess, onFail)
            hookAsyncV1(clazz, fn);
            // V4 运动页（SportTabV4 fragment）：getBannerListAsyncV2(BannerRequestParamV2, onSuccess, onFail, netScope)
            hookAsyncV2(clazz, fn);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: BannerImpl sport banner", t);
        }
    }

    private void hookAsyncV1(Class<?> clazz, Class<?> fn) throws Throwable {
        try {
            Class<?> reqV1 = Class.forName("com.fitness.banner.export.BannerRequestParam", true, clazz.getClassLoader());
            Method m = clazz.getDeclaredMethod("getBannerListAsyncV1", reqV1, fn, fn);
            m.setAccessible(true);
            hook(m).intercept(chain -> {
                if (!Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_SPORT_BANNER)) {
                    return chain.proceed();
                }
                Object onSuccess = chain.getArg(1);
                if (onSuccess instanceof kotlin.jvm.functions.Function1) {
                    ((kotlin.jvm.functions.Function1) onSuccess).invoke(Collections.emptyList());
                }
                return null;
            });
            log(Log.INFO, TAG, "hooked: BannerImpl.getBannerListAsyncV1");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: BannerImpl.getBannerListAsyncV1", t);
        }
    }

    private void hookAsyncV2(Class<?> clazz, Class<?> fn) throws Throwable {
        try {
            Class<?> reqV2 = Class.forName("com.fitness.banner.export.BannerRequestParamV2", true, clazz.getClassLoader());
            Class<?> scope = Class.forName("kotlinx.coroutines.CoroutineScope", true, clazz.getClassLoader());
            Method m = clazz.getDeclaredMethod("getBannerListAsyncV2", reqV2, fn, fn, scope);
            m.setAccessible(true);
            hook(m).intercept(chain -> {
                if (!Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_SPORT_BANNER)) {
                    return chain.proceed();
                }
                Object onSuccess = chain.getArg(1);
                if (onSuccess instanceof kotlin.jvm.functions.Function1) {
                    ((kotlin.jvm.functions.Function1) onSuccess).invoke(Collections.emptyList());
                }
                return null;
            });
            log(Log.INFO, TAG, "hooked: BannerImpl.getBannerListAsyncV2");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: BannerImpl.getBannerListAsyncV2", t);
        }
    }

    // ===================== 健康页问诊卡片（Q6-B / Q7-A） =====================

    /**
     * 数据层：让 PingAnHealthExtKt.bindOneBanner / bindTwoBanners 直接返回（不渲染卡片）。
     * 目标 app 对海外/Play 渠道用户本来就不显示这张卡（isPlayChannel 分支 gone），
     * 此处模拟该官方逻辑：hook 返回 null 即跳过原方法 → 卡片不上屏、不占位。
     * 睡眠（dept 7）、心率（dept 0）、血氧（dept 3）均经 bindOneBanner$default → bindOneBanner。
     */
    private void hookPingAnConsult(ClassLoader cl) throws Throwable {
        String clsName = "com.xiaomi.fitness.util.PingAnHealthExtKt";
        try {
            Class<?> clazz = Class.forName(clsName, true, cl);
            Class<?> cardView = Class.forName("com.xiaomi.fitness.view.HealthBannerCardSetView", true, cl);
            for (String name : new String[]{"bindOneBanner", "bindTwoBanners"}) {
                try {
                    Method m = clazz.getDeclaredMethod(name, cardView, int.class, String.class);
                    m.setAccessible(true);
                    hook(m).intercept(chain -> {
                        if (!Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_ALL)
                                || !Prefs.isEnabled(mPrefs, Prefs.KEY_ENABLE_HEALTH_CONSULT)) {
                            return chain.proceed();
                        }
                        if (debugLog()) {
                            log(Log.INFO, TAG, "consult card blocked: PingAnHealthExtKt." + name);
                        }
                        return null; // 跳过原方法 → 卡片不渲染
                    });
                    log(Log.INFO, TAG, "hooked: PingAnHealthExtKt." + name);
                } catch (Throwable t) {
                    log(Log.ERROR, TAG, "hook failed: PingAnHealthExtKt." + name, t);
                }
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: " + clsName, t);
        }
    }

    /**
     * 视图层（兜底）：睡眠/心率页顶部「蚂蚁阿福 AI 解读」卡（AqView，血氧页无此卡）。
     * AqView 无独立数据接口（内部 queryLastChat 拉聊天数据），Q6-B 决定整卡隐藏：
     * - attach 时同步扫描（视图已存在但尚未 measure/draw → 第一帧即不可见，零闪现）；
     * - 保留 800ms 轮询兜底（防异步重建/后续 visible）。
     */
    private void hookAqViewHide(ClassLoader cl) throws Throwable {
        String[] hosts = new String[]{
                "com.xiaomi.fitness.health.sleep.ui.SleepDayItemFragment",
                "com.xiaomi.fitness.health.hrm.HrmDayItemFragment"};
        android.view.View[] roots = new android.view.View[hosts.length];
        for (int i = 0; i < hosts.length; i++) {
            final int idx = i;
            try {
                Class<?> clazz = Class.forName(hosts[i], true, cl);
                Method m = clazz.getDeclaredMethod("onViewCreated", android.view.View.class, android.os.Bundle.class);
                m.setAccessible(true);
                hook(m).intercept(chain -> {
                    Object result = chain.proceed();
                    android.view.View root = (android.view.View) chain.getArg(0);
                    if (root != null) {
                        roots[idx] = root;
                        // 首帧前隐藏：attach 回调发生在 measure/layout/draw 之前
                        root.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
                            @Override
                            public void onViewAttachedToWindow(android.view.View v) {
                                try {
                                    hideAqCard(v);
                                    // 睡眠页底部研究/改善卡一并首帧隐藏
                                    hideSleepCards(v);
                                } catch (Throwable t) {
                                    log(Log.ERROR, TAG, "AqView attach-hide error", t);
                                }
                            }

                            @Override
                            public void onViewDetachedFromWindow(android.view.View v) {
                            }
                        });
                        // 异步兜底
                        scheduleAqScan(roots[idx], 0);
                    }
                    return result;
                });
                log(Log.INFO, TAG, "hooked: " + hosts[i] + ".onViewCreated (AqView hide)");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "hook failed: " + hosts[i] + ".onViewCreated", t);
            }
        }
    }

    private void scheduleAqScan(final android.view.View root, final int round) {
        if (round > 8) {
            return;
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    hideAqCard(root);
                    scheduleAqScan(root, round + 1);
                } catch (Throwable t) {
                    log(Log.ERROR, TAG, "AqView scan error", t);
                }
            }
        }, 800);
    }

    /** 遍历视图树，找 aqContainer（蚂蚁阿福 AI 解读卡容器）并 GONE。
     * 注意：睡眠/心率页的 aqContainer 位于 LinearLayout 中，GONE 后父容器自动重排，
     * 不能再调 shiftSiblingsAfter（会造成双重上移、内容顶进上方评分卡区域）。 */
    private void hideAqCard(android.view.View v) {
        if (v == null) {
            return;
        }
        if (v.getResources() != null && mAqContainerResId == 0) {
            mAqContainerResId = v.getResources().getIdentifier("aqContainer", "id", "com.mi.health");
        }
        if (mAqContainerResId != 0 && v.getId() == mAqContainerResId) {
            if (v.getVisibility() != android.view.View.GONE) {
                String key = Integer.toHexString(System.identityHashCode(v));
                if (!handledCards.contains(key)) {
                    handledCards.add(key);
                    v.setVisibility(android.view.View.GONE);
                    log(Log.INFO, TAG, "AqView card hidden: aqContainer");
                }
            }
            return; // 容器内无需继续扫描
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                hideAqCard(vg.getChildAt(i));
            }
        }
    }

    // ===================== 睡眠页研究/改善运营卡（Q1-A / Q2-A） =====================

    /** healthSleepResearchLayoutSet / sleepInterfereLayout 资源 id（延时解析） */
    private int mSleepResearchResId;
    private int mSleepInterfereResId;

    /**
     * 睡眠页底部三张运营卡（睡眠呼吸暂停研究 / 睡眠健康研究 → healthSleepResearchLayoutSet，
     * 睡眠改善计划 → sleepInterfereLayout）均位于 LinearLayout 内，GONE 后父容器自动重排。
     * 1) hook setSleepHealthBanner：集合初始化后延时扫描隐藏两个容器；
     * 2) hook updateSleepInterfereVisibility：阻止改善计划卡被重新显示。
     */
    private void hookSleepCards(ClassLoader cl) throws Throwable {
        String clsName = "com.xiaomi.fitness.health.sleep.ui.SleepHealthBannerCardSetLayout";
        try {
            Class<?> clazz = Class.forName(clsName, true, cl);

            // setSleepHealthBanner(Integer) —— 集合数据绑定/显隐入口
            try {
                Method m = clazz.getDeclaredMethod("setSleepHealthBanner", Integer.class);
                m.setAccessible(true);
                hook(m).intercept(chain -> {
                    Object result = chain.proceed();
                    Object self = chain.getThisObject();
                    if (self instanceof android.view.View) {
                        // 原方法内 visible(D)/(J) 已执行；同步 GONE（draw 在下一帧 → 首帧不可见）
                        hideSleepCards((android.view.View) self);
                        scheduleSleepCardScan((android.view.View) self, 0);
                    }
                    return result;
                });
                log(Log.INFO, TAG, "hooked: SleepHealthBannerCardSetLayout.setSleepHealthBanner");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "hook failed: setSleepHealthBanner", t);
            }

            // updateSleepInterfereVisibility(boolean, Function0) —— 改善计划卡显隐
            try {
                Class<?> fn = Class.forName("kotlin.jvm.functions.Function0", true, cl);
                Method m = clazz.getDeclaredMethod("updateSleepInterfereVisibility", boolean.class, fn);
                m.setAccessible(true);
                hook(m).intercept(chain -> {
                    if (!Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_SLEEP_CARDS)) {
                        return chain.proceed();
                    }
                    // 不执行原逻辑：改善计划卡保持/转为隐藏
                    if (debugLog()) {
                        log(Log.INFO, TAG, "sleepInterfere blocked (updateSleepInterfereVisibility)");
                    }
                    return null;
                });
                log(Log.INFO, TAG, "hooked: SleepHealthBannerCardSetLayout.updateSleepInterfereVisibility");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "hook failed: updateSleepInterfereVisibility", t);
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: " + clsName, t);
        }
    }

    private void scheduleSleepCardScan(final android.view.View root, final int round) {
        if (round > 8) {
            return;
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    hideSleepCards(root);
                    scheduleSleepCardScan(root, round + 1);
                } catch (Throwable t) {
                    log(Log.ERROR, TAG, "sleep card scan error", t);
                }
            }
        }, 800);
    }

    /** 扫描集合子树，GONE 研究卡容器与改善计划卡（LinearLayout 自动重排，不做手动 shift） */
    private void hideSleepCards(android.view.View v) {
        if (v == null) {
            return;
        }
        if (v.getResources() != null) {
            if (mSleepResearchResId == 0) {
                mSleepResearchResId = v.getResources().getIdentifier("healthSleepResearchLayoutSet", "id", "com.mi.health");
            }
            if (mSleepInterfereResId == 0) {
                mSleepInterfereResId = v.getResources().getIdentifier("sleepInterfereLayout", "id", "com.mi.health");
            }
        }
        if (mSleepResearchResId != 0 && v.getId() == mSleepResearchResId) {
            goneOnce(v, "sleep research card");
            return;
        }
        if (mSleepInterfereResId != 0 && v.getId() == mSleepInterfereResId) {
            goneOnce(v, "sleep interfere card");
            return;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                hideSleepCards(vg.getChildAt(i));
            }
        }
    }

    /** GONE 一次并按 identityHashCode 去重（不 shift，LinearLayout 自动重排） */
    private void goneOnce(android.view.View v, String what) {
        if (v.getVisibility() == android.view.View.GONE) {
            return;
        }
        String key = Integer.toHexString(System.identityHashCode(v));
        if (handledCards.contains(key)) {
            return;
        }
        handledCards.add(key);
        v.setVisibility(android.view.View.GONE);
        log(Log.INFO, TAG, "hidden: " + what + " (" + v.getClass().getSimpleName() + ")");
    }

    // ===================== 设备页红点（底部tab + 主页系统设置入口） =====================

    /**
     * 清除设备页红点（沿用已验证有效的"返回后清数据"方案）：
     * 1) DeviceTabModel.getDeviceNormalTabList(model, type) —— 穿戴设备主页「系统设置」(type=9)
     *    入口红点（rightTextWithDot=" "）；
     * 2) DeviceTabModel.getDeviceFeatureList(model, type) —— 系统设置页列表条目
     *    （「手环防断连保护」条目的 rightTextWithDot/remind 红点）。
     * 两种条目都是 TabContentItem，返回后清空 rightTextWithDot/remindText/remind 即可。
     */
    /**
     * 设备页红点消除（伪装方案，已验证有效）：
     * 1) PowerManager.isIgnoringBatteryOptimizations → true：伪装「已忽略电池优化」，
     *    同时消除底部导航「设备」tab 红点（!isIgnoringBatteryOptimizations 条件）与
     *    主页「系统设置」入口红点（rightTextWithDot 由同一判断控制）。
     * 2) FaceHelperImpl.getFaceEntranceRedPoint/getFaceEntranceOperationRedPoint → false：
     *    消除表盘红点/表盘运营红点（设备tab红点的独立 OR 条件）。
     * 开关：KEY_ENABLE_DEVICE_RED_DOT（默认开）。
     */
    private void hookDeviceRedDots(ClassLoader cl) throws Throwable {
        // 表盘红点/运营红点 → false
        try {
            Class<?> face = Class.forName("com.xiaomi.fitness.watch.face.export.FaceHelperImpl", true, cl);
            for (String name : new String[]{"getFaceEntranceRedPoint", "getFaceEntranceOperationRedPoint"}) {
                try {
                    Method m = face.getDeclaredMethod(name, String.class);
                    m.setAccessible(true);
                    hook(m).intercept(chain -> {
                        if (!Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_ALL)
                                || !Prefs.isEnabled(mPrefs, Prefs.KEY_ENABLE_DEVICE_RED_DOT)) {
                            return chain.proceed();
                        }
                        return false;
                    });
                    log(Log.INFO, TAG, "hooked: FaceHelperImpl." + name + " (device red dot)");
                } catch (Throwable t) {
                    log(Log.ERROR, TAG, "hook failed: FaceHelperImpl." + name, t);
                }
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: FaceHelperImpl", t);
        }

        // 伪装已忽略电池优化 → 设备tab红点 + 主页系统设置入口红点消失
        try {
            Class<?> pmCls = Class.forName("android.os.PowerManager", true, cl);
            Method m = pmCls.getMethod("isIgnoringBatteryOptimizations", String.class);
            m.setAccessible(true);
            hook(m).intercept(chain -> {
                if (!Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_ALL)
                        || !Prefs.isEnabled(mPrefs, Prefs.KEY_ENABLE_DEVICE_RED_DOT)) {
                    return chain.proceed();
                }
                return true;
            });
            log(Log.INFO, TAG, "hooked: PowerManager.isIgnoringBatteryOptimizations (device red dot)");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: PowerManager.isIgnoringBatteryOptimizations", t);
        }
    }

    // ===================== 表盘自动导出（实验） =====================

    /** 表盘自动导出是否开启（独立开关，默认关；要求总开关同时开启） */
    private boolean faceExport() {
        return Prefs.isEnabled(mPrefs, Prefs.KEY_ENABLE_ALL)
                && Prefs.isEnabled(mPrefs, Prefs.KEY_ENABLE_FACE_EXPORT, false);
    }

    /** 本进程已导出的新 ID（同进程去重） */
    private final java.util.Set<String> exportedFaces = new java.util.HashSet<>();

    /** 最近一次推送发起时间（ms），清理时 15 分钟内有推送则跳过 */
    private volatile long mLastPushMillis;

    /**
     * 表盘自动导出：hook 表盘推送入口 doInstall(path, id, ...)，推送前把
     * resource.bin 按"12→19"规则换新 ID，原样写一份到 Download/face_<新ID>.bin（中文名_新ID.bin），
     * 供第三方软件直接导入。只改 ID（已验证 band 接受），不动其他字节。
     * 导出失败只记日志，绝不影响原推送流程。
     */
    /** 自定义 ID 前缀（导出时 12→19），清理保护只保这个号段 */
    private static final String CUSTOM_FACE_PREFIX = "19";

    private void hookFaceExport(ClassLoader cl) throws Throwable {
        hookFaceCleanupProtect(cl);
        String[] impls = new String[]{
                "com.xiaomi.fitness.watch.face.install.FaceInstallBleImpl",
                "com.xiaomi.fitness.watch.face.install.FaceInstallHuamiImpl"};
        for (String clsName : impls) {
            try {
                Class<?> clazz = Class.forName(clsName, true, cl);
                Class<?> cb = Class.forName(
                        "com.xiaomi.fitness.watch.face.install.FaceInstallPushCallback", true, cl);
                java.lang.reflect.Method m = resolveDoInstall(clazz, cb);
                if (m == null) {
                    log(Log.ERROR, TAG, "hook failed: " + clsName + ".doInstall (no match)");
                    continue;
                }
                m.setAccessible(true);
                hook(m).intercept(chain -> {
                    try {
                        if (faceExport()) {
                            mLastPushMillis = System.currentTimeMillis();
                            exportCachedFaces();
                            scheduleExportScan();
                        }
                    } catch (Throwable t) {
                        log(Log.ERROR, TAG, "face export failed (non-fatal)", t);
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "hooked: " + clsName + ".doInstall (face export)");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "hook failed: " + clsName + ".doInstall", t);
            }
        }
        // preInstall 兜底：部分推送链路不经过 doInstall（如版本漂移），在预安装点也试一次
        for (String clsName : impls) {
            try {
                Class<?> clazz = Class.forName(clsName, true, cl);
                java.lang.reflect.Method m = resolvePreInstall(clazz);
                if (m == null) {
                    log(Log.ERROR, TAG, "hook failed: " + clsName + ".preInstall (no match)");
                    continue;
                }
                m.setAccessible(true);
                hook(m).intercept(chain -> {
                    try {
                        if (faceExport()) {
                            mLastPushMillis = System.currentTimeMillis();
                            exportCachedFaces();
                            scheduleExportScan();
                        }
                    } catch (Throwable t) {
                        log(Log.ERROR, TAG, "face export failed (non-fatal)", t);
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "hooked: " + clsName + ".preInstall (face export)");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "hook failed: " + clsName + ".preInstall", t);
            }
        }
    }

    /**
     * 解析 preInstall(String, String, long, long, boolean, String, String,
     * Integer, Function3)：前 8 精确，末参按名宽松（含 unction3 即可，
     * kotlin-stdlib 混淆导致精确匹配不可靠，见试用转正期的真机实证）。
     */
    private java.lang.reflect.Method resolvePreInstall(Class<?> clazz) {
        Class<?>[] leading = new Class[]{String.class, String.class, long.class, long.class,
                boolean.class, String.class, String.class, Integer.class};
        for (java.lang.reflect.Method cand : clazz.getDeclaredMethods()) {
            if (!"preInstall".equals(cand.getName())) {
                continue;
            }
            Class<?>[] ps = cand.getParameterTypes();
            if (ps.length != 9) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < leading.length; i++) {
                if (ps[i] != leading[i]) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            if (ps[8].getName().contains("unction3")) {
                log(Log.INFO, TAG, "resolved: " + clazz.getSimpleName() + ".preInstall");
                return cand;
            }
        }
        return null;
    }

    /** 解析 doInstall(String, String, Integer, FaceInstallPushCallback)：精确优先，名+元数回退 */
    private java.lang.reflect.Method resolveDoInstall(Class<?> clazz, Class<?> cb) {
        try {
            return clazz.getDeclaredMethod("doInstall", String.class, String.class,
                    Integer.class, cb);
        } catch (NoSuchMethodException e) {
            // fall through
        }
        for (java.lang.reflect.Method cand : clazz.getDeclaredMethods()) {
            if ("doInstall".equals(cand.getName())
                    && cand.getParameterTypes().length == 4) {
                log(Log.INFO, TAG, "resolved: " + clazz.getSimpleName() + ".doInstall");
                return cand;
            }
        }
        return null;
    }

    /** 新 ID 规则：12 位数字 ID 前缀 12→19（等长替换，结构不断） */
    private String remapFaceId(String faceId) {
        if (faceId != null && faceId.length() == 12) {
            return CUSTOM_FACE_PREFIX + faceId.substring(2);
        }
        return null;
    }

    /**
     * 防删除保护：App 每次同步拿服务端 unavailable 名单删本地未知 ID 的表盘
     * （第三方软件刷入的 19 号段必中招）。把 19 号段从名单摘掉，名单空了直接跳过；
     * 只动名单，不动删除逻辑本身。本地 DB 的 deleteOtherWatchFace 后续看情况再保。
     */
    private void hookFaceCleanupProtect(ClassLoader cl) throws Throwable {
        try {
            Class<?> clazz = Class.forName(
                    "com.xiaomi.fitness.watch.face.export.FaceHelperImpl", true, cl);
            hookCleanupMethod(clazz, "removeUnavailableFaces",
                    new Class[]{java.util.List.class, java.util.List.class, java.util.List.class});
            hookCleanupMethod(clazz, "removeUnavailableFacesByGroup",
                    new Class[]{java.util.List.class, int.class});
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: cleanup protect", t);
        }
    }

    private void hookCleanupMethod(Class<?> clazz, String name, Class<?>[] params) throws Throwable {
        java.lang.reflect.Method m = clazz.getDeclaredMethod(name, params);
        m.setAccessible(true);
        final boolean withGroup = params.length == 2;
        hook(m).intercept(chain -> {
            if (!faceExport()) {
                return chain.proceed();
            }
            java.util.List<String> ids = (java.util.List<String>) chain.getArg(0);
            java.util.List<String> kept = filterCustomFaces(ids);
            if (kept.isEmpty()) {
                log(Log.INFO, TAG, "cleanup protect: all skipped (" + name + ")");
                return null;
            }
            if (ids == null || kept.size() != ids.size()) {
                log(Log.INFO, TAG, "cleanup protect: filtered=" + kept);
                if (withGroup) {
                    return chain.proceed(new Object[]{kept, chain.getArg(1)});
                }
                return chain.proceed(new Object[]{kept, chain.getArg(1), chain.getArg(2)});
            }
            return chain.proceed();
        });
        log(Log.INFO, TAG, "hooked: FaceHelperImpl." + name + " (cleanup protect)");
    }

    /** 摘掉 19 号段自定义 ID，返回保留名单 */
    private java.util.List<String> filterCustomFaces(java.util.List<String> ids) {
        java.util.List<String> kept = new java.util.ArrayList<>();
        if (ids == null) {
            return kept;
        }
        for (String id : ids) {
            if (id != null && id.length() == 12 && id.startsWith(CUSTOM_FACE_PREFIX)) {
                log(Log.INFO, TAG, "cleanup protect: keep " + id);
                continue;
            }
            kept.add(id);
        }
        return kept;
    }

    /**
     * 全量扫描导出：遍历 WatchFace 缓存目录，把所有未导出的 resource.bin
     * 换新 ID 写到 Download/。推送拦截 + 页面打开双触发，互为备份
     * （推送链路版本漂移时，扫盘依然能兜住）。
     */
    private void exportCachedFaces() {
        if (!faceExport()) {
            return;
        }
        try {
            java.io.File root = new java.io.File(
                    "/storage/emulated/0/Android/data/com.mi.health/files/WatchFace");
            java.io.File[] dids = root.listFiles();
            if (dids == null) {
                return;
            }
            int found = 0;
            int fresh = 0;
            int cleaned = 0;
            for (java.io.File did : dids) {
                if (!did.isDirectory()) {
                    continue;
                }
                java.io.File[] faces = did.listFiles();
                if (faces == null) {
                    continue;
                }
                for (java.io.File face : faces) {
                    if (!face.isDirectory()) {
                        continue;
                    }
                    found++;
                    if (exportFace(null, face.getName())) {
                        fresh++;
                    } else if (cleanOldBin(face)) {
                        cleaned++;
                    }
                }
            }
            log(Log.INFO, TAG, "export scan done: fresh=" + fresh + " found=" + found
                    + " cleaned=" + cleaned);
            notifyExportResult(fresh, found, cleaned);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "export scan error", t);
        }
    }

    /** 已导出 ID 的跨进程记录（RemotePreferences，MediaStore 查询不可靠） */
    private static final String PREF_EXPORTED_IDS = "exported_face_ids";

    private boolean isExportedMarked(String newId) {
        try {
            if (mPrefs == null || newId == null) {
                return false;
            }
            String csv = mPrefs.getString(PREF_EXPORTED_IDS, "");
            if (csv == null || csv.isEmpty()) {
                return false;
            }
            for (String s : csv.split(",")) {
                if (newId.equals(s)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "read exported marks failed", t);
            return false;
        }
    }

    private void markExported(String newId) {
        try {
            if (mPrefs == null || newId == null) {
                return;
            }
            String csv = mPrefs.getString(PREF_EXPORTED_IDS, "");
            if (csv == null) {
                csv = "";
            }
            if (!csv.isEmpty()) {
                for (String s : csv.split(",")) {
                    if (newId.equals(s)) {
                        return;
                    }
                }
            }
            mPrefs.edit().putString(PREF_EXPORTED_IDS,
                    csv.isEmpty() ? newId : csv + "," + newId).apply();
            log(Log.INFO, TAG, "marked exported: " + newId);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "mark exported failed", t);
        }
    }

    /**
     * 清旧缓存：已导出超过 5 分钟的 resource.bin 删除（只删大文件，描述/预览保留，
     * App 显示不受影响；叠加 15 分钟推送保护，防删正在传的文件）。
     */
    private boolean cleanOldBin(java.io.File faceDir) {
        try {
            if (faceDir == null || !faceDir.isDirectory()) {
                return false;
            }
            String newId = remapFaceId(faceDir.getName());
            if (newId == null || !isExportedMarked(newId)) {
                return false;
            }
            java.io.File[] hashes = faceDir.listFiles();
            if (hashes == null) {
                return false;
            }
            boolean cleaned = false;
            long now = System.currentTimeMillis();
            // 15 分钟内有过推送则整单跳过（防删正在传的文件）
            if (now - mLastPushMillis < 15L * 60 * 1000) {
                return false;
            }
            long cutoff = now - 5L * 60 * 1000;
            for (java.io.File h : hashes) {
                java.io.File bin = new java.io.File(h, "resource.bin");
                if (bin.isFile() && bin.lastModified() < cutoff && bin.delete()) {
                    log(Log.INFO, TAG, "cache cleaned: " + bin.getAbsolutePath());
                    cleaned = true;
                }
            }
            return cleaned;
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "cache clean error", t);
            return false;
        }
    }

    /** 扫盘结果通知：告诉用户新增几张、清了几个、去哪找、下一步干嘛 */
    private void notifyExportResult(int fresh, int found, int cleaned) {
        try {
            String title;
            String text;
            String tail = cleaned > 0 ? "；顺手清了 " + cleaned + " 个旧缓存" : "";
            if (fresh > 0) {
                title = "表盘导出成功";
                text = "新增 " + fresh + " 张 → Download/，去第三方软件导入开刷" + tail;
            } else {
                title = "表盘导出";
                text = "无新增（缓存 " + found + " 张均已导出），试用新表盘后再来" + tail;
            }
            notifyExport(title, text);
            toastResult(text);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "notify failed", t);
        }
    }

    /** Toast 兜底：通知栏权限被关时也能看到（小米运动健康的通知权限当前是关的） */
    private void toastResult(final String text) {
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Object app = Class.forName("android.app.ActivityThread")
                                .getMethod("currentApplication").invoke(null);
                        android.widget.Toast.makeText((android.content.Context) app,
                                text, android.widget.Toast.LENGTH_LONG).show();
                    } catch (Throwable t) {
                        log(Log.ERROR, TAG, "toast failed", t);
                    }
                }
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "toast failed", t);
        }
    }

    private void notifyExport(String title, String text) {
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            android.content.Context ctx = (android.content.Context) app;
            Object nmObj = ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            android.app.NotificationManager nm = (android.app.NotificationManager) nmObj;
            String ch = "face_export";
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationChannel c = new android.app.NotificationChannel(
                        ch, "表盘导出", android.app.NotificationManager.IMPORTANCE_DEFAULT);
                nm.createNotificationChannel(c);
            }
            android.app.Notification.Builder b;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                b = new android.app.Notification.Builder(ctx, ch);
            } else {
                b = new android.app.Notification.Builder(ctx);
            }
            b.setContentTitle(title).setContentText(text)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setAutoCancel(true);
            nm.notify(0xface, b.build());
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "notify failed", t);
        }
    }

    /** 延迟 5 秒再扫一次（等下载/解包落盘） */
    private void scheduleExportScan() {
        if (!faceExport()) {
            return;
        }
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        exportCachedFaces();
                    } catch (Throwable t) {
                        log(Log.ERROR, TAG, "export scan error", t);
                    }
                }
            }, 5000);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "export schedule error", t);
        }
    }

    /**
     * 导出流程：找 resource.bin → 内存换 ID → 写 Download/face_<新ID>.bin。
     * path 非常规时回退按 faceId 在 WatchFace 缓存目录下搜 resource.bin。
     */
    private boolean exportFace(String path, String faceId) {
        String newId = remapFaceId(faceId);
        if (newId == null) {
            return false; // 非 12 位 ID 直接跳过（不记 error，避免扫盘刷屏）
        }
        synchronized (exportedFaces) {
            if (exportedFaces.contains(newId)) {
                markExported(newId);
                return false; // 同一张盘只导一次
            }
            exportedFaces.add(newId);
        }
        if (isExportedMarked(newId)) {
            return false; // 跨进程去重（MediaStore 查询不可靠，改走 prefs 记录）
        }
        java.io.File src = findFaceBin(path, faceId);
        if (src == null) {
            log(Log.ERROR, TAG, "face export skip: bin not found id=" + faceId);
            return false;
        }
        try {
            byte[] data = readAll(src);
            byte[] oldB;
            byte[] newB;
            try {
                oldB = faceId.getBytes("ASCII");
                newB = newId.getBytes("ASCII");
            } catch (Throwable t) {
                oldB = faceId.getBytes();
                newB = newId.getBytes();
            }
            int replaced = replaceAll(data, oldB, newB);
            if (replaced == 0) {
                log(Log.ERROR, TAG, "face export skip: id not in bin");
                return false;
            }
            String faceName = readFaceName(src);
            String fileName = (faceName != null ? faceName + "_" + newId : "face_" + newId) + ".bin";
            Object uri = writeToDownload(fileName, data);
            if (uri == null) {
                markExported(newId);
                return false; // 已存在（跨进程去重）
            }
            markExported(newId);
            log(Log.INFO, TAG, "face exported: " + faceId + " -> " + newId
                    + " (" + replaced + "x, " + data.length + "B) -> " + uri);
            return true;
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "face export error", t);
            return false;
        }
    }

    /** 从同目录 description.xml 读表盘中文名（供导出文件名用，失败返回 null） */
    private String readFaceName(java.io.File bin) {
        try {
            java.io.File parent = bin.getParentFile();
            if (parent == null) {
                return null;
            }
            java.io.File xml = new java.io.File(parent, "description.xml");
            if (!xml.isFile() || xml.length() <= 0 || xml.length() > 65536) {
                return null;
            }
            byte[] buf = readAll(xml);
            String s = new String(buf, "UTF-8");
            int a = s.indexOf("<name>");
            if (a < 0) {
                return null;
            }
            int b = s.indexOf("</name>", a);
            if (b < 0) {
                return null;
            }
            String name = s.substring(a + 6, b).trim();
            if (name.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < name.length(); i++) {
                char ch = name.charAt(i);
                if (ch == '/' || ch == '\\' || ch == ':' || ch == '*' || ch == '?'
                        || ch == '"' || ch == '<' || ch == '>' || ch == '|') {
                    sb.append('_');
                } else {
                    sb.append(ch);
                }
            }
            String clean = sb.toString().trim();
            return clean.isEmpty() ? null : clean;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 定位 resource.bin：优先 doInstall 传的 path，否则按 faceId 搜缓存目录 */
    private java.io.File findFaceBin(String path, String faceId) {
        if (path != null) {
            java.io.File f = new java.io.File(path);
            if (f.isFile() && f.length() > 0) {
                return f;
            }
        }
        try {
            java.io.File root = new java.io.File(
                    "/storage/emulated/0/Android/data/com.mi.health/files/WatchFace");
            return searchBin(root, faceId, 0);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "face bin search error", t);
            return null;
        }
    }

    private java.io.File searchBin(java.io.File dir, String faceId, int depth) {
        if (dir == null || depth > 4 || !dir.isDirectory()) {
            return null;
        }
        java.io.File[] kids = dir.listFiles();
        if (kids == null) {
            return null;
        }
        for (java.io.File k : kids) {
            if (k.isFile() && "resource.bin".equals(k.getName())
                    && k.getAbsolutePath().contains(faceId)) {
                return k;
            }
        }
        for (java.io.File k : kids) {
            java.io.File hit = searchBin(k, faceId, depth + 1);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private byte[] readAll(java.io.File f) throws Throwable {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            long len = f.length();
            if (len <= 0 || len > 64 * 1024 * 1024) {
                throw new RuntimeException("bad size: " + len);
            }
            byte[] buf = new byte[(int) len];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) {
                    break;
                }
                off += n;
            }
            if (off != buf.length) {
                throw new RuntimeException("short read");
            }
            return buf;
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
                // ignore
            }
        }
    }

    private int replaceAll(byte[] data, byte[] oldB, byte[] newB) {
        int count = 0;
        outer:
        for (int i = 0; i + oldB.length <= data.length; i++) {
            for (int j = 0; j < oldB.length; j++) {
                if (data[i + j] != oldB[j]) {
                    continue outer;
                }
            }
            System.arraycopy(newB, 0, data, i, newB.length);
            count++;
            i += oldB.length - 1;
        }
        return count;
    }

    /**
     * 写 Download/：API29+ 走 MediaStore（免权限写自有文件），
     * 低版本回退直接写 Download 目录；同名已存在则跳过（跨进程去重）。
     */
    private Object writeToDownload(String fileName, byte[] data) throws Throwable {
        Object app = Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication").invoke(null);
        android.content.Context ctx = (android.content.Context) app;
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            android.content.ContentResolver cr = ctx.getContentResolver();
            android.net.Uri coll = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            android.database.Cursor c = null;
            try {
                c = cr.query(coll, new String[]{"_id"},
                        "_display_name=?", new String[]{fileName}, null);
                if (c != null && c.moveToFirst()) {
                    log(Log.INFO, TAG, "face export skip: already in Download");
                    return null;
                }
            } finally {
                if (c != null) {
                    try {
                        c.close();
                    } catch (Throwable ignored) {
                        // ignore
                    }
                }
            }
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put("_display_name", fileName);
            cv.put("mime_type", "application/octet-stream");
            cv.put("relative_path", "Download");
            android.net.Uri uri = cr.insert(coll, cv);
            if (uri == null) {
                throw new RuntimeException("mediastore insert null");
            }
            java.io.OutputStream out = null;
            try {
                out = cr.openOutputStream(uri);
                if (out == null) {
                    throw new RuntimeException("open stream null");
                }
                out.write(data);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Throwable ignored) {
                        // ignore
                    }
                }
            }
            return uri;
        }
        java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS);
        java.io.File out = new java.io.File(dir, fileName);
        if (out.isFile()) {
            log(Log.INFO, TAG, "face export skip: already in Download");
            return null;
        }
        java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
        try {
            fos.write(data);
        } finally {
            try {
                fos.close();
            } catch (Throwable ignored) {
                // ignore
            }
        }
        return out.getAbsolutePath();
    }

    // ===================== 反 hook 检测 =====================

    private void hookSensorHelper(ClassLoader cl) throws Throwable {
        String clsName = "com.xiaomi.verificationsdk.internal.SensorHelper";
        try {
            Class<?> clazz = Class.forName(clsName, true, cl);
            for (String name : new String[]{"A", "D"}) {
                Method m = clazz.getDeclaredMethod(name);
                m.setAccessible(true);
                hook(m).intercept(chain -> {
                    if (!Prefs.enabled(mPrefs, Prefs.KEY_ENABLE_ANTI_DETECT)) {
                        return chain.proceed();
                    }
                    return 0;
                });
            }
            log(Log.INFO, TAG, "anti-detect hooked: " + clsName + ".A/.D");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed: " + clsName, t);
        }
    }
}