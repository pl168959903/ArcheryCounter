package com.zgrcc.archeryassistant

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId

/** 已信任的裝置（v1.16.3）：只存憑證的雜湊，不存憑證本身。 */
data class TrustedDevice(
    val id: String,
    val name: String,
    val hash: String,
    val createdMs: Long,
    val lastSeenMs: Long,
    val ip: String,
    val agent: String,
)

/** 一次連線的結果。 */
enum class ShareResult { OK_PIN, OK_TRUST, WRONG_PIN, LOCKED, REFUSED }

/** 連線記錄的一筆。 */
data class ShareEvent(
    val timeMs: Long,
    val ip: String,
    val agent: String,
    val result: ShareResult,
)

/** 瀏覽器自報資訊 → 簡短描述，例如「Android · Chrome」。純字串處理。 */
object AgentLabel {
    fun of(ua: String): String {
        if (ua.isBlank()) return "?"
        val os = when {
            ua.contains("iPhone") || ua.contains("iPad") -> "iOS"
            ua.contains("Android") -> "Android"
            ua.contains("Windows") -> "Windows"
            ua.contains("Macintosh") || ua.contains("Mac OS") -> "macOS"
            ua.contains("Linux") -> "Linux"
            else -> null
        }
        val browser = when {
            ua.contains("Edg/") || ua.contains("EdgA/") -> "Edge"
            ua.contains("Firefox/") || ua.contains("FxiOS/") -> "Firefox"
            ua.contains("OPR/") -> "Opera"
            ua.contains("Chrome/") || ua.contains("CriOS/") -> "Chrome"
            ua.contains("Safari/") -> "Safari"
            ua.contains("curl") -> "curl"
            else -> null
        }
        return listOfNotNull(os, browser).joinToString(" · ").ifEmpty { "?" }
    }
}

/** 文字格式：每筆一行、欄位用 Tab 隔開，並跳脫 \、Tab、換行。 */
object ShareCodec {
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "")

    private fun unesc(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun split(line: String): List<String> = line.split('\t').map { unesc(it) }

    fun encodeDevices(list: List<TrustedDevice>): String = list.joinToString("\n") {
        listOf(it.id, it.name, it.hash, it.createdMs.toString(), it.lastSeenMs.toString(), it.ip, it.agent).joinToString("\t") { f -> esc(f) }
    }

    fun decodeDevices(text: String): List<TrustedDevice> = text.lines().filter { it.isNotBlank() }.mapNotNull { line ->
        val f = split(line)
        if (f.size < 7) null else TrustedDevice(f[0], f[1], f[2], f[3].toLongOrNull() ?: 0L, f[4].toLongOrNull() ?: 0L, f[5], f[6])
    }

    fun encodeEvents(list: List<ShareEvent>): String = list.joinToString("\n") {
        listOf(it.timeMs.toString(), it.ip, it.agent, it.result.name).joinToString("\t") { f -> esc(f) }
    }

    fun decodeEvents(text: String): List<ShareEvent> = text.lines().filter { it.isNotBlank() }.mapNotNull { line ->
        val f = split(line)
        val r = f.getOrNull(3)?.let { n -> ShareResult.values().firstOrNull { it.name == n } }
        if (f.size < 4 || r == null) null else ShareEvent(f[0].toLongOrNull() ?: 0L, f[1], f[2], r)
    }
}

/** 連線記錄與信任裝置的文字（畫面用）。 */
object ShareText {
    fun result(r: ShareResult): String = when (r) {
        ShareResult.OK_PIN -> tr("PIN 進入", "Entered with PIN")
        ShareResult.OK_TRUST -> tr("信任裝置進入", "Trusted device")
        ShareResult.WRONG_PIN -> tr("PIN 錯誤", "Wrong PIN")
        ShareResult.LOCKED -> tr("已鎖定", "Locked")
        ShareResult.REFUSED -> tr("非區網，已拒絕", "Not LAN, refused")
    }

    /** 「今天 3 支裝置、5 次連線、1 次 PIN 錯誤」 */
    fun summary(events: List<ShareEvent>): String {
        val devices = events.map { it.ip + "|" + it.agent }.distinct().size
        val wrong = events.count { it.result == ShareResult.WRONG_PIN }
        return tr("今天 $devices 支裝置、${events.size} 次連線、$wrong 次 PIN 錯誤", "Today: $devices devices, ${events.size} visits, $wrong wrong PINs")
    }

