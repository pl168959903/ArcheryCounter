package com.zgrcc.archeryassistant

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
    onOpenCompetition: () -> Unit,
    onOpenDisc: () -> Unit,
) {
    val count by vm.count.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val lastShot by vm.lastShot.collectAsStateWithLifecycle()
    val liveOn by vm.settings.liveWaveform.collectAsStateWithLifecycle()
    val live2dOn by vm.settings.live2d.collectAsStateWithLifecycle()
    val traceGyro by vm.settings.traceGyro.collectAsStateWithLifecycle()
    val discSize by vm.settings.discSize.collectAsStateWithLifecycle()
    val discFullscreen by vm.settings.discFullscreen.collectAsStateWithLifecycle()
    val lockEnabled by vm.settings.touchLock.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val calibrating by vm.calibrationActive.collectAsStateWithLifecycle()
    val today by vm.todayCount.collectAsStateWithLifecycle()
    val goal by vm.settings.dailyGoal.collectAsStateWithLifecycle()
    val feedback by vm.endFeedback.collectAsStateWithLifecycle()
    val batteryLow by vm.batteryLow.collectAsStateWithLifecycle()
    val batteryPercent by vm.batteryPercent.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val comp by vm.comp.collectAsStateWithLifecycle()
    val compEnabled by vm.settings.compEnabled.collectAsStateWithLifecycle()
    val fatiguePct by vm.fatiguePct.collectAsStateWithLifecycle()
    val measuredHz by vm.measuredHz.collectAsStateWithLifecycle()

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
            Text(tr("箭數", "Arrows"), style = MaterialTheme.typography.caption1)
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
            item { Body(if (today >= goal) tr("今日 $today / $goal　已達標", "Today $today / $goal  goal reached") else tr("今日 $today / $goal", "Today $today / $goal")) }
        }

        // 剛射完一趟的回饋，約 5 秒後消失
        feedback?.let { fb ->
            item {
                Body(
                    tr("第 ${fb.endNumber} 趟　平均晃動 ${Fmt.num(fb.accelAvg)} m/s²\n", "End ${fb.endNumber}  avg shake ${Fmt.num(fb.accelAvg)} m/s²\n") +
                        tr("比上一趟　${describeChange(fb.vsPrevPct)}\n", "vs previous end  ${describeChange(fb.vsPrevPct)}\n") +
                        tr("比本次平均　${describeChange(fb.vsSessionPct)}", "vs session avg  ${describeChange(fb.vsSessionPct)}")
                )
            }
        }

        notice?.let { msg -> item { Note(msg) } }

        // 疲勞提醒：本次已觸發時一直顯示到停止偵測
        if (running) {
            fatiguePct?.let { pct ->
                item {
                    Note(
                        tr(
                            "疲勞提醒：最近兩趟比剛開始抖了 ${pct.toInt()}%，建議休息或收工",
                            "Fatigue: the last two ends are ${pct.toInt()}% shakier than the first two; consider a rest or stopping",
                        )
                    )
                }
            }
        }

        if (running && batteryLow) {
            item { Note(if (batteryPercent >= 0) tr("電量低（$batteryPercent%），請盡快充電", "Battery low ($batteryPercent%); please charge soon") else tr("電量低，請盡快充電", "Battery low; please charge soon")) }
        }

        item {
            if (!vm.sensorAvailable) {
                Text(tr("此手錶沒有加速度感測器", "This watch has no accelerometer"), textAlign = TextAlign.Center)
            } else {
                Chip(
                    onClick = { if (running) vm.stop() else vm.start() },
                    label = { Text(if (running) tr("暫停偵測", "Pause detection") else tr("開始偵測", "Start detection"), maxLines = 1) },
                    colors = if (running) ChipDefaults.secondaryChipColors()
                    else kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.85f),
                )
            }
        }

        // 比賽模式：沒在偵測時開始比賽；比賽進行中回到比賽畫面
        if (!calibrating && vm.sensorAvailable) {
            if (running && comp != null) {
                item {
                    Chip(
                        onClick = onOpenCompetition,
                        label = { Text(tr("回到比賽畫面", "Back to match"), maxLines = 1) },
                        colors = kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.85f),
                    )
                }
            } else if (!running && compEnabled) {
                item {
                    Chip(
                        onClick = {
                            vm.startCompetition()
                            onOpenCompetition()
                        },
                        label = { Text(tr("比賽模式", "Match mode"), maxLines = 1) },
                        secondaryLabel = { Text(tr("倒數計時・逐趟記錄", "Countdown & per-end log"), maxLines = 1) },
                        colors = kindColors(ChipKind.GO, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.85f),
                    )
                }
            }
        }

        if (calibrating) {
            item { Note(tr("校準錄製中：放箭只記錄，不計入箭數。請到 設定 → 校準錄製 操作", "Calibrating: releases are recorded but not counted. Go to Settings → Calibration recording")) }
        }

        // 觸控鎖：只在偵測中、且設定頁開啟時出現
        if (running && lockEnabled) {
            item {
                Chip(
                    onClick = { vm.lockTouch() },
                    label = { Text(tr("鎖定觸控", "Lock touch"), maxLines = 1) },
                    secondaryLabel = { Text(tr("長按 2 秒解鎖", "Hold 2 s to unlock"), maxLines = 1) },
                    colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.85f),
                )
            }
        }

        // 即時晃動圓盤（開啟時取代即時波形），或即時波形：只在偵測中、且設定頁開啟時才畫
        if (running && live2dOn) {
            item { LiveDisc(vm, gyro = traceGyro, sizeDp = DiscSize.dp(discSize), onClick = if (discFullscreen) onOpenDisc else null) }
        } else if (running && liveOn) {
            item { LiveWaveform(vm, threshold)}
        }

        // 歸零只在暫停時出現，避免射箭時誤觸
        if (!running) {
            item {
                Chip(
                    onClick = { vm.resetCount() },
                    label = { Text(tr("歸零", "Reset"), maxLines = 1) },
                    colors = kindColors(ChipKind.DANGER, ChipDefaults.secondaryChipColors()),
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
                Text(tr("閾值 ${threshold.toInt()}", "Threshold ${threshold.toInt()}"))
                SmallRoundButton("+") { vm.adjustThreshold(1f) }
            }
        }
        item { Note(tr("越大越不靈敏", "Higher = less sensitive")) }

        // 最近一箭：峰值與放箭前的晃動量
        item {
            val shot = lastShot
            if (shot == null) {
                Body(tr("尚未偵測到放箭", "No release detected yet"))
            } else {
                Body(
                    buildString {
                        append(tr("上次峰值 ", "Last peak ")).append(Fmt.num(shot.peak, 1))
                        shot.accelRms?.let {
                            append(tr("\n加速度晃動 ", "\nAccel shake ")).append(Fmt.num(it)).append(" m/s²")
                        }
                        shot.gyroRms?.let {
                            append(tr("\n角速度晃動 ", "\nGyro shake ")).append(Fmt.num(it, 1)).append(" °/s")
                        }
                    }
                )
            }
        }

        item {
            Chip(
                onClick = onOpenHistory,
                label = { Text(tr("記錄", "Records") + navMark(), maxLines = 1) },
                colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.85f),
            )
        }
        item {
            Chip(
                onClick = onOpenSettings,
                label = { Text(tr("設定", "Settings") + navMark(), maxLines = 1) },
                colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.85f),
            )
        }

        // 偵測中顯示實測取樣頻率
        if (running && measuredHz > 0f) {
            item { Note(tr("取樣 ${Fmt.num(measuredHz, 0)} Hz（要求 ${vm.samplingHzInUse} Hz）", "Sampling ${Fmt.num(measuredHz, 0)} Hz (requested ${vm.samplingHzInUse} Hz)")) }
        }

        // 版本與作者
        item { Note(tr("${vm.versionLabel}　作者 ${AppInfo.AUTHOR}", "${vm.versionLabel}  by ${AppInfo.AUTHOR}")) }
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
            Text(tr("箭數", "Arrows"), style = MaterialTheme.typography.caption1)
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.display1,
                textAlign = TextAlign.Center,
            )
            Note(tr("已鎖定　長按螢幕約 2 秒解鎖", "Locked  hold the screen 2 s to unlock"))
        }
    }
}

private const val UNLOCK_HOLD_MS = 2000L
