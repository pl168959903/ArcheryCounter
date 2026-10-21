package com.zgrcc.archeryassistant

import java.io.BufferedWriter
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/**
 * 负责「目前这一次记录」：收集每一箭、手动加减，以及（可选）原始数据 CSV，
 * 并随时写入 [SessionStore]。
 *
 * 传感器线程和主线程都会呼叫它，所以所有方法都加了同步锁。
 *
 * 为了避免产生一堆空记录：
 *  - 一般记录在第一箭（或第一次手动加减）发生时才建立文件；
 *  - 开启原始数据录制时，一开始就建立。
 * 每射一箭就立刻存档，所以即使应用被系统中途关掉，已经射的箭也不会丢，
 * 那条记录没有结束时间，界面上会显示为「中斷」。
 */
class SessionRecorder(private val store: SessionStore) {

    private var id: String? = null
    private var startMs = 0L
    private var threshold = 0f
    private var windowSec = 2
    private var hasGyro = false
    private var rawCsv = false
    private var tags: List<String> = emptyList()
    private var calibration = false
    private var comp: CompMeta? = null
    private var app = ""
    private var dev = ""
    private var det = ""
    private var uid = ""

    private val marks = ArrayList<Mark>()
    private val shots = ArrayList<ShotEntry>()
    private val manual = ArrayList<ManualEntry>()

    private var csv: BufferedWriter? = null
    private var csvRows = 0
    private val sb = StringBuilder(128)

    @get:Synchronized
    val activeId: String?
        get() = id

    @Synchronized
    fun start(
        startEpochMs: Long,
        threshold: Float,
        windowSec: Int,
        hasGyro: Boolean,
        rawCsv: Boolean,
        tags: List<String> = emptyList(),
        calibration: Boolean = false,
        comp: CompMeta? = null,
        app: String = "",
        dev: String = "",
        det: String = "",
    ): String {
        finishLocked(null)

        val base = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(startEpochMs))
        var candidate = base
        var n = 2
        while (store.exists(candidate)) {
            candidate = "${base}_$n"
            n++
        }

        id = candidate
        startMs = startEpochMs
        this.threshold = threshold
        this.windowSec = windowSec
        this.hasGyro = hasGyro
        this.rawCsv = rawCsv
        this.tags = tags
        this.calibration = calibration
        this.comp = comp
        this.app = app
        this.dev = dev
        this.det = det
        this.uid = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
        shots.clear()
        manual.clear()
        marks.clear()
        csvRows = 0

