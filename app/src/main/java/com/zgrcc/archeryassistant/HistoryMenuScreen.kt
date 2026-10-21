package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/** 趨勢（[compare] 為 false）或對比（true）的子頁；各項目的出現條件和以前一樣。 */
@Composable
fun HistoryMenuScreen(
    vm: CounterViewModel,
    compare: Boolean,
    onOpenTrend: () -> Unit,
    onOpenOverview: () -> Unit,
    onOpenMatchCompare: () -> Unit,
    onOpenGroups: () -> Unit,
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val showTrend by vm.settings.showSessionTrend.collectAsStateWithLifecycle()
    val matchCompare by vm.settings.matchCompare.collectAsStateWithLifecycle()
    val groupCompare by vm.settings.groupCompare.collectAsStateWithLifecycle()
    val overviewOn by vm.settings.overview.collectAsStateWithLifecycle()
    val e = ScreenRules.historyEntries(sessions, false, showTrend, matchCompare, groupCompare, overviewOn)
    val matchCount = sessions.count { !it.calibration && it.comp != null && it.endMs != null }

    @Composable
    fun entry(label: String, sub: String, onClick: () -> Unit) {
        Chip(
            onClick = onClick,
            label = { Text(label + navMark(), maxLines = 1) },
            secondaryLabel = { Text(sub, maxLines = 1) },
            colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
            modifier = Modifier.fillMaxWidth(0.9f),
        )
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text(if (compare) tr("對比", "Compare") else tr("趨勢", "Trends"), style = MaterialTheme.typography.caption1) }
        if (!compare) {
            if (e.trend) item { entry(tr("跨次趨勢", "Trend across sessions"), tr("最近 ${minOf(sessions.size, 20)} 次的晃動量", "Shake over the last ${minOf(sessions.size, 20)} sessions"), onOpenTrend) }
            if (e.overview) item { entry(tr("訓練總覽", "Training overview"), tr("每週、每月的箭數", "Arrows per week and month"), onOpenOverview) }
        } else {
            if (e.matchCompare) item { entry(tr("比賽對比", "Match comparison"), tr("$matchCount 場比賽", "$matchCount matches"), onOpenMatchCompare) }
            if (e.groups) item { entry(tr("條件對比", "Compare conditions"), tr("依距離、弓種分組", "Grouped by distance and bow"), onOpenGroups) }
        }
        if ((!compare && !HistoryGroups.trendVisible(e)) || (compare && !HistoryGroups.compareVisible(e))) {
            item { Note(tr("目前沒有可看的項目", "Nothing to show yet")) }
        }
    }
}
