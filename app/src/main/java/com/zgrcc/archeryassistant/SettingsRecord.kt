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

/** 設定「記錄」分類（SettingsCategories 2）。 */
@Composable
fun SettingsRecord(vm: CounterViewModel) {
    val s = vm.settings
    val recordRaw by s.recordRaw.collectAsStateWithLifecycle()
    val reportView by s.reportView.collectAsStateWithLifecycle()
    val windowSec by s.windowSec.collectAsStateWithLifecycle()
    val recordWaveform by s.recordWaveform.collectAsStateWithLifecycle()
    val followThrough by s.followThrough.collectAsStateWithLifecycle()
    val recordScore by s.recordScore.collectAsStateWithLifecycle()
    val tagsEnabled by s.tagsEnabled.collectAsStateWithLifecycle()
    val distanceTag by s.distanceTag.collectAsStateWithLifecycle()
    val bowTag by s.bowTag.collectAsStateWithLifecycle()
    val shotFeel by s.shotFeel.collectAsStateWithLifecycle()
    val recordTrace by s.recordTrace.collectAsStateWithLifecycle()
    val quickCheck by s.quickCheck.collectAsStateWithLifecycle()
    val recordDevice by s.recordDevice.collectAsStateWithLifecycle()
    val recordNote by s.recordNote.collectAsStateWithLifecycle()
    val flagSuspicious by s.flagSuspicious.collectAsStateWithLifecycle()
    val historyFilter by s.historyFilter.collectAsStateWithLifecycle()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(SettingsCategories.name(2)) }
        item { ToggleChip(tr("快速檢查", "Quick check"), quickCheck) { s.setQuickCheck(it) } }
        item { Note(tr("記錄詳情多一個「快速檢查」，逐箭滑動看波形，一鍵標記誤判或恢復。只影響顯示，立即生效", "Adds Quick check to record details: step through shots with waveforms and mark or restore false detections in one tap. Display only. Takes effect immediately")) }

        item { ToggleChip(tr("記錄備註", "Record notes"), recordNote) { s.setRecordNote(it) } }
        item { Note(tr("記錄詳情可寫一句備註（最多 60 字），顯示在記錄列表和網頁報告。立即生效", "Write a short note (up to 60 chars) in record details; shown in the list and web report. Takes effect immediately")) }
        item { ToggleChip(tr("標示可疑記錄", "Flag suspicious records"), flagSuspicious) { s.setFlagSuspicious(it) } }
        item { Note(tr("沒正常結束、超過 2 分鐘卻沒偵測到箭、或箭數是中位數 2 倍以上的記錄，會標「⚠ 可疑」。校準不判斷。立即生效", "Records that did not end normally, had no arrows for over 2 minutes, or have over twice the median count are marked Suspicious. Calibration is not checked. Takes effect immediately")) }
        item { ToggleChip(tr("記錄篩選", "Records filter"), historyFilter) { s.setHistoryFilter(it) } }
        item { Note(tr("記錄頁多一個「篩選」按鈕，可依標籤和日期範圍過濾列表。立即生效", "Adds a Filter button to the Records page to filter the list by tag and date range. Takes effect immediately")) }

        item { ToggleChip(tr("記錄裝置型號", "Record device model"), recordDevice) { s.setRecordDevice(it) } }
        item { Note(tr("記錄檔會寫入手錶型號，方便分辨資料來源。App 版本、唯一碼與偵測設定一律寫入，不含個人資料。從下一次記錄開始", "Writes the watch model into records to tell sources apart. App version, unique ID and detection summary are always written; no personal data. Starts with the next record")) }

        item { ToggleChip(tr("錄製原始資料", "Record raw data"), recordRaw) { s.setRecordRaw(it) } }
        item { Note(tr("每次記錄另存一份 CSV（加速度與角速度）。每 30 分鐘約 10 MB 以上，從下一次記錄開始", "Saves a CSV of accel and gyro for each record. 10 MB or more per 30 min. Starts with the next record")) }

        item {
            StepRow(
                text = tr("視窗 $windowSec 秒", "Window $windowSec s"),
                onMinus = { s.setWindowSec(windowSec - 1) },
                onPlus = { s.setWindowSec(windowSec + 1) },
            )
        }
        item { Note(tr("穩定度分析視窗：放箭前幾秒的晃動量。從下一次記錄開始", "Stability window: seconds of shake before the release. Starts with the next record")) }

        item { ToggleChip(tr("單箭 2D 軌跡", "Per-shot 2D trace"), recordTrace) { s.setRecordTrace(it) } }
        item { Note(tr("每一箭另存放箭前（分析視窗內）手錶 x、y 兩軸的軌跡，在單箭頁和快速檢查畫成類似跑車 G 力圖的圓盤，點越集中越穩。每箭約多 0.5～1 KB，只對之後的新記錄生效。資料來源見「圖表與顯示」的「2D 圖數據」", "Saves each shot's x/y trace before release (within the analysis window), drawn as a G-force style disc in the shot page and Quick check; tighter means steadier. About 0.5-1 KB more per shot; only new records. The data source is set by 2D data under Charts & display")) }

        item { ToggleChip(tr("單箭波形", "Per-shot waveform"), recordWaveform) { s.setRecordWaveform(it) } }
        item { Note(tr("每一箭把放箭前後的波形存進記錄，約 1 KB。關閉後記錄檔最小。從下一箭開始生效", "Saves the waveform around each release, about 1 KB. Off gives the smallest files. Starts with the next shot")) }

        item { ToggleChip(tr("放箭後跟隨動作", "Follow-through"), followThrough) { s.setFollowThrough(it) } }
        item { Note(tr("多記放箭後 1 秒的波形與晃動量（持弓手臂放箭後是否穩住）。放箭後 1 秒內就暫停的那一箭沒有這項。從下一箭開始生效", "Also records 1 s after release (does the bow arm stay steady). Not available for a shot paused within 1 s. Starts with the next shot")) }

        item { ToggleChip(tr("每箭環數輸入", "Score entry"), recordScore) { s.setRecordScore(it) } }
        item { Note(tr("在單箭頁輸入環數（0～10），統計與匯出會包含環數。關閉則完全不顯示，立即生效", "Enter a score (0-10) on the shot page; statistics and exports include it. Off hides it completely. Takes effect immediately")) }

        item { ToggleChip(tr("每箭自評", "Shot self-rating"), shotFeel) { s.setShotFeel(it) } }
        item { Note(tr("在單箭頁和快速檢查加「差／普通／好」三顆按鈕，記錄詳情會比較各自評的平均晃動。預設關閉，立即生效", "Adds Bad / OK / Good buttons on the shot page and Quick check; the detail page compares average shake per rating. Off by default. Takes effect immediately")) }

        item { ToggleChip(tr("網頁報告按鈕", "Web report button"), reportView) { s.setReportView(it) } }
        item { Note(tr("開啟後，記錄頁多一個「網頁報告」按鈕，在這支裝置上直接看全部記錄的報告（同 report.html，不含腳本）。手機上比手錶好讀。報告只包含「連線 → 連結設定」裡選的標籤。預設開", "When on, the Records page gets a Web report button that shows the report of all records right on this device (same as report.html, no scripts). Easier to read on a phone than a watch. Only records with the tag chosen under Connection > Link settings are included. On by default")) }

        item { ToggleChip(tr("條件標籤", "Condition tags"), tagsEnabled) { s.setTagsEnabled(it) } }
        item { Note(tr("新記錄自動帶上下面選的條件，記錄列表會顯示，跨次趨勢可以只比較相同條件。立即生效，對之後的新記錄有效", "New records get the conditions chosen below, shown in the record list; trends can compare like with like. Applies to later records")) }
        if (tagsEnabled) {
            item {
                Chip(
                    onClick = { s.setDistanceTag(Tags.nextDistance(distanceTag.ifEmpty { null })) },
                    label = { Text(tr("距離", "Distance"), maxLines = 1) },
                    secondaryLabel = { Text(if (distanceTag.isEmpty()) tr("未設定", "Not set") else Tags.display(distanceTag), maxLines = 1) },
                    colors = if (distanceTag.isEmpty()) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = { s.setBowTag(Tags.nextBow(bowTag.ifEmpty { null })) },
                    label = { Text(tr("弓種", "Bow"), maxLines = 1) },
                    secondaryLabel = { Text(if (bowTag.isEmpty()) tr("未設定", "Not set") else Tags.display(bowTag), maxLines = 1) },
                    colors = if (bowTag.isEmpty()) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item { Note(tr("點一下切換選項，最後一個選項之後回到「未設定」", "Tap to cycle; after the last option it goes back to Not set")) }
        }


        if (!vm.gyroAvailable) {
            item { Note(tr("此手錶沒有陀螺儀，只會記錄加速度晃動量", "This watch has no gyroscope; only accel shake is recorded")) }
        }
    }
}
