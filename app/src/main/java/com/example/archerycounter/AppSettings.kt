package com.example.archerycounter

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 设定页里的设定，存在手表本机。 */
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

    private val _showTrendChart = MutableStateFlow(prefs.getBoolean(KEY_SHOW_TREND, true))
    /** 记录详情页显示趋势图。只影响显示，不影响档案大小。预设：开 */
    val showTrendChart: StateFlow<Boolean> = _showTrendChart.asStateFlow()

    private val _recordWaveform = MutableStateFlow(prefs.getBoolean(KEY_RECORD_WAVEFORM, true))
    /** 每一箭把放箭前后的波形存进记录（每箭约 1 KB）。关掉记录档案最小。预设：开 */
    val recordWaveform: StateFlow<Boolean> = _recordWaveform.asStateFlow()

    private val _liveWaveform = MutableStateFlow(prefs.getBoolean(KEY_LIVE_WAVEFORM, false))
    /** 主画面显示即时波形。只影响显示，但最耗电。预设：关 */
    val liveWaveform: StateFlow<Boolean> = _liveWaveform.asStateFlow()

    private val _showEnds = MutableStateFlow(prefs.getBoolean(KEY_SHOW_ENDS, true))
    /** 分趟统计：详情页显示逐趟，并在满一趟时震动提醒。预设：开 */
    val showEnds: StateFlow<Boolean> = _showEnds.asStateFlow()

    private val _arrowsPerEnd = MutableStateFlow(
        prefs.getInt(KEY_ARROWS_PER_END, DEFAULT_ARROWS_PER_END).coerceIn(MIN_ARROWS_PER_END, MAX_ARROWS_PER_END)
    )
    /** 每趟几箭。预设：6 */
    val arrowsPerEnd: StateFlow<Int> = _arrowsPerEnd.asStateFlow()

    private val _endVibration = MutableStateFlow(
        prefs.getInt(KEY_END_VIBRATION, EndVibration.DEFAULT).coerceIn(0, EndVibration.STYLE_COUNT - 1)
    )
    /** 满一趟的震动样式，见 [EndVibration]。预设：三连震 */
    val endVibration: StateFlow<Int> = _endVibration.asStateFlow()

    private val _followThrough = MutableStateFlow(prefs.getBoolean(KEY_FOLLOW_THROUGH, true))
    /** 记录放箭后 1 秒的跟随动作（更长的波形和放箭后晃动量）。预设：开 */
    val followThrough: StateFlow<Boolean> = _followThrough.asStateFlow()

    private val _showSessionTrend = MutableStateFlow(prefs.getBoolean(KEY_SESSION_TREND, true))
    /** 记录列表显示平均晃动量，并提供跨次趋势图。只影响显示。预设：开 */
    val showSessionTrend: StateFlow<Boolean> = _showSessionTrend.asStateFlow()

    private val _recordScore = MutableStateFlow(prefs.getBoolean(KEY_RECORD_SCORE, false))
    /** 单箭页显示环数输入。预设：关 */
    val recordScore: StateFlow<Boolean> = _recordScore.asStateFlow()

    private val _stillThreshold = MutableStateFlow(
        prefs.getInt(KEY_STILL_THRESHOLD, DEFAULT_STILL_THRESHOLD).coerceIn(MIN_STILL_THRESHOLD, MAX_STILL_THRESHOLD)
    )
    /** 放箭前要先「静止」的上限（m/s²）。预设：5 */
    val stillThreshold: StateFlow<Int> = _stillThreshold.asStateFlow()

    private val _cooldownSec = MutableStateFlow(
        prefs.getInt(KEY_COOLDOWN_SEC, DEFAULT_COOLDOWN_SEC).coerceIn(MIN_COOLDOWN_SEC, MAX_COOLDOWN_SEC)
    )
    /** 两次计数之间的最短间隔（秒）。预设：3 */
    val cooldownSec: StateFlow<Int> = _cooldownSec.asStateFlow()

    private val _touchLock = MutableStateFlow(prefs.getBoolean(KEY_TOUCH_LOCK, true))
    /** 偵測中主畫面提供「鎖定觸控」按鈕，避免拉弓、放箭時誤碰。預設：開 */
    val touchLock: StateFlow<Boolean> = _touchLock.asStateFlow()

    private val _tagsEnabled = MutableStateFlow(prefs.getBoolean(KEY_TAGS_ENABLED, true))
    /** 新記錄自動帶上條件標籤（距離、弓種）。預設：開 */
    val tagsEnabled: StateFlow<Boolean> = _tagsEnabled.asStateFlow()

    private val _distanceTag = MutableStateFlow(prefs.getString(KEY_DISTANCE_TAG, "").orEmpty())
    /** 目前的距離條件；空字串 = 未設定 */
    val distanceTag: StateFlow<String> = _distanceTag.asStateFlow()

    private val _bowTag = MutableStateFlow(prefs.getString(KEY_BOW_TAG, "").orEmpty())
    /** 目前的弓種條件；空字串 = 未設定 */
    val bowTag: StateFlow<String> = _bowTag.asStateFlow()

    private val _autoPause = MutableStateFlow(prefs.getBoolean(KEY_AUTO_PAUSE, false))
    /** 偵測中太久沒有放箭就自動暫停。預設：關 */
    val autoPause: StateFlow<Boolean> = _autoPause.asStateFlow()

    private val _autoPauseMin = MutableStateFlow(
        prefs.getInt(KEY_AUTO_PAUSE_MIN, AutoPause.DEFAULT_MINUTES).coerceIn(AutoPause.MIN_MINUTES, AutoPause.MAX_MINUTES)
    )
    /** 幾分鐘沒有放箭就自動暫停。預設：10 */
    val autoPauseMin: StateFlow<Int> = _autoPauseMin.asStateFlow()

    private val _lowBattery = MutableStateFlow(prefs.getBoolean(KEY_LOW_BATTERY, true))
    /** 電量低於 15% 時震動提醒一次。預設：開 */
    val lowBattery: StateFlow<Boolean> = _lowBattery.asStateFlow()

    private val _dailyGoal = MutableStateFlow(prefs.getInt(KEY_DAILY_GOAL, 0).coerceIn(0, Goal.MAX))
    /** 今日目標箭數，0 = 關閉。預設：0 */
    val dailyGoal: StateFlow<Int> = _dailyGoal.asStateFlow()

    private val _endFeedback = MutableStateFlow(prefs.getBoolean(KEY_END_FEEDBACK, true))
    /** 每趟射完在主畫面顯示這一趟的晃動量回饋。預設：開（需要分趟統計） */
    val endFeedback: StateFlow<Boolean> = _endFeedback.asStateFlow()

    private val _dimWhenRunning = MutableStateFlow(prefs.getBoolean(KEY_DIM, false))
    /** 偵測中降低螢幕亮度。預設：關 */
    val dimWhenRunning: StateFlow<Boolean> = _dimWhenRunning.asStateFlow()

    private val _dimLevel = MutableStateFlow(
        prefs.getInt(KEY_DIM_LEVEL, Brightness.DEFAULT_INDEX).coerceIn(0, Brightness.LEVELS.size - 1)
    )
    /** 降低亮度的檔位，見 [Brightness]。預設：中 */
    val dimLevel: StateFlow<Int> = _dimLevel.asStateFlow()

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

    fun setShowTrendChart(v: Boolean) {
        _showTrendChart.value = v
        prefs.edit().putBoolean(KEY_SHOW_TREND, v).apply()
    }

    fun setRecordWaveform(v: Boolean) {
        _recordWaveform.value = v
        prefs.edit().putBoolean(KEY_RECORD_WAVEFORM, v).apply()
    }

    fun setLiveWaveform(v: Boolean) {
        _liveWaveform.value = v
        prefs.edit().putBoolean(KEY_LIVE_WAVEFORM, v).apply()
    }

    fun setShowEnds(v: Boolean) {
        _showEnds.value = v
        prefs.edit().putBoolean(KEY_SHOW_ENDS, v).apply()
    }

    fun setArrowsPerEnd(v: Int) {
        val value = v.coerceIn(MIN_ARROWS_PER_END, MAX_ARROWS_PER_END)
        _arrowsPerEnd.value = value
        prefs.edit().putInt(KEY_ARROWS_PER_END, value).apply()
    }

    fun setEndVibration(v: Int) {
        val value = v.coerceIn(0, EndVibration.STYLE_COUNT - 1)
        _endVibration.value = value
        prefs.edit().putInt(KEY_END_VIBRATION, value).apply()
    }

    fun setFollowThrough(v: Boolean) {
        _followThrough.value = v
        prefs.edit().putBoolean(KEY_FOLLOW_THROUGH, v).apply()
    }

    fun setShowSessionTrend(v: Boolean) {
        _showSessionTrend.value = v
        prefs.edit().putBoolean(KEY_SESSION_TREND, v).apply()
    }

    fun setRecordScore(v: Boolean) {
        _recordScore.value = v
        prefs.edit().putBoolean(KEY_RECORD_SCORE, v).apply()
    }

    fun setStillThreshold(v: Int) {
        val value = v.coerceIn(MIN_STILL_THRESHOLD, MAX_STILL_THRESHOLD)
        _stillThreshold.value = value
        prefs.edit().putInt(KEY_STILL_THRESHOLD, value).apply()
    }

    fun setCooldownSec(v: Int) {
        val value = v.coerceIn(MIN_COOLDOWN_SEC, MAX_COOLDOWN_SEC)
        _cooldownSec.value = value
        prefs.edit().putInt(KEY_COOLDOWN_SEC, value).apply()
    }

    fun setTouchLock(v: Boolean) {
        _touchLock.value = v
        prefs.edit().putBoolean(KEY_TOUCH_LOCK, v).apply()
    }

    fun setTagsEnabled(v: Boolean) {
        _tagsEnabled.value = v
        prefs.edit().putBoolean(KEY_TAGS_ENABLED, v).apply()
    }

    fun setDistanceTag(v: String?) {
        _distanceTag.value = v.orEmpty()
        prefs.edit().putString(KEY_DISTANCE_TAG, v.orEmpty()).apply()
    }

    fun setBowTag(v: String?) {
        _bowTag.value = v.orEmpty()
        prefs.edit().putString(KEY_BOW_TAG, v.orEmpty()).apply()
    }

    fun setAutoPause(v: Boolean) {
        _autoPause.value = v
        prefs.edit().putBoolean(KEY_AUTO_PAUSE, v).apply()
    }

    fun setAutoPauseMin(v: Int) {
        val value = v.coerceIn(AutoPause.MIN_MINUTES, AutoPause.MAX_MINUTES)
        _autoPauseMin.value = value
        prefs.edit().putInt(KEY_AUTO_PAUSE_MIN, value).apply()
    }

    fun setLowBattery(v: Boolean) {
        _lowBattery.value = v
        prefs.edit().putBoolean(KEY_LOW_BATTERY, v).apply()
    }

    fun setDailyGoal(v: Int) {
        val value = v.coerceIn(0, Goal.MAX)
        _dailyGoal.value = value
        prefs.edit().putInt(KEY_DAILY_GOAL, value).apply()
    }

    fun setEndFeedback(v: Boolean) {
        _endFeedback.value = v
        prefs.edit().putBoolean(KEY_END_FEEDBACK, v).apply()
    }

    fun setDimWhenRunning(v: Boolean) {
        _dimWhenRunning.value = v
        prefs.edit().putBoolean(KEY_DIM, v).apply()
    }

    fun setDimLevel(v: Int) {
        val value = v.coerceIn(0, Brightness.LEVELS.size - 1)
        _dimLevel.value = value
        prefs.edit().putInt(KEY_DIM_LEVEL, value).apply()
    }

    /** 把設定頁的所有項目還原成預設值。不動記錄、主畫面的箭數和閾值。 */
    fun resetDefaults() {
        setKeepScreenOn(true)
        setWakeLock(false)
        setRecordRaw(false)
        setWindowSec(DEFAULT_WINDOW_SEC)
        setShowTrendChart(true)
        setRecordWaveform(true)
        setLiveWaveform(false)
        setShowEnds(true)
        setArrowsPerEnd(DEFAULT_ARROWS_PER_END)
        setEndVibration(EndVibration.DEFAULT)
        setFollowThrough(true)
        setShowSessionTrend(true)
        setRecordScore(false)
        setStillThreshold(DEFAULT_STILL_THRESHOLD)
        setCooldownSec(DEFAULT_COOLDOWN_SEC)
        setTouchLock(true)
        setTagsEnabled(true)
        setDistanceTag(null)
        setBowTag(null)
        setAutoPause(false)
        setAutoPauseMin(AutoPause.DEFAULT_MINUTES)
        setLowBattery(true)
        setDailyGoal(0)
        setEndFeedback(true)
        setDimWhenRunning(false)
        setDimLevel(Brightness.DEFAULT_INDEX)
    }

    companion object {
        const val MIN_ARROWS_PER_END = 1
        const val MAX_ARROWS_PER_END = 12
        const val DEFAULT_ARROWS_PER_END = 6

        const val MIN_STILL_THRESHOLD = 2
        const val MAX_STILL_THRESHOLD = 10
        const val DEFAULT_STILL_THRESHOLD = 5

        const val MIN_COOLDOWN_SEC = 1
        const val MAX_COOLDOWN_SEC = 6
        const val DEFAULT_COOLDOWN_SEC = 3

        const val MIN_WINDOW_SEC = 1
        const val MAX_WINDOW_SEC = 3
        const val DEFAULT_WINDOW_SEC = 2

        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_WAKE_LOCK = "wake_lock"
        private const val KEY_RECORD_RAW = "record_raw"
        private const val KEY_WINDOW_SEC = "window_sec"
        private const val KEY_SHOW_TREND = "show_trend_chart"
        private const val KEY_RECORD_WAVEFORM = "record_waveform"
        private const val KEY_LIVE_WAVEFORM = "live_waveform"
        private const val KEY_SHOW_ENDS = "show_ends"
        private const val KEY_AUTO_PAUSE = "auto_pause"
        private const val KEY_AUTO_PAUSE_MIN = "auto_pause_min"
        private const val KEY_LOW_BATTERY = "low_battery"
        private const val KEY_DAILY_GOAL = "daily_goal"
        private const val KEY_END_FEEDBACK = "end_feedback"
        private const val KEY_DIM = "dim_when_running"
        private const val KEY_DIM_LEVEL = "dim_level"
        private const val KEY_TOUCH_LOCK = "touch_lock"
        private const val KEY_TAGS_ENABLED = "tags_enabled"
        private const val KEY_DISTANCE_TAG = "distance_tag"
        private const val KEY_BOW_TAG = "bow_tag"
        private const val KEY_ARROWS_PER_END = "arrows_per_end"
        private const val KEY_END_VIBRATION = "end_vibration"
        private const val KEY_FOLLOW_THROUGH = "follow_through"
        private const val KEY_SESSION_TREND = "session_trend"
        private const val KEY_RECORD_SCORE = "record_score"
        private const val KEY_STILL_THRESHOLD = "still_threshold"
        private const val KEY_COOLDOWN_SEC = "cooldown_sec"
    }
}
