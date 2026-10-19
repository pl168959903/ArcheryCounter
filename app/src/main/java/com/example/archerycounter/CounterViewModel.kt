package com.example.archerycounter

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 最近一箭，显示在主画面上。 */
data class LastShot(val peak: Float, val accelRms: Float?, val gyroRms: Float?)

/**
 * 管理计数、传感器、设定和记录。
 * 传感器回调在背景线程；StateFlow 本身线程安全，可以直接更新。
 */
class CounterViewModel(app: Application) : AndroidViewModel(app), SensorEventListener {

    private val appContext = app.applicationContext
    private val prefs = appContext.getSharedPreferences("archery", Context.MODE_PRIVATE)
    val settings = AppSettings(prefs)

    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    // 传感器资料约 100Hz，放在背景线程处理，不占用负责画面的主线程
    private val sensorThread = HandlerThread("shot-sensor").apply { start() }
    private val sensorHandler = Handler(sensorThread.looper)

    // 优先用系统算好的「线性加速度」（已扣除重力），没有的话退回普通加速度计自己扣
    private val linearSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    val sensorAvailable: Boolean = linearSensor != null || accelSensor != null
    val gyroAvailable: Boolean = gyroSensor != null

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    // 取得外部存储路径是跨进程呼叫，可能较慢，所以延后到背景线程暖机
    private val store by lazy { SessionStore(appContext.getExternalFilesDir(null) ?: appContext.filesDir) }
    private val recorder by lazy { SessionRecorder(store) }
    private val tracker = StabilityTracker()

    private val countLock = Any()
    private val _count = MutableStateFlow(prefs.getInt(KEY_COUNT, 0))
    val count: StateFlow<Int> = _count.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _threshold = MutableStateFlow(
        prefs.getFloat(KEY_THRESHOLD, ShotDetector.DEFAULT_SPIKE_THRESHOLD)
    )
    val threshold: StateFlow<Float> = _threshold.asStateFlow()

    private val _lastShot = MutableStateFlow<LastShot?>(null)
    val lastShot: StateFlow<LastShot?> = _lastShot.asStateFlow()

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeId.asStateFlow()

    // 本次记录的时间基准与窗口长度（开始时设定，传感器线程读取）
    @Volatile private var baseElapsedMs = 0L
    @Volatile private var windowMs = AppSettings.DEFAULT_WINDOW_SEC * 1000L

    private var wakeLock: PowerManager.WakeLock? = null

    private val detector = ShotDetector(spikeThreshold = _threshold.value) { peak, triggerMs ->
        onShot(peak, triggerMs)
    }

    // 没有线性加速度传感器时，用低通滤波估算重力
    private val gravity = FloatArray(3)
    private var gravityReady = false

    // 最近一笔角速度 (°/s)，写原始数据时和加速度配对
    private var lastGx = 0f
    private var lastGy = 0f
    private var lastGz = 0f

    init {
        viewModelScope.launch(Dispatchers.IO) { recorder.activeId } // 暖机，建立存储目录
        viewModelScope.launch { settings.wakeLock.collect { updateWakeLock() } }
    }

    // ---- 侦测与记录 ----

    fun start() {
        if (_running.value) return
        val sensor = linearSensor ?: accelSensor ?: return

        detector.reset()
        tracker.reset()
        gravityReady = false
        lastGx = 0f
        lastGy = 0f
        lastGz = 0f

        baseElapsedMs = SystemClock.elapsedRealtime()
        val windowSec = settings.windowSec.value
        windowMs = windowSec * 1000L
        _activeId.value = recorder.start(
            startEpochMs = System.currentTimeMillis(),
            threshold = _threshold.value,
            windowSec = windowSec,
            hasGyro = gyroSensor != null,
            rawCsv = settings.recordRaw.value,
        )

        _running.value = true
        sensorManager.registerListener(this, sensor, SAMPLING_PERIOD_US, sensorHandler)
        gyroSensor?.let { sensorManager.registerListener(this, it, SAMPLING_PERIOD_US, sensorHandler) }
        updateWakeLock()
    }

    fun stop() {
        if (!_running.value) return
        _running.value = false // 之后到达的传感器事件会被丢弃
        sensorManager.unregisterListener(this)
        recorder.stop(System.currentTimeMillis())
        _activeId.value = null
        updateWakeLock()
        refreshSessions()
    }

