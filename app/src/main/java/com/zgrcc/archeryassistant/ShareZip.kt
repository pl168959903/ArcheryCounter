package com.zgrcc.archeryassistant

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 區網分享的「下載全部」zip（v1.19）。純 Kotlin（java.util.zip）。 */
object ShareZip {
    /** 把 [entries] 依序寫成 zip；開不起來的略過。回傳實際寫入幾個。檔名只接受安全名稱。 */
    fun write(out: OutputStream, entries: List<Pair<String, () -> InputStream?>>): Int {
        var n = 0
        ZipOutputStream(out).use { z ->
            for ((name, open) in entries) {
                if (!ShareRouter.isSafeName(name)) continue
                val input = try { open() } catch (e: Exception) { null } ?: continue
                input.use {
                    z.putNextEntry(ZipEntry(name))
                    it.copyTo(z)
                    z.closeEntry()
                }
                n++
            }
        }
        return n
    }
}
