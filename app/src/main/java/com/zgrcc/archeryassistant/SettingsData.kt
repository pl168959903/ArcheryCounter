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

/** 設定「資料」分類（SettingsCategories 6）。 */
@Composable
fun SettingsData(vm: CounterViewModel) {
    val s = vm.settings
    val scope = rememberCoroutineScope()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val autoClean by s.autoClean.collectAsStateWithLifecycle()
    val autoCleanDays by s.autoCleanDays.collectAsStateWithLifecycle()
    val cleanLast by vm.cleanLast.collectAsStateWithLifecycle()
    // null = 還沒按過；true / false = 匯出成功 / 失敗

    // 儲存空間：按下才查；刪除原始資料要再確認一次
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var confirmRawDelete by remember { mutableStateOf(false) }
    var rawDeleteMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.refreshSessions() }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(SettingsCategories.name(6)) }
        item { ToggleChip(tr("自動清理", "Auto clean-up"), autoClean) { s.setAutoClean(it) } }
        if (autoClean) {
            item {
                Chip(
                    onClick = { s.setAutoCleanDays(AutoClean.nextDays(autoCleanDays)) },
                    label = { Text(tr("超過 $autoCleanDays 天", "Older than $autoCleanDays days"), maxLines = 1) },
                    secondaryLabel = { Text(tr("點一下切換 30／60／90", "Tap to cycle 30 / 60 / 90"), maxLines = 1) },
                    colors = kindColors(ChipKind.NORMAL, ChipDefaults.primaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
        item { Note(tr("開 App 時，超過設定天數、已結束的記錄會刪掉原始資料和波形／軌跡，統計與環數保留。校準記錄不動。無法復原。", "On launch, finished records older than the set days lose raw data and waveforms/traces; statistics and scores are kept. Calibration records are untouched. Cannot be undone.")) }
        item { Note(AutoClean.describe(cleanLast, ::formatBytes)) }

        item {
            Chip(
                onClick = {
                    scope.launch {
                        usage = vm.storageUsage()
                        confirmRawDelete = false
                        rawDeleteMessage = null
                    }
                },
                label = { Text(tr("查看佔用空間", "Check storage used"), maxLines = 1) },
                colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        usage?.let { u ->
            item {
                Body(
                    tr("記錄 ${u.sessionCount} 筆：${formatBytes(u.jsonBytes)}\n", "Records ${u.sessionCount}: ${formatBytes(u.jsonBytes)}\n") +
                        tr("原始資料 ${u.rawCount} 個：${formatBytes(u.rawBytes)}\n", "Raw files ${u.rawCount}: ${formatBytes(u.rawBytes)}\n") +
                        tr("匯出檔：${formatBytes(u.exportBytes)}", "Export files: ${formatBytes(u.exportBytes)}")
                )
            }
            if (u.rawCount > 0) {
                if (!confirmRawDelete) {
                    item {
                        Chip(
                            onClick = { confirmRawDelete = true },
                            label = { Text(tr("只刪除原始資料", "Delete raw data only"), maxLines = 1) },
                            colors = kindColors(ChipKind.DANGER, ChipDefaults.secondaryChipColors()),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                    item { Note(tr("記錄本身會保留，只清掉原始資料 CSV（進行中的那一筆不動）", "Records are kept; only the raw CSV files are removed (the active one is untouched)")) }
                } else {
                    item { Note(tr("確定刪除 ${u.rawCount} 個原始資料檔嗎？無法復原，記錄本身不受影響", "Delete ${u.rawCount} raw data files? This cannot be undone; the records are not affected")) }
                    item {
                        Chip(
                            onClick = {
                                scope.launch {
                                    val r = vm.deleteRawData()
                                    rawDeleteMessage = tr("已刪除 ${r.count} 個，釋出 ${formatBytes(r.bytes)}", "Deleted ${r.count}, freed ${formatBytes(r.bytes)}")
                                    usage = vm.storageUsage()
                                    confirmRawDelete = false
                                }
                            },
                            label = { Text(tr("確定刪除", "Delete"), maxLines = 1) },
                            colors = kindColors(ChipKind.DANGER, ChipDefaults.primaryChipColors()),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                    item {
                        Chip(
                            onClick = { confirmRawDelete = false },
                            label = { Text(tr("取消", "Cancel"), maxLines = 1) },
                            colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                }
            }
            rawDeleteMessage?.let { msg -> item { Note(msg) } }
        }


    }
}
