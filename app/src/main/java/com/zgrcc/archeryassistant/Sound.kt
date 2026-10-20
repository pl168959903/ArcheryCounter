package com.zgrcc.archeryassistant

/**
 * 比賽模式的聲音（v1.9.1）。純 Kotlin，不依賴 Android，方便單元測試；
 * 實際播放（ToneGenerator）在 CounterViewModel。
 */
enum class SoundCue { COUNT, GO, WARN30, WARN10, TIME_UP, END_FULL, ARROW_UP }

/** 一聲：響 [durationMs] 毫秒，之後停 [gapMs] 毫秒再響下一聲。 */
data class BeepStep(val durationMs: Int, val gapMs: Int)

object MatchSound {
    /** 音量檔位（0～100），低／中／高／最大 */
    val VOLUMES = intArrayOf(25, 50, 75, 100)
    const val DEFAULT_VOLUME_INDEX = 2

    const val STYLE_COUNT = 3
    const val DEFAULT_STYLE = 0

    fun volume(index: Int): Int = VOLUMES[index.coerceIn(0, VOLUMES.size - 1)]
    fun nextVolume(index: Int): Int = (index.coerceIn(0, VOLUMES.size - 1) + 1) % VOLUMES.size
    fun nextStyle(style: Int): Int = (style + 1) % STYLE_COUNT

    fun volumeLabel(index: Int): String = "${volume(index)}%"

    fun styleLabel(style: Int): String = when (style) {
        1 -> tr("短促", "Short")
        2 -> tr("高音", "High")
        else -> tr("嗶聲", "Beep")
    }

    /** 各事件的聲音：幾聲、每聲多長。 */
    fun plan(cue: SoundCue): List<BeepStep> = when (cue) {
        SoundCue.COUNT -> listOf(BeepStep(150, 0))
        SoundCue.GO -> listOf(BeepStep(700, 0))
        SoundCue.WARN30 -> listOf(BeepStep(200, 0))
        SoundCue.WARN10 -> listOf(BeepStep(200, 150), BeepStep(200, 0))
        SoundCue.TIME_UP -> listOf(BeepStep(300, 150), BeepStep(300, 150), BeepStep(300, 0))
        SoundCue.END_FULL -> listOf(BeepStep(250, 0))
        SoundCue.ARROW_UP -> listOf(BeepStep(150, 100), BeepStep(150, 0))
    }

    /** 這個事件的聲音有沒有被使用者開啟（總開關另外判斷）。 */
    fun cueEnabled(cue: SoundCue, count: Boolean, go: Boolean, warn: Boolean, end: Boolean): Boolean = when (cue) {
        SoundCue.COUNT -> count
        SoundCue.GO -> go
        SoundCue.WARN30, SoundCue.WARN10, SoundCue.ARROW_UP -> warn
        SoundCue.TIME_UP, SoundCue.END_FULL -> end
    }
}

/** 比賽畫面配色（v1.9.1）：準備紅底白字、射擊綠底黑字，其他階段用一般配色。顏色是 ARGB。 */
data class MatchColors(val background: Long, val text: Long)

object MatchPalette {
    val PREPARE = MatchColors(0xFFC62828, 0xFFFFFFFF)
    val SHOOTING = MatchColors(0xFF4CAF50, 0xFF000000)

    /** 這個階段的專用配色；沒有專用配色（休息、結束）回傳 null。 */
    fun forPhase(phase: CompPhase): MatchColors? = when (phase) {
        CompPhase.PREPARE -> PREPARE
        CompPhase.SHOOTING -> SHOOTING
        else -> null
    }
}
