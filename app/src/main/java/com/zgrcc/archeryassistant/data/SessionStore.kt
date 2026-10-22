package com.zgrcc.archeryassistant

import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 记录的文件存取。全部文件操作都在同一条背景线程上按顺序执行，
 * 所以「先存档、再读取」的顺序一定正确，也不会卡住画面。
 *
 *   <baseDir>/sessions/<id>.json   记录本身
 *   <baseDir>/sessions/<id>.csv    原始传感器数据（只有开启「錄製原始資料」时才有）
 *
 * id 是开始时间，例如 20261019_211003。
 * baseDir 在手表上是 /sdcard/Android/data/<套件名稱>/files/（GitHub 版 com.zgrcc.archeryassistant，Play 版 com.zgrcc.archeryassistant.play），可以用 adb pull 取出。
 */
class SessionStore(baseDir: File) {

    private val dir = File(baseDir, "sessions")

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "session-io").apply { isDaemon = true }
    }

    /** 已排队、但还没写到磁盘的记录 id，避免同一秒内重复取到同一个 id。 */
    private val pendingIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    companion object {
        /** 匯出檔名（給匯入器排除用）。 */
        val exportNames: Set<String> get() = EXPORT_FILES
        const val MAX_IMPORT_BYTES = 20 * 1024 * 1024
        val EXPORT_FILES = setOf(ExportCsv.SUMMARY_FILE, ExportCsv.SHOTS_FILE, ReportBuilder.FILE_NAME, SettingsBackup.FILE_NAME)
    }

    fun exists(id: String): Boolean = id in pendingIds || jsonFile(id).exists()

    /** 原始数据 CSV 的路径（会先确保目录存在）。 */
    fun csvFile(id: String): File {
        dir.mkdirs()
        return File(dir, "$id.csv")
    }

    /** 排队存档，立刻返回。同一笔记录可以重复存，后面的覆盖前面的。 */
    fun saveAsync(session: Session) {
        pendingIds.add(session.id)
        io.execute {
            try {
                write(session)
            } catch (e: IOException) {
                // 存不下就算了，不要让应用崩溃
            } finally {
                pendingIds.remove(session.id)
            }
        }
    }

    /** 读取全部记录（会等前面排队的存档写完）。请不要在主线程呼叫。 */
    fun listSessions(): List<Session> = onIo(emptyList()) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
        files.orEmpty().mapNotNull { readFile(it) }.sortedByDescending { it.startMs }
    }

    fun load(id: String): Session? = onIo(null) {
        if (!isSafeId(id)) null else jsonFile(id).takeIf { it.isFile }?.let { readFile(it) }
    }

    fun delete(id: String) = onIo(Unit) { deleteFiles(id) }

    /** 记录占用的空间：记录本身（JSON）、原始资料（CSV）、匯出档。 */
    fun usage(): StorageUsage = onIo(StorageUsage(0, 0L, 0, 0L, 0L)) {
        val all = dir.listFiles().orEmpty().filter { it.isFile }
        val json = all.filter { it.name.endsWith(".json") }
        val raw = all.filter { isRawCsv(it) }
        val export = all.filter { it.name in EXPORT_FILES }
        StorageUsage(json.size, json.sumOf { it.length() }, raw.size, raw.sumOf { it.length() }, export.sumOf { it.length() })
    }

    /**
     * 只删除原始资料 CSV，保留记录本身。[keepId]（进行中的那一笔）除外。
     * 对应记录的「有原始资料」标记也会清掉，详情页就不会再说有 CSV。
     */
    fun deleteRawData(keepId: String?): RawDeleteResult = onIo(RawDeleteResult(0, 0L)) {
        var count = 0
        var bytes = 0L
        for (f in dir.listFiles().orEmpty().filter { it.isFile && isRawCsv(it) }) {
            val id = f.name.removeSuffix(".csv")
            if (id == keepId) continue
            bytes += f.length()
            if (f.delete()) count++
            val json = jsonFile(id)
            if (json.isFile) {
                readFile(json)?.takeIf { it.rawCsv }?.let { write(it.copy(rawCsv = false)) }
            }
        }
        RawDeleteResult(count, bytes)
    }

    /**
     * 自動清理：早於 [cutoffMs] 的記錄，刪掉原始資料 CSV，並去掉波形和 2D 軌跡，其他統計資料保留。
     * [keepId]（進行中的那一筆）、校準記錄、不夠舊的都不動。回傳清了幾筆、釋出多少位元組。
     */
    fun cleanOld(cutoffMs: Long, keepId: String?): CleanResult = onIo(CleanResult(0, 0L, System.currentTimeMillis())) {
        var count = 0
        var bytes = 0L
        for (f in dir.listFiles { x -> x.isFile && x.name.endsWith(".json") }.orEmpty()) {
            val id = f.name.removeSuffix(".json")
            if (id == keepId) continue
            val s = readFile(f) ?: continue
            if (!AutoClean.needsClean(s, cutoffMs)) continue
            val before = f.length()
            val csv = File(dir, "$id.csv")
            val csvBytes = if (csv.isFile) csv.length() else 0L
            write(AutoClean.strip(s))
            if (csv.isFile) csv.delete()
            bytes += csvBytes + (before - jsonFile(id).length()).coerceAtLeast(0L)
            count++
        }
        CleanResult(count, bytes, System.currentTimeMillis())
    }

    /** 原始資料 CSV 逐行讀取（校準分析用）；檔案不存在回傳 null。 */
    fun <T> readRawLines(id: String, block: (Sequence<String>) -> T): T? = onIo(null) {
        if (!isSafeId(id)) return@onIo null
        val f = File(dir, "$id.csv")
        if (!f.isFile) null else f.useLines { block(it) }
    }

    private fun isRawCsv(f: File) = f.name.endsWith(".csv") && f.name !in EXPORT_FILES

    /** 一次删除多笔（记录页的管理模式）。 */
    fun deleteMany(ids: Collection<String>) = onIo(Unit) { ids.forEach { deleteFiles(it) } }

    /**
     * 區網分享可下載的檔名：記錄 JSON、原始資料 CSV、設定備份，以及即時產生的 summary.csv／shots.csv
     * （有記錄時才有；v1.19.3 起不再先匯出成檔案）。report.html 另外即時產生。
     */
    fun shareNames(): List<String> = onIo(emptyList()) { namesNow() }

    private fun diskNames(): List<String> =
        dir.listFiles().orEmpty().filter { it.isFile && (it.name.endsWith(".json") || isRawCsv(it) || it.name == SettingsBackup.FILE_NAME) }
            .map { it.name }

    private fun namesNow(): List<String> {
        val disk = diskNames()
        val csv = if (disk.any { it.endsWith(".json") }) listOf(ExportCsv.SUMMARY_FILE, ExportCsv.SHOTS_FILE) else emptyList()
        return (disk + csv).sorted()
    }

    /** 即時產生的 CSV 內容：summary.csv（每次一行）或 shots.csv（每箭一行）；其他名稱回傳 null。 */
    fun csvBytes(name: String): ByteArray? = onIo(null) { csvNow(name) }

    /** 必須在 io 執行緒內呼叫（不可巢狀 onIo，單執行緒會卡死）。 */
    private fun csvNow(name: String): ByteArray? {
        if (name != ExportCsv.SUMMARY_FILE && name != ExportCsv.SHOTS_FILE) return null
        val all = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().mapNotNull { readFile(it) }
        return if (name == ExportCsv.SUMMARY_FILE) ExportCsv.summary(all).toByteArray(Charsets.UTF_8)
        else ExportCsv.shots(all).toByteArray(Charsets.UTF_8)
    }

    /** 取分享用檔案；名稱必須是 [shareNames] 裡的，否則回傳 null。 */
    fun shareFile(name: String): ShareFile? = onIo(null) {
        if (name !in namesNow()) return@onIo null
        if (name == ExportCsv.SUMMARY_FILE || name == ExportCsv.SHOTS_FILE) {
            val bytes = csvNow(name) ?: return@onIo null
            return@onIo ShareFile(bytes.size.toLong()) { bytes.inputStream() }
        }
        val f = File(dir, name)
        if (f.isFile) ShareFile(f.length()) { f.inputStream() } else null
    }

    /** 「下載全部」zip（v1.19）：全部記錄 JSON 加設定備份，不含原始資料 CSV。沒有東西可打包回傳 null；串流讀完或關閉後刪掉暫存檔。 */
    fun shareZip(): ShareFile? = onIo(null) {
        val names = namesNow().filter { it.endsWith(".json") || it == SettingsBackup.FILE_NAME || it == ExportCsv.SUMMARY_FILE || it == ExportCsv.SHOTS_FILE }
        if (names.isEmpty()) return@onIo null
        val tmp = File.createTempFile("archery_share", ".zip")
        try {
            tmp.outputStream().use { out ->
                ShareZip.write(out, names.map { n -> n to { (csvNow(n)?.inputStream() ?: File(dir, n).takeIf { it.isFile }?.inputStream()) as java.io.InputStream? } })
            }
        } catch (e: Exception) {
            tmp.delete()
            return@onIo null
        }
        ShareFile(tmp.length()) {
            object : java.io.FilterInputStream(tmp.inputStream()) {
                override fun close() {
                    try { super.close() } finally { tmp.delete() }
                }
            }
        }
    }

    /**
     * 手機匯出（v1.24）：把 [sessions] 打包成 [target] 這個 zip，內容與名稱見 [RecordExport]。
     * [reportHtml] 是已經組好的網頁報告。回傳實際寫入幾個檔案，失敗回傳 0（並刪掉殘檔）。
     */
    fun writeExportZip(target: File, sessions: List<Session>, reportHtml: String, withRaw: Boolean): Int = onIo(0) {
        val ids = sessions.map { it.id }
        val byId = sessions.associateBy { it.id }
        val rawIds = ids.filter { id -> byId[id]?.rawCsv == true && File(dir, "$id.csv").isFile }.toSet()
        val names = RecordExport.entryNames(ids, rawIds, withRaw)
        try {
            target.parentFile?.mkdirs()
            val n = target.outputStream().use { out ->
                ShareZip.write(out, names.map { name ->
                    name to {
                        when {
                            name == ExportCsv.SUMMARY_FILE -> ExportCsv.summary(sessions).toByteArray(Charsets.UTF_8).inputStream()
                            name == ExportCsv.SHOTS_FILE -> ExportCsv.shots(sessions).toByteArray(Charsets.UTF_8).inputStream()
                            name == ReportBuilder.FILE_NAME -> reportHtml.toByteArray(Charsets.UTF_8).inputStream()
                            name.endsWith(".json") -> byId[name.removeSuffix(".json")]?.let { SessionJson.encode(it).toByteArray(Charsets.UTF_8).inputStream() }
                            else -> File(dir, name).takeIf { it.isFile }?.inputStream()
                        } as java.io.InputStream?
                    }
                })
            }
            if (n == 0) target.delete()
            n
        } catch (e: Exception) {
            target.delete()
            0
        }
    }

    /** 寫入設定備份檔（先寫暫存檔再改名）。回傳是否成功。 */
    fun writeSettingsBackup(text: String): Boolean = onIo(false) {
        try {
            dir.mkdirs()
            val target = File(dir, SettingsBackup.FILE_NAME)
            val tmp = File(dir, SettingsBackup.FILE_NAME + ".tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.writeText(text, Charsets.UTF_8)
                tmp.delete()
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    /** 讀設定備份檔；沒有檔案或太大回傳 null。 */
    fun readSettingsBackup(): String? = onIo(null) {
        val f = File(dir, SettingsBackup.FILE_NAME)
        if (f.isFile && f.length() <= 200_000) f.readText(Charsets.UTF_8) else null
    }

    /** 本機已有的記錄 id（匯入時用來略過重複）。 */
    fun localIds(): Set<String> = onIo(emptySet()) {
        dir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".json") }.map { it.name.removeSuffix(".json") }.toSet()
    }

    /**
     * 匯入一筆記錄 JSON（v1.17，從別台裝置抓回）。先確認能解析、id 安全，再用原文寫入（不重新編碼，保留未知欄位）。
     * 已存在且 [overwrite] 為 false 時略過。
     */
    fun importJsonWithId(name: String, bytes: ByteArray, overwrite: Boolean): Pair<ImportOutcome, String> = onIo(ImportOutcome.INVALID to "") {
        val id = name.removeSuffix(".json")
        if (!name.endsWith(".json") || !isSafeId(id) || bytes.size > MAX_IMPORT_BYTES) return@onIo ImportOutcome.INVALID to ""
        val text = try {
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return@onIo ImportOutcome.INVALID to ""
        }
        val incoming = try {
            SessionJson.decode(text, id)
        } catch (e: Exception) {
            null
        } ?: return@onIo ImportOutcome.INVALID to ""
        var tid = id
        var data = bytes
        // 同 id 但唯一碼不同 = 不同手錶剛好同一秒開始：改用新 id 匯入，不覆蓋也不略過
        val existing = jsonFile(tid)
        if (existing.exists() && incoming.uid.isNotEmpty()) {
            val old = try {
                SessionJson.decode(existing.readText(Charsets.UTF_8), tid)
            } catch (e: Exception) {
                null
            }
            if (old != null && old.uid != incoming.uid) {
                tid = RecordMeta.collisionId(tid, incoming.uid)
                data = SessionJson.encode(incoming.copy(id = tid)).toByteArray(Charsets.UTF_8)
            }
        }
        val target = jsonFile(tid)
        if (target.exists() && !overwrite) return@onIo ImportOutcome.SKIPPED to tid
        try {
            dir.mkdirs()
            val tmp = File(dir, "$tid.json.tmp")
            tmp.writeBytes(data)
            if (!tmp.renameTo(target)) {
                target.writeBytes(data)
                tmp.delete()
            }
            ImportOutcome.IMPORTED to tid
        } catch (e: IOException) {
            ImportOutcome.INVALID to ""
        }
    }

    fun importJson(name: String, bytes: ByteArray, overwrite: Boolean): ImportOutcome =
        importJsonWithId(name, bytes, overwrite).first

    /** 匯入原始資料 CSV；必須是 `<安全 id>.csv`，不能是匯出檔。已存在且 [overwrite] 為 false 時略過。 */
    fun importRaw(name: String, input: java.io.InputStream, overwrite: Boolean): ImportOutcome = onIo(ImportOutcome.INVALID) {
        val id = name.removeSuffix(".csv")
        if (!name.endsWith(".csv") || name in EXPORT_FILES || !isSafeId(id)) return@onIo ImportOutcome.INVALID
        val target = File(dir, name)
        if (target.exists() && !overwrite) return@onIo ImportOutcome.SKIPPED
        try {
            dir.mkdirs()
            val tmp = File(dir, "$name.tmp")
            tmp.outputStream().use { out -> input.copyTo(out) }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            ImportOutcome.IMPORTED
        } catch (e: IOException) {
            ImportOutcome.INVALID
        }
    }

    /** 全部記錄（最新在前），給網頁報告用。 */
    fun allSessions(): List<Session> = listSessions()

    /** 删除全部记录，[keepId]（进行中的那一笔）除外。 */
    fun deleteAll(keepId: String?) = onIo(Unit) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
        for (f in files.orEmpty()) {
            val id = f.name.removeSuffix(".json")
            if (id != keepId) deleteFiles(id)
        }
    }

    // ---- 以下都在 io 线程执行 ----

    private fun write(session: Session) {
        if (!isSafeId(session.id)) return
        dir.mkdirs()
        val target = jsonFile(session.id)
        val tmp = File(dir, "${session.id}.json.tmp")
        tmp.writeText(SessionJson.encode(session), Charsets.UTF_8)
        // 先写临时文件再改名，这样就算写到一半被关掉，旧的记录也不会被写坏
        if (!tmp.renameTo(target)) {
            target.writeText(SessionJson.encode(session), Charsets.UTF_8)
            tmp.delete()
        }
    }

    private fun readFile(f: File): Session? = try {
        SessionJson.decode(f.readText(Charsets.UTF_8), f.name.removeSuffix(".json"))
    } catch (e: IOException) {
        null
    }

    private fun deleteFiles(id: String) {
        if (!isSafeId(id)) return
        jsonFile(id).delete()
        File(dir, "$id.json.tmp").delete()
        File(dir, "$id.csv").delete()
    }

    private fun jsonFile(id: String) = File(dir, "$id.json")

    private fun isSafeId(id: String) =
        id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '_' }

    private fun <T> onIo(fallback: T, block: () -> T): T = try {
        io.submit(Callable { block() }).get()
    } catch (e: Exception) {
        fallback
    }
}

