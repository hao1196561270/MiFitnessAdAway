package io.github.hao1196561270.mifitnessadaway;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * 设置界面：分组卡片式的去广告开关列表。
 * 通过 XposedService 获取 RemotePreferences，写入后框架自动同步到
 * com.mi.health/com.xiaomi.wearable 进程，hook 侧动态读取——改完即生效（无需重启）。
 *
 * v1.0：状态栏高度 padding（edge-to-edge 适配）。
 * v1.1：颜色跟随系统深浅色模式。
 * v1.2：分组卡片 + 顶部启用摘要 + 分组折叠（记忆）+ 总开关联动置灰。
 *
 * 设计约定（与维护者敲定）：
 * - 顶部一张卡＝启用摘要 + 总开关（总开关单独成栏，不混进分类组）
 * - 其余按界面分类：开屏与弹窗 / 我的页 / 运动页 / 设备页 / 健康详情页 / 表盘 / 其他
 * - 摘要只统计「去广告类」子开关；总开关关闭时显示已停用
 * - 折叠状态存本模块自己的 SharedPreferences（不碰 RemotePreferences）
 * - 置灰范围＝受总开关影响的开关（去广告类 + 表盘导出）；
 *   「调试日志」「隐藏桌面图标」「勿扰同步」「Health Connect 导出」不受总开关影响，保持可点
 */
public class SettingsActivity extends Activity implements XposedServiceHelper.OnServiceListener {

    /** 摘要统计口径：去广告类子开关（不含总开关与工具类开关） */
    private static final String[] AD_KEYS = {
            Prefs.KEY_ENABLE_DEVICE_RED_DOT,
            Prefs.KEY_ENABLE_MINE_VIP,
            Prefs.KEY_ENABLE_MINE_DOCTOR,
            Prefs.KEY_ENABLE_SPORT_BANNER,
            Prefs.KEY_ENABLE_SPORT_CARDS,
            Prefs.KEY_ENABLE_SPLASH,
            Prefs.KEY_ENABLE_APP_UPDATE,
            Prefs.KEY_ENABLE_HEALTH_CONSULT,
            Prefs.KEY_ENABLE_SLEEP_CARDS,
            Prefs.KEY_ENABLE_WEIGHT_PLAN,
            Prefs.KEY_ENABLE_VIP_POPUP,
    };

    /** 置灰范围：去广告类 + 表盘导出（其代码要求总开关同时开启） */
    private static final Set<String> DIMMED_KEYS = new LinkedHashSet<>();

    static {
        for (String k : AD_KEYS) {
            DIMMED_KEYS.add(k);
        }
        DIMMED_KEYS.add(Prefs.KEY_ENABLE_FACE_EXPORT);
    }

    /** 折叠状态存储（模块自身 prefs，独立于 RemotePreferences） */
    private static final String UI_PREFS = "adaway_ui";

    private XposedService mService;
    private final Map<String, Switch> switches = new LinkedHashMap<>();
    private final Map<String, View> rows = new LinkedHashMap<>();
    /** 初始化回填开关状态时为 true：此时 setChecked 触发的 listener 不弹说明框、不写回 */
    private boolean mBindingDefaults;
    /** 是否已连上框架读到设置（未连上前摘要显示"读取中"） */
    private boolean mBound;
    private TextView mSummary;

    // ===================== 配色 =====================

    private boolean isDarkMode() {
        int uiMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** 页面背景 */
    private int backgroundColor() {
        return isDarkMode() ? 0xFF121212 : 0xFFF2F3F5;
    }

    /** 卡片底色：浅色=白，深色=#1E1E1E */
    private int cardColor() {
        return isDarkMode() ? 0xFF1E1E1E : Color.WHITE;
    }

    /** 主文字色 */
    private int textColor() {
        return isDarkMode() ? Color.WHITE : Color.BLACK;
    }

    /** 次要文字色（版本号、摘要说明、分组箭头） */
    private int subTextColor() {
        return isDarkMode() ? 0xFF9E9E9E : 0xFF757575;
    }

    /** 分隔线色 */
    private int dividerColor() {
        return isDarkMode() ? 0xFF2E2E2E : 0xFFEEEEEE;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 圆角卡片背景 */
    private GradientDrawable roundedCard() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(16));
        d.setColor(cardColor());
        return d;
    }

