package com.zgrcc.archeryassistant

/** 網頁報告用的 SVG 圖表（v1.16）。純字串，沒有腳本。 */
internal object ReportCharts {
    private const val W = 640
    private const val H = 170
    private const val PAD_L = 46
    private const val PAD_R = 14
    private const val PAD_T = 14
    private const val PAD_B = 26

    private fun f(v: Float): String = Fmt.num(v, 1)

    /** 逐箭折線圖。至少要 2 個點；[steadiest]、[shakiest] 會用綠點、紅點標出。 */
    fun line(
        title: String,
        unit: String,
        values: List<ShotRank>,
        steadiest: ShotRank?,
        shakiest: ShotRank?,
        decimals: Int,
        second: Boolean = false,
    ): String {
        if (values.size < 2) return ""
        val min = values.minOf { it.value }
        val max = values.maxOf { it.value }
        val span = if (max - min > 1e-6f) max - min else 1f
        val lo = min - span * 0.08f
        val hi = max + span * 0.08f
        val first = values.first().number
        val last = values.last().number
        fun x(n: Int): Float =
            PAD_L + (W - PAD_L - PAD_R) * (if (last == first) 0f else (n - first).toFloat() / (last - first))
        fun y(v: Float): Float = PAD_T + (H - PAD_T - PAD_B) * (1f - (v - lo) / (hi - lo))

        return buildString {
            append("<svg class=\"chart\" viewBox=\"0 0 $W $H\" role=\"img\" aria-label=\"${esc(title)}\">")
            for (i in 0..3) {
                val v = lo + (hi - lo) * i / 3f
                val yy = y(v)
                append("<line class=\"grid\" x1=\"$PAD_L\" y1=\"${f(yy)}\" x2=\"${W - PAD_R}\" y2=\"${f(yy)}\"/>")
                append("<text x=\"${PAD_L - 6}\" y=\"${f(yy + 4)}\" text-anchor=\"end\">${Fmt.num(v, decimals)}</text>")
            }
            val pts = values.joinToString(" ") { "${f(x(it.number))},${f(y(it.value))}" }
            append("<polyline class=\"${if (second) "ln2" else "ln1"}\" points=\"$pts\"/>")
            if (values.size <= 80) {
                for (p in values) append("<circle class=\"pt\" cx=\"${f(x(p.number))}\" cy=\"${f(y(p.value))}\" r=\"2.4\"/>")
            }
            if (steadiest != null && values.any { it.number == steadiest.number }) {
                append("<circle class=\"good\" cx=\"${f(x(steadiest.number))}\" cy=\"${f(y(steadiest.value))}\" r=\"5\"/>")
            }
            if (shakiest != null && values.any { it.number == shakiest.number }) {
                append("<circle class=\"badp\" cx=\"${f(x(shakiest.number))}\" cy=\"${f(y(shakiest.value))}\" r=\"5\"/>")
            }
            append("<text x=\"$PAD_L\" y=\"${H - 6}\">${esc(tr("第 $first 箭", "Shot $first"))}</text>")
            append("<text x=\"${W - PAD_R}\" y=\"${H - 6}\" text-anchor=\"end\">${esc(tr("第 $last 箭", "Shot $last"))}</text>")
            append("</svg>")
            append("<div class=\"note\">${esc(title)}（${esc(unit)}）")
            if (steadiest != null) append(" · <span class=\"ok\">● ${esc(tr("最穩 第 ${steadiest.number} 箭", "Steadiest: shot ${steadiest.number}"))}</span>")
            if (shakiest != null) append(" · <span class=\"bad\">● ${esc(tr("最不穩 第 ${shakiest.number} 箭", "Shakiest: shot ${shakiest.number}"))}</span>")
            append("</div>")
        }
    }

    /** 長條圖：每個項目一根，[labels] 與 [values] 等長。 */
    fun bars(title: String, labels: List<String>, values: List<Int>): String {
        if (values.isEmpty()) return ""
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val n = values.size
        val slot = (W - PAD_L - PAD_R).toFloat() / n
        val bw = slot * 0.6f
        val base = (H - PAD_B).toFloat()
        return buildString {
            append("<svg class=\"chart\" viewBox=\"0 0 $W $H\" role=\"img\" aria-label=\"${esc(title)}\">")
            append("<line class=\"grid\" x1=\"$PAD_L\" y1=\"${f(base)}\" x2=\"${W - PAD_R}\" y2=\"${f(base)}\"/>")
            append("<text x=\"${PAD_L - 6}\" y=\"${f(PAD_T + 8f)}\" text-anchor=\"end\">$max</text>")
            append("<text x=\"${PAD_L - 6}\" y=\"${f(base)}\" text-anchor=\"end\">0</text>")
            for (i in 0 until n) {
                val h = (base - PAD_T) * values[i] / max
                val xx = PAD_L + slot * i + (slot - bw) / 2
                append("<rect class=\"bar\" x=\"${f(xx)}\" y=\"${f(base - h)}\" width=\"${f(bw)}\" height=\"${f(h)}\" rx=\"3\"/>")
                if (values[i] > 0) append("<text x=\"${f(xx + bw / 2)}\" y=\"${f(base - h - 3)}\" text-anchor=\"middle\">${values[i]}</text>")
                append("<text x=\"${f(xx + bw / 2)}\" y=\"${H - 8}\" text-anchor=\"middle\">${esc(labels[i])}</text>")
            }
            append("</svg>")
        }
    }

    /** 跳脫 HTML 特殊字元，所有塞進網頁的文字都要經過它。 */
    fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

internal fun esc(s: String): String = ReportCharts.esc(s)
