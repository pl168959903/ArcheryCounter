package com.zgrcc.archeryassistant

/**
 * 從 CounterViewModel 抽出的純計算（v1.21.1），好在本機單元測試。
 * 行為和原本的寫法完全一致。
 */

/** 沒有「線性加速度」感測器時，用低通濾波自己扣掉重力。 */
class GravityFilter(private val alpha: Float = ALPHA) {
    private val g = FloatArray(3)
    private var ready = false

    fun reset() { ready = false }

    /** 輸入原始加速度 (x, y, z)，輸出扣掉重力後的 [out]（長度 3）。第一筆以自身當作重力初值。 */
    fun remove(x: Float, y: Float, z: Float, out: FloatArray) {
        if (!ready) {
            g[0] = x; g[1] = y; g[2] = z
            ready = true
        }
        g[0] = alpha * g[0] + (1 - alpha) * x
        g[1] = alpha * g[1] + (1 - alpha) * y
        g[2] = alpha * g[2] + (1 - alpha) * z
        out[0] = x - g[0]
        out[1] = y - g[1]
        out[2] = z - g[2]
    }

    companion object { const val ALPHA = 0.9f }
}

/** 放箭後震動的優先順序：達標 > 疲勞提醒 > 滿一趟 > 一般放箭（只震最重要的那一種，不疊加）。 */
enum class ShotBuzz { GOAL, FATIGUE, END, SHOT }

object ShotBuzzRule {
    fun pick(goalReached: Boolean, fatigueNow: Boolean, endFull: Boolean, endVibration: Int): ShotBuzz = when {
        goalReached -> ShotBuzz.GOAL
        fatigueNow -> ShotBuzz.FATIGUE
        endFull && endVibration != EndVibration.OFF -> ShotBuzz.END
        else -> ShotBuzz.SHOT
    }
}
