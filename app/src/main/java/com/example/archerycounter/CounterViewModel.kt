package com.example.archerycounter

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 管理计数、传感器和设定。
 * 传感器回调在主线程，所以直接更新 StateFlow 即可。
 */
class CounterViewModel(app: Application) : AndroidViewModel(app), SensorEventListener {

    private val prefs = app.getSharedPreferences("archery", Context.MODE_PRIVATE)
    private val sensorManager = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // 优先用系统算好的"线性加速度"（已扣除重力），没有的话退回普通加速度计自己扣
    private val linearSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val sensorAvailable: Boolean = linearSensor != null || accelSensor != null

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private val _count = MutableStateFlow(prefs.getInt(KEY_COUNT, 0))
    val count: StateFlow<Int> = _count.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _threshold = MutableStateFlow(
        prefs.getFloat(KEY_THRESHOLD, ShotDetector.DEFAULT_SPIKE_THRESHOLD)
    )
    val threshold: StateFlow<Float> = _threshold.asStateFlow()

    private val _lastPeak = MutableStateFlow<Float?>(null)
    val lastPeak: StateFlow<Float?> = _lastPeak.asStateFlow()

    private val detector = ShotDetector(spikeThreshold = _threshold.value) { peak ->
        _lastPeak.value = peak
        setCount(_count.value + 1)
        buzz()
    }

    // 没有线性加速度传感器时，用低通滤波估算重力
    private val gravity = FloatArray(3)
    private var gravityReady = false

    fun start() {
        if (_running.value) return
        val sensor = linearSensor ?: accelSensor ?: return
        detector.reset()
        gravityReady = false
        sensorManager.registerListener(this, sensor, SAMPLING_PERIOD_US)
        _running.value = true
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        _running.value = false
    }

    fun adjustCount(delta: Int) = setCount((_count.value + delta).coerceAtLeast(0))

    fun resetCount() {
        setCount(0)
        _lastPeak.value = null
    }

    fun adjustThreshold(delta: Float) {
        val value = (_threshold.value + delta).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        _threshold.value = value
        detector.spikeThreshold = value
        prefs.edit().putFloat(KEY_THRESHOLD, value).apply()
    }

    private fun setCount(value: Int) {
        _count.value = value
        prefs.edit().putInt(KEY_COUNT, value).apply()
    }

    private fun buzz() {
        vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onSensorChanged(event: SensorEvent) {
        val timeMs = event.timestamp / 1_000_000L
        val v = event.values
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            detector.onSample(v[0], v[1], v[2], timeMs)
        } else {
            if (!gravityReady) {
                for (i in 0..2) gravity[i] = v[i]
                gravityReady = true
            }
            for (i in 0..2) {
                gravity[i] = GRAVITY_ALPHA * gravity[i] + (1 - GRAVITY_ALPHA) * v[i]
            }
            detector.onSample(v[0] - gravity[0], v[1] - gravity[1], v[2] - gravity[2], timeMs)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onCleared() {
        sensorManager.unregisterListener(this)
    }

    private companion object {
        const val KEY_COUNT = "count"
        const val KEY_THRESHOLD = "threshold"
        const val SAMPLING_PERIOD_US = 10_000      // 约 100Hz，系统可能给得更慢
        const val GRAVITY_ALPHA = 0.9f
        const val MIN_THRESHOLD = 6f
        const val MAX_THRESHOLD = 40f
    }
}