/**
 * 记录的 JSON 编码与解码。自己写而不用 org.json，这样可以在一般 JVM 上直接跑单元测试。
 *
 * 结构：
 *   { "id", "startMs", "endMs"（-1 表示没有正常结束）, "threshold", "windowSec", "hasGyro", "rawCsv",
 *     "tags"?: ["室外70米", "反曲"], "cal"?: true, "app"?, "dev"?, "uid"?, "det"?, "note"?, "comp"?: { "sec", "apn", "pr" }, "marks"?: [ { "l", "s", "e"? } ],
 *     "shots":  [ { "rel", "peak", "thr", "acc"?, "gyr"?, "x"?（1=誤判）, "ok"?（1=確認正常）, "wf"?（波形）,
 *                   "fa"?, "fg"?（放箭後跟隨晃動量）, "sc"?（環數） } ],
 *     "manual": [ { "rel", "d" } ] }
 */
internal object SessionJson {

    private const val DEFAULT_WINDOW_SEC = 2

    fun encode(s: Session): String = buildString {
        append("{\"id\":").append(quote(s.id))
        append(",\"startMs\":").append(s.startMs)
        append(",\"endMs\":").append(s.endMs ?: -1L)
        append(",\"threshold\":").append(num(s.threshold))
        append(",\"windowSec\":").append(s.windowSec)
        append(",\"hasGyro\":").append(s.hasGyro)
        append(",\"rawCsv\":").append(s.rawCsv)
        if (s.tags.isNotEmpty()) {
            append(",\"tags\":").append(s.tags.joinToString(",", "[", "]") { quote(it) })
        }
        if (s.calibration) append(",\"cal\":true")
        if (s.app.isNotEmpty()) append(",\"app\":").append(quote(s.app))
        if (s.dev.isNotEmpty()) append(",\"dev\":").append(quote(s.dev))
        if (s.uid.isNotEmpty()) append(",\"uid\":").append(quote(s.uid))
        if (s.det.isNotEmpty()) append(",\"det\":").append(quote(s.det))
        if (s.note.isNotEmpty()) append(",\"note\":").append(quote(s.note))
        s.comp?.let {
            append(",\"comp\":{\"sec\":").append(it.secondsPerEnd)
            append(",\"apn\":").append(it.arrowsPerEnd)
            append(",\"pr\":").append(it.practiceEnds).append('}')
        }
        if (s.marks.isNotEmpty()) {
            append(",\"marks\":[")
            s.marks.forEachIndexed { i, m ->
                if (i > 0) append(',')
                append("{\"l\":").append(quote(m.label))
                append(",\"s\":").append(m.startRelMs)
                if (m.endRelMs != null) append(",\"e\":").append(m.endRelMs)
                append('}')
            }
            append(']')
        }

        append(",\"shots\":[")
        s.shots.forEachIndexed { i, sh ->
            if (i > 0) append(',')
            append("{\"rel\":").append(sh.relMs)
            append(",\"peak\":").append(num(sh.peak))
            append(",\"thr\":").append(num(sh.threshold))
            if (sh.accelRms != null && sh.accelRms.isFinite()) append(",\"acc\":").append(num(sh.accelRms))
            if (sh.gyroRms != null && sh.gyroRms.isFinite()) append(",\"gyr\":").append(num(sh.gyroRms))
            if (sh.cancelled) append(",\"x\":1")
            if (sh.followAccelRms != null && sh.followAccelRms.isFinite()) append(",\"fa\":").append(num(sh.followAccelRms))
            if (sh.followGyroRms != null && sh.followGyroRms.isFinite()) append(",\"fg\":").append(num(sh.followGyroRms))
            if (sh.score != null) append(",\"sc\":").append(sh.score)
            if (sh.waveform != null) append(",\"wf\":").append(encodeWave(sh.waveform))
            if (sh.trace != null) append(",\"tr\":").append(encodeTrace(sh.trace))
            if (sh.feel != null) append(",\"fe\":").append(sh.feel)
            if (sh.reviewed) append(",\"ok\":1")
            append('}')
        }

        append("],\"manual\":[")
        s.manual.forEachIndexed { i, m ->
            if (i > 0) append(',')
            append("{\"rel\":").append(m.relMs).append(",\"d\":").append(m.delta).append('}')
        }
        append("]}")
    }

