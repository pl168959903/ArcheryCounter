package com.zgrcc.archeryassistant

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
        item { Text(tr("校準錄製", "Calibration recording"), style = MaterialTheme.typography.title3) }

        when {
            running && !active -> {
                item { Body(tr("目前正在一般偵測中。請先回主畫面「暫停偵測」，再回來做校準", "Normal detection is running. Pause it on the main screen first, then come back to calibrate")) }
            }

            !active -> {
                if (saved) {
                    item { Body(tr("校準記錄已儲存。它會在「記錄」裡標為「校準」，不計入箭數和趨勢", "Calibration saved. It shows as 'Calibration' in Records and is not counted in arrows or trends")) }
                    item { Note(tr("用 adb pull 把 sessions 資料夾取出，就能拿去分析", "Pull the sessions folder with adb to analyze it")) }
                }
                item {
                    Body(
                        tr("會依序錄 ${steps.size} 段動作：", "Records ${steps.size} movements in order: ") +
                            steps.joinToString(tr("、", ", ")) { it.title() } +
                            tr("。每一段按「開始」和「結束」。錄製期間會自動錄原始資料", ". Press Start and End for each one. Raw data is recorded automatically")
                    )
                }
                item {
                    Chip(
                        onClick = {
                            saved = false
                            vm.startCalibration()
                        },
                        label = { Text(tr("開始校準", "Start calibration"), maxLines = 1) },
                        colors = ChipDefaults.primaryChipColors(),
                        enabled = vm.sensorAvailable,
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item { Note(tr("原始資料每 30 分鐘約 10 MB 以上，三段加起來通常幾分鐘就夠", "Raw data is 10 MB or more per 30 min; the three movements usually take only a few minutes")) }
            }

            step < steps.size -> {
                item { SectionTitle(tr("第 ${step + 1} / ${steps.size} 段", "Step ${step + 1} / ${steps.size}")) }
                item { Body(steps[step].title()) }
                item { Note(steps[step].hintText()) }
                item {
                    if (!marking) {
                        Chip(
                            onClick = { vm.calStartSegment() },
                            label = { Text(tr("開始這一段", "Start this step"), maxLines = 1) },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    } else {
                        Chip(
                            onClick = { vm.calEndSegment() },
                            label = { Text(tr("結束這一段", "End this step"), maxLines = 1) },
                            secondaryLabel = { Text(tr("錄製中", "Recording"), maxLines = 1) },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                }
                item { Note(tr("校準中放箭仍會被記錄，但不會增加主畫面的箭數", "Releases are still recorded during calibration but do not add to the main count")) }
            }

            else -> {
                item { Body(tr("三段都錄完了", "All three steps recorded")) }
            }
        }

        if (active) {
            item {
                Chip(
                    onClick = {
                        vm.stop()
                        saved = true
                    },
                    label = { Text(if (step >= steps.size) tr("完成並儲存", "Finish and save") else tr("中止並儲存", "Stop and save"), maxLines = 1) },
                    colors = if (step >= steps.size) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
    }
}
