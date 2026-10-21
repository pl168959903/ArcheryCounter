package com.zgrcc.archeryassistant

import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.15：設定的預設值在「宣告」和「還原預設值」兩處各寫一次，容易日久不一致。
 * 這裡用反射讀出所有設定，確認：全新安裝的值 = 還原後的值；寫入後重開能讀回相同的值。
 */
class AppSettingsTest {
    /** 刻意不會被「還原預設值」動到的設定。 */
    private val notReset = setOf("compSaved")

    private fun snapshot(s: AppSettings): Map<String, Any?> {
        val out = sortedMapOf<String, Any?>()
        for (m in AppSettings::class.java.methods) {
            if (m.parameterCount != 0 || !StateFlow::class.java.isAssignableFrom(m.returnType)) continue
            if (!m.name.startsWith("get")) continue
            val name = m.name.removePrefix("get").replaceFirstChar { it.lowercase() }
            out[name] = (m.invoke(s) as StateFlow<*>).value
        }
        return out
    }

    /** 把每個有 setXxx 的設定改成和目前不同的值。回傳實際改了幾個。 */
    private fun mutateAll(s: AppSettings): Int {
        var changed = 0
        val before = snapshot(s)
        for ((name, value) in before) {
            val setter = AppSettings::class.java.methods.firstOrNull {
                it.name == "set" + name.replaceFirstChar { c -> c.uppercase() } && it.parameterCount == 1
            } ?: continue
            val candidates: List<Any?> = when (value) {
                is Boolean -> listOf(!value)
                is Int -> listOf(value + 1, value - 1)
                is String -> listOf(if (value.isEmpty()) "x" else "")
                else -> emptyList()
            }
            for (c in candidates) {
                try {
                    setter.invoke(s, c)
                } catch (_: Exception) {
                    continue
                }
                if (snapshot(s)[name] != value) {
                    changed++
                    break
                }
            }
        }
        return changed
    }

    @Test
    fun resetDefaultsEqualsFreshInstall() {
        val fresh = snapshot(AppSettings(FakePrefs()))
        val s = AppSettings(FakePrefs())
        val changed = mutateAll(s)
        assertTrue("應該要能改動很多設定，實際只有 $changed", changed > 40)
        s.resetDefaults()
        val after = snapshot(s)
        val diff = fresh.keys.filter { it !in notReset && fresh[it] != after[it] }
            .map { "$it: 預設=${fresh[it]} 還原後=${after[it]}" }
        assertTrue("還原預設值後和全新安裝不一致：$diff", diff.isEmpty())
    }

    @Test
    fun valuesSurviveReopen() {
        val prefs = FakePrefs()
        val s = AppSettings(prefs)
        mutateAll(s)
        val written = snapshot(s)
        val reopened = snapshot(AppSettings(prefs))
        val diff = written.keys.filter { written[it] != reopened[it] }
            .map { "$it: 寫入=${written[it]} 重讀=${reopened[it]}" }
        assertTrue("重開後讀不回相同的值：$diff", diff.isEmpty())
    }

    @Test
    fun freshInstallKeepsDocumentedDefaults() {
        val s = snapshot(AppSettings(FakePrefs()))
        // 少數重要的預設值固定下來，改動時要有意識地改測試
        assertEquals(true, s["touchLock"])
        assertEquals(true, s["lowBattery"])
        assertEquals(false, s["autoClean"])
        assertEquals(false, s["shotFeel"])
        assertEquals(0, s["updateMode"])
        assertEquals(true, s["paceAnalysis"])
    }
}
