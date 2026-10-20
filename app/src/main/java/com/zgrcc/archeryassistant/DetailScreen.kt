package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/** 從存檔讀記錄的結果。詳情頁和單箭頁共用。 */
internal sealed interface SessionLoad {
    object Loading : SessionLoad
    object Missing : SessionLoad
    class Ready(val session: Session) : SessionLoad
}

private fun buildTimeline(s: Session): List<TimelineRow> {
    val rows = ArrayList<TimelineRow>(s.shots.size + s.manual.size)
    var validCount = 0
    var prevValidRel: Long? = null
    s.shots.forEachIndexed { index, sh ->
        if (sh.cancelled) {
            rows.add(TimelineRow(sh.relMs, 0, index, sh, null, 0))
        } else {
            validCount++
            val interval = prevValidRel?.let { (sh.relMs - it) / 1000f }
            rows.add(TimelineRow(sh.relMs, validCount, index, sh, interval, 0))
            prevValidRel = sh.relMs
        }
    }
    s.manual.forEach { m -> rows.add(TimelineRow(m.relMs, 0, -1, null, null, m.delta)) }
    rows.sortBy { it.relMs }
    return rows
}

@Composable
fun DetailScreen(vm: CounterViewModel, onOpenShot: (Int) -> Unit, onDeleted: () -> Unit, onOpenQuickCheck: () -> Unit) {
    val id by vm.selectedId.collectAsStateWithLifecycle()
    val activeId by vm.activeSessionId.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()

    // 記錄列表有變動（例如在單箭頁標記了誤判）就重新讀一次
    val load by produceState<SessionLoad>(
        initialValue = SessionLoad.Loading,
        key1 = id,
        key2 = sessions,
    ) {
        val sid = id
        val session = if (sid == null) null else vm.loadSession(sid)
        value = if (session == null) SessionLoad.Missing else SessionLoad.Ready(session)
    }

    when (val l = load) {
        SessionLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("載入中…", "Loading…"))
        }
        SessionLoad.Missing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("找不到這筆記錄", "Record not found"))
        }
        is SessionLoad.Ready -> DetailContent(
            vm = vm,
            session = l.session,
            isActive = l.session.id == activeId,
            onOpenShot = onOpenShot,
            onOpenQuickCheck = onOpenQuickCheck,
            onDelete = {
                vm.deleteSession(l.session.id)
                onDeleted()
            },
        )
    }
}

