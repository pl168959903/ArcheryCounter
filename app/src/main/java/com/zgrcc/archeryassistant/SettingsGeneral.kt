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

/** 設定「一般」分類（SettingsCategories 0）。 */
@Composable
fun SettingsGeneral(vm: CounterViewModel) {
    val s = vm.settings
    val splash by s.splash.collectAsStateWithLifecycle()
    val splashTap by s.splashTap.collectAsStateWithLifecycle()
    val keepScreenOn by s.keepScreenOn.collectAsStateWithLifecycle()
    val wakeLock by s.wakeLock.collectAsStateWithLifecycle()
    val touchLock by s.touchLock.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val lowBattery by s.lowBattery.collectAsStateWithLifecycle()
    val dimWhenRunning by s.dimWhenRunning.collectAsStateWithLifecycle()
    val dimLevel by s.dimLevel.collectAsStateWithLifecycle()
    val language by s.language.collectAsStateWithLifecycle()
    val tileLaunch by s.tileLaunch.collectAsStateWithLifecycle()
    val phoneMode by s.phoneMode.collectAsStateWithLifecycle()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(SettingsCategories.name(0)) }
        item {
            Chip(
                onClick = { s.setLanguage(I18n.next(language)) },
                label = { Text(tr("語言", "Language"), maxLines = 1) },
                secondaryLabel = { Text(I18n.label(language), maxLines = 1) },
                colors = kindColors(ChipKind.NORMAL, ChipDefaults.primaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item { Note(tr("點一下切換：\n• 自動（跟隨系統，中文系統用中文，其他用英文）\n• 繁體中文\n• English\n切換後回到主畫面", "Tap to switch:\n• Auto (follow the system: Chinese on Chinese systems, English otherwise)\n• Traditional Chinese\n• English\nReturns to the main screen")) }

        item {
            Chip(
                onClick = { s.setPhoneMode(PhoneMode.next(phoneMode)) },
                label = { Text(tr("裝置模式", "Device mode"), maxLines = 1) },
                secondaryLabel = { Text(PhoneMode.label(phoneMode) + if (PhoneMode.resolve(phoneMode, vm.isWatch)) tr("（目前：手機）", " (now: phone)") else tr("（目前：手錶）", " (now: watch)"), maxLines = 2) },
                colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item { Note(tr("點一下切換：\n• 自動（不是手錶就進手機模式）\n• 手機模式\n• 手錶模式\n手機模式隱藏開始偵測、比賽、校準，主畫面多「從手錶匯入」，用來匯入與檢視記錄", "Tap to switch:\n• Auto (phone mode when this is not a watch)\n• Phone mode\n• Watch mode\nPhone mode hides start detection, match and calibration and adds Import from watch to the main screen, for importing and viewing records")) }

        item { ToggleChip(tr("偵測中觸控鎖", "Touch lock while detecting"), touchLock) { s.setTouchLock(it) } }
        item { Note(tr("偵測中主畫面出現「鎖定觸控」，鎖定時只顯示箭數，長按約 2 秒解鎖。", "A Lock touch button appears on the main screen while detecting. When locked only the arrow count shows; hold about 2 s to unlock.")) }

        item { ToggleChip(tr("低電量提醒", "Low battery alert"), lowBattery) { s.setLowBattery(it) } }
        item { Note(tr("偵測中電量低於 15% 時震動一次，並顯示「電量低」。", "Buzzes once and shows Low battery when it drops below 15% while detecting.")) }

        item { ToggleChip(tr("磁貼快捷啟動", "Tile quick start"), tileLaunch) { s.setTileLaunch(it) } }
        item { Note(tr("加入「射箭助手」磁貼後，可直接開始偵測或比賽。", "After adding the Archery Assistant tile, you can start detection or a match right from it.")) }

        item { ToggleChip(tr("開啟動畫", "Startup animation"), splash) { s.setSplash(it) } }
        item { Note(tr("開啟 App 時播放約 3 秒動畫，顯示版本、作者與簽章。下次開啟生效", "Plays a 3 s animation on launch showing version, author and signature. Takes effect next launch.")) }
        if (splash) {
            item { ToggleChip(tr("點按後才進入", "Tap to enter"), splashTap) { s.setSplashTap(it) } }
            item { Note(tr("動畫播完後停住，點一下才進入（太久沒點會自己進入）。關閉則約 3 秒後自動進入。下次開啟生效", "The animation stops at the end and waits for a tap (enters by itself after a long while). Off: enters automatically after about 3 s. Takes effect next launch.")) }
        }

        item { ToggleChip(tr("保持亮屏", "Keep screen on"), keepScreenOn) { s.setKeepScreenOn(it) } }
        item { Note(tr("應用開著時螢幕不休眠", "The screen stays on while the app is open")) }

        item { ToggleChip(tr("CPU 喚醒鎖", "CPU wake lock"), wakeLock) { s.setWakeLock(it) } }
        item { Note(tr("偵測中保持 CPU 運作，即使螢幕被熄滅也繼續取資料。較耗電", "Keeps the CPU running while detecting, even if the screen turns off. Uses more battery")) }


        item { ToggleChip(tr("偵測中降低亮度", "Dim while detecting"), dimWhenRunning) { s.setDimWhenRunning(it) } }
        item { Note(tr("偵測中把螢幕亮度降低以省電，暫停後恢復。部分手錶可能不理會這個設定", "Lowers screen brightness while detecting to save power and restores it after pausing. Some watches may ignore this")) }
        if (dimWhenRunning) {
            item {
                Chip(
                    onClick = { s.setDimLevel(Brightness.next(dimLevel)) },
                    label = { Text(tr("亮度", "Brightness"), maxLines = 1) },
                    secondaryLabel = { Text(Brightness.label(dimLevel), maxLines = 1) },
                    colors = kindColors(ChipKind.NORMAL, ChipDefaults.primaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item { Note(tr("點一下切換：\n• 低 20%\n• 中 40%\n• 高 60%", "Tap to switch:\n• Low 20%\n• Medium 40%\n• High 60%")) }
        }

    }
}