        if (rawCsv) {
            try {
                val w = BufferedWriter(
                    OutputStreamWriter(FileOutputStream(store.csvFile(candidate)), Charsets.UTF_8)
                )
                w.write("t_ms,ax,ay,az,a_mag,gx,gy,gz\n")
                csv = w
            } catch (_: IOException) {
                csv = null
            }
            saveLocked(null)
        }
        return candidate
    }

    @Synchronized
    fun addShot(
        relMs: Long,
        peak: Float,
        threshold: Float,
        accelRms: Float?,
        gyroRms: Float?,
        waveform: Waveform? = null,
        trace: Trace2D? = null,
    ): Int {
        if (id == null) return -1
        shots.add(ShotEntry(relMs, peak, threshold, accelRms, gyroRms, waveform, trace = trace))
        saveLocked(null)
        return shots.size - 1
    }

    /**
     * 放箭後 1 秒，補上這一箭的跟隨動作：較長的波形（若有）和放箭後晃動量。
     * [index] 是 [addShot] 回傳的位置。記錄已經結束或換成別筆時，[expectedId] 對不上，就什麼都不做。
     */
    @Synchronized
    fun attachFollow(
        expectedId: String?,
        index: Int,
        waveform: Waveform?,
        followAccelRms: Float?,
        followGyroRms: Float?,
    ) {
        if (id == null || id != expectedId || index !in shots.indices) return
        shots[index] = shots[index].copy(
            waveform = waveform ?: shots[index].waveform,
            followAccelRms = followAccelRms,
            followGyroRms = followGyroRms,
        )
        saveLocked(null)
    }

    /**
     * 把这次记录里最近一笔「还没被取消」的箭标记为误判（数据保留）。
     * 回传 true 表示确实取消了一笔；没有进行中的记录或没有可取消的箭则回传 false。
     */
    @Synchronized
    fun cancelLastShot(): Boolean {
        if (id == null) return false
        val index = shots.indexOfLast { !it.cancelled }
        if (index < 0) return false
        shots[index] = shots[index].copy(cancelled = true)
        saveLocked(null)
        return true
    }

    /** 這次記錄裡，時間落在 [startRelMs, endRelMs] 的有效（沒被取消）箭在 shots 裡的位置。 */
    @Synchronized
    fun validShotIndices(startRelMs: Long, endRelMs: Long): List<Int> =
        shots.indices.filter { !shots[it].cancelled && shots[it].relMs in startRelMs..endRelMs }

    @Synchronized
    fun scoreAt(index: Int): Int? = shots.getOrNull(index)?.score

    /** 進行中輸入或清除某一箭的環數（0～10）；位置不存在就什麼都不做。 */
    @Synchronized
    fun setScore(index: Int, score: Int?) {
        if (id == null || index !in shots.indices) return
        shots[index] = shots[index].copy(score = score?.coerceIn(0, 10))
        saveLocked(null)
    }

    /** 進行中設定或清除某一箭的自評（1 差、2 普通、3 好）；位置不存在就什麼都不做。 */
    @Synchronized
    fun setFeel(index: Int, feel: Int?) {
        if (id == null || index !in shots.indices) return
        shots[index] = shots[index].copy(feel = Feel.valid(feel))
        saveLocked(null)
    }

    /** 校准录制：一段动作开始。上一段若还没结束，先把它结束在同一时间。 */
    @Synchronized
    fun startMark(relMs: Long, label: String) {
        if (id == null) return
        closeOpenMark(relMs)
        marks.add(Mark(label, relMs, null))
        saveLocked(null)
    }

    /** 校准录制：目前这一段动作结束。 */
    @Synchronized
    fun endMark(relMs: Long) {
        if (id == null) return
        closeOpenMark(relMs)
        saveLocked(null)
    }

    /** 比賽模式：記一趟的起止（標籤 "P1" 是練習趟，"1" 是正式趟）。 */
    @Synchronized
    fun addMark(label: String, startRelMs: Long, endRelMs: Long) {
        if (id == null) return
        marks.add(Mark(label, startRelMs, endRelMs))
        saveLocked(null)
    }

    private fun closeOpenMark(relMs: Long) {
        val i = marks.indexOfLast { it.endRelMs == null }
        if (i >= 0) marks[i] = marks[i].copy(endRelMs = relMs)
    }

    /** 目前這次記錄的最終箭數（有效的偵測箭數加手動加減，不小於 0）；沒有進行中的記錄為 0。 */
    @Synchronized
    fun currentFinalCount(): Int =
        if (id == null) 0 else (shots.count { !it.cancelled } + manual.sumOf { it.delta }).coerceAtLeast(0)

    /** 目前這次記錄的快照（給每趟回饋用）；沒有進行中的記錄為 null。 */
    @Synchronized
    fun snapshot(): Session? {
        val sid = id ?: return null
        return Session(
            id = sid, startMs = startMs, endMs = null, threshold = threshold, windowSec = windowSec,
            hasGyro = hasGyro, rawCsv = rawCsv, shots = shots.toList(), manual = manual.toList(),
            tags = tags, calibration = calibration, marks = marks.toList(), comp = comp,
            app = app, dev = dev, uid = uid, det = det,
        )
    }

    @Synchronized
    fun addManual(relMs: Long, delta: Int) {
        if (id == null) return
        manual.add(ManualEntry(relMs, delta))
        saveLocked(null)
    }

    /** 原始数据一行：加速度（线性，m/s²）与角速度（°/s）。没开录制时什么都不做。 */
    @Synchronized
    fun writeRaw(relMs: Long, ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) {
        val w = csv ?: return
        val mag = sqrt(ax * ax + ay * ay + az * az)
        sb.setLength(0)
        sb.append(relMs).append(',')
            .append(ax).append(',').append(ay).append(',').append(az).append(',')
            .append(mag).append(',')
            .append(gx).append(',').append(gy).append(',').append(gz).append('\n')
        try {
            w.write(sb.toString())
            csvRows++
            if (csvRows % 500 == 0) w.flush()
        } catch (_: IOException) {
            csv = null
        }
    }

    @Synchronized
    fun stop(endEpochMs: Long) {
        finishLocked(endEpochMs)
    }

    private fun finishLocked(endMs: Long?) {
        if (id == null) return
        try {
            csv?.flush()
            csv?.close()
        } catch (_: IOException) {
        }
        csv = null

        if (endMs != null && (shots.isNotEmpty() || manual.isNotEmpty() || rawCsv)) {
            saveLocked(endMs)
        }
        id = null
        shots.clear()
        manual.clear()
        marks.clear()
    }

    private fun saveLocked(endMs: Long?) {
        val sid = id ?: return
        store.saveAsync(
            Session(
                id = sid,
                startMs = startMs,
                endMs = endMs,
                threshold = threshold,
                windowSec = windowSec,
                hasGyro = hasGyro,
                rawCsv = rawCsv,
                shots = shots.toList(),
                manual = manual.toList(),
                tags = tags,
                calibration = calibration,
                marks = marks.toList(),
                comp = comp,
                app = app,
                dev = dev,
                uid = uid,
                det = det,
            )
        )
    }
}
