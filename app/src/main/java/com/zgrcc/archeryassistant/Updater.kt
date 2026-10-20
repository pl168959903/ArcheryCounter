package com.zgrcc.archeryassistant

/** GitHub Release 裡的更新檔資訊。 */
data class ReleaseInfo(
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    /** GitHub 提供的 SHA-256（小寫十六進位）；沒有為 null */
    val sha256: String?,
)

/** 版本號比較（v1.13）。tag 例：v1.12.1；versionName 例：1.12.1。純 Kotlin。 */
object VersionCompare {
    /** 去掉開頭的 v／V，照「.」拆成整數；格式不對回傳 null。 */
    fun parse(v: String?): List<Int>? {
        val t = v?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
        if (t.isEmpty()) return null
        val parts = t.split('.')
        val nums = parts.map { p -> p.toIntOrNull()?.takeIf { it >= 0 } ?: return null }
        return nums
    }

    /** a 比 b 新回傳正數，一樣回傳 0，舊回傳負數。不足的位數視為 0（1.12 = 1.12.0）。任一邊格式不對回傳 null。 */
    fun compare(a: String?, b: String?): Int? {
        val pa = parse(a) ?: return null
        val pb = parse(b) ?: return null
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    fun isNewer(remote: String?, current: String?): Boolean = (compare(remote, current) ?: 0) > 0
}

/** 解析 GitHub 的 releases/latest 回應。 */
object ReleaseParser {
    const val REPO = "pl168959903/ArcheryCounter"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val NOTES_MAX = 400
    private val HEX64 = Regex("^[0-9a-fA-F]{64}$")

    /** 草稿、預發布、沒有 APK、下載連結不是 https 都回傳 null。 */
    fun parse(json: String): ReleaseInfo? {
        val root = try {
            SessionJson.Parser(json).parseDocument() as? Map<*, *>
        } catch (e: Exception) {
            null
        } ?: return null
        if (root["draft"] == true || root["prerelease"] == true) return null
        val tag = (root["tag_name"] as? String)?.trim().orEmpty()
        if (VersionCompare.parse(tag) == null) return null

        val asset = (root["assets"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }.firstOrNull { a ->
            val name = a["name"] as? String ?: return@firstOrNull false
            name.startsWith("ArcheryAssistant") && name.endsWith(".apk")
        } ?: return null
        val url = asset["browser_download_url"] as? String ?: return null
        if (!url.startsWith("https://")) return null
        val digest = (asset["digest"] as? String)?.removePrefix("sha256:")?.takeIf { HEX64.matches(it) }?.lowercase()

        val notes = ((root["body"] as? String).orEmpty()).trim().replace("\r", "")
        return ReleaseInfo(
            tag = tag,
            notes = if (notes.length > NOTES_MAX) notes.take(NOTES_MAX).trimEnd() + "…" else notes,
            apkUrl = url,
            apkName = asset["name"] as String,
            apkSize = (asset["size"] as? Number)?.toLong() ?: 0L,
            sha256 = digest,
        )
    }
}

enum class UpdateKind { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, INSTALLING, ERROR }

enum class UpdateError { NO_NETWORK, SERVER, NO_APK, CHECKSUM, DISK, NOT_ALLOWED, INSTALL_FAILED }

/** 更新畫面的狀態。 */
data class UpdateUi(
    val kind: UpdateKind = UpdateKind.IDLE,
    val info: ReleaseInfo? = null,
    val percent: Int = 0,
    val error: UpdateError? = null,
)

object UpdatePolicy {
    /** 自動檢查最多一天一次 */
    const val INTERVAL_MS = 24L * 60 * 60 * 1000

    /** 從沒查過、已超過一天，或時鐘被往回調（上次檢查時間在未來）都算到期。 */
    fun due(lastMs: Long, nowMs: Long): Boolean =
        lastMs <= 0L || nowMs < lastMs || nowMs - lastMs >= INTERVAL_MS

    /** 要不要把這個版本提示給使用者。手動檢查一律提示；自動檢查略過使用者已略過的版本。 */
    fun shouldOffer(remoteTag: String, current: String, skipped: String, manual: Boolean): Boolean {
        if (!VersionCompare.isNewer(remoteTag, current)) return false
        if (manual) return true
        return skipped.isEmpty() || VersionCompare.compare(remoteTag, skipped) != 0
    }
}

object UpdateText {
    fun size(bytes: Long): String =
        if (bytes <= 0L) "?" else String.format(java.util.Locale.US, "%.1f MB", bytes / 1024f / 1024f)

    fun error(e: UpdateError): String = when (e) {
        UpdateError.NO_NETWORK -> tr("連不上網路。請確認手錶有 Wi-Fi 或行動網路，再試一次", "Cannot reach the network. Check that the watch has Wi-Fi or mobile data and try again")
        UpdateError.SERVER -> tr("GitHub 沒有正常回應，稍後再試", "GitHub did not respond properly; try again later")
        UpdateError.NO_APK -> tr("最新的發布裡沒有找到更新檔", "No update file was found in the latest release")
        UpdateError.CHECKSUM -> tr("下載的檔案不完整或不符，已丟棄，請重試", "The downloaded file is incomplete or does not match and was discarded; try again")
        UpdateError.DISK -> tr("寫入失敗，可能儲存空間不足", "Writing failed; storage may be full")
        UpdateError.NOT_ALLOWED -> tr("系統還沒允許這個應用安裝更新。請到 設定 > 應用程式 > 特殊應用程式存取 > 安裝未知應用程式，允許 Archery Assistant 後再試", "The system does not yet allow this app to install updates. Allow Archery Assistant under Settings > Apps > Special app access > Install unknown apps, then try again")
        UpdateError.INSTALL_FAILED -> tr("安裝沒有完成。可能被取消，或簽章與目前的版本不同", "Installation did not finish. It may have been cancelled, or the signature differs from the installed version")
    }
}
