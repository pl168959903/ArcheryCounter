package com.example.archerycounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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

@Composable
fun ArcheryApp(vm: CounterViewModel = viewModel()) {
    // 只要應用開著就保持亮屏（可在設定頁關閉），不論有沒有在偵測
    val keepScreenOn by vm.settings.keepScreenOn.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
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
                SettingsScreen(vm = vm)
            }
            composable(ROUTE_HISTORY) {
                HistoryScreen(
                    vm = vm,
                    onOpen = { id ->
                        vm.selectSession(id)
                        nav.navigate(ROUTE_DETAIL)
                    },
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
        }
    }
}
