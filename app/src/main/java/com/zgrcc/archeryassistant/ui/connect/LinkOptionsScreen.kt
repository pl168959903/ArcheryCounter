package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text

/**
 * 連結設定（v1.18.1，從「連線」進入）：把分享、尋找、匯入相關的選項集中在一頁。
 * 順序：信任 → QR → 自動尋找 → 連線記錄 → PIN 錯誤震動 → 報告篩選 → 匯入選項。
 */
@Composable
fun LinkOptionsScreen(vm: CounterViewModel) {
    val s = vm.settings
    val trustOn by s.shareRemember.collectAsStateWithLifecycle()
    val qr by s.shareQr.collectAsStateWithLifecycle()
    val discover by s.importDiscover.collectAsStateWithLifecycle()
    val logOn by s.shareLog.collectAsStateWithLifecycle()
    val alert by s.shareAlert.collectAsStateWithLifecycle()
    val reportTag by s.reportTag.collectAsStateWithLifecycle()
    val preview by s.importPreview.collectAsStateWithLifecycle()
    val mark by s.importMark.collectAsStateWithLifecycle()
    val withSettings by s.importSettings.collectAsStateWithLifecycle()
    val overwrite by s.importOverwrite.collectAsStateWithLifecycle()
    val raw by s.importRaw.collectAsStateWithLifecycle()
    val zipOn by s.shareZip.collectAsStateWithLifecycle()
    val undoOn by s.importUndo.collectAsStateWithLifecycle()
    val autoStop by s.shareAutoStopMin.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val allTags = remember(sessions) { sessions.flatMap { it.tags }.distinct().sorted() }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(tr("連結設定", "Link settings")) }

        item { ToggleChip(tr("記住已信任的裝置", "Remember trusted devices"), trustOn) { s.setShareRemember(it) } }
        item { Note(tr("網頁輸入 PIN 頁多「記住這個裝置」勾選框；別台 App 通過 PIN 後自動記住。\n• 憑證 1 年，最多 5 支，可在「連線」頁改名、刪除", "The web PIN page gets a Remember this device checkbox; another device's app is remembered automatically after a correct PIN.\n• Valid 1 year, up to 5 devices; rename or remove them on the Connection page")) }

        item { ToggleChip(tr("顯示 QR Code", "Show QR code"), qr) { s.setShareQr(it) } }
        item { Note(tr("分享中顯示 QR Code（含網址和 PIN），手機相機掃描即可開啟。", "Shows a QR code (address and PIN) while sharing; scan it with a phone camera to open.")) }

        item { ToggleChip(tr("區域網自動尋找", "LAN auto-discovery"), discover) { s.setImportDiscover(it) } }
        item { Note(tr("分享時讓同一 Wi-Fi 的裝置找得到你，「連線」頁也會列出找到的裝置。有些網路不支援，找不到請手動輸入位址。下次開始分享生效", "Lets devices on the same Wi-Fi find you while sharing, and lists the devices found on the Connection page. Some networks do not support it; enter the address manually if none is found. Takes effect the next time sharing starts.")) }

        item { ToggleChip(tr("記錄連線", "Log connections"), logOn) { s.setShareLog(it) } }
        item { Note(tr("記錄今天誰連進來（位址、瀏覽器或 App、成功或失敗），只存在這支裝置，過午夜清掉，最多 100 筆。", "Logs who connected today (address, browser or app, success or failure), kept only on this device, cleared after midnight, up to 100 entries.")) }

        item {
            Chip(
                onClick = { s.setShareAutoStopMin(ShareAutoStop.next(autoStop)) },
                label = { Text(tr("分享自動結束", "Auto-stop sharing"), maxLines = 1) },
                secondaryLabel = { Text(ShareAutoStop.label(autoStop), maxLines = 1) },
                colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item { Note(tr("點一下切換：\n• 不自動結束\n• 10 分鐘\n• 30 分鐘\n• 60 分鐘\n時間到自動停止分享並震動一次。下次開始分享生效", "Tap to switch:\n• Never\n• 10 min\n• 30 min\n• 60 min\nSharing stops and buzzes once when the time is up. Takes effect the next time sharing starts.")) }
        item { ToggleChip(tr("分享打包下載", "Download-all zip"), zipOn) { s.setShareZip(it) } }
        item { Note(tr("網頁首頁多「下載全部記錄（zip）」，含全部記錄與設定備份，不含原始資料。下次開始分享生效", "The web page gets Download all records (zip) with all records and the settings backup, without raw data. Takes effect the next time sharing starts.")) }
        item { ToggleChip(tr("PIN 輸入錯誤時震動", "Vibrate on wrong PIN"), alert) { s.setShareAlert(it) } }
        item { Note(tr("有人輸入錯誤 PIN 時震動兩下。", "Buzzes twice when someone enters a wrong PIN.")) }

        item {
            Chip(
                onClick = {
                    val order = listOf("") + allTags
                    val i = order.indexOf(reportTag)
                    s.setReportTag(order[(i + 1) % order.size])
                },
                label = { Text(tr("報告篩選標籤", "Report tag filter"), maxLines = 1) },
                secondaryLabel = { Text(if (reportTag.isBlank()) tr("全部記錄", "All records") else reportTag, maxLines = 1) },
                colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item { Note(tr("點一下依序切換：全部 → 各標籤。記錄頁報告、匯出報告和分享給別台的報告都只含選到的標籤。", "Tap to cycle: all, then each tag. The Records page report, the exported report and the one shared to other devices only include the chosen tag.")) }

        item { SectionTitle(tr("匯入", "Import")) }
        item { ToggleChip(tr("匯入前預覽", "Preview before import"), preview) { s.setImportPreview(it) } }
        item { Note(tr("先列出將新增和已有的筆數，確認後才寫入。", "Lists how many will be added or already exist, and only writes after you confirm.")) }
        item { ToggleChip(tr("標示匯入來源", "Mark import source"), mark) { s.setImportMark(it) } }
        item { Note(tr("列表顯示「⇩ 來源裝置」，報告標「來自 …」。", "The list shows the source device and the report marks From …")) }
        item { ToggleChip(tr("一併取得設定備份", "Also get settings backup"), withSettings) { s.setImportSettings(it) } }
        item { Note(tr("取回對方的設定備份檔，不會自動套用。", "Also fetches the other device's settings backup; it is not applied automatically.")) }
        item { ToggleChip(tr("覆蓋已有記錄", "Overwrite existing"), overwrite) { s.setImportOverwrite(it) } }
        item { Note(tr("對方的版本會蓋掉本機同一筆，你在這台改過的自評會被取代。", "The other device's version replaces the same local record; self-ratings you changed here are lost.")) }
        item { ToggleChip(tr("可復原匯入", "Undo import"), undoOn) { s.setImportUndo(it) } }
        item { Note(tr("匯入後多「復原這次匯入」，只刪新增的；覆蓋掉的無法還原。", "After an import, Undo this import deletes only what it added; overwritten records cannot be restored.")) }
        item { ToggleChip(tr("同時匯入原始資料", "Include raw data"), raw) { s.setImportRaw(it) } }
        item { Note(tr("一併抓原始資料 CSV，檔案大、較慢。", "Also fetches raw data CSV; large and slower.")) }
    }
}
