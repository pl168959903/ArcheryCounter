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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.launch

/** 設定「關於與重設」分類（SettingsCategories 7）。 */
@Composable
fun SettingsAbout(vm: CounterViewModel, onOpenChangelog: () -> Unit = {}) {
    val s = vm.settings
    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val updateMode by s.updateMode.collectAsStateWithLifecycle()
    val updateUi by vm.update.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var noBrowser by remember { mutableStateOf(false) }


    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(SettingsCategories.name(7)) }
        item { Body(tr("版本 ${vm.versionLabel}", "Version ${vm.versionLabel}")) }
        item { Body(tr("作者 ${AppInfo.AUTHOR}", "Author ${AppInfo.AUTHOR}")) }
        item { Body(tr("簽章 ${vm.signatureShort}", "Signature ${vm.signatureShort}")) }
        item { Note(AppInfo.signatureStatus(vm.signatureShort)) }
        item {
            Chip(
                onClick = { noBrowser = !openLink(ctx, AppInfo.GITHUB_URL) },
                icon = { Text("</>", maxLines = 1) },
                label = { Text("GitHub", maxLines = 1) },
                secondaryLabel = { Text(AppInfo.GITHUB_SHORT, maxLines = 1) },
                colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        if (noBrowser) {
            item { Note(tr("這支手錶沒有可以開網頁的程式。網址：${AppInfo.GITHUB_URL}", "This watch has no app that can open web pages. Address: ${AppInfo.GITHUB_URL}")) }
        }
        item {
            Chip(
                onClick = onOpenChangelog,
                label = { Text("改版內容" + navMark(), maxLines = 1) },
                colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }

        item { SectionTitle(tr("更新", "Update")) }
        item {
            Chip(
                onClick = { s.setUpdateMode(UpdateMode.next(updateMode)) },
                label = { Text(UpdateMode.label(updateMode), maxLines = 2) },
                secondaryLabel = { Text(tr("點一下切換", "Tap to change"), maxLines = 1) },
                colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item { Note(tr("每次啟動：每次開啟 App 都查一次 GitHub。每天第一次啟動：一天最多查一次。無自動檢查：只在你按「檢查更新」時查。有新版本才會在主畫面提醒並顯示版號；已是最新或連線失敗都不吭聲。不會自己下載，也不會中斷射擊。需要網路。預設「每次啟動」，下次開啟 App 才生效", "Every start: checks GitHub each time the app opens. First start each day: at most once a day. No automatic check: only when you tap Check for updates. Only a new version shows a reminder with its version number on the main screen; up to date or a failed check stay silent. Never downloads by itself or interrupts shooting. Needs a network. Default is Every start; takes effect the next time the app opens")) }
        item {
            val busy = updateUi.kind == UpdateKind.CHECKING || updateUi.kind == UpdateKind.DOWNLOADING || updateUi.kind == UpdateKind.INSTALLING
            Chip(
                onClick = { vm.checkUpdate(manual = true) },
                label = { Text(tr("檢查更新", "Check for updates"), maxLines = 1) },
                secondaryLabel = {
                    Text(
                        when (updateUi.kind) {
                            UpdateKind.CHECKING -> tr("檢查中…", "Checking…")
                            UpdateKind.UP_TO_DATE -> tr("已是最新版", "Up to date")
                            UpdateKind.AVAILABLE -> tr("有新版本 ${updateUi.info?.tag}", "New version ${updateUi.info?.tag}")
                            else -> tr("目前 ${vm.versionName}", "Current ${vm.versionName}")
                        },
                        maxLines = 1,
                    )
                },
                colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        val found = updateUi.info
        when (updateUi.kind) {
            UpdateKind.AVAILABLE -> if (found != null) {
                item {
                    Body(
                        tr("有新版本 ${found.tag}（目前 ${vm.versionName}），大小約 ${UpdateText.size(found.apkSize)}", "New version ${found.tag} (current ${vm.versionName}), about ${UpdateText.size(found.apkSize)}") +
                            if (found.notes.isNotEmpty()) "\n\n" + found.notes else ""
                    )
                }
                item {
                    Chip(
                        onClick = { vm.downloadAndInstall() },
                        label = { Text(tr("下載並安裝", "Download and install"), maxLines = 1) },
                        colors = kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item {
                    Chip(
                        onClick = { vm.skipUpdate() },
                        label = { Text(tr("略過這個版本", "Skip this version"), maxLines = 1) },
                        colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item { Note(tr("下載完成後，系統會跳出安裝確認畫面，由你按「安裝」。簽章相同，記錄會保留", "When the download finishes, the system asks you to confirm the install. The signature is the same, so your records are kept")) }
            }
            UpdateKind.DOWNLOADING -> item { Body(tr("下載中 ${updateUi.percent}%", "Downloading ${updateUi.percent}%")) }
            UpdateKind.INSTALLING -> item { Body(tr("已交給系統安裝程式。請在系統畫面按「安裝」；安裝完成後應用會重新開啟", "Handed to the system installer. Tap Install on the system screen; the app reopens when it finishes")) }
            UpdateKind.ERROR -> item { Body(updateUi.error?.let { UpdateText.error(it) } ?: "") }
            else -> Unit
        }
        item { Note(tr("記錄資料夾（用 adb pull 取出）：\n${vm.dataPath}", "Records folder (pull with adb):\n${vm.dataPath}")) }
        if (!confirmReset) {
            item {
                Chip(
                    onClick = { confirmReset = true },
                    label = { Text(tr("恢復預設設定", "Restore default settings"), maxLines = 1) },
                    colors = kindColors(ChipKind.DANGER, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item { Note(tr("把設定頁所有項目還原成預設值。不動記錄、主畫面的箭數和閾值", "Resets every item on this page to its default. Records, the main count and the threshold are untouched")) }
        } else {
            item { Note(tr("確定要把所有設定還原成預設值嗎？", "Reset all settings to defaults?")) }
            item {
                Chip(
                    onClick = {
                        vm.resetSettings()
                        confirmReset = false
                    },
                    label = { Text(tr("確定還原", "Reset"), maxLines = 1) },
                    colors = kindColors(ChipKind.DANGER, ChipDefaults.primaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = { confirmReset = false },
                    label = { Text(tr("取消", "Cancel"), maxLines = 1) },
                    colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
    }
}

/** 用系統開啟網址；沒有能處理的程式回傳 false（不會當掉）。 */
private fun openLink(ctx: android.content.Context, url: String): Boolean = try {
    ctx.startActivity(
        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (e: Exception) {
    false
}
