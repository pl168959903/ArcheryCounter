package com.zgrcc.archeryassistant

import java.security.MessageDigest

/** 作者、版本顯示、簽章指紋。純邏輯，不依賴 Android，可以直接在 JVM 上測試。 */
object AppInfo {
    /** 作者名稱，各處畫面都用這一個 */
    const val AUTHOR = "ZGrCc"

    /** 專案頁面（v1.17.1）：「關於」頁的 GitHub 按鈕打開它。 */
    const val GITHUB_URL = "https://github.com/pl168959903/ArcheryCounter"
    const val GITHUB_SHORT = "pl168959903/ArcheryCounter"

    /**
     * 固定金鑰的指紋前 4 組（SHA-256）。手錶上顯示的簽章如果和它一樣，就是用固定金鑰編譯的，
     * 之後可以直接 adb install -r 覆蓋安裝。若重新產生了金鑰，要把這裡改成新的前 4 組。
     */
    const val FIXED_KEY_PREFIX = "C7:08:6E:C9"

    /** 簽章證書的 SHA-256 指紋，取前 [groups] 組，格式像 C7:08:6E:C9。 */
    fun fingerprintShort(certBytes: ByteArray, groups: Int = 4): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
        val n = groups.coerceIn(1, digest.size)
        return digest.take(n).joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
    }

    /** 例如 v1.7（8）。versionCode 讀不到時只顯示名稱。 */
    fun versionLabel(name: String, code: Long): String =
        if (code >= 0) "v$name（$code）" else "v$name"

    /** 這個簽章是不是固定金鑰。比較時不分大小寫。 */
    fun isFixedKey(short: String): Boolean = short.equals(FIXED_KEY_PREFIX, ignoreCase = true)

    /** 給「關於」顯示的簽章狀態說明。 */
    fun signatureStatus(short: String): String = when {
        short == "?" -> tr("讀不到簽章", "Signature unavailable")
        isFixedKey(short) -> tr("與固定金鑰相符，之後可直接覆蓋安裝", "Matches the fixed key; future updates install over this one")
        else -> tr("不是固定金鑰（可能是臨時簽章），更新時可能要先卸載", "Not the fixed key (maybe a temporary signature); you may have to uninstall before updating")
    }
}
