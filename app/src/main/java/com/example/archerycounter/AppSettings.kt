package com.example.archerycounter

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 设定页里的四个设定，存在手表本机。 */
class AppSettings(private val prefs: SharedPreferences) {

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, true))
    /** 应用开着时保持亮屏。预设：开 */
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _wakeLock = MutableStateFlow(prefs.getBoolean(KEY_WAKE_LOCK, false))
    /** 侦测中持有 CPU 唤醒锁。预设：关 */
    val wakeLock: StateFlow<Boolean> = _wakeLock.asStateFlow()

    private val _recordRaw = MutableStateFlow(prefs.getBoolean(KEY_RECORD_RAW, false))
    /** 每次记录另存一份原始数据 CSV。预设：关 */
    val recordRaw: StateFlow<Boolean> = _recordRaw.asStateFlow()

    private val _windowSec = MutableStateFlow(prefs.getInt(KEY_WINDOW_SEC, DEFAULT_WINDOW_SEC).coerceIn(MIN_WINDOW_SEC, MAX_WINDOW_SEC))
    /** 稳定度分析窗口（秒）。预设：2 */
    val windowSec: StateFlow<Int> = _windowSec.asStateFlow()

    fun setKeepScreenOn(v: Boolean) {
        _keepScreenOn.value = v
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, v).apply()
    }

    fun setWakeLock(v: Boolean) {
        _wakeLock.value = v
        prefs.edit().putBoolean(KEY_WAKE_LOCK, v).apply()
    }

    fun setRecordRaw(v: Boolean) {
        _recordRaw.value = v
        prefs.edit().putBoolean(KEY_RECORD_RAW, v).apply()
    }

    fun setWindowSec(v: Int) {
        val value = v.coerceIn(MIN_WINDOW_SEC, MAX_WINDOW_SEC)
        _windowSec.value = value
        prefs.edit().putInt(KEY_WINDOW_SEC, value).apply()
    }

    companion object {
        const val MIN_WINDOW_SEC = 1
        const val MAX_WINDOW_SEC = 3
        const val DEFAULT_WINDOW_SEC = 2

        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_WAKE_LOCK = "wake_lock"
        private const val KEY_RECORD_RAW = "record_raw"
        private const val KEY_WINDOW_SEC = "window_sec"
    }
}
