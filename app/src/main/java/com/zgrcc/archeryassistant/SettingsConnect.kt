package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * 設定「連線」分類（SettingsCategories 7，v1.16.3）：區網分享、信任裝置、連線記錄。
 * 離開這一頁就停止分享；分享中螢幕保持亮著。
 */
@Composable
fun SettingsConnect(vm: CounterViewModel) {
    val s = vm.settings
    val lanShare by s.lanShare.collectAsStateWithLifecycle()
    val rememberOn by s.shareRemember.collectAsStateWithLifecycle()
    val logOn by s.shareLog.collectAsStateWithLifecycle()
    val alert by s.shareAlert.collectAsStateWithLifecycle()
    val share by vm.share.collectAsStateWithLifecycle()
    val devices by vm.trustedDevices.collectAsStateWithLifecycle()
    val events by vm.shareEvents.collectAsStateWithLifecycle()
    val view = LocalView.current
    // 正在管理的信任裝置（null = 看列表）與改名輸入
    var selectedId by remember { mutableStateOf<String?>(null) }
    var nameDraft by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    DisposableEffect(share.running) {
        view.keepScreenOn = share.running
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) { onDispose { vm.stopShare() } }

    val selected = devices.firstOrNull { it.id == selectedId }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(SettingsCategories.name(SettingsCategories.CONNECT)) }

        if (selected != null) {
            // ── 單一信任裝置：改名、刪除 ──
            item { Body(tr("管理裝置", "Manage device")) }
            item { Note(tr("位址 ${selected.ip}\n${selected.agent.take(60)}\n最近連線 ${ShareText.clock(selected.lastSeenMs)}", "Address ${selected.ip}\n${selected.agent.take(60)}\nLast seen ${ShareText.clock(selected.lastSeenMs)}")) }
            item {
                BasicTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(24) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = { vm.renameTrusted(selected.id, nameDraft) },
                    label = { Text(tr("儲存名稱", "Save name"), maxLines = 1) },
                    colors = kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = {
                        vm.removeTrusted(selected.id)
                        selectedId = null
                    },
                    label = { Text(tr("刪除這支裝置", "Remove device"), maxLines = 1) },
                    colors = kindColors(ChipKind.DANGER, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = { selectedId = null },
                    label = { Text(tr("返回", "Back"), maxLines = 1) },
                    colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            return@ScalingLazyColumn
        }

        // ── 區網分享 ──
        item { ToggleChip(tr("區網分享", "LAN share"), lanShare) { on -> s.setLanShare(on); if (!on) vm.stopShare() } }
        item { Note(tr("開啟後出現「開始分享」。手錶和手機連同一個 Wi-Fi 時，在手機瀏覽器輸入畫面上的網址和 PIN，就能直接看報告、下載資料，不必用 adb。只提供唯讀下載，PIN 錯 10 次就鎖住，10 分鐘後或離開此頁自動關閉。預設關閉", "When on, a Start sharing button appears. With the watch and phone on the same Wi-Fi, enter the address and PIN shown into the phone browser to view the report and download data without adb. Read-only downloads; 10 wrong PINs lock it; it stops after 10 minutes or when you leave this page. Off by default")) }
        if (lanShare) {
            if (!share.running) {
                item {
                    Chip(
                        onClick = { vm.startShare() },
                        label = { Text(tr("開始分享", "Start sharing"), maxLines = 1) },
                        colors = kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                share.error?.let { e ->
                    item {
                        Note(
                            when (e) {
                                ShareError.NO_WIFI -> tr("找不到區網位址，請確認已連上 Wi-Fi", "No LAN address; make sure Wi-Fi is connected")
                                ShareError.PORT -> tr("連線埠 ${ShareServer.PORT} 被占用，請稍後再試", "Port ${ShareServer.PORT} is busy; try again later")
                            }
                        )
                    }
                }
            } else {
                item { Body(tr("瀏覽器輸入：\n${share.url}\nPIN：${share.pin}", "Open in a browser:\n${share.url}\nPIN: ${share.pin}")) }
                item { Note(tr("分享中，螢幕會保持亮著。10 分鐘後自動關閉", "Sharing; the screen stays on. Stops automatically after 10 minutes")) }
                item {
                    Chip(
                        onClick = { vm.stopShare() },
                        label = { Text(tr("停止分享", "Stop sharing"), maxLines = 1) },
                        colors = kindColors(ChipKind.DANGER, ChipDefaults.primaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
        }

        // ── 設定開關 ──
        item { ToggleChip(tr("記住已信任的裝置", "Remember trusted devices"), rememberOn) { s.setShareRemember(it) } }
        item { Note(tr("開啟後，網頁輸入 PIN 的頁面多一個「記住這個裝置」勾選框，勾選後該瀏覽器之後免輸入 PIN（憑證 1 年，最多 5 支裝置）。注意分享是普通 HTTP、沒有加密，同一個 Wi-Fi 的人理論上可能偷到憑證。預設關閉", "When on, the PIN page gets a Remember this device checkbox; that browser then skips the PIN (valid 1 year, up to 5 devices). Note sharing is plain HTTP without encryption, so someone on the same Wi-Fi could in theory capture the token. Off by default")) }
        item { ToggleChip(tr("記錄連線", "Log connections"), logOn) { s.setShareLog(it) } }
        item { Note(tr("開啟後記錄今天誰連進來（位址、瀏覽器、成功或失敗），只存在這支裝置上，過了午夜自動清掉，最多 100 筆。預設開", "When on, records who connected today (address, browser, success or failure), stored only on this device, cleared after midnight, up to 100 entries. On by default")) }
        item { ToggleChip(tr("PIN 錯誤時震動", "Vibrate on wrong PIN"), alert) { s.setShareAlert(it) } }
        item { Note(tr("有人輸入錯誤 PIN 時震動兩下提醒。預設關閉", "Two short vibrations when someone enters a wrong PIN. Off by default")) }

        // ── 已信任的裝置 ──
        item { SectionTitle(tr("已信任的裝置", "Trusted devices")) }
        if (devices.isEmpty()) {
            item { Note(tr("目前沒有。在網頁勾選「記住這個裝置」後會出現在這裡", "None yet. Devices appear here after you tick Remember this device on the web page")) }
        } else {
            val onlineIds = vm.onlineTrusted().map { it.id }.toSet()
            for (d in devices) {
                item {
                    Chip(
                        onClick = {
                            selectedId = d.id
                            nameDraft = d.name
                        },
                        label = { Text((if (d.id in onlineIds) "● " else "") + d.name + navMark(), maxLines = 1) },
                        secondaryLabel = { Text(ShareText.clock(d.lastSeenMs) + " · " + d.ip, maxLines = 1) },
                        colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
            item { Note(tr("● 表示最近 5 分鐘內連過。點一下可改名或刪除", "● = connected in the last 5 minutes. Tap to rename or remove")) }
            item {
                if (!confirmClear) {
                    Chip(
                        onClick = { confirmClear = true },
                        label = { Text(tr("全部清除信任", "Remove all trust"), maxLines = 1) },
                        colors = kindColors(ChipKind.DANGER, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                } else {
                    Chip(
                        onClick = {
                            vm.clearTrusted()
                            confirmClear = false
                        },
                        label = { Text(tr("確定清除全部", "Confirm: remove all"), maxLines = 1) },
                        colors = kindColors(ChipKind.DANGER, ChipDefaults.primaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
        }

        // ── 連線記錄 ──
        item { SectionTitle(tr("連線記錄", "Connection log")) }
        if (!logOn) {
            item { Note(tr("「記錄連線」已關閉", "Logging is off")) }
        } else {
            item { Body(ShareText.summary(events)) }
            for (e in events.takeLast(20).reversed()) {
                item {
                    Note(ShareText.clock(e.timeMs) + " " + e.ip + " " + AgentLabel.of(e.agent) + "\n" + ShareText.result(e.result))
                }
            }
            if (events.isNotEmpty()) {
                item {
                    Chip(
                        onClick = { vm.clearShareLog() },
                        label = { Text(tr("清除連線記錄", "Clear log"), maxLines = 1) },
                        colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
        }
    }
}
