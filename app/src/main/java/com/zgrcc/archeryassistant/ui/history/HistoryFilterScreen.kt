package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text

/** 記錄頁篩選（v1.19）：標籤、日期範圍。只影響記錄列表。 */
@Composable
fun HistoryFilterScreen(vm: CounterViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val tag by vm.histTag.collectAsStateWithLifecycle()
    val days by vm.histDays.collectAsStateWithLifecycle()
    val matched = remember(sessions, tag, days) { HistoryFilter.apply(sessions, tag, days, System.currentTimeMillis()).size }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(tr("篩選", "Filter")) }
        item {
            Chip(
                onClick = { vm.cycleHistTag() },
                label = { Text(tr("標籤", "Tag"), maxLines = 1) },
                secondaryLabel = { Text(tag.ifEmpty { tr("全部", "All") }, maxLines = 1) },
                colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item {
            Chip(
                onClick = { vm.cycleHistDays() },
                label = { Text(tr("日期", "Date"), maxLines = 1) },
                secondaryLabel = { Text(HistoryFilter.daysLabel(days), maxLines = 1) },
                colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item { Note(tr("點一下依序切換。符合 $matched 筆。只影響記錄列表；趨勢、對比、網頁報告不受影響", "Tap to cycle. $matched match. Only affects the records list; trends, comparisons and the web report are not affected")) }
        if (HistoryFilter.active(tag, days)) {
            item {
                Chip(
                    onClick = { vm.clearHistFilter() },
                    label = { Text(tr("清除篩選", "Clear filter"), maxLines = 1) },
                    colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
    }
}
