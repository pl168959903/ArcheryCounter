package com.zgrcc.archeryassistant

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 比賽聲音（ToneGenerator，不需要權限；沒有喇叭的手錶會沒有聲音）。
 * v1.14.1 從 CounterViewModel 拆出，行為不變。
 */
class MatchSoundPlayer(
    private val scope: CoroutineScope,
    private val settings: AppSettings,
) {
    private var tone: ToneGenerator? = null
    private var toneVolume = -1
    private var soundJob: Job? = null

    private fun toneType(style: Int): Int = when (style) {
        1 -> ToneGenerator.TONE_DTMF_1
        2 -> ToneGenerator.TONE_DTMF_D
        else -> ToneGenerator.TONE_PROP_BEEP
    }

    /** 依設定播放比賽事件的聲音；總開關或該事件關閉時什麼都不做。 */
    fun playCue(cue: SoundCue) {
        val s = settings
        if (!s.soundEnabled.value) return
        if (!MatchSound.cueEnabled(cue, s.soundCount.value, s.soundGo.value, s.soundWarn.value, s.soundEnd.value)) return
        playPlan(MatchSound.plan(cue))
    }

    /** 設定頁選音量、音色時試播（用「開始射擊」的長音，不受事件開關影響）。 */
    fun preview() {
        if (!settings.soundEnabled.value) return
        playPlan(MatchSound.plan(SoundCue.WARN10))
    }

    private fun playPlan(steps: List<BeepStep>) {
        val volume = MatchSound.volume(settings.soundVolume.value)
        val type = toneType(settings.soundStyle.value)
        soundJob?.cancel()
        soundJob = scope.launch {
            try {
                if (tone == null || toneVolume != volume) {
                    tone?.release()
                    tone = ToneGenerator(AudioManager.STREAM_MUSIC, volume)
                    toneVolume = volume
                }
                for (step in steps) {
                    tone?.startTone(type, step.durationMs)
                    delay((step.durationMs + step.gapMs).toLong())
                }
            } catch (_: RuntimeException) {
                // 沒有可用的音訊輸出或系統拒絕：靜默略過，震動照常
            }
        }
    }

    fun release() {
        tone?.release()
        tone = null
    }
}
