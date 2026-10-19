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
fun HistoryScreen(vm: CounterViewModel, onOpen: (String) -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val activeId by vm.activeSessionId.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshSessions() }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text("記錄", style = MaterialTheme.typography.caption1) }

        if (sessions.isEmpty()) {
            item { Note("尚無記錄。按「開始偵測」後，每次偵測都會自動存成一筆記錄") }
        }

        sessions.forEach { s ->
            item {
                val tag = when {
                    s.endMs != null -> ""
                    s.id == activeId -> " · 進行中"
                    else -> " · 中斷"
                }
                Chip(
                    onClick = { onOpen(s.id) },
                    label = { Text(Fmt.dateTime(s.startMs), maxLines = 1) },
                    secondaryLabel = {
                        Text("${s.finalCount()} 箭 · ${Fmt.duration(s.durationMs())}$tag", maxLines = 1)
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }

        if (sessions.isNotEmpty()) {
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