    /** 解析失败回传 null。[id] 用文件名，不信任内容里的 id。 */
    fun decode(text: String, id: String): Session? {
        return try {
            val root = Parser(text).parseDocument() as? Map<*, *> ?: return null
            val startMs = (root["startMs"] as? Number)?.toLong() ?: return null
            val endRaw = (root["endMs"] as? Number)?.toLong() ?: -1L
            val threshold = (root["threshold"] as? Number)?.toFloat() ?: 0f

            val shots = (root["shots"] as? List<*>).orEmpty().mapNotNull { e ->
                val m = e as? Map<*, *> ?: return@mapNotNull null
                val rel = (m["rel"] as? Number)?.toLong() ?: return@mapNotNull null
                val peak = (m["peak"] as? Number)?.toFloat() ?: return@mapNotNull null
                ShotEntry(
                    relMs = rel,
                    peak = peak,
                    threshold = (m["thr"] as? Number)?.toFloat() ?: threshold,
                    accelRms = (m["acc"] as? Number)?.toFloat(),
                    gyroRms = (m["gyr"] as? Number)?.toFloat(),
                    waveform = parseWave(m["wf"]),
                    trace = parseTrace(m["tr"]),
                    feel = Feel.valid((m["fe"] as? Number)?.toInt()),
                    followAccelRms = (m["fa"] as? Number)?.toFloat(),
                    followGyroRms = (m["fg"] as? Number)?.toFloat(),
                    score = (m["sc"] as? Number)?.toInt()?.coerceIn(0, 10),
                    reviewed = when (val ok = m["ok"]) {
                        is Boolean -> ok
                        is Number -> ok.toInt() != 0
                        else -> false
                    },
                    cancelled = when (val x = m["x"]) {
                        is Boolean -> x
                        is Number -> x.toInt() != 0
                        else -> false
                    },
                )
            }
            val manual = (root["manual"] as? List<*>).orEmpty().mapNotNull { e ->
                val m = e as? Map<*, *> ?: return@mapNotNull null
                val rel = (m["rel"] as? Number)?.toLong() ?: return@mapNotNull null
                val d = (m["d"] as? Number)?.toInt() ?: return@mapNotNull null
                ManualEntry(rel, d)
            }

            Session(
                id = id,
                startMs = startMs,
                endMs = if (endRaw < 0) null else endRaw,
                threshold = threshold,
                windowSec = (root["windowSec"] as? Number)?.toInt() ?: DEFAULT_WINDOW_SEC,
                hasGyro = root["hasGyro"] as? Boolean ?: false,
                rawCsv = root["rawCsv"] as? Boolean ?: false,
                shots = shots,
                manual = manual,
                tags = (root["tags"] as? List<*>).orEmpty().mapNotNull { it as? String },
                calibration = root["cal"] as? Boolean ?: false,
                app = (root["app"] as? String).orEmpty().take(40),
                dev = (root["dev"] as? String).orEmpty().take(60),
                uid = (root["uid"] as? String).orEmpty().filter { it.isLetterOrDigit() }.take(16),
                det = (root["det"] as? String).orEmpty().take(200),
                note = (root["note"] as? String).orEmpty().take(60),
                comp = (root["comp"] as? Map<*, *>)?.let { c ->
                    val sec = (c["sec"] as? Number)?.toInt() ?: return@let null
                    val apn = (c["apn"] as? Number)?.toInt() ?: return@let null
                    CompMeta(sec, apn, (c["pr"] as? Number)?.toInt() ?: 0)
                },
                marks = (root["marks"] as? List<*>).orEmpty().mapNotNull { e ->
                    val m = e as? Map<*, *> ?: return@mapNotNull null
                    val label = m["l"] as? String ?: return@mapNotNull null
                    val start = (m["s"] as? Number)?.toLong() ?: return@mapNotNull null
                    Mark(label, start, (m["e"] as? Number)?.toLong())
                },
            )
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun num(v: Float): String =
        if (v.isNaN() || v.isInfinite()) "0" else String.format(Locale.US, "%.4f", v)

    /** 波形的数值只留一位小数，档案才不会太大。 */
    private fun num1(v: Float): String =
        if (v.isNaN() || v.isInfinite()) "0.0" else String.format(Locale.US, "%.1f", v)

    private fun numList(values: List<Float>): String = values.joinToString(",", "[", "]") { num1(it) }

    private fun encodeWave(w: Waveform): String = buildString {
        append("{\"s\":").append(w.stepMs)
        append(",\"i\":").append(w.impactIndex)
        append(",\"a\":").append(numList(w.accel))
        if (w.gyro.isNotEmpty()) append(",\"g\":").append(numList(w.gyro))
        append('}')
    }

    private fun num2(v: Float): String =
        if (v.isNaN() || v.isInfinite()) "0.00" else String.format(Locale.US, "%.2f", v)

    private fun encodeTrace(t: Trace2D): String = buildString {
        append("{\"s\":").append(t.stepMs)
        if (t.gyro) append(",\"g\":1")
        append(",\"x\":").append(t.x.joinToString(",", "[", "]") { num2(it) })
        append(",\"y\":").append(t.y.joinToString(",", "[", "]") { num2(it) })
        append('}')
    }

    private fun parseTrace(raw: Any?): Trace2D? {
        val m = raw as? Map<*, *> ?: return null
        val step = (m["s"] as? Number)?.toInt() ?: return null
        val x = floats(m["x"])
        val y = floats(m["y"])
        if (step <= 0 || x.isEmpty() || x.size != y.size) return null
        val gyro = when (val g = m["g"]) {
            is Boolean -> g
            is Number -> g.toInt() != 0
            else -> false
        }
        return Trace2D(step, gyro, x, y)
    }

    private fun floats(raw: Any?): List<Float> =
        (raw as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toFloat() }

    private fun parseWave(raw: Any?): Waveform? {
        val m = raw as? Map<*, *> ?: return null
        val step = (m["s"] as? Number)?.toInt() ?: return null
        val impact = (m["i"] as? Number)?.toInt() ?: return null
        val accel = floats(m["a"])
        if (step <= 0 || accel.isEmpty()) return null
        return Waveform(step, impact.coerceIn(0, accel.size - 1), accel, floats(m["g"]))
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c)
            }
        }
        append('"')
    }

    /** 只支援我们自己写出的这种 JSON：对象、数组、数字、布尔、字符串、null。 */
    internal class Parser(private val s: String) {
        private var i = 0

        fun parseDocument(): Any? {
            val v = parseValue()
            skipWs()
            if (i != s.length) fail("多余的内容")
            return v
        }

        private fun parseValue(): Any? {
            skipWs()
            if (i >= s.length) fail("意外结束")
            return when (s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++ // {
            skipWs()
            if (peek() == '}') {
                i++
                return map
            }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                map[key] = parseValue()
                skipWs()
                when (next()) {
                    ',' -> continue
                    '}' -> return map
                    else -> fail("物件格式錯誤")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            val list = ArrayList<Any?>()
            i++ // [
            skipWs()
            if (peek() == ']') {
                i++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWs()
                when (next()) {
                    ',' -> continue
                    ']' -> return list
                    else -> fail("陣列格式錯誤")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("字符串没有结束")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) fail("字符串没有结束")
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("\\u 不完整")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> fail("不認識的跳脫字元")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Double {
            val start = i
            while (i < s.length && s[i] in "+-0123456789.eE") i++
            if (start == i) fail("預期是數字")
            return s.substring(start, i).toDouble()
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, i)) fail("预期是 $word")
            i += word.length
            return value
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

        private fun next(): Char {
            if (i >= s.length) fail("意外结束")
            return s[i++]
        }

        private fun expect(c: Char) {
            if (next() != c) fail("预期是 $c")
        }

        private fun fail(msg: String): Nothing = throw IllegalArgumentException(msg)
    }
}

/** 记录占用的空间。 */
data class StorageUsage(
    val sessionCount: Int,
    val jsonBytes: Long,
    val rawCount: Int,
    val rawBytes: Long,
    val exportBytes: Long,
)

/** 刪除原始资料的结果。 */
data class RawDeleteResult(val count: Int, val bytes: Long)

/** 匯入一個檔案的結果。 */
enum class ImportOutcome { IMPORTED, SKIPPED, INVALID }
