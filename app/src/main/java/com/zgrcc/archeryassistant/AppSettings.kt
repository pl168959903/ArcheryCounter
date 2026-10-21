package com.zgrcc.archeryassistant

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

    private val _splash = MutableStateFlow(prefs.getBoolean(KEY_SPLASH, true))
    /** 開啟應用時先播放約 3 秒的啟動動畫（圖示、版本、作者、簽章）。預設：開 */
    val splash: StateFlow<Boolean> = _splash.asStateFlow()

    private val _splashTap = MutableStateFlow(prefs.getBoolean(KEY_SPLASH_TAP, true))
    /** 啟動動畫播完後停在畫面上，點按螢幕才進入主畫面；關閉則約 3 秒後自動進入。預設：開 */
    val splashTap: StateFlow<Boolean> = _splashTap.asStateFlow()

    private val _language = MutableStateFlow(prefs.getInt(KEY_LANGUAGE, I18n.AUTO).coerceIn(0, I18n.COUNT - 1))
    /** 介面語言：0 自動、1 繁體中文、2 English。預設：自動 */
    val language: StateFlow<Int> = _language.asStateFlow()

    fun setLanguage(v: Int) {
        val value = v.coerceIn(0, I18n.COUNT - 1)
        _language.value = value
        prefs.edit().putInt(KEY_LANGUAGE, value).apply()
    }

    private val _samplingHz = MutableStateFlow(prefs.getInt(KEY_SAMPLING_HZ, SamplingRate.DEFAULT_HZ).let { SamplingRate.valid(it) })
    /** 感測器取樣頻率（Hz）：50／100／200。下次開始偵測時生效。預設：100 */
    val samplingHz: StateFlow<Int> = _samplingHz.asStateFlow()

    fun setSamplingHz(v: Int) {
        val value = v.let { SamplingRate.valid(it) }
        _samplingHz.value = value
        prefs.edit().putInt(KEY_SAMPLING_HZ, value).apply()
    }

    private val _fatigueAlert = MutableStateFlow(prefs.getBoolean(KEY_FATIGUE_ALERT, true))
    /** 最近幾趟比剛開始抖很多時震動提醒一次。預設：開 */
    val fatigueAlert: StateFlow<Boolean> = _fatigueAlert.asStateFlow()

    fun setFatigueAlert(v: Boolean) {
        val value = v
        _fatigueAlert.value = value
        prefs.edit().putBoolean(KEY_FATIGUE_ALERT, value).apply()
    }

    private val _fatiguePct = MutableStateFlow(prefs.getInt(KEY_FATIGUE_PCT, FatigueRule.DEFAULT_PCT).coerceIn(FatigueRule.MIN_PCT, FatigueRule.MAX_PCT))
    /** 疲勞提醒的門檻（%）。預設：25 */
    val fatiguePct: StateFlow<Int> = _fatiguePct.asStateFlow()

    fun setFatiguePct(v: Int) {
        val value = v.coerceIn(FatigueRule.MIN_PCT, FatigueRule.MAX_PCT)
        _fatiguePct.value = value
        prefs.edit().putInt(KEY_FATIGUE_PCT, value).apply()
    }

    private val _compEnabled = MutableStateFlow(prefs.getBoolean(KEY_COMP_ENABLED, true))
    /** 比賽模式總開關：關閉時主畫面不顯示比賽入口。預設：開 */
    val compEnabled: StateFlow<Boolean> = _compEnabled.asStateFlow()

    fun setCompEnabled(v: Boolean) {
        val value = v
        _compEnabled.value = value
        prefs.edit().putBoolean(KEY_COMP_ENABLED, value).apply()
    }

    private val _compFormat = MutableStateFlow(prefs.getInt(KEY_COMP_FORMAT, CompFormat.INDOOR_18).coerceIn(0, CompFormat.MAX_ID))
    /** 比賽賽制：0 室內18米、1 室外70米、2 自訂。預設：室內18米 */
    val compFormat: StateFlow<Int> = _compFormat.asStateFlow()

    fun setCompFormat(v: Int) {
        val value = v.coerceIn(0, CompFormat.MAX_ID)
        _compFormat.value = value
        prefs.edit().putInt(KEY_COMP_FORMAT, value).apply()
    }

    private val _compArrows = MutableStateFlow(prefs.getInt(KEY_COMP_ARROWS, CompLimits.DEFAULT_ARROWS).coerceIn(CompLimits.MIN_ARROWS, CompLimits.MAX_ARROWS))
    /** 自訂賽制：每趟幾箭。預設：3 */
    val compArrows: StateFlow<Int> = _compArrows.asStateFlow()

    fun setCompArrows(v: Int) {
        val value = v.coerceIn(CompLimits.MIN_ARROWS, CompLimits.MAX_ARROWS)
        _compArrows.value = value
        prefs.edit().putInt(KEY_COMP_ARROWS, value).apply()
    }

    private val _compEnds = MutableStateFlow(prefs.getInt(KEY_COMP_ENDS, CompLimits.DEFAULT_ENDS).coerceIn(CompLimits.MIN_ENDS, CompLimits.MAX_ENDS))
    /** 自訂賽制：共幾趟（正式趟）。預設：10 */
    val compEnds: StateFlow<Int> = _compEnds.asStateFlow()

    fun setCompEnds(v: Int) {
        val value = v.coerceIn(CompLimits.MIN_ENDS, CompLimits.MAX_ENDS)
        _compEnds.value = value
        prefs.edit().putInt(KEY_COMP_ENDS, value).apply()
    }

    private val _compSeconds = MutableStateFlow(prefs.getInt(KEY_COMP_SECONDS, CompLimits.DEFAULT_SECONDS).coerceIn(CompLimits.MIN_SECONDS, CompLimits.MAX_SECONDS))
    /** 自訂賽制：每趟幾秒。預設：120 */
    val compSeconds: StateFlow<Int> = _compSeconds.asStateFlow()

    fun setCompSeconds(v: Int) {
        val value = v.coerceIn(CompLimits.MIN_SECONDS, CompLimits.MAX_SECONDS)
        _compSeconds.value = value
        prefs.edit().putInt(KEY_COMP_SECONDS, value).apply()
    }

    private val _compPractice = MutableStateFlow(prefs.getInt(KEY_COMP_PRACTICE, CompLimits.DEFAULT_PRACTICE).coerceIn(0, CompLimits.MAX_PRACTICE))
    /** 練習趟數（不計入正式成績）。預設：2 */
    val compPractice: StateFlow<Int> = _compPractice.asStateFlow()

    fun setCompPractice(v: Int) {
        val value = v.coerceIn(0, CompLimits.MAX_PRACTICE)
        _compPractice.value = value
        prefs.edit().putInt(KEY_COMP_PRACTICE, value).apply()
    }

    private val _compPrepare = MutableStateFlow(prefs.getInt(KEY_COMP_PREPARE, CompLimits.DEFAULT_PREPARE).coerceIn(0, CompLimits.MAX_PREPARE))
    /** 每趟開始前的準備倒數秒數，0 = 關閉。預設：10 */
    val compPrepare: StateFlow<Int> = _compPrepare.asStateFlow()

    fun setCompPrepare(v: Int) {
        val value = v.coerceIn(0, CompLimits.MAX_PREPARE)
        _compPrepare.value = value
        prefs.edit().putInt(KEY_COMP_PREPARE, value).apply()
    }

    private val _compRemind30 = MutableStateFlow(prefs.getBoolean(KEY_COMP_R30, true))
    /** 每趟剩 30 秒震動提醒。預設：開 */
    val compRemind30: StateFlow<Boolean> = _compRemind30.asStateFlow()

    fun setCompRemind30(v: Boolean) {
        val value = v
        _compRemind30.value = value
        prefs.edit().putBoolean(KEY_COMP_R30, value).apply()
    }

    private val _compRemind10 = MutableStateFlow(prefs.getBoolean(KEY_COMP_R10, true))
    /** 每趟剩 10 秒震動提醒。預設：開 */
    val compRemind10: StateFlow<Boolean> = _compRemind10.asStateFlow()

    fun setCompRemind10(v: Boolean) {
        val value = v
        _compRemind10.value = value
        prefs.edit().putBoolean(KEY_COMP_R10, value).apply()
    }

    private val _compAutoNext = MutableStateFlow(prefs.getInt(KEY_COMP_AUTO_NEXT, CompLimits.DEFAULT_AUTO_NEXT).let { if (it in CompLimits.AUTO_NEXT_OPTIONS) it else CompLimits.DEFAULT_AUTO_NEXT })
    /** 休息幾秒後自動開始下一趟，0 = 手動按「下一趟」。預設：0 */
    val compAutoNext: StateFlow<Int> = _compAutoNext.asStateFlow()

    fun setCompAutoNext(v: Int) {
        val value = v.let { if (it in CompLimits.AUTO_NEXT_OPTIONS) it else CompLimits.DEFAULT_AUTO_NEXT }
        _compAutoNext.value = value
        prefs.edit().putInt(KEY_COMP_AUTO_NEXT, value).apply()
    }

    private val _compColors = MutableStateFlow(prefs.getBoolean(KEY_COMP_COLORS, true))
    /** 比賽畫面配色：準備紅底白字、射擊綠底黑字。預設：開 */
    val compColors: StateFlow<Boolean> = _compColors.asStateFlow()

    fun setCompColors(v: Boolean) {
        val value = v
        _compColors.value = value
        prefs.edit().putBoolean(KEY_COMP_COLORS, value).apply()
    }

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUND_ENABLED, true))
    /** 比賽模式聲音總開關。預設：開 */
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    fun setSoundEnabled(v: Boolean) {
        val value = v
        _soundEnabled.value = value
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()
    }

    private val _soundVolume = MutableStateFlow(prefs.getInt(KEY_SOUND_VOLUME, MatchSound.DEFAULT_VOLUME_INDEX).coerceIn(0, MatchSound.VOLUMES.size - 1))
    /** 比賽聲音音量檔位。預設：高（75%） */
    val soundVolume: StateFlow<Int> = _soundVolume.asStateFlow()

    fun setSoundVolume(v: Int) {
        val value = v.coerceIn(0, MatchSound.VOLUMES.size - 1)
        _soundVolume.value = value
        prefs.edit().putInt(KEY_SOUND_VOLUME, value).apply()
    }

    private val _soundStyle = MutableStateFlow(prefs.getInt(KEY_SOUND_STYLE, MatchSound.DEFAULT_STYLE).coerceIn(0, MatchSound.STYLE_COUNT - 1))
    /** 比賽聲音音色。預設：嗶聲 */
    val soundStyle: StateFlow<Int> = _soundStyle.asStateFlow()

    fun setSoundStyle(v: Int) {
        val value = v.coerceIn(0, MatchSound.STYLE_COUNT - 1)
        _soundStyle.value = value
        prefs.edit().putInt(KEY_SOUND_STYLE, value).apply()
    }

    private val _soundCount = MutableStateFlow(prefs.getBoolean(KEY_SOUND_COUNT, true))
    /** 準備倒數最後 3 秒的聲音。預設：開 */
    val soundCount: StateFlow<Boolean> = _soundCount.asStateFlow()

    fun setSoundCount(v: Boolean) {
        val value = v
        _soundCount.value = value
        prefs.edit().putBoolean(KEY_SOUND_COUNT, value).apply()
    }

    private val _soundGo = MutableStateFlow(prefs.getBoolean(KEY_SOUND_GO, true))
    /** 開始射擊的聲音。預設：開 */
    val soundGo: StateFlow<Boolean> = _soundGo.asStateFlow()

    fun setSoundGo(v: Boolean) {
        val value = v
        _soundGo.value = value
        prefs.edit().putBoolean(KEY_SOUND_GO, value).apply()
    }

    private val _soundWarn = MutableStateFlow(prefs.getBoolean(KEY_SOUND_WARN, true))
    /** 剩 30、10 秒提醒的聲音。預設：開 */
    val soundWarn: StateFlow<Boolean> = _soundWarn.asStateFlow()

    fun setSoundWarn(v: Boolean) {
        val value = v
        _soundWarn.value = value
        prefs.edit().putBoolean(KEY_SOUND_WARN, value).apply()
    }

    private val _soundEnd = MutableStateFlow(prefs.getBoolean(KEY_SOUND_END, true))
    /** 時間到、射滿箭數的聲音。預設：開 */
    val soundEnd: StateFlow<Boolean> = _soundEnd.asStateFlow()

    fun setSoundEnd(v: Boolean) {
        val value = v
        _soundEnd.value = value
        prefs.edit().putBoolean(KEY_SOUND_END, value).apply()
    }

    private val _paceAnalysis = MutableStateFlow(prefs.getBoolean(KEY_PACE, true))
    /** 比賽記錄顯示節奏分析。預設：開 */
    val paceAnalysis: StateFlow<Boolean> = _paceAnalysis.asStateFlow()
    fun setPaceAnalysis(v: Boolean) {
        _paceAnalysis.value = v
        prefs.edit().putBoolean(KEY_PACE, v).apply()
    }

    private val _groupCompare = MutableStateFlow(prefs.getBoolean(KEY_GROUP_COMPARE, true))
    /** 記錄列表顯示「條件對比」。預設：開 */
    val groupCompare: StateFlow<Boolean> = _groupCompare.asStateFlow()
    fun setGroupCompare(v: Boolean) {
        _groupCompare.value = v
        prefs.edit().putBoolean(KEY_GROUP_COMPARE, v).apply()
    }

    private val _personalBest = MutableStateFlow(prefs.getBoolean(KEY_PERSONAL_BEST, true))
    /** 顯示個人最佳的 ★ 標記。預設：開 */
    val personalBest: StateFlow<Boolean> = _personalBest.asStateFlow()
    fun setPersonalBest(v: Boolean) {
        _personalBest.value = v
        prefs.edit().putBoolean(KEY_PERSONAL_BEST, v).apply()
    }

    private val _overview = MutableStateFlow(prefs.getBoolean(KEY_OVERVIEW, true))
    /** 記錄列表顯示「訓練總覽」。預設：開 */
    val overview: StateFlow<Boolean> = _overview.asStateFlow()
    fun setOverview(v: Boolean) {
        _overview.value = v
        prefs.edit().putBoolean(KEY_OVERVIEW, v).apply()
    }

    private val _autoClean = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CLEAN, false))
    /** 開啟應用時自動清理舊記錄的原始資料、波形和 2D 軌跡。預設：關 */
    val autoClean: StateFlow<Boolean> = _autoClean.asStateFlow()
    fun setAutoClean(v: Boolean) {
        _autoClean.value = v
        prefs.edit().putBoolean(KEY_AUTO_CLEAN, v).apply()
    }

    private val _autoCleanDays = MutableStateFlow(AutoClean.validDays(prefs.getInt(KEY_AUTO_CLEAN_DAYS, AutoClean.DEFAULT_DAYS)))
    /** 超過幾天的記錄才清理（30／60／90）。預設：60 */
    val autoCleanDays: StateFlow<Int> = _autoCleanDays.asStateFlow()
    fun setAutoCleanDays(v: Int) {
        val value = AutoClean.validDays(v)
        _autoCleanDays.value = value
        prefs.edit().putInt(KEY_AUTO_CLEAN_DAYS, value).apply()
    }

    /** 上次自動清理的結果；不屬於「恢復預設設定」 */
    var cleanLast: String
        get() = prefs.getString(KEY_CLEAN_LAST, "") ?: ""
        set(v) { prefs.edit().putString(KEY_CLEAN_LAST, v).apply() }

    private val _shotFeel = MutableStateFlow(prefs.getBoolean(KEY_SHOT_FEEL, false))
    /** 單箭頁提供「差／普通／好」自評。預設：關 */
    val shotFeel: StateFlow<Boolean> = _shotFeel.asStateFlow()
    fun setShotFeel(v: Boolean) {
        _shotFeel.value = v
        prefs.edit().putBoolean(KEY_SHOT_FEEL, v).apply()
    }

    private val _advancedDetect = MutableStateFlow(prefs.getBoolean(KEY_ADVANCED_DETECT, true))
    /** 進階放箭判定（v1.16 試用）：穩定檢查緩衝加長、加上角速度穩定條件。預設：開 */
    val advancedDetect: StateFlow<Boolean> = _advancedDetect.asStateFlow()
    fun setAdvancedDetect(v: Boolean) {
        _advancedDetect.value = v
        prefs.edit().putBoolean(KEY_ADVANCED_DETECT, v).apply()
    }

    private val _recordDevice = MutableStateFlow(prefs.getBoolean(KEY_RECORD_DEVICE, true))
    /** 記錄檔寫入裝置型號（v1.18.1）。App 版本、唯一碼與偵測設定一律寫入。預設：開 */
    val recordDevice: StateFlow<Boolean> = _recordDevice.asStateFlow()
    fun setRecordDevice(v: Boolean) {
        _recordDevice.value = v
        prefs.edit().putBoolean(KEY_RECORD_DEVICE, v).apply()
    }

    private val _mainThreshold = MutableStateFlow(prefs.getBoolean(KEY_MAIN_THRESHOLD, false))
    /** 主畫面顯示閾值調整（v1.18.5）。閾值預設只在設定「偵測」裡調整。預設：關 */
    val mainThreshold: StateFlow<Boolean> = _mainThreshold.asStateFlow()
    fun setMainThreshold(v: Boolean) {
        _mainThreshold.value = v
        prefs.edit().putBoolean(KEY_MAIN_THRESHOLD, v).apply()
    }

    private val _recordNote = MutableStateFlow(prefs.getBoolean(KEY_RECORD_NOTE, true))
    /** 記錄備註（v1.19）：詳情頁可寫一句備註，列表與報告顯示。 預設：開 */
    val recordNote: StateFlow<Boolean> = _recordNote.asStateFlow()
    fun setRecordNote(v: Boolean) {
        _recordNote.value = v
        prefs.edit().putBoolean(KEY_RECORD_NOTE, v).apply()
    }

    private val _flagSuspicious = MutableStateFlow(prefs.getBoolean(KEY_FLAG_SUSPICIOUS, true))
    /** 標示可疑記錄（v1.19）。 預設：開 */
    val flagSuspicious: StateFlow<Boolean> = _flagSuspicious.asStateFlow()
    fun setFlagSuspicious(v: Boolean) {
        _flagSuspicious.value = v
        prefs.edit().putBoolean(KEY_FLAG_SUSPICIOUS, v).apply()
    }

    private val _recordMeasuredHz = MutableStateFlow(prefs.getBoolean(KEY_RECORD_MEASURED_HZ, true))
    /** 記錄實測取樣率（v1.19.4）：結束時把實測 Hz 寫進偵測摘要。 預設：開 */
    val recordMeasuredHz: StateFlow<Boolean> = _recordMeasuredHz.asStateFlow()
    fun setRecordMeasuredHz(v: Boolean) {
        _recordMeasuredHz.value = v
        prefs.edit().putBoolean(KEY_RECORD_MEASURED_HZ, v).apply()
    }

    private val _flagFastShots = MutableStateFlow(prefs.getBoolean(KEY_FLAG_FAST_SHOTS, true))
    /** 標示短間隔可疑箭（v1.19.4）。 預設：開 */
    val flagFastShots: StateFlow<Boolean> = _flagFastShots.asStateFlow()
    fun setFlagFastShots(v: Boolean) {
        _flagFastShots.value = v
        prefs.edit().putBoolean(KEY_FLAG_FAST_SHOTS, v).apply()
    }

    private val _positionTrend = MutableStateFlow(prefs.getBoolean(KEY_POSITION_TREND, true))
    /** 箭序趨勢（v1.19.4）：一趟裡第幾箭的平均晃動。 預設：開 */
    val positionTrend: StateFlow<Boolean> = _positionTrend.asStateFlow()
    fun setPositionTrend(v: Boolean) {
        _positionTrend.value = v
        prefs.edit().putBoolean(KEY_POSITION_TREND, v).apply()
    }

    private val _trendExtra = MutableStateFlow(prefs.getBoolean(KEY_TREND_EXTRA, true))
    /** 跨次趨勢加入節奏與放箭後晃動（v1.19.4）。 預設：開 */
    val trendExtra: StateFlow<Boolean> = _trendExtra.asStateFlow()
    fun setTrendExtra(v: Boolean) {
        _trendExtra.value = v
        prefs.edit().putBoolean(KEY_TREND_EXTRA, v).apply()
    }

    private val _autoEnds = MutableStateFlow(prefs.getBoolean(KEY_AUTO_ENDS, true))
    /** 自動分趟（v1.19.4）：詳情的逐趟只看兩箭間隔，不再用固定箭數切。 預設：開 */
    val autoEnds: StateFlow<Boolean> = _autoEnds.asStateFlow()
    fun setAutoEnds(v: Boolean) {
        _autoEnds.value = v
        prefs.edit().putBoolean(KEY_AUTO_ENDS, v).apply()
    }

    private val _phoneMode = MutableStateFlow(PhoneMode.valid(prefs.getInt(KEY_PHONE_MODE, PhoneMode.AUTO)))
    /** 手機模式（v1.20）：0 自動（不是手表就進手機模式）、1 手機模式、2 手錶模式。 預設：自動 */
    val phoneMode: StateFlow<Int> = _phoneMode.asStateFlow()
    fun setPhoneMode(v: Int) {
        val value = PhoneMode.valid(v)
        _phoneMode.value = value
        prefs.edit().putInt(KEY_PHONE_MODE, value).apply()
    }

    private val _shotTapNav = MutableStateFlow(prefs.getBoolean(KEY_SHOT_TAP_NAV, true))
    /** 單箭頁點擊換箭（v1.20.2）：點非按鈕的地方，左半邊上一箭、右半邊下一箭。 預設：開 */
    val shotTapNav: StateFlow<Boolean> = _shotTapNav.asStateFlow()
    fun setShotTapNav(v: Boolean) {
        _shotTapNav.value = v
        prefs.edit().putBoolean(KEY_SHOT_TAP_NAV, v).apply()
    }

    private val _historyFilter = MutableStateFlow(prefs.getBoolean(KEY_HISTORY_FILTER, true))
    /** 記錄頁篩選（v1.19）。 預設：開 */
    val historyFilter: StateFlow<Boolean> = _historyFilter.asStateFlow()
    fun setHistoryFilter(v: Boolean) {
        _historyFilter.value = v
        prefs.edit().putBoolean(KEY_HISTORY_FILTER, v).apply()
    }

    private val _shareZip = MutableStateFlow(prefs.getBoolean(KEY_SHARE_ZIP, true))
    /** 區網分享提供「下載全部（zip）」（v1.19）。 預設：開 */
    val shareZip: StateFlow<Boolean> = _shareZip.asStateFlow()
    fun setShareZip(v: Boolean) {
        _shareZip.value = v
        prefs.edit().putBoolean(KEY_SHARE_ZIP, v).apply()
    }

    private val _importUndo = MutableStateFlow(prefs.getBoolean(KEY_IMPORT_UNDO, true))
    /** 匯入完成後可復原這次匯入（v1.19）。 預設：開 */
    val importUndo: StateFlow<Boolean> = _importUndo.asStateFlow()
    fun setImportUndo(v: Boolean) {
        _importUndo.value = v
        prefs.edit().putBoolean(KEY_IMPORT_UNDO, v).apply()
    }

    private val _shareAutoStopMin = MutableStateFlow(prefs.getInt(KEY_SHARE_AUTO_STOP, ShareAutoStop.DEFAULT_MIN).let { if (it in ShareAutoStop.OPTIONS) it else ShareAutoStop.DEFAULT_MIN })
    /** 分享自動結束的分鐘數，0 = 不自動結束（v1.19）。預設：10（和以前相同） */
    val shareAutoStopMin: StateFlow<Int> = _shareAutoStopMin.asStateFlow()
    fun setShareAutoStopMin(v: Int) {
        val x = if (v in ShareAutoStop.OPTIONS) v else ShareAutoStop.DEFAULT_MIN
        _shareAutoStopMin.value = x
        prefs.edit().putInt(KEY_SHARE_AUTO_STOP, x).apply()
    }

    private val _lanShare = MutableStateFlow(prefs.getBoolean(KEY_LAN_SHARE, false))
    /** 資料頁提供「區網分享」（手機瀏覽器下載報告與資料）。預設：關 */
    val lanShare: StateFlow<Boolean> = _lanShare.asStateFlow()
    fun setLanShare(v: Boolean) {
        _lanShare.value = v
        prefs.edit().putBoolean(KEY_LAN_SHARE, v).apply()
    }

    private val _shareRemember = MutableStateFlow(prefs.getBoolean(KEY_SHARE_REMEMBER, true))
    /** 區網分享的網頁提供「記住這個裝置」（之後免輸入 PIN）；別台的 App 通過 PIN 後也會被記住。預設：開 */
    val shareRemember: StateFlow<Boolean> = _shareRemember.asStateFlow()
    fun setShareRemember(v: Boolean) {
        _shareRemember.value = v
        prefs.edit().putBoolean(KEY_SHARE_REMEMBER, v).apply()
    }

    private val _shareLog = MutableStateFlow(prefs.getBoolean(KEY_SHARE_LOG, true))
    /** 記錄區網分享的連線（只留今天）。預設：開 */
    val shareLog: StateFlow<Boolean> = _shareLog.asStateFlow()
    fun setShareLog(v: Boolean) {
        _shareLog.value = v
        prefs.edit().putBoolean(KEY_SHARE_LOG, v).apply()
    }

    private val _shareAlert = MutableStateFlow(prefs.getBoolean(KEY_SHARE_ALERT, false))
    /** 有人輸入錯誤 PIN 時震動提醒。預設：關 */
    val shareAlert: StateFlow<Boolean> = _shareAlert.asStateFlow()
    fun setShareAlert(v: Boolean) {
        _shareAlert.value = v
        prefs.edit().putBoolean(KEY_SHARE_ALERT, v).apply()
    }

    private val _importOverwrite = MutableStateFlow(prefs.getBoolean(KEY_IMPORT_OVERWRITE, false))
    /** 匯入時覆蓋本機已有的同一筆記錄。 預設：關 */
    val importOverwrite: StateFlow<Boolean> = _importOverwrite.asStateFlow()
    fun setImportOverwrite(v: Boolean) {
        _importOverwrite.value = v
        prefs.edit().putBoolean(KEY_IMPORT_OVERWRITE, v).apply()
    }

    private val _importRaw = MutableStateFlow(prefs.getBoolean(KEY_IMPORT_RAW, false))
    /** 匯入時一併抓原始資料 CSV（檔案較大）。 預設：關 */
    val importRaw: StateFlow<Boolean> = _importRaw.asStateFlow()
    fun setImportRaw(v: Boolean) {
        _importRaw.value = v
        prefs.edit().putBoolean(KEY_IMPORT_RAW, v).apply()
    }

    private val _reportView = MutableStateFlow(prefs.getBoolean(KEY_REPORT_VIEW, true))
    /** 「連線」頁提供「開啟網頁報告」（在這台裝置上直接看）。 預設：開 */
    val reportView: StateFlow<Boolean> = _reportView.asStateFlow()
    fun setReportView(v: Boolean) {
        _reportView.value = v
        prefs.edit().putBoolean(KEY_REPORT_VIEW, v).apply()
    }

    private val _shareQr = MutableStateFlow(prefs.getBoolean(KEY_SHARE_QR, true))
    /** 分享中顯示 QR Code，手機掃描就能開。 預設：開 */
    val shareQr: StateFlow<Boolean> = _shareQr.asStateFlow()
    fun setShareQr(v: Boolean) {
        _shareQr.value = v
        prefs.edit().putBoolean(KEY_SHARE_QR, v).apply()
    }

    private val _importDiscover = MutableStateFlow(prefs.getBoolean(KEY_IMPORT_DISCOVER, true))
    /** 區網自動尋找：分享時讓別台找得到，連線頁列出找到的裝置。 預設：開 */
    val importDiscover: StateFlow<Boolean> = _importDiscover.asStateFlow()
    fun setImportDiscover(v: Boolean) {
        _importDiscover.value = v
        prefs.edit().putBoolean(KEY_IMPORT_DISCOVER, v).apply()
    }

    private val _importMark = MutableStateFlow(prefs.getBoolean(KEY_IMPORT_MARK, true))
    /** 標示從別台匯入的記錄來源（記錄列表與報告）。 預設：開 */
    val importMark: StateFlow<Boolean> = _importMark.asStateFlow()
    fun setImportMark(v: Boolean) {
        _importMark.value = v
        prefs.edit().putBoolean(KEY_IMPORT_MARK, v).apply()
    }

    private val _importPreview = MutableStateFlow(prefs.getBoolean(KEY_IMPORT_PREVIEW, true))
    /** 匯入前先預覽，確認後才寫入。 預設：開 */
    val importPreview: StateFlow<Boolean> = _importPreview.asStateFlow()
    fun setImportPreview(v: Boolean) {
        _importPreview.value = v
        prefs.edit().putBoolean(KEY_IMPORT_PREVIEW, v).apply()
    }

    private val _importSettings = MutableStateFlow(prefs.getBoolean(KEY_IMPORT_SETTINGS, false))
    /** 匯入時一併取得對方的設定備份檔。 預設：關 */
    val importSettings: StateFlow<Boolean> = _importSettings.asStateFlow()
    fun setImportSettings(v: Boolean) {
        _importSettings.value = v
        prefs.edit().putBoolean(KEY_IMPORT_SETTINGS, v).apply()
    }

    private val _settingsBackup = MutableStateFlow(prefs.getBoolean(KEY_SETTINGS_BACKUP, true))
    /** 「連線」頁提供設定備份與還原。 預設：開 */
    val settingsBackup: StateFlow<Boolean> = _settingsBackup.asStateFlow()
    fun setSettingsBackup(v: Boolean) {
        _settingsBackup.value = v
        prefs.edit().putBoolean(KEY_SETTINGS_BACKUP, v).apply()
    }

    private val _reportTag = MutableStateFlow(prefs.getString(KEY_REPORT_TAG, "") ?: "")
    /** 網頁報告只包含帶這個標籤的記錄；空字串 = 全部。 預設：全部 */
    val reportTag: StateFlow<String> = _reportTag.asStateFlow()
    fun setReportTag(v: String) {
        _reportTag.value = v
        prefs.edit().putString(KEY_REPORT_TAG, v).apply()
    }

    private val _calAdvice = MutableStateFlow(prefs.getBoolean(KEY_CAL_ADVICE, true))
    /** 校準錄製完成後與校準記錄詳情提供「分析校準資料」。預設：開 */
    val calAdvice: StateFlow<Boolean> = _calAdvice.asStateFlow()
    fun setCalAdvice(v: Boolean) {
        _calAdvice.value = v
        prefs.edit().putBoolean(KEY_CAL_ADVICE, v).apply()
    }

    private val _updateMode = MutableStateFlow(readUpdateMode())
    /** 自動檢查更新的方式：0 每次啟動、1 每天第一次啟動、2 無自動檢查。預設：0。舊版的兩個開關會轉換過來 */
    val updateMode: StateFlow<Int> = _updateMode.asStateFlow()

    fun setUpdateMode(v: Int) {
        val m = UpdateMode.valid(v)
        _updateMode.value = m
        prefs.edit().putInt(KEY_UPDATE_MODE, m).apply()
    }

    private fun readUpdateMode(): Int = when {
        prefs.contains(KEY_UPDATE_MODE) -> UpdateMode.valid(prefs.getInt(KEY_UPDATE_MODE, UpdateMode.EVERY_START))
        prefs.contains(KEY_UPDATE_AUTO) ->
            UpdateMode.fromLegacy(prefs.getBoolean(KEY_UPDATE_AUTO, false), prefs.getBoolean(KEY_UPDATE_EVERY_START, true))
        else -> UpdateMode.EVERY_START
    }

    /** 上次檢查更新的時間（毫秒）；不屬於「恢復預設設定」 */
    var updateLastCheck: Long
        get() = prefs.getLong(KEY_UPDATE_LAST, 0L)
        set(v) { prefs.edit().putLong(KEY_UPDATE_LAST, v).apply() }

    /** 使用者按「略過這個版本」的版本標籤；不屬於「恢復預設設定」 */
    var updateSkipped: String
        get() = prefs.getString(KEY_UPDATE_SKIPPED, "") ?: ""
        set(v) { prefs.edit().putString(KEY_UPDATE_SKIPPED, v).apply() }

    private val _discSize = MutableStateFlow(DiscSize.valid(prefs.getInt(KEY_DISC_SIZE, DiscSize.DEFAULT)))
    /** 圓盤大小（0 小、1 中、2 大）。預設：大 */
    val discSize: StateFlow<Int> = _discSize.asStateFlow()

    fun setDiscSize(v: Int) {
        val value = DiscSize.valid(v)
        _discSize.value = value
        prefs.edit().putInt(KEY_DISC_SIZE, value).apply()
    }

    private val _discFullscreen = MutableStateFlow(prefs.getBoolean(KEY_DISC_FULLSCREEN, true))
    /** 點一下主畫面的即時圓盤，放大成全屏。預設：開 */
    val discFullscreen: StateFlow<Boolean> = _discFullscreen.asStateFlow()

    fun setDiscFullscreen(v: Boolean) {
        _discFullscreen.value = v
        prefs.edit().putBoolean(KEY_DISC_FULLSCREEN, v).apply()
    }

    private val _live2d = MutableStateFlow(prefs.getBoolean(KEY_LIVE_2D, false))
    /** 偵測中主畫面顯示即時晃動圓盤（取代即時波形）。預設：關 */
    val live2d: StateFlow<Boolean> = _live2d.asStateFlow()

    fun setLive2d(v: Boolean) {
        _live2d.value = v
        prefs.edit().putBoolean(KEY_LIVE_2D, v).apply()
    }

    private val _recordTrace = MutableStateFlow(prefs.getBoolean(KEY_RECORD_TRACE, false))
    /** 每一箭另存放箭前的 2D 軌跡。預設：關 */
    val recordTrace: StateFlow<Boolean> = _recordTrace.asStateFlow()

    fun setRecordTrace(v: Boolean) {
        _recordTrace.value = v
        prefs.edit().putBoolean(KEY_RECORD_TRACE, v).apply()
    }

    private val _traceGyro = MutableStateFlow(prefs.getBoolean(KEY_TRACE_GYRO, false))
    /** 2D 圖用角速度（true）還是加速度（false）。預設：加速度 */
    val traceGyro: StateFlow<Boolean> = _traceGyro.asStateFlow()

    fun setTraceGyro(v: Boolean) {
        _traceGyro.value = v
        prefs.edit().putBoolean(KEY_TRACE_GYRO, v).apply()
    }

    private val _chipKinds = MutableStateFlow(prefs.getBoolean(KEY_CHIP_KINDS, true))
    /** 按鈕依用途分色（綠＝開始／確認、藍＝進入另一頁、紅＝刪除）。預設：開 */
    val chipKinds: StateFlow<Boolean> = _chipKinds.asStateFlow()

    fun setChipKinds(v: Boolean) {
        _chipKinds.value = v
        prefs.edit().putBoolean(KEY_CHIP_KINDS, v).apply()
    }

    private val _matchCompare = MutableStateFlow(prefs.getBoolean(KEY_MATCH_COMPARE, true))
    /** 記錄頁顯示「比賽對比」入口。預設：開 */
    val matchCompare: StateFlow<Boolean> = _matchCompare.asStateFlow()

    fun setMatchCompare(v: Boolean) {
        _matchCompare.value = v
        prefs.edit().putBoolean(KEY_MATCH_COMPARE, v).apply()
    }

    private val _sensitivityAdvice = MutableStateFlow(prefs.getBoolean(KEY_SENSITIVITY_ADVICE, true))
    /** 閾值建議裡同時顯示「靜止條件」與「冷卻」的建議。預設：開 */
    val sensitivityAdvice: StateFlow<Boolean> = _sensitivityAdvice.asStateFlow()

    fun setSensitivityAdvice(v: Boolean) {
        _sensitivityAdvice.value = v
        prefs.edit().putBoolean(KEY_SENSITIVITY_ADVICE, v).apply()
    }

    private val _quickCheck = MutableStateFlow(prefs.getBoolean(KEY_QUICK_CHECK, true))
    /** 記錄詳情在有可疑箭時顯示「檢查可疑箭」入口。預設：開 */
    val quickCheck: StateFlow<Boolean> = _quickCheck.asStateFlow()

    fun setQuickCheck(v: Boolean) {
        _quickCheck.value = v
        prefs.edit().putBoolean(KEY_QUICK_CHECK, v).apply()
    }

    private val _tileLaunch = MutableStateFlow(prefs.getBoolean(KEY_TILE_LAUNCH, true))
    /** 從磁貼的按鈕進入時直接開始偵測／比賽；關閉時磁貼只會打開應用。預設：開 */
    val tileLaunch: StateFlow<Boolean> = _tileLaunch.asStateFlow()

    fun setTileLaunch(v: Boolean) {
        _tileLaunch.value = v
        prefs.edit().putBoolean(KEY_TILE_LAUNCH, v).apply()
    }

    private val _compScoreEntry = MutableStateFlow(prefs.getBoolean(KEY_COMP_SCORE_ENTRY, true))
    /** 比賽休息時可輸入該趟各箭環數。預設：開 */
    val compScoreEntry: StateFlow<Boolean> = _compScoreEntry.asStateFlow()

    fun setCompScoreEntry(v: Boolean) {
        val value = v
        _compScoreEntry.value = value
        prefs.edit().putBoolean(KEY_COMP_SCORE_ENTRY, value).apply()
    }

    private val _compArrowTimer = MutableStateFlow(prefs.getBoolean(KEY_COMP_ARROW_TIMER, false))
    /** 單箭限時提醒。預設：關 */
    val compArrowTimer: StateFlow<Boolean> = _compArrowTimer.asStateFlow()

    fun setCompArrowTimer(v: Boolean) {
        val value = v
        _compArrowTimer.value = value
        prefs.edit().putBoolean(KEY_COMP_ARROW_TIMER, value).apply()
    }

    private val _compArrowSec = MutableStateFlow(prefs.getInt(KEY_COMP_ARROW_SEC, CompLimits.DEFAULT_ARROW_SEC).coerceIn(CompLimits.MIN_ARROW_SEC, CompLimits.MAX_ARROW_SEC))
    /** 單箭限時秒數。預設：40 */
    val compArrowSec: StateFlow<Int> = _compArrowSec.asStateFlow()

    fun setCompArrowSec(v: Int) {
        val value = v.coerceIn(CompLimits.MIN_ARROW_SEC, CompLimits.MAX_ARROW_SEC)
        _compArrowSec.value = value
        prefs.edit().putInt(KEY_COMP_ARROW_SEC, value).apply()
    }

    private val _compSaved = MutableStateFlow(prefs.getString(KEY_COMP_SAVED, "").orEmpty())
    /** 我的賽制（見 [SavedFormats]）存成的文字。恢復預設設定不會清掉它。 */
    val compSaved: StateFlow<String> = _compSaved.asStateFlow()

    fun setCompSaved(v: String) {
        _compSaved.value = v
        prefs.edit().putString(KEY_COMP_SAVED, v).apply()
    }

    fun setSplashTap(v: Boolean) {
        _splashTap.value = v
        prefs.edit().putBoolean(KEY_SPLASH_TAP, v).apply()
    }

    fun setSplash(v: Boolean) {
        _splash.value = v
        prefs.edit().putBoolean(KEY_SPLASH, v).apply()
    }

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
        setSplash(true)
        setSplashTap(true)
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
        setLanguage(I18n.AUTO)
        setSamplingHz(SamplingRate.DEFAULT_HZ)
        setFatigueAlert(true)
        setFatiguePct(FatigueRule.DEFAULT_PCT)
        setCompEnabled(true)
        setCompFormat(CompFormat.INDOOR_18)
        setCompArrows(CompLimits.DEFAULT_ARROWS)
        setCompEnds(CompLimits.DEFAULT_ENDS)
        setCompSeconds(CompLimits.DEFAULT_SECONDS)
        setCompPractice(CompLimits.DEFAULT_PRACTICE)
        setCompPrepare(CompLimits.DEFAULT_PREPARE)
        setCompRemind30(true)
        setCompRemind10(true)
        setCompAutoNext(CompLimits.DEFAULT_AUTO_NEXT)
        setCompColors(true)
        setSoundEnabled(true)
        setSoundVolume(MatchSound.DEFAULT_VOLUME_INDEX)
        setSoundStyle(MatchSound.DEFAULT_STYLE)
        setSoundCount(true)
        setSoundGo(true)
        setSoundWarn(true)
        setSoundEnd(true)
        setCompScoreEntry(true)
        setMatchCompare(true)
        setChipKinds(true)
        setLive2d(false)
        setDiscSize(DiscSize.DEFAULT)
        setUpdateMode(UpdateMode.EVERY_START)
        setPaceAnalysis(true)
        setGroupCompare(true)
        setPersonalBest(true)
        setOverview(true)
        setAutoClean(false)
        setAutoCleanDays(AutoClean.DEFAULT_DAYS)
        setShotFeel(false)
        setCalAdvice(true)
        setAdvancedDetect(true)
        setRecordDevice(true)
        setMainThreshold(false)
        setShareAutoStopMin(ShareAutoStop.DEFAULT_MIN)
        setImportUndo(true)
        setShareZip(true)
        setHistoryFilter(true)
        setFlagSuspicious(true)
        setShotTapNav(true)
        setPhoneMode(PhoneMode.AUTO)
        setAutoEnds(true)
        setRecordMeasuredHz(true)
        setFlagFastShots(true)
        setPositionTrend(true)
        setTrendExtra(true)
        setRecordNote(true)
        setLanShare(false)
        setShareRemember(true)
        setShareLog(true)
        setShareAlert(false)
        setImportOverwrite(false)
        setImportRaw(false)
        setReportView(true)
        setShareQr(true)
        setImportDiscover(true)
        setImportMark(true)
        setImportPreview(true)
        setImportSettings(false)
        setSettingsBackup(true)
        setReportTag("")
        setDiscFullscreen(true)
        setRecordTrace(false)
        setTraceGyro(false)
        setSensitivityAdvice(true)
        setQuickCheck(true)
        setTileLaunch(true)
        setCompArrowTimer(false)
        setCompArrowSec(CompLimits.DEFAULT_ARROW_SEC)
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

        private const val KEY_LANGUAGE = "language"
        private const val KEY_SAMPLING_HZ = "sampling_hz"
        private const val KEY_FATIGUE_ALERT = "fatigue_alert"
        private const val KEY_FATIGUE_PCT = "fatigue_pct"
        private const val KEY_COMP_ENABLED = "comp_enabled"
        private const val KEY_COMP_FORMAT = "comp_format"
        private const val KEY_COMP_ARROWS = "comp_arrows"
        private const val KEY_COMP_ENDS = "comp_ends"
        private const val KEY_COMP_SECONDS = "comp_seconds"
        private const val KEY_COMP_PRACTICE = "comp_practice"
        private const val KEY_COMP_PREPARE = "comp_prepare"
        private const val KEY_COMP_R30 = "comp_r30"
        private const val KEY_COMP_R10 = "comp_r10"
        private const val KEY_COMP_AUTO_NEXT = "comp_auto_next"
        private const val KEY_COMP_COLORS = "comp_colors"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_SOUND_VOLUME = "sound_volume"
        private const val KEY_SOUND_STYLE = "sound_style"
        private const val KEY_SOUND_COUNT = "sound_count"
        private const val KEY_SOUND_GO = "sound_go"
        private const val KEY_SOUND_WARN = "sound_warn"
        private const val KEY_SOUND_END = "sound_end"
        private const val KEY_COMP_SCORE_ENTRY = "comp_score_entry"
        private const val KEY_MATCH_COMPARE = "match_compare"
        private const val KEY_CHIP_KINDS = "chip_kinds"
        private const val KEY_LIVE_2D = "live_2d"
        private const val KEY_DISC_SIZE = "disc_size"
        private const val KEY_UPDATE_MODE = "update_mode"
        private const val KEY_UPDATE_AUTO = "update_auto"      // 舊版開關，只用來轉換
        private const val KEY_UPDATE_EVERY_START = "update_every_start"
        private const val KEY_PACE = "pace_analysis"
        private const val KEY_GROUP_COMPARE = "group_compare"
        private const val KEY_PERSONAL_BEST = "personal_best"
        private const val KEY_OVERVIEW = "overview"
        private const val KEY_AUTO_CLEAN = "auto_clean"
        private const val KEY_AUTO_CLEAN_DAYS = "auto_clean_days"
        private const val KEY_CLEAN_LAST = "clean_last"
        private const val KEY_SHOT_FEEL = "shot_feel"
        private const val KEY_CAL_ADVICE = "cal_advice"
        private const val KEY_ADVANCED_DETECT = "advanced_detect"
        private const val KEY_RECORD_DEVICE = "record_device"
        private const val KEY_MAIN_THRESHOLD = "main_threshold"
        private const val KEY_SHARE_AUTO_STOP = "share_auto_stop_min"
        private const val KEY_IMPORT_UNDO = "import_undo"
        private const val KEY_SHARE_ZIP = "share_zip"
        private const val KEY_HISTORY_FILTER = "history_filter"
        private const val KEY_FLAG_SUSPICIOUS = "flag_suspicious"
        private const val KEY_SHOT_TAP_NAV = "shot_tap_nav"
        private const val KEY_PHONE_MODE = "phone_mode"
        private const val KEY_AUTO_ENDS = "auto_ends"
        private const val KEY_RECORD_MEASURED_HZ = "record_measured_hz"
        private const val KEY_FLAG_FAST_SHOTS = "flag_fast_shots"
        private const val KEY_POSITION_TREND = "position_trend"
        private const val KEY_TREND_EXTRA = "trend_extra"
        private const val KEY_RECORD_NOTE = "record_note"
        private const val KEY_LAN_SHARE = "lan_share"
        private const val KEY_SHARE_REMEMBER = "share_remember"
        private const val KEY_SHARE_LOG = "share_log"
        private const val KEY_SHARE_ALERT = "share_alert"
        private const val KEY_IMPORT_OVERWRITE = "import_overwrite"
        private const val KEY_IMPORT_RAW = "import_raw"
        private const val KEY_REPORT_VIEW = "report_view"
        private const val KEY_SHARE_QR = "share_qr"
        private const val KEY_IMPORT_DISCOVER = "import_discover"
        private const val KEY_IMPORT_MARK = "import_mark"
        private const val KEY_IMPORT_PREVIEW = "import_preview"
        private const val KEY_IMPORT_SETTINGS = "import_settings"
        private const val KEY_SETTINGS_BACKUP = "settings_backup"
        private const val KEY_REPORT_TAG = "report_tag"
        private const val KEY_UPDATE_LAST = "update_last_check"
        private const val KEY_UPDATE_SKIPPED = "update_skipped"
        private const val KEY_DISC_FULLSCREEN = "disc_fullscreen"
        private const val KEY_RECORD_TRACE = "record_trace"
        private const val KEY_TRACE_GYRO = "trace_gyro"
        private const val KEY_SENSITIVITY_ADVICE = "sensitivity_advice"
        private const val KEY_QUICK_CHECK = "quick_check"
        private const val KEY_TILE_LAUNCH = "tile_launch"
        private const val KEY_COMP_ARROW_TIMER = "comp_arrow_timer"
        private const val KEY_COMP_ARROW_SEC = "comp_arrow_sec"
        private const val KEY_COMP_SAVED = "comp_saved"
        private const val KEY_SPLASH = "splash"
        private const val KEY_SPLASH_TAP = "splash_tap"
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
