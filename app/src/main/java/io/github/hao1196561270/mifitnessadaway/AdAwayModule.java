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