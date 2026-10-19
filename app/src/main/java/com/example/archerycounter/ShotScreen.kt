package com.example.archerycounter

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
            Text("載入中…")
        }
        SessionLoad.Missing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("找不到這筆記錄")
        }
        is SessionLoad.Ready -> {
            val shot = if (i == null) null else l.session.shots.getOrNull(i)
            if (i == null || shot == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("找不到這一箭")
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
                text = if (shot.cancelled) "已取消的一箭" else "第 $validNo 箭",
                style = MaterialTheme.typography.title3,
            )
        }
        item {
            Body(
                buildString {
                    appendLine("時間 ${Fmt.clock(session.startMs + shot.relMs)}")
                    if (shot.cancelled) appendLine("狀態 誤判（不計入箭數、統計與圖表）")
                    appendLine("峰值 ${Fmt.num(shot.peak, 1)} m/s²")
                    appendLine("閾值 ${Fmt.num(shot.threshold, 0)}")
                    if (intervalSec != null) appendLine("距上一箭 ${Fmt.num(intervalSec, 0)} 秒")
                    appendLine("加速度晃動 ${Fmt.numOrDash(shot.accelRms)} m/s²")
                    if (session.hasGyro) appendLine("角速度晃動 ${Fmt.numOrDash(shot.gyroRms, 1)} °/s")
                    append("分析視窗 放箭前 ${session.windowSec} 秒")
                    if (shot.followAccelRms != null || shot.followGyroRms != null) {
                        append("\n放箭後 0.3～1 秒晃動")
                        if (shot.followAccelRms != null) append("\n加速度 ${Fmt.num(shot.followAccelRms)} m/s²")
                        if (session.hasGyro && shot.followGyroRms != null) append("\n角速度 ${Fmt.num(shot.followGyroRms, 1)} °/s")
                    }
                }
            )
        }

        item { SectionTitle("波形") }
        val wave = shot.waveform
        if (wave != null) {
            item { ShotWaveform(wave) }
        } else {
            item { Note("這一箭沒有波形：記錄時「單箭波形」是關閉的，或當時資料不足") }
        }

        // 環數：設定頁開啟，或這一箭已經有環數時才顯示；誤判的箭不用輸入
        if ((recordScore || shot.score != null) && !shot.cancelled) {
            item { SectionTitle("環數") }
            if (isActive) {
                item { Note("進行中，暫停偵測後才能輸入環數") }
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
                item { Note("第一次按 + 是 10 環，再按 − 往下減；減到 0 再按 − 清除") }
            }
        }

        item {
            if (isActive) {
                Note("進行中，暫停偵測後才能標記誤判")
            } else {
                Chip(
                    onClick = {
                        scope.launch { vm.setShotCancelled(session.id, index, !shot.cancelled) }
                    },
                    label = { Text(if (shot.cancelled) "恢復為有效" else "標記為誤判", maxLines = 1) },
                    colors = if (shot.cancelled) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
    }
}
