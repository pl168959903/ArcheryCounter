package com.zgrcc.archeryassistant

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/** 毫秒轉成 m:ss，向上取整（還剩 0.4 秒顯示 0:01）。 */
private fun clockText(ms: Long): String {
    val total = ((ms + 999L) / 1000L).coerceAtLeast(0L)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

private fun endTitle(c: CompUi): String =
    if (c.practice) tr("練習 ${c.number} / ${c.practiceEnds}", "Practice ${c.number} / ${c.practiceEnds}")
    else tr("第 ${c.number} / ${c.formalEnds} 趟", "End ${c.number} / ${c.formalEnds}")

/**
 * 比賽模式畫面。準備倒數 → 射擊倒數（顯示剩餘時間與箭數）→ 休息（下一趟）→ … → 結束。
 * 觸控鎖開著時，準備與射擊階段只顯示時間和箭數，長按約 2 秒才解鎖；休息與結束階段自動解鎖。
 */
@Composable
fun CompetitionScreen(vm: CounterViewModel, onOpenRecord: (String) -> Unit) {
    val ui by vm.comp.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val finishedId by vm.compFinishedId.collectAsStateWithLifecycle()

    val c = ui
    if (c == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Note(tr("沒有進行中的比賽。回主畫面按「比賽模式」開始", "No match in progress. Start one with Match mode on the main screen"))
        }
        return
    }

    val busy = c.phase == CompPhase.PREPARE || c.phase == CompPhase.SHOOTING
    if (locked && running && busy) {
        LockedMatch(c) { vm.unlockTouch() }
        return
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (c.phase) {
            CompPhase.PREPARE -> {
                item { Text(endTitle(c), style = MaterialTheme.typography.caption1) }
                item { Text(tr("準備", "Get ready"), style = MaterialTheme.typography.title3) }
                item {
                    Text(
                        text = clockText(c.remainingMs),
                        style = MaterialTheme.typography.display1,
                        textAlign = TextAlign.Center,
                    )
                }
                item { Note(tr("倒數結束會長震一下，開始計時射擊", "A long buzz marks the start of the shooting time")) }
            }
            CompPhase.SHOOTING -> {
                item { Text(endTitle(c), style = MaterialTheme.typography.caption1) }
                item {
                    Text(
                        text = clockText(c.remainingMs),
                        style = MaterialTheme.typography.display1,
                        color = timeColor(c.remainingMs),
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    Text(
                        tr("箭 ${c.arrows} / ${c.arrowsPerEnd}", "Arrows ${c.arrows} / ${c.arrowsPerEnd}"),
                        style = MaterialTheme.typography.title3,
                    )
                }
                item { Note(tr("射滿箭數或時間到，這一趟自動結束", "The end finishes when the arrows are used or time runs out")) }
            }
            CompPhase.REST -> {
                val last = c.lastEnd
                item { Text(tr("休息・取箭", "Break"), style = MaterialTheme.typography.title3) }
                if (last != null) {
                    item {
                        Body(
                            buildString {
                                append(if (last.practice) tr("練習 ${last.number}", "Practice ${last.number}") else tr("第 ${last.number - c.practiceEnds} 趟", "End ${last.number - c.practiceEnds}"))
                                append(tr("　${last.arrows} 箭　", "  ${last.arrows} arrows  "))
                                append(Fmt.duration((last.endMs - last.startMs).coerceAtLeast(0L)))
                                if (last.timedOut) append(tr("\n時間到", "\nTime up"))
                                if (last.lateArrows > 0) append(tr("\n逾時箭 ${last.lateArrows}", "\nLate arrows ${last.lateArrows}"))
                            }
                        )
                    }
                }
                if (c.autoNext) {
                    item { Note(tr("${clockText(c.remainingMs)} 後自動開始下一趟", "Next end starts automatically in ${clockText(c.remainingMs)}")) }
                }
                item {
                    Chip(
                        onClick = { vm.compNextEnd() },
                        label = { Text(tr("下一趟", "Next end"), maxLines = 1) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.85f),
                    )
                }
                item { EndMatchChip(vm) }
            }
            CompPhase.DONE -> {
                item { Text(tr("比賽結束", "Match finished"), style = MaterialTheme.typography.title3) }
                item { Note(tr("已存成一筆「比賽」記錄，詳情頁有逐趟用時與逾時統計", "Saved as a Match record; its details show per-end time and overtime")) }
                finishedId?.let { id ->
                    item {
                        Chip(
                            onClick = { onOpenRecord(id) },
                            label = { Text(tr("查看記錄", "View record"), maxLines = 1) },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(0.85f),
                        )
                    }
                }
                if (running) item { EndMatchChip(vm) }
            }
        }
        if (busy) item { EndMatchChip(vm) }
    }
}

@Composable
private fun EndMatchChip(vm: CounterViewModel) {
    Chip(
        onClick = { vm.stop() },
        label = { Text(tr("結束比賽", "End match"), maxLines = 1) },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(0.85f),
    )
}

@Composable
private fun timeColor(remainingMs: Long): Color = when {
    remainingMs <= 10_000L -> MaterialTheme.colors.error
    remainingMs <= 30_000L -> MaterialTheme.colors.secondary
    else -> MaterialTheme.colors.onBackground
}

/** 鎖定中的比賽畫面：只有時間和箭數，長按約 2 秒解鎖。 */
@Composable
private fun LockedMatch(c: CompUi, onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val up = withTimeoutOrNull(2000L) { waitForUpOrCancellation() }
                    if (up == null) onUnlock()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(endTitle(c), style = MaterialTheme.typography.caption1)
            Text(
                text = clockText(c.remainingMs),
                style = MaterialTheme.typography.display1,
                color = if (c.phase == CompPhase.SHOOTING) timeColor(c.remainingMs) else MaterialTheme.colors.onBackground,
                textAlign = TextAlign.Center,
            )
            if (c.phase == CompPhase.SHOOTING) {
                Text(
                    tr("箭 ${c.arrows} / ${c.arrowsPerEnd}", "Arrows ${c.arrows} / ${c.arrowsPerEnd}"),
                    style = MaterialTheme.typography.title3,
                )
            } else {
                Text(tr("準備", "Get ready"), style = MaterialTheme.typography.title3)
            }
            Note(tr("已鎖定　長按螢幕約 2 秒解鎖", "Locked  hold the screen 2 s to unlock"))
        }
    }
}
