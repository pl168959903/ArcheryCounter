package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * 手機首頁（v1.23）：手機不戴在手腕上，不做偵測，只匯入與檢視手錶的記錄。
 * 一般的單欄捲動清單：「從手錶匯入」、「記錄」（進記錄頁）、趨勢與對比、設定（v1.24 起記錄清單移到記錄頁）。
 */
@Composable
fun PhoneHome(
    vm: CounterViewModel,
    onOpenHistory: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenTrend: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val showTrend by vm.settings.showSessionTrend.collectAsStateWithLifecycle()
    val matchCompare by vm.settings.matchCompare.collectAsStateWithLifecycle()
    val groupCompare by vm.settings.groupCompare.collectAsStateWithLifecycle()
    val overviewOn by vm.settings.overview.collectAsStateWithLifecycle()
    val entries = remember(sessions, showTrend, matchCompare, groupCompare, overviewOn) {
        ScreenRules.historyEntries(sessions, false, showTrend, matchCompare, groupCompare, overviewOn)
    }
    LaunchedEffect(Unit) { vm.refreshSessions() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(tr("射箭助理", "Archery Assistant"), style = MaterialTheme.typography.title1, color = Color.White)
        }
        item {
            Text(
                tr("${vm.versionLabel}　作者 ${AppInfo.AUTHOR}", "${vm.versionLabel}  by ${AppInfo.AUTHOR}"),
                style = MaterialTheme.typography.caption1,
                color = Color(0xFFBDBDBD),
            )
        }
        item {
            Text(
                tr("手機用來匯入與檢視手錶的記錄；偵測在手錶上進行", "Use the phone to import and view records from your watch; detection runs on the watch"),
                style = MaterialTheme.typography.body2,
                color = Color(0xFFBDBDBD),
            )
        }
        item { PhoneButton(tr("從手錶匯入", "Import from watch"), primary = true, onClick = onOpenImport) }
        item {
            PhoneButton(
                if (sessions.isEmpty()) tr("記錄", "Records") else tr("記錄（${sessions.size}）", "Records (${sessions.size})"),
                onClick = onOpenHistory,
            )
        }
        if (HistoryGroups.trendVisible(entries) || HistoryGroups.compareVisible(entries)) {
            item { PhoneButton(tr("趨勢與對比", "Trends and comparison"), onClick = onOpenTrend) }
        }
        item { PhoneButton(tr("設定", "Settings"), onClick = onOpenSettings) }
    }
}
