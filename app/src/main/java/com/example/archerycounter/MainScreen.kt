package com.example.archerycounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

@Composable
fun MainScreen(
    vm: CounterViewModel,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val count by vm.count.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val lastShot by vm.lastShot.collectAsStateWithLifecycle()
    val liveOn by vm.settings.liveWaveform.collectAsStateWithLifecycle()
    val lockEnabled by vm.settings.touchLock.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val calibrating by vm.calibrationActive.collectAsStateWithLifecycle()
    val today by vm.todayCount.collectAsStateWithLifecycle()
    val goal by vm.settings.dailyGoal.collectAsStateWithLifecycle()
    val feedback by vm.endFeedback.collectAsStateWithLifecycle()
    val batteryLow by vm.batteryLow.collectAsStateWithLifecycle()
    val batteryPercent by vm.batteryPercent.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()

    // 鎖定時只顯示箭數；長按約 2 秒才解鎖。其他按鈕全部不在畫面上，所以不會被誤觸
    if (locked && running) {
        LockedScreen(count = count, onUnlock = { vm.unlockTouch() })
        return
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text("箭數", style = MaterialTheme.typography.caption1)
        }

        // 計數，左右兩側是手動 -1 / +1，用來修正誤判或漏判
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

        // 今日目標：今天所有記錄的箭數合計（含進行中的這一次，不含誤判與校準）
        if (goal > 0) {
            item { Body(if (today >= goal) "今日 $today / $goal　已達標" else "今日 $today / $goal") }
        }

        // 剛射完一趟的回饋，約 5 秒後消失
        feedback?.let { fb ->
            item {
                Body(
                    "第 ${fb.endNumber} 趟　平均晃動 ${Fmt.num(fb.accelAvg)} m/s²\n" +
                        "比上一趟　${describeChange(fb.vsPrevPct)}\n" +
                        "比本次平均　${describeChange(fb.vsSessionPct)}"
                )
            }
        }

        notice?.let { msg -> item { Note(msg) } }

        if (running && batteryLow) {
            item { Note(if (batteryPercent >= 0) "電量低（$batteryPercent%），請盡快充電" else "電量低，請盡快充電") }
        }

        item {
            if (!vm.sensorAvailable) {
                Text("此手錶沒有加速度感測器", textAlign = TextAlign.Center)
            } else {
                Chip(
                    onClick = { if (running) vm.stop() else vm.start() },
                    label = { Text(if (running) "暫停偵測" else "開始偵測", maxLines = 1) },
                    colors = if (running) ChipDefaults.secondaryChipColors()
                    else ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.85f),
                )
            }
        }

        if (calibrating) {
            item { Note("校準錄製中：放箭只記錄，不計入箭數。請到 設定 → 校準錄製 操作") }
        }

        // 觸控鎖：只在偵測中、且設定頁開啟時出現
        if (running && lockEnabled) {
            item {
                Chip(
                    onClick = { vm.lockTouch() },
                    label = { Text("鎖定觸控", maxLines = 1) },
                    secondaryLabel = { Text("長按 2 秒解鎖", maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.85f),
                )
            }
        }

        // 即時波形：只在偵測中、且設定頁開啟時才畫
        if (running && liveOn) {
            item { LiveWaveform(vm, threshold)}
        }

        // 歸零只在暫停時出現，避免射箭時誤觸
        if (!running) {
            item {
                Chip(
                    onClick = { vm.resetCount() },
                    label = { Text("歸零", maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.85f),
                )
            }
        }

        // 閾值：數值越大越不靈敏
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallRoundButton("−") { vm.adjustThreshold(-1f) }
                Text("閾值 ${threshold.toInt()}")
                SmallRoundButton("+") { vm.adjustThreshold(1f) }
            }
        }
        item { Note("越大越不靈敏") }

        // 最近一箭：峰值與放箭前的晃動量
        item {
            val shot = lastShot
            if (shot == null) {
                Body("尚未偵測到放箭")
            } else {
                Body(
                    buildString {
                        append("上次峰值 ").append(Fmt.num(shot.peak, 1))
                        shot.accelRms?.let {
                            append("\n加速度晃動 ").append(Fmt.num(it)).append(" m/s²")
                        }
                        shot.gyroRms?.let {
                            append("\n角速度晃動 ").append(Fmt.num(it, 1)).append(" °/s")
                        }
                    }
                )
            }
        }

        item {
            Chip(
                onClick = onOpenHistory,
                label = { Text("記錄", maxLines = 1) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.85f),
            )
        }
        item {
            Chip(
                onClick = onOpenSettings,
                label = { Text("設定", maxLines = 1) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.85f),
            )
        }

        // 版本與作者
        item { Note("${vm.versionLabel}　作者 ${AppInfo.AUTHOR}") }
    }
}

/**
 * 觸控鎖畫面：只顯示箭數。長按螢幕約 2 秒才解鎖，手掌或手臂短暫碰到不會有任何反應。
 * 這只擋住螢幕觸碰，擋不住手錶的實體按鍵。
 */
@Composable
private fun LockedScreen(count: Int, onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    // 2 秒內放開（或被取消）就什麼都不做；一直按著超過 2 秒才解鎖
                    val up = withTimeoutOrNull(UNLOCK_HOLD_MS) { waitForUpOrCancellation() }
                    if (up == null) onUnlock()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("箭數", style = MaterialTheme.typography.caption1)
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.display1,
                textAlign = TextAlign.Center,
            )
            Note("已鎖定　長按螢幕約 2 秒解鎖")
        }
    }
}

private const val UNLOCK_HOLD_MS = 2000L
