package com.zgrcc.archeryassistant

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import kotlinx.coroutines.launch

/**
 * 校準自動建議（v1.14）：分析一筆校準記錄的原始資料，給出放箭閾值與靜止條件的建議，可一鍵套用。
 * 只在「校準自動建議」開啟、且這筆校準記錄有原始資料時顯示（由呼叫端判斷）。
 */
@Composable
fun CalibrationAdviceBlock(vm: CounterViewModel, sessionId: String) {
    val scope = rememberCoroutineScope()
    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val still by vm.settings.stillThreshold.collectAsStateWithLifecycle()

    var busy by remember(sessionId) { mutableStateOf(false) }
    var loaded by remember(sessionId) { mutableStateOf(false) }
    var advice by remember(sessionId) { mutableStateOf<CalibAdvice?>(null) }
    var thApplied by remember(sessionId) { mutableStateOf(false) }
    var stillApplied by remember(sessionId) { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Chip(
            onClick = {
                busy = true
                thApplied = false
                stillApplied = false
                scope.launch {
                    advice = vm.analyzeCalibration(sessionId)
                    loaded = true
                    busy = false
                }
            },
            label = { Text(tr("分析校準資料", "Analyze calibration"), maxLines = 1) },
            secondaryLabel = { Text(if (busy) tr("分析中…", "Analyzing…") else tr("給閾值與靜止建議", "Suggest threshold and Still"), maxLines = 1) },
            colors = kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(0.9f),
        )
        if (loaded) {
            val a = advice
            if (a == null) {
                Body(tr("這筆校準沒有原始資料，無法分析", "This calibration has no raw data to analyze"))
            } else when (a.kind) {
                CalibKind.NO_DATA -> Body(tr("三段動作的資料不完整，請重新做一次完整的校準錄製", "The three movements are incomplete; please redo a full calibration"))
                CalibKind.NOT_ENOUGH_SHOTS -> Body(
                    tr(
                        "「正常射擊」那一段只偵測到 ${a.shotCount} 箭，至少要 ${CalibrationAnalyzer.MIN_SHOTS} 箭。請正常射 6～12 箭再校準；若一箭都沒偵測到，可能是目前閾值太高",
                        "Only ${a.shotCount} arrows were detected in Normal shooting; at least ${CalibrationAnalyzer.MIN_SHOTS} are needed. Shoot 6-12 arrows and calibrate again; if none were detected, the current threshold may be too high",
                    )
                )
                CalibKind.OK -> {
                    Body(
                        tr(
                            "拉弓與走動的動作上限約 ${Fmt.num(a.nonShotCeil, 1)}，正常射擊最小的放箭峰值 ${Fmt.num(a.shotLow, 1)}（共 ${a.shotCount} 箭）",
                            "Draw and walk movements top out around ${Fmt.num(a.nonShotCeil, 1)}; the smallest release peak while shooting is ${Fmt.num(a.shotLow, 1)} (${a.shotCount} arrows)",
                        )
                    )
                    val th = a.threshold
                    if (th != null) {
                        Body(tr("建議閾值 $th（目前 ${threshold.toInt()}）", "Suggested threshold $th (now ${threshold.toInt()})"))
                        Chip(
                            onClick = {
                                vm.setThreshold(th.toFloat())
                                thApplied = true
                            },
                            label = { Text(if (thApplied) tr("已套用閾值 $th", "Applied threshold $th") else tr("套用閾值 $th", "Apply threshold $th"), maxLines = 1) },
                            colors = if (thApplied) ChipDefaults.secondaryChipColors() else kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    } else {
                        Body(tr("拉弓、走動的動作和放箭的峰值太接近，調閾值分不開。可以試試調整「靜止」條件", "Draw and walk movements are too close to release peaks for the threshold to separate them. Try adjusting Still instead"))
                    }
                    val st = a.still
                    if (st != null) {
                        Body(tr("建議靜止 $st（目前 $still）", "Suggested Still $st (now $still)"))
                        Chip(
                            onClick = {
                                vm.settings.setStillThreshold(st)
                                stillApplied = true
                            },
                            label = { Text(if (stillApplied) tr("已套用靜止 $st", "Applied Still $st") else tr("套用靜止 $st", "Apply Still $st"), maxLines = 1) },
                            colors = if (stillApplied) ChipDefaults.secondaryChipColors() else kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                    Note(tr("只看得到用目前設定偵測到的箭，被漏掉的看不到；只是建議，不會自動套用", "Only arrows detected with the current settings can be seen; missed ones cannot. These are suggestions and are never applied automatically"))
                }
            }
        }
    }
}
