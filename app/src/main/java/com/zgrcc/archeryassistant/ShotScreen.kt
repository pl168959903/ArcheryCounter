package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.launch

/** 記錄詳情裡點開的某一箭：數據、波形，以及標記／恢復誤判。 */
@Composable
fun ShotScreen(vm: CounterViewModel) {
    val id by vm.selectedId.collectAsStateWithLifecycle()
    val index by vm.selectedShot.collectAsStateWithLifecycle()
    val activeId by vm.activeSessionId.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()

    // 標記誤判後記錄列表會更新，這裡跟著重新讀
    val load by produceState<SessionLoad>(
        initialValue = SessionLoad.Loading,
        key1 = id,
        key2 = sessions,
    ) {
        val sid = id
        val session = if (sid == null) null else vm.loadSession(sid)
        value = if (session == null) SessionLoad.Missing else SessionLoad.Ready(session)
    }

    val i = index
    when (val l = load) {
        SessionLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("載入中…", "Loading…"))
        }
        SessionLoad.Missing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("找不到這筆記錄", "Record not found"))
        }
        is SessionLoad.Ready -> {
            val shot = if (i == null) null else l.session.shots.getOrNull(i)
            if (i == null || shot == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(tr("找不到這一箭", "Shot not found"))
                }
            } else {
                ShotContent(vm, l.session, i, shot, isActive = l.session.id == activeId)
            }
        }
    }
}

@Composable
private fun ShotContent(vm: CounterViewModel, session: Session, index: Int, shot: ShotEntry, isActive: Boolean) {
    val scope = rememberCoroutineScope()
    val recordScore by vm.settings.recordScore.collectAsStateWithLifecycle()

    // 有效的第幾箭（不含誤判）
    val validNo = if (shot.cancelled) 0 else session.shots.take(index + 1).count { !it.cancelled }
    // 距上一個有效的箭
    val prevValid = session.shots.take(index).lastOrNull { !it.cancelled }
    val intervalSec = if (shot.cancelled || prevValid == null) null else (shot.relMs - prevValid.relMs) / 1000f

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                text = if (shot.cancelled) tr("已取消的一箭", "Cancelled shot") else tr("第 $validNo 箭", "Shot $validNo"),
                style = MaterialTheme.typography.title3,
            )
        }
        item {
            Body(
                buildString {
                    appendLine(tr("時間 ${Fmt.clock(session.startMs + shot.relMs)}", "Time ${Fmt.clock(session.startMs + shot.relMs)}"))
                    if (shot.cancelled) appendLine(tr("狀態 誤判（不計入箭數、統計與圖表）", "Status: false detection (not counted in arrows, statistics or charts)"))
                    appendLine(tr("峰值 ${Fmt.num(shot.peak, 1)} m/s²", "Peak ${Fmt.num(shot.peak, 1)} m/s²"))
                    appendLine(tr("閾值 ${Fmt.num(shot.threshold, 0)}", "Threshold ${Fmt.num(shot.threshold, 0)}"))
                    if (intervalSec != null) appendLine(tr("距上一箭 ${Fmt.num(intervalSec, 0)} 秒", "${Fmt.num(intervalSec, 0)} s since previous"))
                    appendLine(tr("加速度晃動 ${Fmt.numOrDash(shot.accelRms)} m/s²", "Accel shake ${Fmt.numOrDash(shot.accelRms)} m/s²"))
                    if (session.hasGyro) appendLine(tr("角速度晃動 ${Fmt.numOrDash(shot.gyroRms, 1)} °/s", "Gyro shake ${Fmt.numOrDash(shot.gyroRms, 1)} °/s"))
                    append(tr("分析視窗 放箭前 ${session.windowSec} 秒", "Analysis window: ${session.windowSec} s before release"))
                    if (shot.followAccelRms != null || shot.followGyroRms != null) {
                        append(tr("\n放箭後 0.3～1 秒晃動", "\nShake 0.3-1 s after release"))
                        if (shot.followAccelRms != null) append(tr("\n加速度 ${Fmt.num(shot.followAccelRms)} m/s²", "\nAccel ${Fmt.num(shot.followAccelRms)} m/s²"))
                        if (session.hasGyro && shot.followGyroRms != null) append(tr("\n角速度 ${Fmt.num(shot.followGyroRms, 1)} °/s", "\nGyro ${Fmt.num(shot.followGyroRms, 1)} °/s"))
                    }
                }
            )
        }

        item { SectionTitle(tr("波形", "Waveform")) }
        val wave = shot.waveform
        if (wave != null) {
            item { ShotWaveform(wave) }
        } else {
            item { Note(tr("這一箭沒有波形：記錄時「單箭波形」是關閉的，或當時資料不足", "No waveform for this shot: Per-shot waveform was off, or there was not enough data")) }
        }

        // 環數：設定頁開啟，或這一箭已經有環數時才顯示；誤判的箭不用輸入
        if ((recordScore || shot.score != null) && !shot.cancelled) {
            item { SectionTitle(tr("環數", "Score")) }
            if (isActive) {
                item { Note(tr("進行中，暫停偵測後才能輸入環數", "In progress; pause detection before entering scores")) }
            } else {
                item {
                    val score = shot.score
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SmallRoundButton("−") {
                            // 往下減到 0 之後再按一次就清除（回到沒輸入）
                            val next = when {
                                score == null -> null
                                score <= 0 -> null
                                else -> score - 1
                            }
                            scope.launch { vm.setShotScore(session.id, index, next) }
                        }
                        Text(if (score == null) "—" else score.toString(), style = MaterialTheme.typography.display3)
                        SmallRoundButton("+") {
                            val next = if (score == null) 10 else minOf(score + 1, 10)
                            scope.launch { vm.setShotScore(session.id, index, next) }
                        }
                    }
                }
                item { Note(tr("第一次按 + 是 10 環，再按 − 往下減；減到 0 再按 − 清除", "First press of + gives 10; press − to go down; at 0 press − again to clear")) }
            }
        }

        item {
            if (isActive) {
                Note(tr("進行中，暫停偵測後才能標記誤判", "In progress; pause detection before marking false detections"))
            } else {
                Chip(
                    onClick = {
                        scope.launch { vm.setShotCancelled(session.id, index, !shot.cancelled) }
                    },
                    label = { Text(if (shot.cancelled) tr("恢復為有效", "Restore as valid") else tr("標記為誤判", "Mark as false detection"), maxLines = 1) },
                    colors = if (shot.cancelled) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
    }
}
