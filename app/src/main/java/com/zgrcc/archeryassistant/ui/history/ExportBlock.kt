package com.zgrcc.archeryassistant

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import kotlinx.coroutines.launch

/** 目前是不是手機畫面（設定的「畫面模式」，自動時看是不是手錶）。匯出功能只在手機畫面出現（v1.24）。 */
@Composable
fun rememberPhoneMode(vm: CounterViewModel): Boolean {
    val setting by vm.settings.phoneMode.collectAsStateWithLifecycle()
    return PhoneMode.resolve(setting, vm.isWatch)
}

/**
 * 匯出區塊（v1.24，只在手機畫面使用）：先把 [ids] 打包成一個 zip，再選「儲存到手機」（系統另存新檔）或「分享」。
 * 選取或「含原始資料」改變時，已建好的檔案作廢，重新按一次。[single] 為 true 時按鈕寫「匯出這筆」。
 */
@Composable
fun ExportBlock(vm: CounterViewModel, ids: Set<String>, single: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val hasRaw = remember(sessions, ids) { RecordExport.anyRaw(sessions, ids) }
    var withRaw by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var ready by remember(ids, withRaw) { mutableStateOf<ExportFile?>(null) }
    var message by remember(ids, withRaw) { mutableStateOf<String?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(RecordExport.ZIP_MIME)) { uri ->
        val f = ready
        if (uri != null && f != null) {
            scope.launch {
                message = if (vm.saveExport(f.file, uri)) tr("已儲存到手機", "Saved to the phone")
                else tr("儲存失敗，請確認儲存空間", "Save failed; check storage")
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (hasRaw) {
            ToggleChip(tr("含原始資料", "Include raw data"), withRaw) { withRaw = it }
            if (withRaw) Note(tr("原始資料檔很大，匯出較慢", "Raw data files are large; exporting is slower"))
        }
        val f = ready
        if (f == null) {
            Chip(
                onClick = {
                    scope.launch {
                        busy = true
                        message = null
                        val r = vm.buildExport(ids, withRaw)
                        busy = false
                        ready = r
                        if (r == null) message = tr("匯出失敗，沒有可匯出的記錄或儲存空間不足", "Export failed: nothing to export or not enough space")
                    }
                },
                label = {
                    Text(
                        when {
                            busy -> tr("打包中…", "Packing…")
                            single -> tr("匯出這筆", "Export this record")
                            else -> tr("匯出已選 ${ids.size} 筆", "Export ${ids.size} selected")
                        },
                        maxLines = 1,
                    )
                },
                colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                enabled = ids.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        } else {
            Note(tr("已打包 ${f.records} 筆（${formatBytes(f.bytes)}）：${f.name}", "Packed ${f.records} record(s) (${formatBytes(f.bytes)}): ${f.name}"))
            Chip(
                onClick = { saver.launch(f.name) },
                label = { Text(tr("儲存到手機", "Save to phone"), maxLines = 1) },
                colors = kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
            Chip(
                onClick = {
                    if (!ExternalOpen.shareFile(ctx, f.file, RecordExport.ZIP_MIME)) {
                        message = tr("找不到可以分享的程式", "No app found to share with")
                    }
                },
                label = { Text(tr("分享", "Share"), maxLines = 1) },
                colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        message?.let { Note(it) }
    }
}
