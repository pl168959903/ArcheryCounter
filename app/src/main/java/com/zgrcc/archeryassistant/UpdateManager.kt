package com.zgrcc.archeryassistant

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class UpdateException(val error: UpdateError) : Exception(error.name)

/** 安裝程式回報失敗時，用這個把結果傳回應用（安裝成功時系統會重啟應用，不需要回報）。 */
object UpdateBus {
    val installFailed = MutableStateFlow(0)
}

/**
 * 檢查 GitHub 最新 Release、下載更新檔、交給系統安裝程式（v1.13）。
 * 只連 api.github.com 與 GitHub 的下載位址，只讀公開資料。所有方法都會阻塞，請在背景線程呼叫。
 */
class UpdateManager(private val context: Context) {

    /** 回傳最新正式版的資訊；沒有可用的發布（或沒有 APK）回傳 null。 */
    fun fetchLatest(): ReleaseInfo? {
        val conn = open(ReleaseParser.LATEST_URL, json = true)
        try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (code != HttpURLConnection.HTTP_OK) throw UpdateException(UpdateError.SERVER)
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return ReleaseParser.parse(text)
        } catch (e: UpdateException) {
            throw e
        } catch (e: IOException) {
            throw UpdateException(UpdateError.NO_NETWORK)
        } finally {
            conn.disconnect()
        }
    }

    /** 讀取一段文字（改版內容、releases 清單）。連不上或狀態不是 200 時丟 [UpdateException]。 */
    fun fetchText(url: String, json: Boolean): String {
        val conn = open(url, json)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw UpdateException(UpdateError.SERVER)
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: UpdateException) {
            throw e
        } catch (e: IOException) {
            throw UpdateException(UpdateError.NO_NETWORK)
        } finally {
            conn.disconnect()
        }
    }

    /** 下載更新檔到應用的快取資料夾，並核對 SHA-256（若 GitHub 有提供）。回傳檔案。 */
    fun download(info: ReleaseInfo, onProgress: (Int) -> Unit): File {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "update.apk")
        val conn = open(info.apkUrl, json = false)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw UpdateException(UpdateError.SERVER)
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastPercent = -1
            try {
                conn.inputStream.use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            md.update(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val p = (done * 100 / total).toInt().coerceIn(0, 100)
                                if (p != lastPercent) {
                                    lastPercent = p
                                    onProgress(p)
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                target.delete()
                // 已經寫了一部分又失敗：多半是網路中斷；磁碟滿也會是 IOException，用檔案大小粗略區分
                throw UpdateException(if (done > 0 && (target.parentFile?.usableSpace ?: Long.MAX_VALUE) < 1_000_000L) UpdateError.DISK else UpdateError.NO_NETWORK)
            }
            if (total > 0 && done != total) {
                target.delete()
                throw UpdateException(UpdateError.CHECKSUM)
            }
            val actual = md.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256 != null && actual != info.sha256) {
                target.delete()
                throw UpdateException(UpdateError.CHECKSUM)
            }
            return target
        } catch (e: UpdateException) {
            throw e
        } catch (e: IOException) {
            throw UpdateException(UpdateError.NO_NETWORK)
        } finally {
            conn.disconnect()
        }
    }

    /** 系統是否已允許這個應用安裝 APK。 */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** 打開「安裝未知應用程式」設定頁；手錶上沒有這個頁面時什麼都不做。 */
    fun openInstallSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // 這台手錶沒有該設定頁，錯誤訊息裡已說明手動路徑
        }
    }

    /** 交給系統安裝程式。系統會跳確認畫面，由使用者按「安裝」。 */
    fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("update.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
            val pi = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pi.intentSender)
        }
    }

    private fun open(url: String, json: Boolean): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "ArcheryAssistant")
        if (json) conn.setRequestProperty("Accept", "application/vnd.github+json")
        return conn
    }
}

/** 接收安裝程式的結果：需要使用者確認時打開系統的確認畫面；失敗時通知應用。 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirm)
                    } catch (e: Exception) {
                        UpdateBus.installFailed.value = UpdateBus.installFailed.value + 1
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> UpdateBus.installFailed.value = UpdateBus.installFailed.value + 1
        }
    }
}
