package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/** 快速檢查（v1.11）：逐箭看波形，一鍵標記誤判或恢復，標完自動跳下一箭。 */
@Composable
fun QuickCheckScreen(vm: CounterViewModel) {
    val id by vm.selectedId.collectAsStateWithLifecycle()
    val activeId by vm.activeSessionId.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()

    // 標記後記錄列表會更新，跟著重新讀
    val load by produceState<SessionLoad>(
        initialValue = SessionLoad.Loading,
        key1 = id,
        key2 = sessions,
    ) {
        val sid = id
        val session = if (sid == null) null else vm.loadSession(sid)
        value = if (session == null) SessionLoad.Missing else SessionLoad.Ready(session)
    }

    when (val l = load) {
        SessionLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("載入中…", "Loading…"))
        }
        SessionLoad.Missing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("找不到這筆記錄", "Record not found"))
        }
        is SessionLoad.Ready -> QuickCheckContent(vm, l.session, isActive = l.session.id == activeId)
    }
}

@Composable
private fun QuickCheckContent(vm: CounterViewModel, session: Session, isActive: Boolean) {
    val scope = rememberCoroutineScope()
    val recordTrace by vm.settings.recordTrace.collectAsStateWithLifecycle()
    val discSize by vm.settings.discSize.collectAsStateWithLifecycle()
    val shotFeel by vm.settings.shotFeel.collectAsStateWithLifecycle()
    val count = session.shots.size
    var pos by remember(session.id) { mutableStateOf(QuickCheck.start(count)) }
    val index = pos.coerceIn(0, (count - 1).coerceAtLeast(0))
    val shot = session.shots.getOrNull(index)

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text(tr("快速檢查", "Quick check"), style = MaterialTheme.typography.title3) }

        if (shot == null) {
            item { Note(tr("這次沒有偵測到放箭", "No release detected this time")) }
        } else if (isActive) {
            item { Note(tr("進行中，暫停偵測後才能檢查", "In progress; pause detection first")) }
        } else {
            item {
                Text(
                    text = QuickCheck.progress(count, index) + if (shot.cancelled) tr("　已取消", "  Cancelled") else "",
                    style = MaterialTheme.typography.caption1,
                )
            }
            item {
                Body(
                    buildString {
                        appendLine(tr("時間 ${Fmt.clock(session.startMs + shot.relMs)}", "Time ${Fmt.clock(session.startMs + shot.relMs)}"))
                        appendLine(tr("峰值 ${Fmt.num(shot.peak, 1)} m/s²", "Peak ${Fmt.num(shot.peak, 1)} m/s²"))
                        append(tr("加速度晃動 ${Fmt.numOrDash(shot.accelRms)} m/s²", "Accel shake ${Fmt.numOrDash(shot.accelRms)} m/s²"))
                    }
                )
            }
            val wave = shot.waveform
            if (wave != null) {
                item { ShotWaveform(wave) }
            } else {
                item { Note(tr("這一箭沒有波形", "No waveform for this shot")) }
            }
            val trace = shot.trace
            if (trace != null) {
                item { ShotTrace(trace, DiscSize.dp(discSize)) }
            } else if (recordTrace) {
                item { Note(tr("這一箭沒有 2D 軌跡", "No 2D trace for this shot")) }
            }
            if (shotFeel && !shot.cancelled) {
                item {
                    FeelRow(shot.feel) { v -> scope.launch { vm.setShotFeel(session.id, index, v) } }
                }
            }
            item {
                Chip(
                    onClick = {
                        val target = index
                        val next = !shot.cancelled
                        scope.launch { vm.setShotCancelled(session.id, target, next) }
                        pos = QuickCheck.afterMark(count, target)
                    },
                    label = { Text(if (shot.cancelled) tr("恢復為有效", "Restore as valid") else tr("標記誤判", "Mark false"), maxLines = 1) },
                    colors = if (shot.cancelled) kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()) else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallRoundButton("‹") { pos = QuickCheck.move(count, index, -1) }
                    Text(QuickCheck.progress(count, index), style = MaterialTheme.typography.caption2)
                    SmallRoundButton("›") { pos = QuickCheck.move(count, index, 1) }
                }
            }
            item { Note(tr("‹ › 切換上一箭、下一箭；標記或恢復後會自動跳到下一箭", "‹ › move between shots; marking or restoring jumps to the next shot")) }
        }
    }
}
