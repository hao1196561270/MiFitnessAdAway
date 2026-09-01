package io.github.hao1196561270.mifitnessadaway

import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Miuix 设置页：展示模块开关，并通过 XposedService 读写 RemotePreferences。
 */
class SettingsActivity : ComponentActivity(), XposedServiceHelper.OnServiceListener {

    private companion object {
        private const val RESTART_NOTICE_DURATION_SECONDS = 3L
    }

    private var service: XposedService? = null
    private val values: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    private var restartNoticeJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val transparentBarStyle = if (darkMode) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(
            statusBarStyle = transparentBarStyle,
            navigationBarStyle = transparentBarStyle,
        )
        setContent {
            val controller = remember {
                ThemeController(ColorSchemeMode.System)
            }
            MiuixTheme(controller = controller) {
                SettingsScreen(
                    versionName = getVersionName(),
                    values = values,
                )
            }
        }
    }

    @Composable
    private fun SettingsScreen(
        versionName: String,
        values: SnapshotStateMap<String, Boolean>,
    ) {
        val scrollBehavior = MiuixScrollBehavior()
        val snackbarHostState = remember { SnackbarHostState() }
        val snackbarScope = rememberCoroutineScope()
        Scaffold(
            topBar = {
                TopAppBar(
                    title = "MiFitnessAdAway",
                    subtitle = "版本：$versionName",
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = {
                SnackbarHost(state = snackbarHostState)
            },
        ) { paddingValues ->
            val groups = remember { SettingsItems.groupedEntries() }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.displayCutout
                            .union(WindowInsets.navigationBars)
                            .only(WindowInsetsSides.Horizontal),
                    )
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
            ) {
                groups.forEach { (group, entries) ->
                    item(key = "title:$group", contentType = "group-title") {
                        SmallTitle(
                            text = group,
                        )
                    }
                    groupedCardItems(
                        keyPrefix = group,
                        entries = entries,
                        outerBottomPadding = 12.dp,
                    ) { entry ->
                        SwitchPreference(
                            title = entry.title,
                            checked = values[entry.key] ?: true,
                            onCheckedChange = { checked ->
                                updatePreference(entry, checked)
                                restartNoticeJob?.cancel()
                                restartNoticeJob = snackbarScope.launch {
                                    snackbarHostState.newestSnackbarData()?.dismiss()
                                    snackbarHostState.showSnackbar(
                                        message = "设置已保存，重启运动健康后生效",
                                        duration = SnackbarDuration.Custom(
                                            RESTART_NOTICE_DURATION_SECONDS * 1_000L,
                                        ),
                                    )
                                }
                            },
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }

                item {
                    Spacer(
                        modifier = Modifier
                            .height(24.dp)
                            .navigationBarsPadding(),
                    )
                }
            }
        }
    }

    private fun updatePreference(entry: SettingsItems.Entry, checked: Boolean) {
        values[entry.key] = checked
        service?.getRemotePreferences(Prefs.GROUP)?.edit {
            putBoolean(entry.key, checked)
        }
        if (entry.key == Prefs.KEY_HIDE_ICON) {
            applyLauncherIcon(visible = !checked)
        }
    }

    private fun getVersionName(): String = try {
        val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
        packageInfo.versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    private fun applyLauncherIcon(visible: Boolean) {
        try {
            val alias = ComponentName(this, "$packageName.LauncherAlias")
            packageManager.setComponentEnabledSetting(
                alias,
                if (visible) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP,
            )
        } catch (error: Throwable) {
            Toast.makeText(this, "隐藏桌面图标失败: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        (application as MiFitnessApp).addServiceStateListener(this, true)
    }

    override fun onStop() {
        (application as MiFitnessApp).removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceBind(boundService: XposedService) {
        service = boundService
        runOnUiThread {
            val preferences: SharedPreferences = boundService.getRemotePreferences(Prefs.GROUP)
            SettingsItems.entries().forEach { entry ->
                values[entry.key] = if (entry.key == Prefs.KEY_HIDE_ICON) {
                    !isLauncherIconEnabled()
                } else {
                    preferences.getBoolean(entry.key, true)
                }
            }
        }
    }

    override fun onServiceDied(deadService: XposedService) {
        if (service === deadService) service = null
    }

    private fun isLauncherIconEnabled(): Boolean = try {
        val alias = ComponentName(this, "$packageName.LauncherAlias")
        val state = packageManager.getComponentEnabledSetting(alias)
        state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
            state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
    } catch (_: Throwable) {
        true
    }
}

/**
 * 将分组中的每一行拆成独立 Lazy item，同时保持一张连续卡片的视觉效果。
 */
private fun LazyListScope.groupedCardItems(
    keyPrefix: String,
    entries: List<SettingsItems.Entry>,
    outerBottomPadding: Dp,
    content: @Composable (SettingsItems.Entry) -> Unit,
) {
    entries.forEachIndexed { index, entry ->
        val isFirst = index == 0
        val isLast = index == entries.lastIndex
        item(
            key = "$keyPrefix:${entry.key}",
            contentType = "setting-card-segment",
        ) {
            CardSegment(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = if (isLast) outerBottomPadding else 0.dp),
                isFirst = isFirst,
                isLast = isLast,
            ) {
                content(entry)
            }
        }
    }
}

/**
 * 卡片分段：首末段负责圆角，中间段使用直角以拼接成完整卡片。
 */
@Composable
private fun CardSegment(
    modifier: Modifier,
    isFirst: Boolean,
    isLast: Boolean,
    content: @Composable () -> Unit,
) {
    val cornerRadius = 16.dp
    val surfaceColor = MiuixTheme.colorScheme.surfaceContainer
    val surfaceModifier = if (isFirst || isLast) {
        modifier.squircleSurface(
            color = surfaceColor,
            topStart = if (isFirst) cornerRadius else 0.dp,
            topEnd = if (isFirst) cornerRadius else 0.dp,
            bottomEnd = if (isLast) cornerRadius else 0.dp,
            bottomStart = if (isLast) cornerRadius else 0.dp,
        )
    } else {
        modifier.background(surfaceColor)
    }
    CompositionLocalProvider(
        LocalContentColor provides MiuixTheme.colorScheme.onSurfaceContainer,
    ) {
        Box(modifier = surfaceModifier) {
            content()
        }
    }
}
