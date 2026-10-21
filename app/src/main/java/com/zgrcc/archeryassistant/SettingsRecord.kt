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
    val flagFastShots by s.flagFastShots.collectAsStateWithLifecycle()
    val recordMeasuredHz by s.recordMeasuredHz.collectAsStateWithLifecycle()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(SettingsCategories.name(2)) }
        item { ToggleChip(tr("檢查可疑箭", "Review suspicious shots"), quickCheck) { s.setQuickCheck(it) } }
        item { Note(tr("記錄詳情有可疑箭時多「檢查可疑箭」：直接開第一支可疑箭的詳情，用 ‹ › 只在可疑箭之間跳，每支按「標記為正常」或「標記為誤判」，處理完自動跳下一支。", "Adds Review suspicious shots to record details when a record has suspicious shots: opens the first one's details, ‹ › jump between suspicious shots only; tap Mark as normal or Mark as false detection and it moves to the next.")) }

        item { ToggleChip(tr("記錄備註", "Record notes"), recordNote) { s.setRecordNote(it) } }
        item { Note(tr("記錄詳情可寫一句備註（最多 60 字），顯示在列表和網頁報告。", "Write a short note (up to 60 chars) in record details; shown in the list and web report.")) }
        item { ToggleChip(tr("標示可疑記錄", "Flag suspicious records"), flagSuspicious) { s.setFlagSuspicious(it) } }
        item { Note(tr("符合下列任一項會標「⚠ 可疑」：\n• 沒正常結束\n• 超過 2 分鐘沒偵測到箭\n• 有還沒確認的可疑箭（顯示支數，全部處理完就消失）\n箭數多不算可疑。校準記錄不判斷。", "A record is marked Suspicious if any of these applies:\n• It did not end normally\n• No arrows detected for over 2 minutes\n• It has suspicious shots you have not reviewed (count shown; disappears once all are handled)\nA high arrow count is not suspicious. Calibration records are not checked.")) }
        item { ToggleChip(tr("標示可疑的箭", "Flag suspicious shots"), flagFastShots) { s.setFlagFastShots(it) } }
        item { Note(tr("離上一箭不到一般間隔的一半、放箭後又幾乎沒動作的箭，在詳情的逐箭明細（橘紅色）與單箭頁標「⚠ 可疑」，只標示不會自動取消，由你標為正常或誤判", "Shots much closer to the previous one than usual and with almost no follow-through are marked Suspicious in the shot list and shot details. Only marked, never cancelled automatically; mark each one normal or false detection")) }
        item { ToggleChip(tr("記錄實測取樣率", "Record measured sample rate"), recordMeasuredHz) { s.setRecordMeasuredHz(it) } }
        item { Note(tr("結束時把手錶實際的取樣率寫進偵測摘要，例如設定 100 Hz 但手錶只給 50 Hz 就看得出來", "Writes the watch's actual sample rate into the detection summary when a record ends, so a requested 100 Hz that the watch only delivers at 50 Hz becomes visible")) }
        item { ToggleChip(tr("記錄篩選", "Records filter"), historyFilter) { s.setHistoryFilter(it) } }
        item { Note(tr("記錄頁多一個「篩選」按鈕，可依標籤和日期範圍過濾列表", "Adds a Filter button to the Records page to filter the list by tag and date range")) }

        item { ToggleChip(tr("記錄裝置型號", "Record device model"), recordDevice) { s.setRecordDevice(it) } }
        item { Note(tr("記錄檔會寫入手錶型號，方便分辨資料來源。App 版本、唯一碼與偵測設定一律寫入，不含個人資料。從下一次記錄開始", "Writes the watch model into records to tell sources apart. App version, unique ID and detection summary are always written; no personal data. Starts with the next record")) }

        item { ToggleChip(tr("錄製原始資料", "Record raw data"), recordRaw) { s.setRecordRaw(it) } }
        item { Note(tr("每次記錄另存 CSV（加速度、角速度），每 30 分鐘約 10 MB。下次記錄生效", "Saves a CSV (accel, gyro) for each record, about 10 MB per 30 min. Takes effect next record.")) }

        item {
            StepRow(
                text = tr("視窗 $windowSec 秒", "Window $windowSec s"),
                onMinus = { s.setWindowSec(windowSec - 1) },
                onPlus = { s.setWindowSec(windowSec + 1) },
            )
        }
        item { Note(tr("穩定度分析視窗：放箭前幾秒的晃動量。從下一次記錄開始", "Stability window: seconds of shake before the release. Starts with the next record")) }

        item { ToggleChip(tr("單箭 2D 軌跡", "Per-shot 2D trace"), recordTrace) { s.setRecordTrace(it) } }
        item { Note(tr("每箭另存放箭前手腕 x、y 軌跡，畫成圓盤圖，點越集中越穩。每箭約多 0.5～1 KB。下一箭生效", "Saves the wrist x, y trace before each release and draws it as a disc; tighter points mean steadier. About 0.5-1 KB per shot. Takes effect next shot.")) }

        item { ToggleChip(tr("單箭波形", "Per-shot waveform"), recordWaveform) { s.setRecordWaveform(it) } }
        item { Note(tr("每箭把放箭前後波形存進記錄，約 1 KB。下一箭生效", "Stores the waveform around each release, about 1 KB. Takes effect next shot.")) }

        item { ToggleChip(tr("放箭後跟隨動作", "Follow-through"), followThrough) { s.setFollowThrough(it) } }
        item { Note(tr("多記放箭後 1 秒的波形與晃動（看放箭後是否穩住）。放箭後 1 秒內就暫停的那一箭沒有。下一箭生效", "Also records 1 s after release (waveform and shake) to see whether you hold steady. A shot paused within 1 s has none. Takes effect next shot.")) }

        item { ToggleChip(tr("每箭環數輸入", "Score entry"), recordScore) { s.setRecordScore(it) } }
        item { Note(tr("在單箭頁輸入環數（0～10），統計與匯出會包含環數。關閉則完全不顯示", "Enter a score (0-10) on the shot page; statistics and exports include it. Off hides it completely")) }

        item { ToggleChip(tr("每箭自評", "Shot self-rating"), shotFeel) { s.setShotFeel(it) } }
        item { Note(tr("在單箭頁加「差／普通／好」三顆按鈕，記錄詳情會比較各自評的平均晃動", "Adds Bad / OK / Good buttons on the shot page; the detail page compares average shake per rating. Off by default")) }

        item { ToggleChip(tr("網頁報告按鈕", "Web report button"), reportView) { s.setReportView(it) } }
        item { Note(tr("記錄頁多「網頁報告」，用瀏覽器開啟全部記錄。只含「連結設定」選到的標籤。", "Adds Web report to the Records page to open all records in a browser. Only includes the tag chosen in Link settings.")) }

        item { ToggleChip(tr("條件標籤", "Condition tags"), tagsEnabled) { s.setTagsEnabled(it) } }
        item { Note(tr("新記錄自動帶上下面選的條件，記錄列表會顯示，跨次趨勢可以只比較相同條件。對之後的新記錄有效", "New records get the conditions chosen below, shown in the record list; trends can compare like with like. Applies to later records")) }
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
