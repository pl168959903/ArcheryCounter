package com.zgrcc.archeryassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 電量監看與低電量提醒（v1.6，v1.14.2 從 CounterViewModel 拆出，行為不變）。
 * 只在偵測中註冊廣播：[start] 開始、[stop] 結束。提醒規則在 [BatteryRule]；
 * 需要警告時呼叫 [onWarn]（用來震動）。
 */
class BatteryMonitor(
    private val context: Context,
    private val settings: AppSettings,
    private val onWarn: () -> Unit,
) {
    private val _low = MutableStateFlow(false)
    val low: StateFlow<Boolean> = _low.asStateFlow()
    private val _percent = MutableStateFlow(-1)
    val percent: StateFlow<Int> = _percent.asStateFlow()

    private var warned = false
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) handle(intent)
        }
    }

    /** 開始偵測時呼叫：清掉上次的警告狀態並註冊。 */
    fun start() {
        warned = false
        _low.value = false
        register()
    }

    /** 停止偵測（或清理）時呼叫。 */
    fun stop() {
        _low.value = false
        unregister()
    }

    private fun register() {
        if (registered) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // ACTION_BATTERY_CHANGED 是黏性廣播：註冊的當下就會收到目前的電量
        val sticky = if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        registered = true
        if (sticky != null) handle(sticky)
    }

    private fun unregister() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
        registered = false
    }

    private fun handle(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        _percent.value = pct

        if (!settings.lowBattery.value) {
            _low.value = false
            return
        }
        val d = BatteryRule.decide(pct, charging, warned)
        warned = d.warned
        _low.value = d.warned
        if (d.warnNow) onWarn()
    }
}
