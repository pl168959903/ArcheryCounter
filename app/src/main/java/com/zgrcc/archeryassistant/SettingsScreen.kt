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

/**
 * 設定頁路由（v1.14.1 起拆分）：[category] 為 -1 時顯示分類列表，否則交給對應分類的檔案。
 * 分類內容在 SettingsGeneral / SettingsDetect / SettingsRecord / SettingsTrain / SettingsMatch /
 * SettingsDisplay / SettingsData / SettingsAbout，共用元件在 SettingsCommon。
 */
@Composable
fun SettingsScreen(
    vm: CounterViewModel,
    category: Int,
    onOpenCategory: (Int) -> Unit,
    onOpenCalibration: () -> Unit,
) {
    when (category) {
        0 -> SettingsGeneral(vm)
        1 -> SettingsDetect(vm, onOpenCalibration)
        2 -> SettingsRecord(vm)
        3 -> SettingsTrain(vm)
        4 -> SettingsMatch(vm)
        5 -> SettingsDisplay(vm)
        6 -> SettingsData(vm)
        7 -> SettingsAbout(vm)
        else -> SettingsCategoryList(onOpenCategory)
    }
}

@Composable
private fun SettingsCategoryList(onOpenCategory: (Int) -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text(tr("設定", "Settings"), style = MaterialTheme.typography.caption1) }
        for (c in 0 until SettingsCategories.COUNT) {
            item {
                Chip(
                    onClick = { onOpenCategory(c) },
                    label = { Text(SettingsCategories.name(c) + navMark(), maxLines = 1) },
                    colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
    }
}
