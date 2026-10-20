package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun HistoryScreen(vm: CounterViewModel, onOpen: (String) -> Unit, onOpenTrend: () -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val activeId by vm.activeSessionId.collectAsStateWithLifecycle()
    val showTrend by vm.settings.showSessionTrend.collectAsStateWithLifecycle()

    var confirmClear by remember { mutableStateOf(false) }

    // 管理模式：點記錄是勾選，不是進入詳情
    var manage by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshSessions() }

    // 勾選的記錄可能已經被刪掉（或正在進行），只算還存在、可刪的
    val deletable = sessions.filter { it.id != activeId }.map { it.id }.toSet()
    val chosen = selected.intersect(deletable)

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text(if (manage) tr("管理記錄", "Manage records") else tr("記錄", "Records"), style = MaterialTheme.typography.caption1) }

        if (sessions.isNotEmpty()) {
            item {
                Chip(
                    onClick = {
                        manage = !manage
                        selected = emptySet()
                        confirmDelete = false
                    },
                    label = { Text(if (manage) tr("完成", "Done") else tr("管理", "Manage"), maxLines = 1) },
                    colors = if (manage) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }

        if (manage) {
            item {
                Chip(
                    onClick = {
                        selected = if (chosen.size == deletable.size) emptySet() else deletable
                        confirmDelete = false
                    },
                    label = { Text(if (chosen.size == deletable.size && deletable.isNotEmpty()) tr("取消全選", "Select none") else tr("全選", "Select all"), maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    enabled = deletable.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            if (!confirmDelete) {
                item {
                    Chip(
                        onClick = { confirmDelete = true },
                        label = { Text(tr("刪除已選 ${chosen.size} 筆", "Delete ${chosen.size} selected"), maxLines = 1) },
                        colors = ChipDefaults.secondaryChipColors(),
                        enabled = chosen.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            } else {
                item { Note(tr("確定要刪除這 ${chosen.size} 筆記錄嗎？無法復原", "Delete these ${chosen.size} records? This cannot be undone")) }
                item {
                    Chip(
                        onClick = {
                            vm.deleteSessions(chosen)
                            selected = emptySet()
                            confirmDelete = false
                            manage = false
                        },
                        label = { Text(tr("確定刪除", "Delete"), maxLines = 1) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item {
                    Chip(
                        onClick = { confirmDelete = false },
                        label = { Text(tr("取消", "Cancel"), maxLines = 1) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
        } else if (showTrend && sessions.size >= 2) {
            item {
                Chip(
                    onClick = onOpenTrend,
                    label = { Text(tr("跨次趨勢", "Trend across sessions"), maxLines = 1) },
                    secondaryLabel = { Text(tr("最近 ${minOf(sessions.size, 20)} 次的晃動量", "Shake over the last ${minOf(sessions.size, 20)} sessions"), maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }

        if (sessions.isEmpty()) {
            item { Note(tr("尚無記錄。按「開始偵測」後，每次偵測都會自動存成一筆記錄", "No records yet. Each time you press Start detection, a record is saved automatically")) }
        }

        sessions.forEach { s ->
            item {
                val isActive = s.id == activeId
                val tag = when {
                    s.endMs != null -> ""
                    isActive -> tr(" · 進行中", " · In progress")
                    else -> tr(" · 中斷", " · Interrupted")
                }
                val avg = if (showTrend && !s.calibration) remember(s) { computeStats(s).accelAvg } else null
                val isChosen = s.id in chosen

                // 管理模式用文字符號表示勾選，不單靠顏色
                val title = if (manage) {
                    (if (isChosen) "☑ " else "☐ ") + Fmt.dateTime(s.startMs)
                } else {
                    Fmt.dateTime(s.startMs)
                }
                val second = buildString {
                    append(if (s.calibration) tr("校準 · ", "Calibration · ") else "")
                    append(tr("${s.finalCount()} 箭 · ${Fmt.duration(s.durationMs())}$tag", "${s.finalCount()} arrows · ${Fmt.duration(s.durationMs())}$tag"))
                    Tags.label(s.tags)?.let { append("\n").append(it) }
                    if (avg != null) append(tr("\n平均晃動 ${Fmt.num(avg)}", "\nAvg shake ${Fmt.num(avg)}"))
                }

                Chip(
                    onClick = {
                        if (manage) {
                            selected = if (isChosen) selected - s.id else selected + s.id
                            confirmDelete = false
                        } else {
                            onOpen(s.id)
                        }
                    },
                    label = { Text(title, maxLines = 1) },
                    secondaryLabel = {
                        Text(
                            if (manage && isActive) tr("進行中，無法選取", "In progress; cannot select") else second,
                            maxLines = 3,
                        )
                    },
                    colors = if (manage && isChosen) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    enabled = !(manage && isActive),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }

        if (sessions.isNotEmpty() && !manage) {
            if (!confirmClear) {
                item {
                    Chip(
                        onClick = { confirmClear = true },
                        label = { Text(tr("清除全部", "Clear all"), maxLines = 1) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            } else {
                item { Note(tr("確定要刪除全部記錄嗎？無法復原", "Delete all records? This cannot be undone")) }
                item {
                    Chip(
                        onClick = {
                            vm.deleteAllSessions()
                            confirmClear = false
                        },
                        label = { Text(tr("確定刪除", "Delete"), maxLines = 1) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item {
                    Chip(
                        onClick = { confirmClear = false },
                        label = { Text(tr("取消", "Cancel"), maxLines = 1) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
        }
    }
}
