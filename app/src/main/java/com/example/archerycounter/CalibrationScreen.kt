package com.example.archerycounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * 校準錄製：引導依序錄「只拉弓不放箭」「正常射擊」「取箭走動」三段。
 * 偵測照常運作、放箭也會記錄，但不計入主畫面箭數；每一段的起止會標注在記錄裡，並強制錄原始資料。
 */
@Composable
fun CalibrationScreen(vm: CounterViewModel) {
    val running by vm.running.collectAsStateWithLifecycle()
    val active by vm.calibrationActive.collectAsStateWithLifecycle()
    val step by vm.calStep.collectAsStateWithLifecycle()
    val marking by vm.calMarking.collectAsStateWithLifecycle()
    var saved by remember { mutableStateOf(false) }

    val steps = Calibration.STEPS

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text("校準錄製", style = MaterialTheme.typography.title3) }

        when {
            running && !active -> {
                item { Body("目前正在一般偵測中。請先回主畫面「暫停偵測」，再回來做校準") }
            }

            !active -> {
                if (saved) {
                    item { Body("校準記錄已儲存。它會在「記錄」裡標為「校準」，不計入箭數和趨勢") }
                    item { Note("用 adb pull 把 sessions 資料夾取出，交給 Claude 分析") }
                }
                item {
                    Body(
                        "會依序錄 ${steps.size} 段動作：" +
                            steps.joinToString("、") { it.label } +
                            "。每一段按「開始」和「結束」。錄製期間會自動錄原始資料"
                    )
                }
                item {
                    Chip(
                        onClick = {
                            saved = false
                            vm.startCalibration()
                        },
                        label = { Text("開始校準", maxLines = 1) },
                        colors = ChipDefaults.primaryChipColors(),
                        enabled = vm.sensorAvailable,
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item { Note("原始資料每 30 分鐘約 10 MB 以上，三段加起來通常幾分鐘就夠") }
            }

            step < steps.size -> {
                item { SectionTitle("第 ${step + 1} / ${steps.size} 段") }
                item { Body(steps[step].label) }
                item { Note(steps[step].hint) }
                item {
                    if (!marking) {
                        Chip(
                            onClick = { vm.calStartSegment() },
                            label = { Text("開始這一段", maxLines = 1) },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    } else {
                        Chip(
                            onClick = { vm.calEndSegment() },
                            label = { Text("結束這一段", maxLines = 1) },
                            secondaryLabel = { Text("錄製中", maxLines = 1) },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                }
                item { Note("校準中放箭仍會被記錄，但不會增加主畫面的箭數") }
            }

            else -> {
                item { Body("三段都錄完了") }
            }
        }

        if (active) {
            item {
                Chip(
                    onClick = {
                        vm.stop()
                        saved = true
                    },
                    label = { Text(if (step >= steps.size) "完成並儲存" else "中止並儲存", maxLines = 1) },
                    colors = if (step >= steps.size) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
    }
}
