package com.zgrcc.archeryassistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** 用系統預設的瀏覽器（例如 Chrome）開網頁報告（v1.18.3）。找不到能開啟的程式時回傳 false，不會當掉。 */
object ExternalOpen {
    fun url(ctx: Context, url: String): Boolean = try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    /** 把本機報告存成檔案，交給系統開啟。 */
    fun localReport(ctx: Context, html: String): Boolean = try {
        val dir = File(ctx.cacheDir, "reports").apply { mkdirs() }
        val f = File(dir, ReportBuilder.FILE_NAME)
        f.writeText(html, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "text/html")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        false
    }
}
