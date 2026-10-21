package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text

/**
 * 改版內容（v1.17.1，從「關於」進入）。內容來自 GitHub 上的 CHANGELOG.md，只有中文，不翻英。
 * 先列版本清單，點一個看該版內容；沒網路時顯示上次存下來的。
 */
@Composable
fun ChangelogScreen(vm: CounterViewModel) {
    val ui by vm.changelog.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.loadChangelog() }
    val entry = ui.entries.firstOrNull { it.version == selected }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (entry != null) {
            item { SectionTitle(entry.version) }
            for (p in Changelog.paragraphs(entry)) item { Note(p) }
            item {
                Chip(
                    onClick = { selected = null },
                    label = { Text("返回", maxLines = 1) },
                    colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            return@ScalingLazyColumn
        }
        item { SectionTitle("改版內容") }
        if (ui.loading && ui.entries.isEmpty()) item { Note("讀取中…") }
        if (ui.failed) item { Note("讀不到改版內容。請確認網路後再開一次") }
        if (ui.offline && !ui.loading) item { Note("目前沒有網路，顯示上次讀到的內容") }
        for (e in ui.entries) {
            item {
                Chip(
                    onClick = { selected = e.version },
                    label = { Text(e.version + (if (e.version == "v" + vm.versionName) "（目前）" else "") + navMark(), maxLines = 1) },
                    colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
    }
}
