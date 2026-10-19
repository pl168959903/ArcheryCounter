package com.example.archerycounter

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
        item { Text(if (manage) "管理記錄" else "記錄", style = MaterialTheme.typography.caption1) }

        if (sessions.isNotEmpty()) {
            item {
                Chip(
                    onClick = {
                        manage = !manage
                        selected = emptySet()
                        confirmDelete = false
                    },
                    label = { Text(if (manage) "完成" else "管理", maxLines = 1) },
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
                    label = { Text(if (chosen.size == deletable.size && deletable.isNotEmpty()) "取消全選" else "全選", maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    enabled = deletable.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            if (!confirmDelete) {
                item {
                    Chip(
                        onClick = { confirmDelete = true },
                        label = { Text("刪除已選 ${chosen.size} 筆", maxLines = 1) },
                        colors = ChipDefaults.secondaryChipColors(),
                        enabled = chosen.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            } else {
                item { Note("確定要刪除這 ${chosen.size} 筆記錄嗎？無法復原") }
                item {
                    Chip(
                        onClick = {
                            vm.deleteSessions(chosen)
                            selected = emptySet()
                            confirmDelete = false
                            manage = false
                        },
                        label = { Text("確定刪除", maxLines = 1) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item {
                    Chip(
                        onClick = { confirmDelete = false },
                        label = { Text("取消", maxLines = 1) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
        } else if (showTrend && sessions.size >= 2) {
            item {
                Chip(
                    onClick = onOpenTrend,
                    label = { Text("跨次趨勢", maxLines = 1) },
                    secondaryLabel = { Text("最近 ${minOf(sessions.size, 20)} 次的晃動量", maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }

        if (sessions.isEmpty()) {
            item { Note("尚無記錄。按「開始偵測」後，每次偵測都會自動存成一筆記錄") }
        }

        sessions.forEach { s ->
            item {
                val isActive = s.id == activeId
                val tag = when {
                    s.endMs != null -> ""
                    isActive -> " · 進行中"
                    else -> " · 中斷"
                }
                val avg = if (showTrend) remember(s) { computeStats(s).accelAvg } else null
                val isChosen = s.id in chosen

                // 管理模式用文字符號表示勾選，不單靠顏色
                val title = if (manage) {
                    (if (isChosen) "☑ " else "☐ ") + Fmt.dateTime(s.startMs)
                } else {
                    Fmt.dateTime(s.startMs)
                }
                val second = buildString {
                    append("${s.finalCount()} 箭 · ${Fmt.duration(s.durationMs())}$tag")
                    if (avg != null) append("\n平均晃動 ${Fmt.num(avg)}")
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
                            if (manage && isActive) "進行中，無法選取" else second,
                            maxLines = 2,
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
                        label = { Text("清除全部", maxLines = 1) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            } else {
                item { Note("確定要刪除全部記錄嗎？無法復原") }
                item {
                    Chip(
                        onClick = {
                            vm.deleteAllSessions()
                            confirmClear = false
                        },
                        label = { Text("確定刪除", maxLines = 1) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item {
                    Chip(
                        onClick = { confirmClear = false },
                        label = { Text("取消", maxLines = 1) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
        }
    }
}
