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
import androidx.wear.compose.material.Text

/** 連線記錄頁（v1.18.5）：摘要、清除按鈕放在最上面，記錄由新到舊（最新在上、最舊在最下）。 */
@Composable
fun ConnectLogScreen(vm: CounterViewModel) {
    val events by vm.shareEvents.collectAsStateWithLifecycle()
    val logOn by vm.settings.shareLog.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(tr("連線記錄", "Connection log")) }
        if (!logOn) item { Note(tr("「記錄連線」已關閉", "Logging is off")) }
        item { Body(ShareText.summary(events)) }
        if (events.isNotEmpty()) {
            item {
                Chip(
                    onClick = {
                        if (confirm) {
                            confirm = false
                            vm.clearShareLog()
                        } else {
                            confirm = true
                        }
                    },
                    label = { Text(if (confirm) tr("確定清除全部", "Confirm: clear all") else tr("清除全部記錄", "Clear all"), maxLines = 1) },
                    colors = kindColors(ChipKind.DANGER, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
        val rows = ShareText.newestFirst(events)
        if (rows.isEmpty()) {
            item { Note(tr("今天還沒有連線", "No connections today")) }
        }
        for (e in rows) {
            item {
                Note(ShareText.clock(e.timeMs) + " " + e.ip + " " + AgentLabel.of(e.agent) + "\n" + ShareText.result(e.result))
            }
        }
    }
}
