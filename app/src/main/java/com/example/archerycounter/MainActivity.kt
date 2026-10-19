package com.example.archerycounter

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArcheryApp() }
    }
}

private const val ROUTE_MAIN = "main"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_DETAIL = "detail"
private const val ROUTE_SHOT = "shot"
private const val ROUTE_TREND = "trend"
private const val ROUTE_CALIBRATION = "calibration"

@Composable
fun ArcheryApp(vm: CounterViewModel = viewModel()) {
    // 只要應用開著就保持亮屏（可在設定頁關閉），不論有沒有在偵測
    val keepScreenOn by vm.settings.keepScreenOn.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

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

    MaterialTheme {
        val nav = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(navController = nav, startDestination = ROUTE_MAIN) {
            composable(ROUTE_MAIN) {
                MainScreen(
                    vm = vm,
                    onOpenHistory = { nav.navigate(ROUTE_HISTORY) },
                    onOpenSettings = { nav.navigate(ROUTE_SETTINGS) },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(vm = vm, onOpenCalibration = { nav.navigate(ROUTE_CALIBRATION) })
            }
            composable(ROUTE_HISTORY) {
                HistoryScreen(
                    vm = vm,
                    onOpen = { id ->
                        vm.selectSession(id)
                        nav.navigate(ROUTE_DETAIL)
                    },
                    onOpenTrend = { nav.navigate(ROUTE_TREND) },
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
                )
            }
            composable(ROUTE_SHOT) {
                ShotScreen(vm = vm)
            }
            composable(ROUTE_TREND) {
                TrendScreen(vm = vm)
            }
            composable(ROUTE_CALIBRATION) {
                CalibrationScreen(vm = vm)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
