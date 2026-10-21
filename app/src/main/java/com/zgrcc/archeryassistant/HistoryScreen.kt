package com.zgrcc.archeryassistant

import kotlinx.coroutines.launch
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
fun HistoryScreen(vm: CounterViewModel, onOpen: (String) -> Unit, onOpenTrendMenu: () -> Unit, onOpenCompareMenu: () -> Unit, onOpenFilter: () -> Unit = {}) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var openFailed by remember { mutableStateOf(false) }
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val activeId by vm.activeSessionId.collectAsStateWithLifecycle()
    val showTrend by vm.settings.showSessionTrend.collectAsStateWithLifecycle()
    val matchCompare by vm.settings.matchCompare.collectAsStateWithLifecycle()
    val groupCompare by vm.settings.groupCompare.collectAsStateWithLifecycle()
    val overviewOn by vm.settings.overview.collectAsStateWithLifecycle()
    val bestOn by vm.settings.personalBest.collectAsStateWithLifecycle()
    val reportViewOn by vm.settings.reportView.collectAsStateWithLifecycle()
    val origins = remember(sessions) { vm.importOrigins() }
    val noteOn by vm.settings.recordNote.collectAsStateWithLifecycle()
    val flagOn by vm.settings.flagSuspicious.collectAsStateWithLifecycle()
    val filterOn by vm.settings.historyFilter.collectAsStateWithLifecycle()
    val histTag by vm.histTag.collectAsStateWithLifecycle()
    val histDays by vm.histDays.collectAsStateWithLifecycle()
    val flags = remember(sessions, activeId, flagOn) { if (flagOn) Suspicion.flags(sessions, activeId) else emptyMap() }
    val shown = remember(sessions, filterOn, histTag, histDays) {
        if (filterOn) HistoryFilter.apply(sessions, histTag, histDays, System.currentTimeMillis()) else sessions
    }
    val steadiestId = remember(sessions, bestOn) { if (bestOn) PersonalBest.steadiestId(sessions) else null }


    // 管理模式：點記錄是勾選，不是進入詳情
    var manage by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val entries = remember(sessions, manage, showTrend, matchCompare, groupCompare, overviewOn) {
        ScreenRules.historyEntries(sessions, manage, showTrend, matchCompare, groupCompare, overviewOn)
    }

    LaunchedEffect(Unit) { vm.refreshSessions() }

    // 勾選的記錄可能已經被刪掉（或正在進行），只算還存在、可刪的
    val deletable = shown.filter { it.id != activeId }.map { it.id }.toSet()
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

        if (filterOn && sessions.isNotEmpty() && !manage) {
            item {
                Chip(
                    onClick = onOpenFilter,
                    label = { Text(tr("篩選", "Filter") + navMark(), maxLines = 1) },
                    secondaryLabel = {
                        Text(
                            if (HistoryFilter.active(histTag, histDays)) {
                                (if (histTag.isNotEmpty()) histTag + " · " else "") + HistoryFilter.daysLabel(histDays)
                            } else tr("全部記錄", "All records"),
                            maxLines = 2,
                        )
                    },
                    colors = if (HistoryFilter.active(histTag, histDays)) ChipDefaults.primaryChipColors() else kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
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
                    colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                    enabled = deletable.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            if (!confirmDelete) {
                item {
                    Chip(
                        onClick = { confirmDelete = true },
                        label = { Text(tr("刪除已選 ${chosen.size} 筆", "Delete ${chosen.size} selected"), maxLines = 1) },
                        colors = kindColors(ChipKind.DANGER, ChipDefaults.secondaryChipColors()),
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
                        colors = kindColors(ChipKind.DANGER, ChipDefaults.primaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item {
                    Chip(
                        onClick = { confirmDelete = false },
                        label = { Text(tr("取消", "Cancel"), maxLines = 1) },
                        colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
        }

        // 趨勢、對比各收成一個入口（v1.18.3）
        if (HistoryGroups.trendVisible(entries)) {
            item {
                Chip(
                    onClick = onOpenTrendMenu,
                    label = { Text(tr("趨勢", "Trends") + navMark(), maxLines = 1) },
                    secondaryLabel = { Text(tr("跨次趨勢、訓練總覽", "Trend across sessions, overview"), maxLines = 1) },
                    colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
        if (HistoryGroups.compareVisible(entries)) {
            item {
                Chip(
                    onClick = onOpenCompareMenu,
                    label = { Text(tr("對比", "Compare") + navMark(), maxLines = 1) },
                    secondaryLabel = { Text(tr("比賽對比、條件對比", "Matches, conditions"), maxLines = 1) },
                    colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }

        if (reportViewOn && sessions.isNotEmpty() && !manage) {
            item {
                Chip(
                    onClick = {
                        scope.launch {
                            val html = vm.reportHtml()
                            openFailed = !ExternalOpen.localReport(ctx, html)
                        }
                    },
                    label = { Text(tr("網頁報告", "Web report") + navMark(), maxLines = 1) },
                    secondaryLabel = { Text(tr("用瀏覽器開啟全部記錄", "Open all records in the browser"), maxLines = 1) },
                    colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }

        if (openFailed) item { Note(tr("找不到可以開啟網頁的程式（瀏覽器）", "No browser found to open the page")) }

        if (sessions.isEmpty()) {
            item { Note(tr("尚無記錄。按「開始偵測」後，每次偵測都會自動存成一筆記錄", "No records yet. Each time you press Start detection, a record is saved automatically")) }
        }

        sessions.takeIf { it.isNotEmpty() && shown.isEmpty() }?.let {
            item { Note(tr("沒有符合篩選的記錄", "No records match the filter")) }
        }

        shown.forEach { s ->
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
                val star = if (s.id == steadiestId) "★ " else ""
                val title = if (manage) {
                    (if (isChosen) "☑ " else "☐ ") + Fmt.dateTime(s.startMs)
                } else {
                    star + Fmt.dateTime(s.startMs)
                }
                val second = buildString {
                    append(if (s.calibration) tr("校準 · ", "Calibration · ") else "")
                    append(tr("${s.finalCount()} 箭 · ${Fmt.duration(s.durationMs())}$tag", "${s.finalCount()} arrows · ${Fmt.duration(s.durationMs())}$tag"))
                    Tags.label(s.tags)?.let { append("\n").append(it) }
                    origins[s.id]?.let { append("\n⇩ ").append(it) }
                    flags[s.id]?.let { append("\n⚠ ").append(Suspicion.shortText(it)) }
                    if (noteOn && s.note.isNotEmpty()) append("\n✎ ").append(s.note)
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
                    label = { Text(if (manage) title else title + navMark(), maxLines = 1) },
                    secondaryLabel = {
                        Text(
                            if (manage && isActive) tr("進行中，無法選取", "In progress; cannot select") else second,
                            maxLines = 5,
                        )
                    },
                    colors = when {
                        manage && isChosen -> ChipDefaults.primaryChipColors()
                        manage -> ChipDefaults.secondaryChipColors()
                        else -> kindColors(ChipKind.LIST, ChipDefaults.secondaryChipColors())
                    },
                    enabled = !(manage && isActive),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
    }
}
