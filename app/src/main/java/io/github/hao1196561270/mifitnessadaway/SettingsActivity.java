package io.github.hao1196561270.mifitnessadaway;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * 设置界面：去广告开关列表（Q15-A：libxposed RemotePreferences 方案）。
 * 通过 XposedService 获取 RemotePreferences，写入后框架自动同步到
 * com.mi.health 进程，hook 侧动态读取——改完即生效（无需重启）。
 *
 * v1.0：状态栏高度 padding（edge-to-edge 适配）。
 * v1.1：颜色跟随系统深浅色模式（浅色=白底黑字，深色=深底白字）。
 */
public class SettingsActivity extends Activity implements XposedServiceHelper.OnServiceListener {

    private XposedService mService;
    private final Map<String, Switch> switches = new LinkedHashMap<>();

    /** 是否深色模式 */
    private boolean isDarkMode() {
        int uiMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** 背景色：浅色=白，深色=深灰蓝（#1E1E1E 风格） */
    private int backgroundColor() {
        return isDarkMode() ? 0xFF1E1E1E : Color.WHITE;
    }

    /** 主文字色：浅色=黑，深色=白 */
    private int textColor() {
        return isDarkMode() ? Color.WHITE : Color.BLACK;
    }

    /** 次要文字色（版本号等）：浅色=灰，深色=浅灰 */
    private int subTextColor() {
        return isDarkMode() ? 0xFF9E9E9E : Color.GRAY;
    }

    /** 分隔线色：浅色=浅灰，深色=深灰 */
    private int dividerColor() {
        return isDarkMode() ? 0xFF3A3A3A : Color.LTGRAY;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Android 15+ edge-to-edge：内容会绘制到状态栏后面，需把状态栏高度计入顶部 padding
        int statusBarHeight = 0;
        int sbRes = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (sbRes > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(sbRes);
        }

        // 外：ScrollView（保证全部开关可滚动显示，修复显示不全）
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(backgroundColor());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(56, 48 + statusBarHeight, 56, 200);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 标题 + 版本
        TextView title = new TextView(this);
        title.setText("MiFitnessAdAway 开关设置");
        title.setTextSize(22);
        title.setTextColor(textColor());
        title.setPadding(0, 0, 0, 8);
        root.addView(title);

        TextView ver = new TextView(this);
        ver.setText("版本 " + getVersionName() + " · 修改后需重启应用，设置方可生效");
        ver.setTextSize(12);
        ver.setTextColor(subTextColor());
        ver.setPadding(0, 0, 0, 24);
        root.addView(ver);

        addSwitch(root, "总开关（启用去广告）", Prefs.KEY_ENABLE_ALL);
        addSwitch(root, "首页健康界面推广卡片", Prefs.KEY_ENABLE_HEALTH_BANNER);
        addSwitch(root, "设备界面推广卡片", Prefs.KEY_ENABLE_DEVICE_BANNER);
        addSwitch(root, "设备红点（底部tab/系统设置入口）", Prefs.KEY_ENABLE_DEVICE_RED_DOT);
        addSwitch(root, "我的界面 VIP 会员卡", Prefs.KEY_ENABLE_MINE_VIP);
        addSwitch(root, "我的界面健康问诊卡", Prefs.KEY_ENABLE_MINE_DOCTOR);
        addSwitch(root, "运动界面轮播卡片", Prefs.KEY_ENABLE_SPORT_BANNER);
        addSwitch(root, "运动界面运营卡片（训练指标以下）", Prefs.KEY_ENABLE_SPORT_CARDS);
        addSwitch(root, "健康问诊卡片（睡眠/心率/血氧）", Prefs.KEY_ENABLE_HEALTH_CONSULT);
        addSwitch(root, "睡眠界面研究/改善卡片", Prefs.KEY_ENABLE_SLEEP_CARDS);
        addSwitch(root, "开屏广告", Prefs.KEY_ENABLE_SPLASH);
        addSwitch(root, "公告 banner", Prefs.KEY_ENABLE_ANNOUNCE);
        addSwitch(root, "反 hook 检测", Prefs.KEY_ENABLE_ANTI_DETECT);
        addSwitch(root, "调试日志", Prefs.KEY_DEBUG_LOG);
        addSwitch(root, "隐藏桌面图标", Prefs.KEY_HIDE_ICON);

        setContentView(scroll);
    }

    private String getVersionName() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private void addSwitch(LinearLayout root, String label, final String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 22, 0, 22);
        row.setBackgroundColor(backgroundColor());

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(16);
        tv.setTextColor(textColor());
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tv);

        final Switch sw = new Switch(this);
        sw.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(sw);
        root.addView(row);
        switches.put(key, sw);

        // 分隔线
        View divider = new View(this);
        divider.setBackgroundColor(dividerColor());
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        root.addView(divider);

        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mService != null) {
                    mService.getRemotePreferences(Prefs.GROUP).edit()
                            .putBoolean(key, isChecked).apply();
                }
                // 隐藏桌面图标：开关开启 = 隐藏，关闭 = 显示（即时生效，无需重启）
                if (Prefs.KEY_HIDE_ICON.equals(key)) {
                    applyLauncherIcon(!isChecked);
                }
            }
        });
    }

    /**
     * 设置桌面图标 alias（LauncherAlias）显隐。
     * 注意：参数为「是否显示」——visible=true 启用图标，false 隐藏图标；
     * 设置页开关的语义是「隐藏桌面图标」，故调用处传 !isChecked。
     */
    private void applyLauncherIcon(boolean visible) {
        try {
            android.content.ComponentName alias = new android.content.ComponentName(
                    getPackageName(), getPackageName() + ".LauncherAlias");
            getPackageManager().setComponentEnabledSetting(alias,
                    visible
                            ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
            android.widget.Toast.makeText(this,
                    visible ? "桌面图标已显示" : "桌面图标已隐藏（LSPosed 中仍可打开设置）",
                    android.widget.Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            android.widget.Toast.makeText(this, "图标切换失败: " + t.getMessage(),
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        ((MiFitnessApp) getApplication()).addServiceStateListener(this, true);
    }

    @Override
    protected void onStop() {
        ((MiFitnessApp) getApplication()).removeServiceStateListener(this);
        super.onStop();
    }

    @Override
    public void onServiceBind(XposedService service) {
        mService = service;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SharedPreferences sp = mService.getRemotePreferences(Prefs.GROUP);
                for (Map.Entry<String, Switch> e : switches.entrySet()) {
                    if (Prefs.KEY_HIDE_ICON.equals(e.getKey())) {
                        // 开关语义=「隐藏图标」：图标显示(TRUE)时开关应 OFF
                        e.getValue().setChecked(!isLauncherIconEnabled());
                    } else {
                        e.getValue().setChecked(sp.getBoolean(e.getKey(), true));
                    }
                }
            }
        });
    }

    /** 桌面图标 alias 当前是否启用 */
    private boolean isLauncherIconEnabled() {
        try {
            android.content.ComponentName alias = new android.content.ComponentName(
                    getPackageName(), getPackageName() + ".LauncherAlias");
            int state = getPackageManager().getComponentEnabledSetting(alias);
            return state != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    && state != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        } catch (Throwable t) {
            return true;
        }
    }

    @Override
    public void onServiceDied(XposedService service) {
        mService = null;
    }
}