    fun clock(ms: Long): String {
        val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        return String.format("%02d:%02d", t.hour, t.minute)
    }
}

/**
 * 信任裝置與連線記錄的儲存（v1.16.3）。放在自己的 SharedPreferences 鍵，不屬於「還原預設值」。
 * 憑證只存 SHA-256；最多 [MAX_DEVICES] 支，超過擠掉最舊的；憑證從最後一次使用起 [VALID_MS] 內有效。
 * 連線記錄只留今天、最多 [MAX_EVENTS] 筆。
 */
class ShareData(private val prefs: SharedPreferences, private val now: () -> Long = { System.currentTimeMillis() }) {
    private val _devices = MutableStateFlow(ShareCodec.decodeDevices(prefs.getString(KEY_DEVICES, "") ?: ""))
    val devices: StateFlow<List<TrustedDevice>> = _devices.asStateFlow()

    private val _events = MutableStateFlow(todayOnly(ShareCodec.decodeEvents(prefs.getString(KEY_EVENTS, "") ?: "")))
    val events: StateFlow<List<ShareEvent>> = _events.asStateFlow()

    private val rng = SecureRandom()

    @Synchronized
    fun issue(ip: String, agent: String): String {
        val bytes = ByteArray(16).also { rng.nextBytes(it) }
        val token = bytes.joinToString("") { String.format("%02x", it) }
        val t = now()
        val id = token.take(8)
        val d = TrustedDevice(id, AgentLabel.of(agent), hash(token), t, t, ip, agent)
        saveDevices((listOf(d) + _devices.value).take(MAX_DEVICES))
        return token
    }

    /** 憑證有效就回傳該裝置並更新最後使用時間，否則 null。 */
    @Synchronized
    fun verify(token: String): TrustedDevice? {
        if (token.isEmpty() || token.length > 64) return null
        val h = hash(token)
        val t = now()
        val d = _devices.value.firstOrNull { it.hash == h } ?: return null
        if (t - d.lastSeenMs > VALID_MS) return null
        val updated = d.copy(lastSeenMs = t)
        saveDevices(_devices.value.map { if (it.id == d.id) updated else it })
        return updated
    }

    @Synchronized
    fun rename(id: String, name: String) {
        val n = name.trim().take(24)
        if (n.isEmpty()) return
        saveDevices(_devices.value.map { if (it.id == id) it.copy(name = n) else it })
    }

    @Synchronized
    fun remove(id: String) = saveDevices(_devices.value.filter { it.id != id })

    @Synchronized
    fun clearDevices() = saveDevices(emptyList())

    @Synchronized
    fun log(e: ShareEvent) {
        saveEvents((todayOnly(_events.value) + e).takeLast(MAX_EVENTS))
    }

    @Synchronized
    fun clearLog() = saveEvents(emptyList())

    /** 最近 [ONLINE_MS] 內有連線的信任裝置。 */
    fun online(): List<TrustedDevice> = _devices.value.filter { now() - it.lastSeenMs <= ONLINE_MS }

    private fun todayOnly(list: List<ShareEvent>): List<ShareEvent> {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
        return list.filter { Instant.ofEpochMilli(it.timeMs).atZone(zone).toLocalDate() == today }
    }

    private fun saveDevices(list: List<TrustedDevice>) {
        _devices.value = list
        prefs.edit().putString(KEY_DEVICES, ShareCodec.encodeDevices(list)).apply()
    }

    private fun saveEvents(list: List<ShareEvent>) {
        _events.value = list
        prefs.edit().putString(KEY_EVENTS, ShareCodec.encodeEvents(list)).apply()
    }

    companion object {
        const val MAX_DEVICES = 5
        const val MAX_EVENTS = 100
        const val VALID_MS = 365L * 24 * 60 * 60 * 1000
        const val ONLINE_MS = 5L * 60 * 1000
        private const val KEY_DEVICES = "share_devices"
        private const val KEY_EVENTS = "share_events"

        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { String.format("%02x", it) }
    }
}
