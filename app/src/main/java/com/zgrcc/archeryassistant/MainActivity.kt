package com.zgrcc.archeryassistant

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

class MainActivity : ComponentActivity() {
    /** 磁貼帶來的啟動動作（見 [TileInfo]）；處理完設回 null */
    private var launchAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 只在全新啟動時認磁貼參數，轉螢幕、被系統重建時不要再觸發一次
        if (savedInstanceState == null) takeLaunch(intent)
        setContent { ArcheryApp(launch = launchAction, onLaunchHandled = { launchAction = null }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeLaunch(intent)
    }

    private fun takeLaunch(intent: Intent?) {
        val value = TileInfo.parse(intent?.getStringExtra(TileInfo.EXTRA_LAUNCH))
        intent?.removeExtra(TileInfo.EXTRA_LAUNCH)
        if (value != null) launchAction = value
    }
}

private const val ROUTE_MAIN = "main"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_DETAIL = "detail"
private const val ROUTE_SHOT = "shot"
private const val ROUTE_TREND = "trend"
private const val ROUTE_CALIBRATION = "calibration"
private const val ROUTE_COMPETITION = "competition"
private const val ROUTE_MATCH_COMPARE = "match_compare"
private const val ROUTE_QUICK_CHECK = "quick_check"
private const val ROUTE_GROUPS = "groups"
private const val ROUTE_OVERVIEW = "overview"
private const val ROUTE_REPORT = "report_view"
private const val ROUTE_DISC = "disc"

@Composable
fun ArcheryApp(vm: CounterViewModel = viewModel(), launch: String? = null, onLaunchHandled: () -> Unit = {}) {
    // 只要應用開著就保持亮屏（可在設定頁關閉），不論有沒有在偵測
    val keepScreenOnSetting by vm.settings.keepScreenOn.collectAsStateWithLifecycle()
    val compState by vm.comp.collectAsStateWithLifecycle()
    val runningNow by vm.running.collectAsStateWithLifecycle()
    // 比賽進行中一律保持亮屏，不管設定
    val keepScreenOn = keepScreenOnSetting || (runningNow && compState != null)
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // 介面語言：設定變動時整個畫面重新組合（見 I18n）
    val language by vm.settings.language.collectAsStateWithLifecycle()
    val english = I18n.resolveEnglish(language, java.util.Locale.getDefault().language)
    I18n.english = english

    // 偵測中降低亮度（設定頁可關）：只在偵測中且開啟時覆寫視窗亮度，其他時候交還給系統
    val running by vm.running.collectAsStateWithLifecycle()
    val dim by vm.settings.dimWhenRunning.collectAsStateWithLifecycle()
    val dimLevel by vm.settings.dimLevel.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    DisposableEffect(running, dim, dimLevel) {
        val window = activity?.window
        if (window != null && running && dim) {
            val lp = window.attributes
            lp.screenBrightness = Brightness.level(dimLevel)
            window.attributes = lp
        }
        onDispose {
            window?.let {
                val lp = it.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.attributes = lp
            }
        }
    }

    // 啟動動畫：只在剛開啟應用時播放一次（設定頁可關）。轉場、旋轉不會重播
    val splashOn = remember { vm.settings.splash.value }
    val splashTap = remember { vm.settings.splashTap.value }
    var showSplash by rememberSaveable { mutableStateOf(splashOn) }

    val chipKinds by vm.settings.chipKinds.collectAsStateWithLifecycle()

    key(english) {
      CompositionLocalProvider(LocalChipKinds provides chipKinds) {
        MaterialTheme {
            if (showSplash) {
                SplashScreen(
                    versionLabel = vm.versionLabel,
                    signature = vm.signatureShort,
                    waitForTap = splashTap,
                    onDone = { showSplash = false },
                )
            } else {
                AppNav(vm, launch, onLaunchHandled)
            }
        }
      }
    }
}

@Composable
private fun AppNav(vm: CounterViewModel, launch: String?, onLaunchHandled: () -> Unit) {
    val nav = rememberSwipeDismissableNavController()

    // 從磁貼進來：依「磁貼快捷啟動」設定直接開始偵測或比賽；沒在偵測才會動作，處理完就清掉
    LaunchedEffect(launch) {
        if (launch != null) {
            if (vm.settings.tileLaunch.value && !vm.running.value) {
                when (launch) {
                    TileInfo.LAUNCH_DETECT -> vm.start()
                    TileInfo.LAUNCH_MATCH -> {
                        vm.startCompetition()
                        if (vm.running.value) nav.navigate(ROUTE_COMPETITION)
                    }
                }
            }
            onLaunchHandled()
        }
    }
    SwipeDismissableNavHost(navController = nav, startDestination = ROUTE_MAIN) {
        composable(ROUTE_MAIN) {
            MainScreen(
                vm = vm,
                onOpenHistory = { nav.navigate(ROUTE_HISTORY) },
                onOpenSettings = { nav.navigate(ROUTE_SETTINGS) },
                onOpenCompetition = { nav.navigate(ROUTE_COMPETITION) },
                onOpenDisc = { nav.navigate(ROUTE_DISC) },
            )
        }
        composable(ROUTE_DISC) {
            val gyro by vm.settings.traceGyro.collectAsStateWithLifecycle()
            DiscFullScreen(vm = vm, gyro = gyro, onBack = { nav.popBackStack() })
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                vm = vm,
                category = -1,
                onOpenCategory = { nav.navigate(SettingsCategories.route(it)) },
                onOpenCalibration = { nav.navigate(ROUTE_CALIBRATION) },
            )
        }
        // 設定的各個分類頁（settings_0 … settings_8）
        for (c in 0 until SettingsCategories.COUNT) {
            composable(SettingsCategories.route(c)) {
                SettingsScreen(
                    vm = vm,
                    category = c,
                    onOpenCategory = {},
                    onOpenCalibration = { nav.navigate(ROUTE_CALIBRATION) },
                    onOpenReport = { nav.navigate(ROUTE_REPORT) },
                )
            }
        }
        composable(ROUTE_HISTORY) {
            HistoryScreen(
                vm = vm,
                onOpen = { id ->
                    vm.selectSession(id)
                    nav.navigate(ROUTE_DETAIL)
                },
                onOpenTrend = { nav.navigate(ROUTE_TREND) },
                onOpenMatchCompare = { nav.navigate(ROUTE_MATCH_COMPARE) },
                onOpenGroups = { nav.navigate(ROUTE_GROUPS) },
                onOpenOverview = { nav.navigate(ROUTE_OVERVIEW) },
            )
        }
        composable(ROUTE_DETAIL) {
            DetailScreen(
                vm = vm,
                onOpenShot = { index ->
                    vm.selectShot(index)
                    nav.navigate(ROUTE_SHOT)
                },
                onDeleted = { nav.popBackStack() },
                onOpenQuickCheck = { nav.navigate(ROUTE_QUICK_CHECK) },
            )
        }
        composable(ROUTE_SHOT) {
            ShotScreen(vm = vm)
        }
        composable(ROUTE_GROUPS) { GroupCompareScreen(vm) }
        composable(ROUTE_OVERVIEW) { OverviewScreen(vm) }
        composable(ROUTE_REPORT) { ReportScreen(vm) }
        composable(ROUTE_MATCH_COMPARE) {
            MatchCompareScreen(
                vm = vm,
                onOpen = { id ->
                    vm.selectSession(id)
                    nav.navigate(ROUTE_DETAIL)
                },
            )
        }
        composable(ROUTE_QUICK_CHECK) {
            QuickCheckScreen(vm = vm)
        }
        composable(ROUTE_TREND) {
            TrendScreen(vm = vm)
        }
        composable(ROUTE_COMPETITION) {
            CompetitionScreen(
                vm = vm,
                onOpenRecord = { id ->
                    vm.selectSession(id)
                    nav.navigate(ROUTE_DETAIL)
                },
            )
        }
        composable(ROUTE_CALIBRATION) {
            CalibrationScreen(vm = vm)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