    /** 放箭侦测到时（传感器线程）。 */
    private fun onShot(peak: Float, triggerMs: Long) {
        // 取放箭前的窗口，避开最近 GAP_MS（冲击的上升沿）
        val st = tracker.compute(endMs = triggerMs - ShotDetector.GAP_MS, windowMs = windowMs)
        val gyr = if (gyroSensor != null) st.gyroRms else null

        recorder.addShot(
            relMs = (triggerMs - baseElapsedMs).coerceAtLeast(0L),
            peak = peak,
            threshold = detector.spikeThreshold,
            accelRms = st.accelRms,
            gyroRms = gyr,
        )
        _lastShot.value = LastShot(peak, st.accelRms, gyr)
        changeCount(+1)
        buzz()
    }

    // ---- 计数与阈值 ----

    fun adjustCount(delta: Int) {
        val applied = changeCount(delta)
        if (applied != 0 && _running.value) {
            recorder.addManual(
                relMs = (SystemClock.elapsedRealtime() - baseElapsedMs).coerceAtLeast(0L),
                delta = applied,
            )
        }
    }

    fun resetCount() {
        synchronized(countLock) {
            _count.value = 0
            prefs.edit().putInt(KEY_COUNT, 0).apply()
        }
        _lastShot.value = null
    }

    /** 回传实际改变的量（到 0 为止不会变负）。 */
    private fun changeCount(delta: Int): Int = synchronized(countLock) {
        val old = _count.value
        val new = (old + delta).coerceAtLeast(0)
        _count.value = new
        prefs.edit().putInt(KEY_COUNT, new).apply()
        new - old
    }

    fun adjustThreshold(delta: Float) {
        val value = (_threshold.value + delta).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        _threshold.value = value
        detector.spikeThreshold = value
        prefs.edit().putFloat(KEY_THRESHOLD, value).apply()
    }

    // ---- 记录列表 ----

    fun refreshSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            _sessions.value = store.listSessions().sortedByDescending { it.startMs }
        }
    }

    fun selectSession(id: String) {
        _selectedId.value = id
    }

    suspend fun loadSession(id: String): Session? = withContext(Dispatchers.IO) { store.load(id) }

    fun deleteSession(id: String) {
        if (id == _activeId.value) return
        viewModelScope.launch(Dispatchers.IO) {
            store.delete(id)
            _sessions.value = store.listSessions().sortedByDescending { it.startMs }
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            store.deleteAll(keepId = _activeId.value)
            _sessions.value = store.listSessions().sortedByDescending { it.startMs }
        }
    }

    // ---- 唤醒锁与震动 ----

    private fun updateWakeLock() {
        val want = _running.value && settings.wakeLock.value
        if (want) {
            val wl = wakeLock ?: powerManager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ArcheryCounter:detect")
                .apply { setReferenceCounted(false) }
                .also { wakeLock = it }
            if (!wl.isHeld) wl.acquire(MAX_WAKE_LOCK_MS)
        } else {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
    }

    private fun buzz() {
        vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // ---- 传感器回调（背景线程） ----

    override fun onSensorChanged(event: SensorEvent) {
        if (!_running.value) return
        val timeMs = event.timestamp / 1_000_000L
        val v = event.values

        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            lastGx = v[0] * RAD_TO_DEG
            lastGy = v[1] * RAD_TO_DEG
            lastGz = v[2] * RAD_TO_DEG
            tracker.addGyro(lastGx, lastGy, lastGz, timeMs)
            return
        }

        val ax: Float
        val ay: Float
        val az: Float
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            ax = v[0]
            ay = v[1]
            az = v[2]
        } else {
            if (!gravityReady) {
                for (i in 0..2) gravity[i] = v[i]
                gravityReady = true
            }
            for (i in 0..2) {
                gravity[i] = GRAVITY_ALPHA * gravity[i] + (1 - GRAVITY_ALPHA) * v[i]
            }
            ax = v[0] - gravity[0]
            ay = v[1] - gravity[1]
            az = v[2] - gravity[2]
        }

        tracker.addAccel(ax, ay, az, timeMs)
        recorder.writeRaw(timeMs - baseElapsedMs, ax, ay, az, lastGx, lastGy, lastGz)
        detector.onSample(ax, ay, az, timeMs)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onCleared() {
        sensorManager.unregisterListener(this)
        if (_running.value) {
            _running.value = false
            recorder.stop(System.currentTimeMillis())
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        sensorThread.quitSafely()
    }

    private companion object {
        const val KEY_COUNT = "count"
        const val KEY_THRESHOLD = "threshold"
        const val SAMPLING_PERIOD_US = 10_000      // 约 100Hz，系统可能给得更慢或更快
        const val GRAVITY_ALPHA = 0.9f
        const val MIN_THRESHOLD = 6f
        const val MAX_THRESHOLD = 40f
        const val RAD_TO_DEG = 57.29578f
        const val MAX_WAKE_LOCK_MS = 4L * 60 * 60 * 1000 // 安全上限 4 小时
    }
}
