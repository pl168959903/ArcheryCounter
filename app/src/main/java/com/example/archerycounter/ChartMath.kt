package com.example.archerycounter

/** 图表座标的计算。纯 Kotlin，画图的部分只负责把这里算好的 0..1 比例乘上画布大小。 */
object ChartMath {

    /**
     * 把数值缩放成 0..1，**1 在最上面**。范围预设取数据的最小到最大值，也可用 [lo]／[hi] 固定范围
     * （例如即时波形固定从 0 开始）。数据全部相同（范围太小）时画在正中间。
     */
    fun yFractions(values: List<Float>, lo: Float? = null, hi: Float? = null): List<Float> {
        if (values.isEmpty()) return emptyList()
        val min = lo ?: values.min()
        val max = hi ?: values.max()
        val range = max - min
        if (range < MIN_RANGE) return values.map { 0.5f }
        return values.map { ((it - min) / range).coerceIn(0f, 1f) }
    }

    /** 第 [index] 个点（共 [count] 个）在横轴上的位置 0..1；只有一个点时放正中间。 */
    fun xFraction(index: Int, count: Int): Float =
        if (count <= 1) 0.5f else index.coerceIn(0, count - 1) / (count - 1).toFloat()

    private const val MIN_RANGE = 1e-6f
}
