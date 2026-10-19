package com.example.archerycounter

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
        shots.clear()
        manual.clear()
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
    fun addShot(relMs: Long, peak: Float, threshold: Float, accelRms: Float?, gyroRms: Float?) {
        if (id == null) return
        shots.add(ShotEntry(relMs, peak, threshold, accelRms, gyroRms))
        saveLocked(null)
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
            )
        )
    }
}
