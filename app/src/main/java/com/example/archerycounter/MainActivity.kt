package com.example.archerycounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArcheryCounterScreen() }
    }
}

@Composable
fun ArcheryCounterScreen(vm: CounterViewModel = viewModel()) {
    val count by vm.count.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val lastPeak by vm.lastPeak.collectAsStateWithLifecycle()

    // 侦测期间保持亮屏，否则手腕放下后屏幕熄灭就会停止工作
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    MaterialTheme {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text("箭数", style = MaterialTheme.typography.caption1)
            }

            // 计数，左右两侧是手动 -1 / +1，用来修正误判或漏判
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SmallRoundButton("−") { vm.adjustCount(-1) }
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.display1,
                        textAlign = TextAlign.Center,
                    )
                    SmallRoundButton("+") { vm.adjustCount(1) }
                }
            }

            item {
                if (!vm.sensorAvailable) {
                    Text("此手表没有加速度传感器", textAlign = TextAlign.Center)
                } else {
                    Chip(
                        onClick = { if (running) vm.stop() else vm.start() },
                        label = { Text(if (running) "暂停侦测" else "开始侦测", maxLines = 1) },
                        colors = if (running) ChipDefaults.secondaryChipColors()
                        else ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.85f),
                    )
                }
            }

            // 清零只在暂停时出现，避免射箭时误触
            if (!running) {
                item {
                    Chip(
                        onClick = { vm.resetCount() },
                        label = { Text("清零", maxLines = 1) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.85f),
                    )
                }
            }

            // 阈值：数值越大越不灵敏
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SmallRoundButton("−") { vm.adjustThreshold(-1f) }
                    Text("阈值 ${threshold.toInt()}")
                    SmallRoundButton("+") { vm.adjustThreshold(1f) }
                }
            }
            item {
                Text(
                    text = "越大越不灵敏",
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                )
            }

            item {
                val peakText = lastPeak?.let { "上次峰值 %.1f".format(it) } ?: "尚未侦测到放箭"
                Text(
                    text = peakText,
                    style = MaterialTheme.typography.caption1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SmallRoundButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        colors = ButtonDefaults.secondaryButtonColors(),
    ) {
        Text(label)
    }
}