@Composable
private fun DetailContent(
    vm: CounterViewModel,
    session: Session,
    isActive: Boolean,
    onOpenShot: (Int) -> Unit,
    onOpenQuickCheck: () -> Unit,
    onDelete: () -> Unit,
) {
    val showTrend by vm.settings.showTrendChart.collectAsStateWithLifecycle()
    val showEnds by vm.settings.showEnds.collectAsStateWithLifecycle()
    val quickCheck by vm.settings.quickCheck.collectAsStateWithLifecycle()
    val arrowsPerEnd by vm.settings.arrowsPerEnd.collectAsStateWithLifecycle()
    val stats = remember(session) { computeStats(session) }
    val timeline = remember(session) { buildTimeline(session) }
    val ends = remember(session, arrowsPerEnd) { computeEnds(session, arrowsPerEnd) }
    val accelSeries = remember(session) { metricSeries(session, gyro = false) }
    val gyroSeries = remember(session) { metricSeries(session, gyro = true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val tagsEnabled by vm.settings.tagsEnabled.collectAsStateWithLifecycle()
    val paceOn by vm.settings.paceAnalysis.collectAsStateWithLifecycle()
    val bestOn by vm.settings.personalBest.collectAsStateWithLifecycle()
    val calAdvice by vm.settings.calAdvice.collectAsStateWithLifecycle()
    val allSessions by vm.sessions.collectAsStateWithLifecycle()
    val isSteadiest = remember(allSessions, session.id, bestOn) {
        bestOn && !session.calibration && session.comp == null && PersonalBest.steadiestId(allSessions) == session.id
    }
    val matchStars = remember(allSessions, session.id, bestOn) {
        if (!bestOn || session.comp == null) emptyList<String>() else {
            val rows = MatchCompare.rows(allSessions)
            buildList {
                if (session.id in PersonalBest.bestScoreIds(rows)) add(tr("★ 同賽制最高環數", "★ Highest score in this format"))
                if (session.id in PersonalBest.steadiestMatchIds(rows)) add(tr("★ 同賽制最穩", "★ Steadiest in this format"))
            }
        }
    }
    val pace = remember(session, paceOn) { if (paceOn && !session.calibration) PaceAnalyzer.analyze(session) else null }
    val scope = rememberCoroutineScope()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(Fmt.dateTime(session.startMs), style = MaterialTheme.typography.title3)
        }
        if (session.calibration) {
            item { Body(tr("校準錄製（不計入箭數與趨勢）", "Calibration (not counted in arrows or trends)")) }
            if (session.marks.isNotEmpty()) {
                item {
                    Body(
                        session.marks.joinToString("\n") { m ->
                            val end = m.endRelMs
                            "${Calibration.display(m.label)}　${Fmt.duration(m.startRelMs)}～" + if (end == null) tr("（未結束）", "(not finished)") else Fmt.duration(end)
                        }
                    )
                }
            }
        }
        item { Body(summaryText(session, stats, isActive)) }
        if (isSteadiest) {
            item { Body(tr("★ 個人最佳：這是你最穩的一次練習（平均晃動最低）", "★ Personal best: your steadiest practice session (lowest average shake)")) }
        }
        if (matchStars.isNotEmpty()) {
            item { Body(matchStars.joinToString("\n")) }
        }
        if (session.calibration && calAdvice && session.rawCsv && !isActive && session.endMs != null) {
            item { CalibrationAdviceBlock(vm, session.id) }
        }

        // 比賽模式（v1.9）：逐趟用時、逾時、箭數與晃動變化
        if (!session.calibration && session.comp != null) {
            val compSum = CompSummaryBuilder.build(session)
            if (compSum != null) {
                item { Text(tr("比賽統計", "Match summary"), style = MaterialTheme.typography.title3) }
                item { Body(compSummaryText(compSum, session.comp)) }
            }
            if (pace != null) {
                item { Text(tr("節奏分析", "Pace analysis"), style = MaterialTheme.typography.title3) }
                item { Body(paceText(pace)) }
            }
        }

        // 條件標籤：記錄時自動帶上，這裡可以事後修改（進行中的不能改）
        if (!session.calibration && (tagsEnabled || session.tags.isNotEmpty())) {
            item { SectionTitle(tr("條件", "Conditions")) }
            if (isActive) {
                item { Body(Tags.label(session.tags) ?: tr("未設定", "Not set")) }
            } else {
                item {
                    Chip(
                        onClick = {
                            val tags = Tags.rebuild(session.tags, Tags.nextDistance(Tags.distanceOf(session.tags)), Tags.bowOf(session.tags))
                            scope.launch { vm.setSessionTags(session.id, tags) }
                        },
                        label = { Text(tr("距離", "Distance"), maxLines = 1) },
                        secondaryLabel = { Text(Tags.distanceOf(session.tags)?.let { Tags.display(it) } ?: tr("未設定", "Not set"), maxLines = 1) },
                        colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item {
                    Chip(
                        onClick = {
                            val tags = Tags.rebuild(session.tags, Tags.distanceOf(session.tags), Tags.nextBow(Tags.bowOf(session.tags)))
                            scope.launch { vm.setSessionTags(session.id, tags) }
                        },
                        label = { Text(tr("弓種", "Bow"), maxLines = 1) },
                        secondaryLabel = { Text(Tags.bowOf(session.tags)?.let { Tags.display(it) } ?: tr("未設定", "Not set"), maxLines = 1) },
                        colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item { Note(tr("點一下切換選項", "Tap to change")) }
            }
        }

        item { SectionTitle(tr("統計", "Statistics")) }
        item { Body(statsText(session, stats)) }

        if (showTrend) {
            item { SectionTitle(tr("趨勢圖", "Trend charts")) }
            if (accelSeries.size >= 2) {
                item {
                    TrendChart(
                        title = tr("加速度晃動量", "Accel shake"),
                        unit = "m/s²",
                        series = accelSeries,
                        steadiest = stats.accelSteadiest,
                        shakiest = stats.accelShakiest,
                        decimals = 2,
                    )
                }
            }
            if (session.hasGyro && gyroSeries.size >= 2) {
                item {
                    TrendChart(
                        title = tr("角速度晃動量", "Gyro shake"),
                        unit = "°/s",
                        series = gyroSeries,
                        steadiest = stats.gyroSteadiest,
                        shakiest = stats.gyroShakiest,
                        decimals = 1,
                    )
                }
            }
            if (accelSeries.size < 2 && gyroSeries.size < 2) {
                item { Note(tr("至少要 2 箭有晃動量數據才畫得出趨勢圖", "At least 2 shots with shake data are needed for a trend chart")) }
            }
        }

        if (showEnds && ends.isNotEmpty()) {
            item { SectionTitle(tr("逐趟（每趟 $arrowsPerEnd 箭）", "Per end ($arrowsPerEnd arrows each)")) }
            ends.forEach { e -> item { Body(endText(session, e)) } }
        }

        item { SectionTitle(tr("逐箭明細", "Shot by shot")) }
        if (timeline.isEmpty()) {
            item { Note(tr("這次沒有偵測到放箭", "No release detected this time")) }
        } else {
            item { Note(tr("點一箭可看波形，也可以標記為誤判", "Tap a shot to see its waveform or mark it as a false detection")) }
            if (quickCheck && !isActive && !session.calibration) {
                item {
                    Chip(
                        onClick = onOpenQuickCheck,
                        label = { Text(tr("快速檢查", "Quick check") + navMark(), maxLines = 1) },
                        secondaryLabel = { Text(tr("逐箭滑動，一鍵標記誤判", "Step through shots, one-tap marking"), maxLines = 2) },
                        colors = kindColors(ChipKind.NAV, ChipDefaults.primaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
        }
        timeline.forEach { row ->
            item {
                val shot = row.shot
                if (shot == null) {
                    Body(manualText(session, row))
                } else {
                    Chip(
                        onClick = { onOpenShot(row.shotIndex) },
                        label = { Text(shotText(session, row, shot), style = MaterialTheme.typography.caption1, maxLines = 5) },
                        colors = if (shot.cancelled) ChipDefaults.childChipColors() else kindColors(ChipKind.LIST, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.95f),
                    )
                }
            }
        }

        if (session.rawCsv) {
            item { Note(tr("原始資料在手錶上的 CSV 檔，用 adb 取出，檔名：${session.id}.csv", "Raw data is a CSV on the watch; pull it with adb. File: ${session.id}.csv")) }
        }

        // 刪除放在最底，並且要再確認一次
        item {
            when {
                isActive -> Note(tr("進行中，暫停偵測後才能刪除", "In progress; pause detection before deleting"))
                !confirmDelete -> Chip(
                    onClick = { confirmDelete = true },
                    label = { Text(tr("刪除此記錄", "Delete this record"), maxLines = 1) },
                    colors = kindColors(ChipKind.DANGER, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
                else -> Note(tr("確定要刪除這筆記錄嗎？無法復原", "Delete this record? This cannot be undone"))
            }
        }
        if (confirmDelete && !isActive) {
            item {
                Chip(
                    onClick = onDelete,
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
}
