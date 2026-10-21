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
        item { Note(tr("開啟後：網頁輸入 PIN 的頁面多一個「記住這個裝置」勾選框（預設不勾），勾選後該瀏覽器之後免輸入 PIN；別台裝置的 App 通過 PIN 後也會自動記住。憑證 1 年，最多 5 支裝置，可在「連線」頁改名或刪除。注意分享是普通 HTTP、沒有加密，同一個 Wi-Fi 的人理論上可能偷到憑證。預設開", "When on: the PIN page gets a Remember this device checkbox (unticked by default) and that browser then skips the PIN; another device's app is remembered automatically after a correct PIN. Valid 1 year, up to 5 devices; rename or remove them on the Connection page. Note sharing is plain HTTP without encryption, so someone on the same Wi-Fi could in theory capture the token. On by default")) }

        item { ToggleChip(tr("顯示 QR Code", "Show QR code"), qr) { s.setShareQr(it) } }
        item { Note(tr("分享中在畫面上顯示 QR Code（含網址和 PIN），用手機相機掃描就能開，不必手打。預設開", "While sharing, shows a QR code (address and PIN) you can scan with the phone camera instead of typing. On by default")) }

        item { ToggleChip(tr("區域網自動尋找", "LAN auto-discovery"), discover) { s.setImportDiscover(it) } }
        item { Note(tr("開啟後：分享時讓同一個 Wi-Fi 的另一台裝置找得到你，「連線」頁也會列出找到的裝置。用系統的 mDNS，有些網路不支援，找不到時請改手動輸入位址。需要重新開始分享才生效。預設開", "When on: while sharing, other devices on the same Wi-Fi can find you, and the Connection page lists the devices found. Uses system mDNS, which some networks block; enter the address manually if nothing is found. Restart sharing to apply. On by default")) }

        item { ToggleChip(tr("記錄連線", "Log connections"), logOn) { s.setShareLog(it) } }
        item { Note(tr("開啟後記錄今天誰連進來（位址、瀏覽器或 App、成功或失敗），只存在這支裝置上，過了午夜自動清掉，最多 100 筆。預設開", "When on, records who connected today (address, browser or app, success or failure), stored only on this device, cleared after midnight, up to 100 entries. On by default")) }

        item {
            Chip(
                onClick = { s.setShareAutoStopMin(ShareAutoStop.next(autoStop)) },
                label = { Text(tr("分享自動結束", "Auto-stop sharing"), maxLines = 1) },
                secondaryLabel = { Text(ShareAutoStop.label(autoStop), maxLines = 1) },
                colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item { Note(tr("點一下切換：不自動結束／10／30／60 分鐘。時間到自動停止分享並震動一次。預設 10 分鐘。下次開始分享時生效", "Tap to cycle: never / 10 / 30 / 60 min. Sharing stops by itself and buzzes once. Default 10 min. Takes effect the next time sharing starts")) }
        item { ToggleChip(tr("分享打包下載", "Download-all zip"), zipOn) { s.setShareZip(it) } }
        item { Note(tr("開啟：網頁首頁多一個「下載全部記錄（zip）」，含全部記錄與設定備份，不含原始資料。下次開始分享時生效。預設開", "On: the web page gets a Download all records (zip) link with all records and the settings backup, without raw data. Takes effect the next time sharing starts. Default on")) }
        item { ToggleChip(tr("PIN 輸入錯誤時震動", "Vibrate on wrong PIN"), alert) { s.setShareAlert(it) } }
        item { Note(tr("有人輸入錯誤 PIN 時震動兩下提醒。預設關閉", "Two short vibrations when someone enters a wrong PIN. Off by default")) }

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
        item { Note(tr("點一下依序切換：全部記錄 → 各個標籤（例如距離、弓種）。記錄頁的網頁報告、匯出報告和區網分享給別台的報告都只包含選到標籤的記錄。預設全部", "Tap to cycle: all records, then each tag (e.g. distance, bow). The web report on the Records page, the exported report and the report shared to other devices only include records with the chosen tag. Default: all")) }

        item { SectionTitle(tr("匯入", "Import")) }
        item { ToggleChip(tr("匯入前預覽", "Preview before import"), preview) { s.setImportPreview(it) } }
        item { Note(tr("開啟：按「開始匯入」先列出將新增和已有的筆數，確認後才寫入。預設開", "On: Start import first lists how many will be added or already exist, and writes only after you confirm. On by default")) }
        item { ToggleChip(tr("標示匯入來源", "Mark import source"), mark) { s.setImportMark(it) } }
        item { Note(tr("開啟：記錄列表顯示「⇩ 來源裝置名稱」，報告也標示「來自 …」。不改記錄檔本身。預設開", "On: the record list shows ⇩ and the source device name, and the report says From …. Record files are not modified. On by default")) }
        item { ToggleChip(tr("一併取得設定備份", "Also get settings backup"), withSettings) { s.setImportSettings(it) } }
        item { Note(tr("開啟：若對方有設定備份檔，一併取回（不會自動套用，要到「連線」頁按「還原設定」）。預設關", "On: if the other device has a settings backup file, fetch it too (not applied automatically; tap Restore settings on the Connection page). Off by default")) }
        item { ToggleChip(tr("覆蓋已有記錄", "Overwrite existing"), overwrite) { s.setImportOverwrite(it) } }
        item { Note(tr("開啟：對方的版本會蓋掉本機同一筆（你在這台改過的自評等會被取代）。預設關", "On: the other device's copy replaces the local one (edits like self-ratings made here are lost). Off by default")) }
        item { ToggleChip(tr("可復原匯入", "Undo import"), undoOn) { s.setImportUndo(it) } }
        item { Note(tr("開啟：匯入完成後多一顆「復原這次匯入」，只刪這次新增的記錄；覆蓋掉的無法還原。預設開", "On: after an import, an Undo button deletes the records this import added; overwritten ones cannot be restored. Default on")) }
        item { ToggleChip(tr("同時匯入原始資料", "Include raw data"), raw) { s.setImportRaw(it) } }
        item { Note(tr("開啟：一併抓原始資料 CSV，檔案大、較慢。預設關", "On: also fetch raw data CSV; large and slower. Off by default")) }
    }
}