    // ===================== 构建界面 =====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Android 15+ edge-to-edge：内容会绘制到状态栏后面，需把状态栏高度计入顶部 padding
        int statusBarHeight = 0;
        int sbRes = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (sbRes > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(sbRes);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(backgroundColor());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16) + statusBarHeight, dp(16), dp(48));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 标题 + 版本
        TextView title = new TextView(this);
        title.setText("MiFitnessAdAway");
        title.setTextSize(24);
        title.setTextColor(textColor());
        root.addView(title);

        TextView ver = new TextView(this);
        ver.setText("版本 " + getVersionName() + " · 修改后需重启运动健康生效");
        ver.setTextSize(12);
        ver.setTextColor(subTextColor());
        ver.setPadding(0, dp(4), 0, dp(14));
        root.addView(ver);

        // 顶部卡：启用摘要 + 总开关（总开关单独成栏，不放进取广告组）
        LinearLayout topCard = new LinearLayout(this);
        topCard.setOrientation(LinearLayout.VERTICAL);
        topCard.setBackground(roundedCard());

        mSummary = new TextView(this);
        mSummary.setTextSize(17);
        mSummary.setTextColor(textColor());
        mSummary.setText("正在读取设置…");
        mSummary.setPadding(dp(16), dp(14), dp(16), dp(6));
        topCard.addView(mSummary);

        TextView summaryHint = new TextView(this);
        summaryHint.setTextSize(12);
        summaryHint.setTextColor(subTextColor());
        summaryHint.setPadding(dp(16), 0, dp(16), dp(2));
        summaryHint.setText("「调试日志」「隐藏桌面图标」「勿扰同步」「Health Connect 导出」不受总开关影响");
        topCard.addView(summaryHint);

        TextView dndHint = new TextView(this);
        dndHint.setTextSize(12);
        dndHint.setTextColor(subTextColor());
        dndHint.setPadding(dp(16), 0, dp(16), dp(10));
        dndHint.setText("勿扰同步需先在系统设置给运动健康授予「勿扰访问权限」，否则手机侧不会随手环切换");
        topCard.addView(dndHint);

        LinearLayout masterBox = new LinearLayout(this);
        masterBox.setOrientation(LinearLayout.VERTICAL);
        topCard.addView(masterBox);
        addRow(masterBox, "总开关（启用去广告）", Prefs.KEY_ENABLE_ALL, true);
        addCardWithMargin(root, topCard);

        // 其余按界面分类（顺序＝用户使用路径）
        addGroup(root, "开屏与弹窗", new String[][]{
                {"开屏广告", Prefs.KEY_ENABLE_SPLASH},
                {"应用更新弹窗", Prefs.KEY_ENABLE_APP_UPDATE},
                {"会员推广弹窗", Prefs.KEY_ENABLE_VIP_POPUP},
        });
        addGroup(root, "我的页", new String[][]{
                {"我的界面 VIP 会员卡", Prefs.KEY_ENABLE_MINE_VIP},
                {"我的界面健康问诊卡", Prefs.KEY_ENABLE_MINE_DOCTOR},
                {"Health Connect 导出", Prefs.KEY_ENABLE_HEALTH_CONNECT},
        });
        addGroup(root, "运动页", new String[][]{
                {"运动界面轮播卡片", Prefs.KEY_ENABLE_SPORT_BANNER},
                {"运动界面运营卡片（训练指标以下）", Prefs.KEY_ENABLE_SPORT_CARDS},
        });
        addGroup(root, "设备页", new String[][]{
                {"设备红点（底部tab/系统设置入口）", Prefs.KEY_ENABLE_DEVICE_RED_DOT},
                {"勿扰同步（手机 ↔ 手环）", Prefs.KEY_ENABLE_DND_SYNC},
        });
        addGroup(root, "健康详情页", new String[][]{
                {"健康问诊卡片（睡眠/心率/血氧/压力）", Prefs.KEY_ENABLE_HEALTH_CONSULT},
                {"睡眠界面研究/改善卡片", Prefs.KEY_ENABLE_SLEEP_CARDS},
                {"个性化减重方案栏（体重页）", Prefs.KEY_ENABLE_WEIGHT_PLAN},
        });
        addGroup(root, "表盘", new String[][]{
                {"表盘自动导出（实验）", Prefs.KEY_ENABLE_FACE_EXPORT},
        }, this::addExportLocationRow);
        addGroup(root, "其他", new String[][]{
                {"反 hook 检测", Prefs.KEY_ENABLE_ANTI_DETECT},
                {"调试日志", Prefs.KEY_DEBUG_LOG},
                {"隐藏桌面图标", Prefs.KEY_HIDE_ICON},
        });

        setContentView(scroll);
        updateSummary();
    }

    /** 卡片之间留间距后加入根容器 */
    private void addCardWithMargin(LinearLayout root, View card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        root.addView(card, lp);
    }

    /**
     * 一组开关卡片：点标题行折叠/展开（状态持久化），组内行间细线分隔。
     * items 为 {显示名, 设置键} 数组。
     */
    private void addGroup(LinearLayout root, final String groupTitle, String[][] items) {
        addGroup(root, groupTitle, items, null);
    }

    /**
     * 一组开关卡片。extraRows 非空时，在开关行之后追加自定义行
     * （用于「导出位置」这类点开对话框而非开关的条目）。
     */
    private void addGroup(LinearLayout root, final String groupTitle, String[][] items,
                          ExtraRows extraRows) {
        final SharedPreferences ui = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        final String collapseKey = "collapsed_" + groupTitle;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedCard());

        // 标题行（可点，折叠/展开）
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(groupTitle);
        tvTitle.setTextSize(15);
        tvTitle.setTextColor(subTextColor());
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(tvTitle);

        final TextView arrow = new TextView(this);
        arrow.setTextSize(13);
        arrow.setTextColor(subTextColor());
        header.addView(arrow);
        card.addView(header);

        // 组内容器
        final LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length; i++) {
            final String label = items[i][0];
            final String key = items[i][1];
            final boolean last = (i == items.length - 1) && extraRows == null;
            addRow(container, label, key, last);
        }
        if (extraRows != null) {
            extraRows.add(container);
        }
        card.addView(container);

        final boolean collapsed = ui.getBoolean(collapseKey, false);
        applyCollapse(container, arrow, collapsed);

        // 折叠监听：只挂标题行（子控件也挂会导致一次点击多次回调、状态被翻回原样）
        header.setClickable(true);
        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 可见 → 本次要收起；已收起 → 本次要展开
                boolean nextCollapsed = container.getVisibility() == View.VISIBLE;
                applyCollapse(container, arrow, nextCollapsed);
                getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit()
                        .putBoolean(collapseKey, nextCollapsed).apply();
            }
        });

        addCardWithMargin(root, card);
    }

    /** 卡片内追加自定义行的回调 */
    private interface ExtraRows {
        void add(LinearLayout container);
    }

    /**
     * 「导出位置」行：右侧显示当前相对路径，点开对话框输入新位置。
     * 位置是相对公共存储的路径（如 Download/表盘导出），可多级、可中文；
     * 写入走 MediaStore Files 集合，免权限。
     */
    private void addExportLocationRow(LinearLayout container) {
        // 分隔线（与上一行分隔）
        View divider = new View(this);
        divider.setBackgroundColor(dividerColor());
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f)));
        dlp.leftMargin = dp(16);
        dlp.rightMargin = dp(16);
        container.addView(divider, dlp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackgroundColor(Color.TRANSPARENT);

        TextView tv = new TextView(this);
        tv.setText("导出位置");
        tv.setTextSize(15);
        tv.setTextColor(textColor());
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tv);

        final TextView value = new TextView(this);
        value.setTextSize(14);
        value.setTextColor(subTextColor());
        value.setText(currentExportPath());
        row.addView(value);
        exportPathView = value;

        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExportPathDialog();
            }
        });
        container.addView(row);
    }

    /** 位置行右侧的当前值（onServiceBind 后刷新） */
    private TextView exportPathView;

    /** 当前导出位置：未连框架前给默认值，连上后读 RemotePreferences */
    private String currentExportPath() {
        if (mService == null) {
            return "Download";
        }
        String v = mService.getRemotePreferences(Prefs.GROUP)
                .getString(Prefs.KEY_EXPORT_PATH, "Download");
        return (v == null || v.trim().isEmpty()) ? "Download" : v.trim();
    }

    /**
     * 导出位置对话框：一个输入框（相对路径）+ 确定/取消 + 恢复默认。
     * 改完位置后询问是否把已导出的表盘重新导一份到新位置（重置去重标记，
     * 通过 KEY_EXPORT_RESET_TOKEN 递增跨进程通知目标进程）。
     */
    private void showExportPathDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(currentExportPath());
        input.setSelection(input.getText().length());
        input.setHint("例如 Download/表盘导出");
        input.setSingleLine(true);

        int pad = dp(20);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, dp(8), pad, 0);
        box.addView(input, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("表盘导出位置")
                .setMessage("相对内部存储的路径，首级目录只能是 Download 或 Documents，"
                        + "可多级、可中文。\n"
                        + "例：Download/表盘导出、Documents/2026/表盘\n"
                        + "（免权限：走系统媒体库写入；其他首级目录系统不允许，会自动回退 Download）")
                .setView(box)
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String oldPath = currentExportPath();
                        String newPath = normalizeExportPath(input.getText().toString());
                        if (newPath.equals(oldPath)) {
                            return;
                        }
                        if (mService != null) {
                            mService.getRemotePreferences(Prefs.GROUP).edit()
                                    .putString(Prefs.KEY_EXPORT_PATH, newPath).apply();
                        }
                        if (exportPathView != null) {
                            exportPathView.setText(newPath);
                        }
                        askReExport(newPath);
                    }
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (mService != null) {
                            mService.getRemotePreferences(Prefs.GROUP).edit()
                                    .putString(Prefs.KEY_EXPORT_PATH, "Download").apply();
                        }
                        if (exportPathView != null) {
                            exportPathView.setText("Download");
                        }
                        askReExport("Download");
                    }
                })
                .show();
    }

    /** 换位置后询问是否重导：是 → 递增重导信号，目标进程下次扫描清标记重新导出 */
    private void askReExport(final String newPath) {
        new AlertDialog.Builder(this)
                .setTitle("重新导出？")
                .setMessage("已导出过的表盘默认不会重复导出。\n"
                        + "要把它们重新导一份到「" + newPath + "」吗？\n\n"
                        + "选「重新导出」后，下次打开运动健康“我的”页即开始。")
                .setPositiveButton("重新导出", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (mService != null) {
                            android.content.SharedPreferences sp =
                                    mService.getRemotePreferences(Prefs.GROUP);
                            int token = sp.getInt(Prefs.KEY_EXPORT_RESET_TOKEN, 0) + 1;
                            sp.edit().putInt(Prefs.KEY_EXPORT_RESET_TOKEN, token).apply();
                        }
                        android.widget.Toast.makeText(SettingsActivity.this,
                                "已安排重新导出，重启运动健康后打开“我的”页触发",
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("不用", null)
                .show();
    }

    /**
     * 与 hook 侧同规则地规范化路径：去首尾斜杠、归一斜杠、剔除非法字符与 . / ..，
     * 并校验首级目录必须在 Download / Documents 里（系统只允许这两个），否则回退 Download。
     */
    private String normalizeExportPath(String raw) {
        if (raw == null) {
            return "Download";
        }
        String v = raw.trim().replace("\\", "/");
        StringBuilder sb = new StringBuilder();
        for (String seg : v.split("/")) {
            String s = seg.trim();
            if (s.isEmpty() || s.equals(".") || s.equals("..")) {
                continue;
            }
            s = s.replaceAll("[\\\\:*?\"<>|\\x00-\\x1f]", "_");
            if (s.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(s);
        }
        String out = sb.toString();
        if (out.isEmpty()) {
            return "Download";
        }
        String root = out.split("/")[0];
        if (!"Download".equals(root) && !"Documents".equals(root)) {
            android.widget.Toast.makeText(this,
                    "首级目录只能是 Download 或 Documents，已回退为 Download",
                    android.widget.Toast.LENGTH_LONG).show();
            return "Download";
        }
        String[] parts = out.split("/");
        if (parts.length > 6) {
            StringBuilder trimmed = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                if (i > 0) {
                    trimmed.append('/');
                }
                trimmed.append(parts[i]);
            }
            out = trimmed.toString();
        }
        return out;
    }

    private void applyCollapse(LinearLayout container, TextView arrow, boolean collapsed) {
        container.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        arrow.setText(collapsed ? "展开 ▸" : "收起 ▾");
    }

    /** 单行开关：左标题右 Switch，末行不加分隔线 */
    private void addRow(LinearLayout container, String label, final String key, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(15);
        tv.setTextColor(textColor());
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tv);

        final Switch sw = new Switch(this);
        sw.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(sw);

        container.addView(row);
        if (!last) {
            View divider = new View(this);
            divider.setBackgroundColor(dividerColor());
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f)));
            dlp.leftMargin = dp(16);
            dlp.rightMargin = dp(16);
            container.addView(divider, dlp);
        }

        switches.put(key, sw);
        rows.put(key, row);

        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mService != null) {
                    mService.getRemotePreferences(Prefs.GROUP).edit()
                            .putBoolean(key, isChecked).apply();
                }
                // 隐藏桌面图标：开关开启 = 隐藏，关闭 = 显示（即时生效，无需重启）
                if (Prefs.KEY_HIDE_ICON.equals(key)) {
                    if (!mBindingDefaults) {
                        applyLauncherIcon(!isChecked);
                    }
                }
                // 表盘导出：用户手动打开时弹说明（初始化回填不弹）
                if (Prefs.KEY_ENABLE_FACE_EXPORT.equals(key) && isChecked && !mBindingDefaults) {
                    showFaceExportNotice();
                }
                // 总开关变化会带动其他开关置灰与摘要，统一刷新
                updateSummary();
            }
        });
    }

    /**
     * 刷新摘要与置灰：
     * - 摘要只数去广告类子开关；总开关关闭时显示已停用
     * - 置灰范围 = 去广告类 + 表盘导出（调试日志/隐藏图标保持可点）
     */
    private void updateSummary() {
        if (mSummary == null) {
            return;
        }
        if (!mBound) {
            mSummary.setText("正在读取设置…");
            return;
        }
        Switch master = switches.get(Prefs.KEY_ENABLE_ALL);
        boolean masterOn = master == null || master.isChecked();
        int on = 0;
        for (String k : AD_KEYS) {
            Switch s = switches.get(k);
            if (s != null && s.isChecked()) {
                on++;
            }
        }
        if (masterOn) {
            mSummary.setText("已启用 " + on + "/" + AD_KEYS.length + " 项广告清理");
        } else {
            mSummary.setText("已全部停用（总开关关闭）");
        }
        for (String k : DIMMED_KEYS) {
            Switch s = switches.get(k);
            View row = rows.get(k);
            if (s != null) {
                s.setEnabled(masterOn);
            }
            if (row != null) {
                row.setAlpha(masterOn ? 1f : 0.4f);
            }
        }
    }

    private String getVersionName() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "?";
        }
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
                mBindingDefaults = true;
                for (Map.Entry<String, Switch> e : switches.entrySet()) {
                    if (Prefs.KEY_HIDE_ICON.equals(e.getKey())) {
                        // 开关语义=「隐藏图标」：图标显示(TRUE)时开关应 OFF
                        e.getValue().setChecked(!isLauncherIconEnabled());
                    } else if (Prefs.KEY_ENABLE_FACE_EXPORT.equals(e.getKey())
                            || Prefs.KEY_ENABLE_DND_SYNC.equals(e.getKey())) {
                        // 实验/独立开关默认关闭：必须显式取 false，否则回填成 ON，
                        // 与 hook 侧（同样默认 false）不一致，界面会"骗人"
                        e.getValue().setChecked(sp.getBoolean(e.getKey(), false));
                    } else {
                        e.getValue().setChecked(sp.getBoolean(e.getKey(), true));
                    }
                }
                mBindingDefaults = false;
                mBound = true;
                updateSummary();
            }
        });
    }

    /**
     * 表盘自动导出说明：用户手动打开开关时弹窗一次。
     * 开关默认关闭（见 onServiceBind 回填），关闭后重开会再次提示。
     */
    private void showFaceExportNotice() {
        new AlertDialog.Builder(this)
                .setTitle("表盘自动导出（实验）")
                .setMessage("开启后，试用表盘下载完成后会自动导出：\n\n"
                        + "• 换新 ID（12→19 前缀，等长替换）\n"
                        + "• 以“中文名_新ID.bin”存到 Download/ 目录\n"
                        + "• 可用第三方软件（如 AstroBox）导入手环\n"
                        + "• 导出 ID 自动防删除保护，同步不再被清掉\n"
                        + "• 已导出缓存下次扫描自动清理\n"
                        + "• 每次扫描经 Toast/通知告知结果\n\n"
                        + "使用步骤：\n"
                        + "1. 此开关保持开启\n"
                        + "2. 在运动健康里试用喜欢的表盘，等下载完成\n"
                        + "3. 打开 App“我的”页，停留几秒触发扫描\n"
                        + "4. 去手机 Download/ 目录，按“中文名_新ID.bin”找文件\n"
                        + "5. 用 AstroBox 等第三方软件把文件导入手环\n\n"
                        + "注意：通知需给运动健康开通知权限，Toast 始终有效；"
                        + "已导出的不会重复导出。")
                .setPositiveButton("知道了", null)
                .show();
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
