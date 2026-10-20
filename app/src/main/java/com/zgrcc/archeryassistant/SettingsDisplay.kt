package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

/** 設定「圖表與顯示」分類（SettingsCategories 5）。 */
@Composable
fun SettingsDisplay(vm: CounterViewModel) {
    val s = vm.settings
    val showTrend by s.showTrendChart.collectAsStateWithLifecycle()
    val liveWaveform by s.liveWaveform.collectAsStateWithLifecycle()
    val showSessionTrend by s.showSessionTrend.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val matchCompare by s.matchCompare.collectAsStateWithLifecycle()
    val paceAnalysis by s.paceAnalysis.collectAsStateWithLifecycle()
    val groupCompare by s.groupCompare.collectAsStateWithLifecycle()
    val personalBest by s.personalBest.collectAsStateWithLifecycle()
    val overviewOn by s.overview.collectAsStateWithLifecycle()
    val chipKinds by s.chipKinds.collectAsStateWithLifecycle()
    val live2d by s.live2d.collectAsStateWithLifecycle()
    val discSize by s.discSize.collectAsStateWithLifecycle()
    val discFullscreen by s.discFullscreen.collectAsStateWithLifecycle()
    val traceGyro by s.traceGyro.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshSessions() }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(SettingsCategories.name(5)) }
        item { ToggleChip(tr("趨勢圖", "Trend charts"), showTrend) { s.setShowTrendChart(it) } }
        item { Note(tr("記錄詳情頁顯示每箭晃動量的趨勢圖。只影響顯示，不影響檔案大小，立即生效", "Shows a per-shot shake trend chart in record details. Display only. Takes effect immediately")) }

        item { ToggleChip(tr("跨次趨勢", "Trend across sessions"), showSessionTrend) { s.setShowSessionTrend(it) } }
        item { Note(tr("記錄列表顯示每次的平均晃動量，並提供最近 20 次的趨勢圖。只影響顯示，立即生效", "Shows each session's average shake in the list and a trend chart of the last 20. Display only. Takes effect immediately")) }

        item { ToggleChip(tr("比賽對比", "Match comparison"), matchCompare) { s.setMatchCompare(it) } }
        item { Note(tr("記錄列表多一個「比賽對比」，把比賽紀錄的日期、總環數、平均晃動、時間到的趟數放在一起比，並畫趨勢圖。只影響顯示，立即生效", "Adds Match comparison to the records list: date, total score, average shake and timed-out ends side by side, with trend charts. Display only. Takes effect immediately")) }

        item { ToggleChip(tr("節奏分析", "Pace analysis"), paceAnalysis) { s.setPaceAnalysis(it) } }
        item { Note(tr("比賽紀錄詳情多一段節奏分析（第一箭等待、最後一箭剩幾秒、壓線趟數、用時），比賽對比也會列平均第一箭等待。只影響顯示，立即生效", "Adds pace analysis to match details (first-arrow wait, time left on the last arrow, tight ends, time used), and average first-arrow wait to Match comparison. Display only. Takes effect immediately")) }

        item { ToggleChip(tr("條件對比", "Condition comparison"), groupCompare) { s.setGroupCompare(it) } }
        item { Note(tr("記錄列表多一個「條件對比」，依距離和弓種分組比較平均晃動與環數，並標出最穩的一組。只影響顯示，立即生效", "Adds Compare conditions to the records list, grouping by distance and bow with average shake and score, and marking the steadiest group. Display only. Takes effect immediately")) }

        item { ToggleChip(tr("個人最佳", "Personal best"), personalBest) { s.setPersonalBest(it) } }
        item { Note(tr("在最穩的練習、同賽制下環數最高或最穩的比賽前面標 ★。只影響顯示，立即生效", "Marks the steadiest practice session, and the highest-scoring or steadiest match within the same format, with ★. Display only. Takes effect immediately")) }

        item { ToggleChip(tr("訓練總覽", "Training overview"), overviewOn) { s.setOverview(it) } }
        item { Note(tr("記錄列表多一個「訓練總覽」，看每週、每月的箭數、練習天數和連續練習天數。只影響顯示，立即生效", "Adds Training overview to the records list: arrows per week and month, practice days and streak. Display only. Takes effect immediately")) }

        item { ToggleChip(tr("即時晃動圓盤", "Live shake disc"), live2d) { s.setLive2d(it) } }
        item { Note(tr("偵測中主畫面顯示像跑車 G 力圖的圓盤：點是最近約 2 秒的手錶 x、y 位置，越集中在圓心越穩，旁邊有外圈大小和虛線圈（均方根半徑）。開啟後取代即時波形，畫圖較耗電。軸是手錶表面方向，取決於怎麼戴表。立即生效", "While detecting, the main screen shows a G-force style disc: the dots are the watch's x/y over the last ~2 s; tighter around the center is steadier. Shows the outer ring size and a dashed ring (RMS radius). Replaces the live waveform and uses more battery. Axes follow the watch face, so they depend on how you wear it. Takes effect immediately")) }

        item {
            Chip(
                onClick = { s.setTraceGyro(!traceGyro) },
                label = { Text(tr("2D 圖數據", "2D data"), maxLines = 1) },
                secondaryLabel = { Text(if (traceGyro) tr("角速度（°/s）", "Gyro (°/s)") else tr("加速度（m/s²）", "Accel (m/s²)"), maxLines = 1) },
                colors = kindColors(ChipKind.NORMAL, ChipDefaults.primaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item { Note(tr("即時圓盤和單箭 2D 軌跡用的數據：加速度看手腕晃動，角速度看瞄準的轉動（需要陀螺儀）。單箭軌跡只對切換之後的新記錄生效，舊記錄維持原來的類型", "Data used by the live disc and the per-shot 2D trace: accel shows wrist shake, gyro shows aim rotation (needs a gyroscope). Per-shot traces follow the setting at the time of recording; old records keep their type")) }

        item {
            Chip(
                onClick = { s.setDiscSize(DiscSize.next(discSize)) },
                label = { Text(tr("圓盤大小", "Disc size"), maxLines = 1) },
                secondaryLabel = { Text("${DiscSize.label(discSize)}（${DiscSize.dp(discSize)} dp）", maxLines = 1) },
                colors = kindColors(ChipKind.NORMAL, ChipDefaults.primaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item { Note(tr("即時圓盤、單箭頁和快速檢查裡的圓盤大小：小 100、中 130、大 160 dp，預設大。圓盤越大，下面的內容要多滑一點才看得到。立即生效", "Disc size in the live disc, shot page and Quick check: Small 100, Medium 130, Large 160 dp, default Large. A bigger disc means more scrolling to reach what is below. Takes effect immediately")) }

        item { ToggleChip(tr("點圓盤全屏", "Tap disc for full screen"), discFullscreen) { s.setDiscFullscreen(it) } }
        item { Note(tr("偵測中點一下主畫面的即時圓盤，放大成全屏；再點一下或往右滑返回。偵測照常運行。觸控鎖定時點不到。立即生效", "While detecting, tap the live disc on the main screen to go full screen; tap again or swipe right to return. Detection keeps running. Not tappable while touch-locked. Takes effect immediately")) }

        item { ToggleChip(tr("按鈕分色", "Colored buttons"), chipKinds) { s.setChipKinds(it) } }
        item { Note(tr("按鈕依用途上色：綠＝開始／確認，藍＝點進去是另一頁（標籤尾巴有 ›），黃＝清單裡的每一項（每筆記錄、每場比賽、每一箭），紅＝刪除或結束，灰＝一般操作。關閉則全部維持原本配色。立即生效", "Buttons are colored by purpose: green = start/confirm, blue = opens another page (label ends with ›), yellow = each item in a list (record, match, shot), red = delete or end, gray = ordinary actions. Off keeps the original look. Takes effect immediately")) }

        item { ToggleChip(tr("即時波形", "Live waveform"), liveWaveform) { s.setLiveWaveform(it) } }
        item { Note(tr("偵測中在主畫面顯示即時加速度波形。只影響顯示，但最耗電，立即生效", "Shows a live accel waveform on the main screen while detecting. Display only, but uses the most battery. Takes effect immediately")) }

    }
}
