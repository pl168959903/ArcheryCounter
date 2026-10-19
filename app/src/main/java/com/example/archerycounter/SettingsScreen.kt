package com.example.archerycounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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

@Composable
fun SettingsScreen(vm: CounterViewModel) {
    val s = vm.settings
    val keepScreenOn by s.keepScreenOn.collectAsStateWithLifecycle()
    val wakeLock by s.wakeLock.collectAsStateWithLifecycle()
    val recordRaw by s.recordRaw.collectAsStateWithLifecycle()
    val windowSec by s.windowSec.collectAsStateWithLifecycle()
    val showTrend by s.showTrendChart.collectAsStateWithLifecycle()
    val recordWaveform by s.recordWaveform.collectAsStateWithLifecycle()
    val liveWaveform by s.liveWaveform.collectAsStateWithLifecycle()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text("設定", style = MaterialTheme.typography.caption1) }

        item { ToggleChip("保持亮屏", keepScreenOn) { s.setKeepScreenOn(it) } }
        item { Note("應用開著時螢幕不休眠，立即生效") }

        item { ToggleChip("CPU 喚醒鎖", wakeLock) { s.setWakeLock(it) } }
        item { Note("偵測中保持 CPU 運作，即使螢幕被熄滅也繼續取資料。較耗電，立即生效") }

        item { ToggleChip("錄製原始資料", recordRaw) { s.setRecordRaw(it) } }
        item { Note("每次記錄另存一份 CSV（加速度與角速度），從下一次記錄開始") }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallRoundButton("−") { s.setWindowSec(windowSec - 1) }
                Text("視窗 $windowSec 秒")
                SmallRoundButton("+") { s.setWindowSec(windowSec + 1) }
            }
        }
        item { Note("穩定度分析視窗：放箭前幾秒的晃動量。從下一次記錄開始") }

        item { ToggleChip("趨勢圖", showTrend) { s.setShowTrendChart(it) } }
        item { Note("記錄詳情頁顯示每箭晃動量的趨勢圖。只影響顯示，不影響檔案大小，立即生效") }

        item { ToggleChip("單箭波形", recordWaveform) { s.setRecordWaveform(it) } }
        item { Note("每一箭把放箭前後的波形存進記錄，約 1 KB。關閉後記錄檔最小。從下一箭開始生效") }

        item { ToggleChip("即時波形", liveWaveform) { s.setLiveWaveform(it) } }
        item { Note("偵測中在主畫面顯示即時加速度波形。只影響顯示，但最耗電，立即生效") }

        if (!vm.gyroAvailable) {
            item { Note("此手錶沒有陀螺儀，只會記錄加速度晃動量") }
        }
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
