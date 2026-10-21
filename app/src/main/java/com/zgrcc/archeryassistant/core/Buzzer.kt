package com.zgrcc.archeryassistant

import android.os.VibrationEffect
import android.os.Vibrator

/** 震動輸出（v1.14.2 從 CounterViewModel 拆出，行為不變）。沒有震動馬達（[vibrator] 為 null）時什麼都不做。 */
class Buzzer(private val vibrator: Vibrator?) {
    /** 每一箭：一次很短的輕震。 */
    fun shot() {
        vibrator?.vibrate(VibrationEffect.createOneShot(EndVibration.SHOT_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** 滿一趟：節奏明顯不同的震動；樣式為「關閉」時什麼都不做。設定頁選樣式時也用它試震。 */
    fun end(style: Int) {
        val pattern = EndVibration.pattern(style) ?: return
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    /** 播放指定的震動節奏。 */
    fun pattern(pattern: LongArray) {
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
