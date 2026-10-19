package com.example.archerycounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
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

@Composable
fun SettingsScreen(vm: CounterViewModel) {
    val s = vm.settings
    val scope = rememberCoroutineScope()

    val keepScreenOn by s.keepScreenOn.collectAsStateWithLifecycle()
    val wakeLock by s.wakeLock.collectAsStateWithLifecycle()
    val recordRaw by s.recordRaw.collectAsStateWithLifecycle()
    val windowSec by s.windowSec.collectAsStateWithLifecycle()
    val showTrend by s.showTrendChart.collectAsStateWithLifecycle()
    val recordWaveform by s.recordWaveform.collectAsStateWithLifecycle()
    val liveWaveform by s.liveWaveform.collectAsStateWithLifecycle()
    val showEnds by s.showEnds.collectAsStateWithLifecycle()
    val arrowsPerEnd by s.arrowsPerEnd.collectAsStateWithLifecycle()
    val endVibration by s.endVibration.collectAsStateWithLifecycle()
    val followThrough by s.followThrough.collectAsStateWithLifecycle()
    val showSessionTrend by s.showSessionTrend.collectAsStateWithLifecycle()
    val recordScore by s.recordScore.collectAsStateWithLifecycle()
    val stillThreshold by s.stillThreshold.collectAsStateWithLifecycle()
    val cooldownSec by s.cooldownSec.collectAsStateWithLifecycle()

    // null = 還沒按過；true / false = 匯出成功 / 失敗
    var exportResult by remember { mutableStateOf<Boolean?>(null) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text("設定", style = MaterialTheme.typography.caption1) }

        // ---- 一般 ----
        item { SectionTitle("一般") }
        item { ToggleChip("保持亮屏", keepScreenOn) { s.setKeepScreenOn(it) } }
        item { Note("應用開著時螢幕不休眠，立即生效") }

        item { ToggleChip("CPU 喚醒鎖", wakeLock) { s.setWakeLock(it) } }
        item { Note("偵測中保持 CPU 運作，即使螢幕被熄滅也繼續取資料。較耗電，立即生效") }

        // ---- 偵測 ----
        item { SectionTitle("偵測") }
        item {
            StepRow(
                text = "靜止 $stillThreshold",
                onMinus = { s.setStillThreshold(stillThreshold - 1) },
                onPlus = { s.setStillThreshold(stillThreshold + 1) },
            )
        }
        item {
            Note("放箭前要先靜止到合力低於此值（m/s²，預設 5）。調大較容易算進去，但容易誤判；調小較嚴格，可能漏判。立即生效")
        }
        item {
            StepRow(
                text = "冷卻 $cooldownSec 秒",
                onMinus = { s.setCooldownSec(cooldownSec - 1) },
                onPlus = { s.setCooldownSec(cooldownSec + 1) },
            )
        }
        item { Note("兩次計數之間至少間隔多久（預設 3 秒）。調短可能把一次放箭算成兩次；調長可能漏掉連續的兩箭。立即生效") }

        // ---- 記錄 ----
        item { SectionTitle("記錄") }
        item { ToggleChip("錄製原始資料", recordRaw) { s.setRecordRaw(it) } }
        item { Note("每次記錄另存一份 CSV（加速度與角速度）。每 30 分鐘約 10 MB 以上，從下一次記錄開始") }

        item {
            StepRow(
                text = "視窗 $windowSec 秒",
                onMinus = { s.setWindowSec(windowSec - 1) },
                onPlus = { s.setWindowSec(windowSec + 1) },
            )
        }
        item { Note("穩定度分析視窗：放箭前幾秒的晃動量。從下一次記錄開始") }

        item { ToggleChip("單箭波形", recordWaveform) { s.setRecordWaveform(it) } }
        item { Note("每一箭把放箭前後的波形存進記錄，約 1 KB。關閉後記錄檔最小。從下一箭開始生效") }

        item { ToggleChip("放箭後跟隨動作", followThrough) { s.setFollowThrough(it) } }
        item { Note("多記放箭後 1 秒的波形與晃動量（持弓手臂放箭後是否穩住）。放箭後 1 秒內就暫停的那一箭沒有這項。從下一箭開始生效") }

        item { ToggleChip("每箭環數輸入", recordScore) { s.setRecordScore(it) } }
        item { Note("在單箭頁輸入環數（0～10），統計與匯出會包含環數。關閉則完全不顯示，立即生效") }

        // ---- 分趟 ----
        item { SectionTitle("分趟") }
        item { ToggleChip("分趟統計", showEnds) { s.setShowEnds(it) } }
        item { Note("詳情頁顯示逐趟統計，並在滿一趟時震動提醒。兩箭間隔超過 60 秒也算新的一趟。立即生效") }
        if (showEnds) {
            item {
                StepRow(
                    text = "每趟 $arrowsPerEnd 箭",
                    onMinus = { s.setArrowsPerEnd(arrowsPerEnd - 1) },
                    onPlus = { s.setArrowsPerEnd(arrowsPerEnd + 1) },
                )
            }
            item {
                Chip(
                    onClick = {
                        val next = EndVibration.next(endVibration)
                        s.setEndVibration(next)
                        vm.buzzEnd(next) // 選了就先震一下讓你感覺
                    },
                    label = { Text("滿趟震動", maxLines = 1) },
                    secondaryLabel = { Text(EndVibration.label(endVibration), maxLines = 1) },
                    colors = if (endVibration == EndVibration.OFF) ChipDefaults.secondaryChipColors()
                    else ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item { Note("點一下切換樣式並試震：三連震 → 兩短一長 → 一次長震 → 關閉。平常每一箭只輕震一下") }
        }

        // ---- 圖表與顯示 ----
        item { SectionTitle("圖表") }
        item { ToggleChip("趨勢圖", showTrend) { s.setShowTrendChart(it) } }
        item { Note("記錄詳情頁顯示每箭晃動量的趨勢圖。只影響顯示，不影響檔案大小，立即生效") }

        item { ToggleChip("跨次趨勢", showSessionTrend) { s.setShowSessionTrend(it) } }
        item { Note("記錄列表顯示每次的平均晃動量，並提供最近 20 次的趨勢圖。只影響顯示，立即生效") }

        item { ToggleChip("即時波形", liveWaveform) { s.setLiveWaveform(it) } }
        item { Note("偵測中在主畫面顯示即時加速度波形。只影響顯示，但最耗電，立即生效") }

        // ---- 資料 ----
        item { SectionTitle("資料") }
        item {
            Chip(
                onClick = {
                    scope.launch { exportResult = vm.exportSummary() }
                },
                label = { Text("匯出摘要", maxLines = 1) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item {
            when (exportResult) {
                null -> Note("把全部記錄整理成 summary.csv（每次一行）和 shots.csv（每箭一行），放在 sessions 資料夾，用 adb 一起取出")
                true -> Note("已匯出 summary.csv 和 shots.csv 到 sessions 資料夾")
                false -> Note("匯出失敗，請確認手錶儲存空間")
            }
        }

        if (!vm.gyroAvailable) {
            item { Note("此手錶沒有陀螺儀，只會記錄加速度晃動量") }
        }
    }
}

/** 「− 文字 +」一列，用來調整數值。 */
@Composable
private fun StepRow(text: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SmallRoundButton("−", onMinus)
        Text(text)
        SmallRoundButton("+", onPlus)
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Chip(
        onClick = { onChange(!on) },
        label = { Text(label, maxLines = 1) },
        secondaryLabel = { Text(if (on) "開" else "關") },
        colors = if (on) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(0.9f),
    )
}
