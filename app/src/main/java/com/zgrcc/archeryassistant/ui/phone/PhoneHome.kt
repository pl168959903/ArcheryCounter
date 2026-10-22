package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
 * 一般的單欄捲動清單：上面是「從手錶匯入」，下面是記錄卡片，點進去看詳情。
 */
@Composable
fun PhoneHome(
    vm: CounterViewModel,
    onOpenSession: (String) -> Unit,
    onOpenImport: () -> Unit,
    onOpenTrend: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val showTrend by vm.settings.showSessionTrend.collectAsStateWithLifecycle()
    val matchCompare by vm.settings.matchCompare.collectAsStateWithLifecycle()
    val groupCompare by vm.settings.groupCompare.collectAsStateWithLifecycle()
    val overviewOn by vm.settings.overview.collectAsStateWithLifecycle()
    val origins = remember(sessions) { vm.importOrigins() }
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
        if (HistoryGroups.trendVisible(entries) || HistoryGroups.compareVisible(entries)) {
            item { PhoneButton(tr("趨勢與對比", "Trends and comparison"), onClick = onOpenTrend) }
        }
        item { PhoneButton(tr("設定", "Settings"), onClick = onOpenSettings) }

        item {
            Text(
                if (sessions.isEmpty()) tr("記錄", "Records") else tr("記錄（${sessions.size}）", "Records (${sessions.size})"),
                style = MaterialTheme.typography.title2,
                color = Color.White,
            )
        }
        if (sessions.isEmpty()) {
            item {
                Text(
                    tr("還沒有記錄。按「從手錶匯入」，把手錶上的記錄抓過來", "No records yet. Tap Import from watch to bring records over"),
                    style = MaterialTheme.typography.body2,
                    color = Color(0xFFBDBDBD),
                )
            }
        }
        items(sessions, key = { it.id }) { s ->
            val avg = if (s.calibration) null else computeStats(s).accelAvg
            val lines = buildList {
                add(
                    (if (s.calibration) tr("校準 · ", "Calibration · ") else "") +
                        tr("${s.finalCount()} 箭 · ${Fmt.duration(s.durationMs())}", "${s.finalCount()} arrows · ${Fmt.duration(s.durationMs())}"),
                )
                if (avg != null) add(tr("平均晃動 ${Fmt.num(avg)} m/s²", "Avg shake ${Fmt.num(avg)} m/s²"))
                Tags.label(s.tags)?.let { add(it) }
                origins[s.id]?.let { add("⇩ $it") }
                if (s.note.isNotEmpty()) add("✎ ${s.note}")
            }
            PhoneCard(title = Fmt.dateTime(s.startMs), lines = lines, onClick = { onOpenSession(s.id) })
        }
    }
}
