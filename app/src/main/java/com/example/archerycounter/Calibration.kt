package com.example.archerycounter

/**
 * 校准录制的三段动作。每一段开始、结束都会标注在记录里，
 * 之后拿原始资料分析时就知道哪一段时间是什么动作。纯 Kotlin。
 */
object Calibration {
    class Step(val label: String, val hint: String)

    val STEPS: List<Step> = listOf(
        Step("只拉弓不放箭", "拉滿弓、瞄準，然後慢慢收回，不要放箭。重複 5～10 次。"),
        Step("正常射擊", "照平常的節奏正常射 6～12 箭。"),
        Step("取箭走動", "走去取箭、拔箭、走回射線，照平常的動作做。"),
    )
}
