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
        item { Note(tr("點一下切換：自動 → 繁體中文 → English。自動＝系統是中文就用中文，其他語言用英文。切換後會回到主畫面", "Tap to change: Auto → Traditional Chinese → English. Auto uses Chinese when the system language is Chinese, otherwise English. You return to the main screen after switching")) }

        item { ToggleChip(tr("偵測中觸控鎖", "Touch lock while detecting"), touchLock) { s.setTouchLock(it) } }
        item { Note(tr("開啟後，偵測中主畫面有「鎖定觸控」按鈕，鎖定時只顯示箭數，長按約 2 秒解鎖，避免拉弓、放箭時誤碰。立即生效", "When on, the main screen has a Lock touch button while detecting. Locked shows only the count; hold about 2 s to unlock, so draws and releases do not cause stray touches. Takes effect immediately")) }

        item { ToggleChip(tr("低電量提醒", "Low battery alert"), lowBattery) { s.setLowBattery(it) } }
        item { Note(tr("偵測中電量低於 15% 時震動一次，主畫面顯示「電量低」，只提醒不會自己動作。立即生效", "Vibrates once when the battery drops below 15% while detecting and shows Battery low on the main screen. Only a reminder. Takes effect immediately")) }

        item { ToggleChip(tr("磁貼快捷啟動", "Tile quick start"), tileLaunch) { s.setTileLaunch(it) } }
        item { Note(tr("在手錶的磁貼列表加入「射箭助手」磁貼後，可直接從磁貼開始偵測或開始比賽。關閉時點磁貼只會打開應用。立即生效", "After adding the Archery Assistant tile to your watch, you can start detection or a match straight from it. When off, tapping the tile only opens the app. Takes effect immediately")) }

        item { ToggleChip(tr("開啟動畫", "Startup animation"), splash) { s.setSplash(it) } }
        item { Note(tr("開啟應用時先播放約 3 秒的動畫（圖示放大，並顯示版本、作者與簽章）。下次開啟應用時生效", "Plays a short animation when the app opens (icon, version, author and signature). Takes effect next launch")) }
        if (splash) {
            item { ToggleChip(tr("點按後才進入", "Tap to enter"), splashTap) { s.setSplashTap(it) } }
            item { Note(tr("開啟時動畫播完會停在畫面上，出現「點按螢幕進入」，點一下才進入主畫面（太久沒點會自己進入）。關閉則約 3 秒後自動進入。動畫中隨時點一下都會直接進入。下次開啟應用時生效", "The animation stays on screen and shows Tap to enter; tap to continue (it continues by itself after a long time). When off, it continues automatically after about 3 s. Tapping at any time skips it. Takes effect next launch")) }
        }

        item { ToggleChip(tr("保持亮屏", "Keep screen on"), keepScreenOn) { s.setKeepScreenOn(it) } }
        item { Note(tr("應用開著時螢幕不休眠，立即生效", "The screen stays on while the app is open. Takes effect immediately")) }

        item { ToggleChip(tr("CPU 喚醒鎖", "CPU wake lock"), wakeLock) { s.setWakeLock(it) } }
        item { Note(tr("偵測中保持 CPU 運作，即使螢幕被熄滅也繼續取資料。較耗電，立即生效", "Keeps the CPU running while detecting, even if the screen turns off. Uses more battery. Takes effect immediately")) }


        item { ToggleChip(tr("偵測中降低亮度", "Dim while detecting"), dimWhenRunning) { s.setDimWhenRunning(it) } }
        item { Note(tr("偵測中把螢幕亮度降低以省電，暫停後恢復。部分手錶可能不理會這個設定。立即生效", "Lowers screen brightness while detecting to save power and restores it after pausing. Some watches may ignore this. Takes effect immediately")) }
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
            item { Note(tr("點一下切換：低 20% → 中 40% → 高 60%", "Tap to change: Low 20% → Mid 40% → High 60%")) }
        }

    }
}
