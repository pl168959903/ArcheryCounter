package com.zgrcc.archeryassistant

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
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

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
    // 緩衝區容量依取樣頻率而定，所以每次開始偵測時重建（v1.9）
    @Volatile private var tracker = StabilityTracker()
    private val rateMeter = RateMeter()
    private val _measuredHz = MutableStateFlow(0f)
    /** 實測取樣頻率（Hz），偵測中約每秒更新一次；還沒量到是 0 */
    val measuredHz: StateFlow<Float> = _measuredHz.asStateFlow()
    @Volatile private var requestedHz = SamplingRate.DEFAULT_HZ
    /** 本次偵測要求的取樣頻率（Hz） */
    val samplingHzInUse: Int get() = requestedHz
    private val live = LiveBuffer() // 即时波形用，传感器线程写、画面读
    private val liveTrace = LiveTrace() // 即時晃動圓盤用

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

    private val _selectedShot = MutableStateFlow<Int?>(null)
    /** 记录详情里被点开的那一箭（在 Session.shots 里的位置，包含被取消的）。 */
    val selectedShot: StateFlow<Int?> = _selectedShot.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeId.asStateFlow()

    // ---- v1.6：今日目標、趟回饋、電量、通知 ----

    private val _todayDone = MutableStateFlow(0)   // 今天已結束的記錄合計
    private val _activeCount = MutableStateFlow(0) // 進行中這一次目前的箭數
    /** 今日箭數：今天已結束的記錄加上進行中的這一次（不含校準） */
    val todayCount: StateFlow<Int> =
        combine(_todayDone, _activeCount) { done, active -> done + active }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _endFeedback = MutableStateFlow<EndFeedback?>(null)
    /** 剛射完一趟的回饋，顯示約 5 秒後消失 */
    val endFeedback: StateFlow<EndFeedback?> = _endFeedback.asStateFlow()

    // 震動與電量（邏輯在 Buzzer、BatteryMonitor）
    private val buzzer = Buzzer(vibrator)
    private val battery = BatteryMonitor(appContext, settings) { buzzEnd(EndVibration.LONG) }
    val batteryLow: StateFlow<Boolean> = battery.low
    val batteryPercent: StateFlow<Int> = battery.percent

    private val _notice = MutableStateFlow<String?>(null)
    /** 主畫面上的一次性通知（例如「已自動暫停」），下次開始偵測時清掉 */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    // 自動暫停：最後一次放箭（或開始偵測後第一筆資料）的傳感器時間；-1 = 還沒有
    @Volatile private var lastActivityMs = -1L
    @Volatile private var autoPausing = false

    private val _locked = MutableStateFlow(false)
    /** 觸控鎖：鎖定時主畫面只顯示箭數，長按約 2 秒才解鎖 */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    // 校準錄製：偵測照常運作、放箭也會記錄，但不計入主畫面箭數；另外在記錄裡標注每一段動作
    @Volatile private var calibrating = false
    private val _calibrationActive = MutableStateFlow(false)
    val calibrationActive: StateFlow<Boolean> = _calibrationActive.asStateFlow()
    private val _calStep = MutableStateFlow(0)
    /** 校準進行到第幾段（0 起算）；等於 [Calibration.STEPS] 的長度代表三段都做完了 */
    val calStep: StateFlow<Int> = _calStep.asStateFlow()
    private val _calMarking = MutableStateFlow(false)
    /** 目前這一段已經按了「開始」、還沒按「結束」 */
    val calMarking: StateFlow<Boolean> = _calMarking.asStateFlow()

    // ---- v1.9：疲勞提醒、比賽模式 ----
    private val _fatiguePct = MutableStateFlow<Float?>(null)
    /** 本次已觸發疲勞提醒時，最近幾趟比剛開始抖了幾 %；沒有觸發為 null */
    val fatiguePct: StateFlow<Float?> = _fatiguePct.asStateFlow()
    private var fatigueWarned = false

    private val compLock = Any()
    private var round: CompetitionRound? = null
    private var compMarksWritten = 0
    private var compJob: kotlinx.coroutines.Job? = null
    @Volatile private var competing = false
    private val _comp = MutableStateFlow<CompUi?>(null)
    /** 比賽進行中的狀態；不是比賽為 null */
    val comp: StateFlow<CompUi?> = _comp.asStateFlow()
    private val _compFinishedId = MutableStateFlow<String?>(null)
    /** 剛結束的比賽紀錄 id，畫面據此顯示「查看紀錄」；開始新的偵測時清掉 */
    val compFinishedId: StateFlow<String?> = _compFinishedId.asStateFlow()

    // 本次记录的时间基准与窗口长度（开始时设定，传感器线程读取）
    @Volatile private var baseElapsedMs = 0L
    @Volatile private var windowMs = AppSettings.DEFAULT_WINDOW_SEC * 1000L

    private var wakeLock: PowerManager.WakeLock? = null

    private val detector = ShotDetector(
        spikeThreshold = _threshold.value,
        stillThreshold = settings.stillThreshold.value.toFloat(),
        cooldownMs = settings.cooldownSec.value * 1000L,
    ) { peak, triggerMs ->
        onShot(peak, triggerMs)
    }

    // 分趟：即時數到第幾箭（滿一趟時震動提醒）
    private val endTracker = EndTracker()

    // 放箭後等滿 1 秒才能算跟隨動作；這裡記著還在等的那幾箭。只有傳感器線程會新增，但 start/stop 會清空，所以加鎖
    private class PendingFollow(val sessionId: String?, val index: Int, val triggerMs: Long)
    private val pendingFollow = ArrayList<PendingFollow>()

    // 没有线性加速度传感器时，用低通滤波估算重力
    private val gravity = FloatArray(3)
    private var gravityReady = false

    // 最近一笔角速度 (°/s)，写原始数据时和加速度配对
    private var lastGx = 0f
    private var lastGy = 0f
    private var lastGz = 0f

    init {
        viewModelScope.launch(Dispatchers.IO) {
            recorder.activeId // 暖机，建立存储目录
            publishSessions() // 讀入記錄，算出今日已完成的箭數
        }
        viewModelScope.launch { settings.wakeLock.collect { updateWakeLock() } }
        viewModelScope.launch { settings.stillThreshold.collect { detector.stillThreshold = it.toFloat() } }
        viewModelScope.launch { settings.cooldownSec.collect { detector.cooldownMs = it * 1000L } }
        viewModelScope.launch {
            settings.advancedDetect.collect { on ->
                detector.gapMs = if (on) ShotDetector.ADVANCED_GAP_MS else ShotDetector.GAP_MS
                detector.gyroStill = if (on) ShotDetector.ADVANCED_GYRO_STILL else 0f
            }
        }
    }

    // ---- 自動清理與校準分析（v1.14） ----

    private val _cleanLast = MutableStateFlow(AutoClean.decode(settings.cleanLast))
    /** 上次自動清理的結果（沒清過為 null） */
    val cleanLast: StateFlow<CleanResult?> = _cleanLast.asStateFlow()

    /** 開啟自動清理時，應用啟動後清一次。進行中的記錄與校準記錄不會動。 */
    private fun runAutoClean() {
        if (!settings.autoClean.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val cutoff = AutoClean.cutoff(System.currentTimeMillis(), settings.autoCleanDays.value)
            val r = store.cleanOld(cutoff, keepId = _activeId.value)
            if (r.count > 0) {
                settings.cleanLast = AutoClean.encode(r)
                _cleanLast.value = r
                publishSessions()
            }
        }
    }

    /** 分析一筆校準記錄的原始資料，回傳建議；沒有原始資料或記錄回傳 null。 */
    suspend fun analyzeCalibration(id: String): CalibAdvice? = withContext(Dispatchers.IO) {
        val s = store.load(id) ?: return@withContext null
        if (!s.calibration) return@withContext null
        val samples = store.readRawLines(id) { CalibrationAnalyzer.parseCsv(it) } ?: return@withContext null
        CalibrationAnalyzer.analyze(s.marks, s.shots, samples)
    }

    // ---- 檢查更新（v1.13；邏輯在 UpdateController） ----

    private val updates = UpdateController(viewModelScope, settings, UpdateManager(appContext)) { versionName }
    val update: StateFlow<UpdateUi> = updates.update
    fun checkUpdate(manual: Boolean) = updates.checkUpdate(manual)
    fun downloadAndInstall() = updates.downloadAndInstall()
    fun skipUpdate() = updates.skipUpdate()

    // ---- 侦测与记录 ----

    fun start() = begin(calibration = false)

    /** 開始比賽模式（需要設定裡開啟比賽模式）。 */
    fun startCompetition() {
        if (_running.value || !settings.compEnabled.value) return
        val custom = CompFormat(
            CompFormat.CUSTOM, settings.compArrows.value, settings.compEnds.value, settings.compSeconds.value,
        )
        val format = CompFormat.resolve(settings.compFormat.value, custom, SavedFormats.decode(settings.compSaved.value))
        val practice = settings.compPractice.value
        val meta = CompMeta(format.secondsPerEnd, format.arrowsPerEnd, practice)
        begin(calibration = false, comp = meta)
        if (!_running.value) return
        val now = SystemClock.elapsedRealtime()
        val r = CompetitionRound(
            format = format,
            practiceEnds = practice,
            prepareSec = settings.compPrepare.value,
            remind30 = settings.compRemind30.value,
            remind10 = settings.compRemind10.value,
            autoNextSec = settings.compAutoNext.value,
            arrowLimitSec = if (settings.compArrowTimer.value) settings.compArrowSec.value else 0,
        )
        synchronized(compLock) {
            round = r
            compMarksWritten = 0
            competing = true
            val events = r.start(now)
            _comp.value = r.ui(now)
            handleCompEvents(events)
        }
        compJob?.cancel()
        compJob = viewModelScope.launch {
            while (true) {
                delay(COMP_TICK_MS)
                tickComp()
            }
        }
    }

    private fun tickComp() {
        synchronized(compLock) {
            val r = round ?: return
            val now = SystemClock.elapsedRealtime()
            val events = r.tick(now)
            handleCompEvents(events)
            _comp.value = r.ui(now)
        }
    }

    /** 比賽休息時輸入環數用：最近結束那一趟的各箭（位置、第幾箭、目前環數）。 */
    class EndShotScore(val index: Int, val number: Int, val score: Int?)

    fun compEndScores(): List<EndShotScore> {
        val res = synchronized(compLock) { round?.results?.lastOrNull() } ?: return emptyList()
        val start = (res.startMs - baseElapsedMs).coerceAtLeast(0L)
        val end = (res.endMs - baseElapsedMs).coerceAtLeast(0L)
        return recorder.validShotIndices(start, end).mapIndexed { i, ix ->
            EndShotScore(ix, i + 1, recorder.scoreAt(ix))
        }
    }

    fun compSetScore(index: Int, score: Int?) = recorder.setScore(index, score)

    /** 休息中按「下一趟」。 */
    fun compNextEnd() {
        synchronized(compLock) {
            val r = round ?: return
            val now = SystemClock.elapsedRealtime()
            val events = r.next(now)
            if (r.phase == CompPhase.PREPARE) _locked.value = settings.touchLock.value
            handleCompEvents(events)
            _comp.value = r.ui(now)
        }
    }

    /** 處理比賽事件：震動、補寫各趟的起止、全部射完就結束偵測。呼叫時已持有 [compLock]。 */
    private fun handleCompEvents(events: List<CompEvent>) {
        val r = round ?: return
        for (e in events) {
            when (e) {
                CompEvent.COUNT_TICK -> playCue(SoundCue.COUNT)
                CompEvent.ARROW_TIME_UP -> {
                    playCue(SoundCue.ARROW_UP)
                    buzzPattern(EndVibration.COMP_ARROW)
                }
                CompEvent.GO -> {
                    playCue(SoundCue.GO)
                    buzzPattern(EndVibration.COMP_GO)
                    _locked.value = settings.touchLock.value // 開始射擊：鎖定觸控
                }
                CompEvent.REMIND_30 -> {
                    playCue(SoundCue.WARN30)
                    buzzPattern(EndVibration.COMP_30)
                }
                CompEvent.REMIND_10 -> {
                    playCue(SoundCue.WARN10)
                    buzzPattern(EndVibration.COMP_10)
                }
                CompEvent.TIME_UP -> {
                    playCue(SoundCue.TIME_UP)
                    buzzPattern(EndVibration.COMP_TIME_UP)
                    _locked.value = false // 這一趟結束：解鎖，方便按「下一趟」
                }
                CompEvent.END_FULL -> {
                    playCue(SoundCue.END_FULL)
                    _locked.value = false
                    if (settings.endVibration.value != EndVibration.OFF) buzzEnd(settings.endVibration.value) else buzzShot()
                }
                CompEvent.MATCH_DONE -> viewModelScope.launch {
                    _locked.value = false
                    delay(COMP_DONE_DELAY_MS)
                    stop()
                }
            }
        }
        flushCompMarks()
    }

    /** 把還沒寫進記錄的各趟起止補寫進去。呼叫時已持有 [compLock]。 */
    private fun flushCompMarks() {
        val r = round ?: return
        while (compMarksWritten < r.results.size) {
            val res = r.results[compMarksWritten]
            recorder.addMark(
                r.markLabel(res),
                (res.startMs - baseElapsedMs).coerceAtLeast(0L),
                (res.endMs - baseElapsedMs).coerceAtLeast(0L),
            )
            compMarksWritten++
        }
    }

    // ---- 比賽聲音（邏輯在 MatchSoundPlayer） ----
    private val sound = MatchSoundPlayer(viewModelScope, settings)
    private fun playCue(cue: SoundCue) = sound.playCue(cue)

    /** 設定頁選音量、音色時試播。 */
    fun previewSound() = sound.preview()

    private fun buzzPattern(pattern: LongArray) = buzzer.pattern(pattern)

    /** 開始校準錄製：強制錄原始資料，記錄標為「校準」。 */
    fun startCalibration() = begin(calibration = true)

    private fun begin(calibration: Boolean, comp: CompMeta? = null) {
        if (_running.value) return
        val sensor = linearSensor ?: accelSensor ?: return

        val hz = SamplingRate.valid(settings.samplingHz.value)
        requestedHz = hz
        tracker = StabilityTracker(SamplingRate.trackerCapacity(hz))
        rateMeter.reset()
        _measuredHz.value = 0f
        detector.reset()
        live.clear()
        liveTrace.clear()
        endTracker.reset()
        fatigueWarned = false
        _fatiguePct.value = null
        _compFinishedId.value = null
        _comp.value = null
        round = null
        competing = false
        synchronized(pendingFollow) { pendingFollow.clear() }
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
            rawCsv = settings.recordRaw.value || calibration,
            tags = (if (!calibration && settings.tagsEnabled.value) {
                Tags.build(
                    settings.distanceTag.value.ifEmpty { null },
                    settings.bowTag.value.ifEmpty { null },
                )
            } else {
                emptyList()
            }) + (if (comp != null) listOf(Tags.COMPETITION) else emptyList()),
            calibration = calibration,
            comp = comp,
        )
        calibrating = calibration
        _calibrationActive.value = calibration
        _calStep.value = 0
        _calMarking.value = false
        _locked.value = comp != null && settings.touchLock.value // 比賽中自動鎖定觸控，避免誤碰
        _activeCount.value = 0
        _endFeedback.value = null
        _notice.value = null
        lastActivityMs = -1L
        autoPausing = false
        battery.start()

        _running.value = true
        val periodUs = SamplingRate.periodUs(hz)
        sensorManager.registerListener(this, sensor, periodUs, sensorHandler)
        gyroSensor?.let { sensorManager.registerListener(this, it, periodUs, sensorHandler) }
        updateWakeLock()
    }

    fun stop() {
        if (!_running.value) return
        _running.value = false // 之后到达的传感器事件会被丢弃
        sensorManager.unregisterListener(this)
        compJob?.cancel()
        compJob = null
        synchronized(compLock) {
            val r = round
            if (r != null) {
                r.finishNow(SystemClock.elapsedRealtime())
                flushCompMarks()
                _compFinishedId.value = recorder.activeId
                _comp.value = r.ui(SystemClock.elapsedRealtime())
            }
            competing = false
        }
        _locked.value = false
        calibrating = false
        _calibrationActive.value = false
        _calMarking.value = false
        _endFeedback.value = null
        battery.stop()
        synchronized(pendingFollow) { pendingFollow.clear() } // 還沒滿 1 秒的就不補了
        recorder.stop(System.currentTimeMillis())
        _activeId.value = null
        updateWakeLock()
        // 這一筆記錄存好後再併入「今日已完成」，並把進行中的計數歸零，避免今日箭數閃一下
        viewModelScope.launch(Dispatchers.IO) {
            publishSessions()
            if (_activeId.value == null) _activeCount.value = 0 // 這段期間已經又開始新的記錄就不要歸零
        }
    }

    /** 放箭侦测到时（传感器线程）。 */
    private fun onShot(peak: Float, triggerMs: Long) {
        // 取放箭前的窗口，避开最近 GAP_MS（冲击的上升沿）
        val st = tracker.compute(endMs = triggerMs - ShotDetector.GAP_MS, windowMs = windowMs)
        val gyr = if (gyroSensor != null) st.gyroRms else null

        // 單箭波形：放箭前（含避開的 GAP_MS）到放箭後 CAPTURE_MS。關掉設定就不存，記錄檔案最小。
        val wave = if (settings.recordWaveform.value) {
            tracker.waveform(
                triggerMs = triggerMs,
                preMs = windowMs + ShotDetector.GAP_MS,
                postMs = ShotDetector.CAPTURE_MS,
            )
        } else {
            null
        }

        // 單箭 2D 軌跡：放箭前分析視窗內的 x、y 軌跡。用角速度但沒有陀螺儀時不存
        val traceGyro = settings.traceGyro.value
        val trace = if (settings.recordTrace.value && (!traceGyro || gyroSensor != null)) {
            tracker.trace(endMs = triggerMs - ShotDetector.GAP_MS, windowMs = windowMs, gyro = traceGyro)
        } else {
            null
        }

        val index = recorder.addShot(
            relMs = (triggerMs - baseElapsedMs).coerceAtLeast(0L),
            peak = peak,
            threshold = detector.spikeThreshold,
            accelRms = st.accelRms,
            gyroRms = gyr,
            waveform = wave,
            trace = trace,
        )
        if (index >= 0 && settings.followThrough.value) {
            synchronized(pendingFollow) { pendingFollow.add(PendingFollow(_activeId.value, index, triggerMs)) }
        }
        lastActivityMs = triggerMs
        if (calibrating) {
            buzzShot() // 校準中：只記錄，不計數、不分趟
            return
        }
        _lastShot.value = LastShot(peak, st.accelRms, gyr)
        changeCount(+1)

        if (competing) {
            _activeCount.value = recorder.currentFinalCount()
            // 比賽模式：趟數、倒數由 CompetitionRound 管，不用一般的分趟與疲勞提醒
            synchronized(compLock) {
                val r = round
                if (r != null) {
                    val events = r.onShot(triggerMs)
                    if (events.isEmpty()) buzzShot()
                    handleCompEvents(events)
                    _comp.value = r.ui(triggerMs)
                } else {
                    buzzShot()
                }
            }
            return
        }

        // 今日目標：這一箭讓今日合計跨過目標才算達標
        val before = _todayDone.value + _activeCount.value
        _activeCount.value = recorder.currentFinalCount()
        val goalReached = Goal.reached(before, _todayDone.value + _activeCount.value, settings.dailyGoal.value)

        val endFull = settings.showEnds.value && endTracker.onShot(triggerMs, settings.arrowsPerEnd.value)
        if (endFull && settings.endFeedback.value) showEndFeedback()
        val fatigueNow = endFull && checkFatigue()

        // 震動優先順序：達標 > 疲勞提醒 > 滿一趟 > 一般放箭（只震最重要的那一種，不疊加）
        when {
            goalReached -> buzzEnd(EndVibration.LONG)
            fatigueNow -> buzzPattern(FatigueRule.PATTERN)
            endFull && settings.endVibration.value != EndVibration.OFF -> buzzEnd(settings.endVibration.value)
            else -> buzzShot()
        }
    }

    /** 每滿一趟檢查一次疲勞：最近幾趟比剛開始抖很多就提醒一次。回傳 true 表示這次要提醒。 */
    private fun checkFatigue(): Boolean {
        if (!settings.fatigueAlert.value) return false
        val snap = recorder.snapshot() ?: return false
        val d = FatigueRule.decide(
            computeEnds(snap, settings.arrowsPerEnd.value), settings.fatiguePct.value, fatigueWarned,
        )
        fatigueWarned = d.warned
        if (d.warnNow) _fatiguePct.value = d.pct
        return d.warnNow
    }

    /** 滿一趟：算出這一趟和上一趟、和本次平均的差，顯示約 5 秒。 */
    private fun showEndFeedback() {
        val snap = recorder.snapshot() ?: return
        val fb = buildEndFeedback(snap, computeEnds(snap, settings.arrowsPerEnd.value)) ?: return
        _endFeedback.value = fb
        viewModelScope.launch {
            delay(END_FEEDBACK_MS)
            if (_endFeedback.value === fb) _endFeedback.value = null
        }
    }

    /**
     * 放箭後滿 1 秒（傳感器時間）才補上跟隨動作：較長的波形和放箭後晃動量。
     * 每一筆傳感器資料都會呼叫，沒有在等的箭時只是檢查一下空清單。
     */
    private fun finishFollowThrough(nowMs: Long) {
        val ready: List<PendingFollow> = synchronized(pendingFollow) {
            if (pendingFollow.isEmpty()) return
            val done = pendingFollow.filter { nowMs >= it.triggerMs + StabilityTracker.FOLLOW_END_MS }
            pendingFollow.removeAll(done.toSet())
            done
        }
        for (p in ready) {
            val f = tracker.followThrough(p.triggerMs)
            val wave = if (settings.recordWaveform.value) {
                tracker.waveform(
                    triggerMs = p.triggerMs,
                    preMs = windowMs + ShotDetector.GAP_MS,
                    postMs = StabilityTracker.FOLLOW_END_MS,
                )
            } else {
                null
            }
            recorder.attachFollow(
                expectedId = p.sessionId,
                index = p.index,
                waveform = wave,
                followAccelRms = f.accelRms,
                followGyroRms = if (gyroSensor != null) f.gyroRms else null,
            )
        }
    }

    // ---- 计数与阈值 ----

    fun adjustCount(delta: Int) {
        if (calibrating) return
        // 按「−」時，如果這次記錄裡還有沒取消的箭，就把最近一筆標記為誤判（數據保留），
        // 不另外記一筆手動 −1，避免重複扣掉。沒有可取消的箭才當作一般的手動修正。
        if (delta < 0 && recorder.cancelLastShot()) {
            endTracker.cancelLast()
            synchronized(compLock) { round?.cancelLast() }
            _activeCount.value = recorder.currentFinalCount()
            changeCount(delta)
            _lastShot.value = null
            return
        }
        val applied = changeCount(delta)
        if (applied != 0 && _running.value) {
            recorder.addManual(
                relMs = (SystemClock.elapsedRealtime() - baseElapsedMs).coerceAtLeast(0L),
                delta = applied,
            )
            _activeCount.value = recorder.currentFinalCount()
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

    /** 直接設定閾值（阈值建议套用）。範圍同 [adjustThreshold]。 */
    fun setThreshold(value: Float) {
        val v = value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        _threshold.value = v
        detector.spikeThreshold = v
        prefs.edit().putFloat(KEY_THRESHOLD, v).apply()
    }

    fun lockTouch() {
        if (_running.value) _locked.value = true
    }

    fun unlockTouch() {
        _locked.value = false
    }

    // ---- 校準錄製 ----

    private fun relNow() = (SystemClock.elapsedRealtime() - baseElapsedMs).coerceAtLeast(0L)

    /** 開始目前這一段動作。 */
    fun calStartSegment() {
        val step = _calStep.value
        if (!calibrating || _calMarking.value || step >= Calibration.STEPS.size) return
        recorder.startMark(relNow(), Calibration.STEPS[step].label) // 存中文名稱，顯示時才換語言
        _calMarking.value = true
    }

    /** 結束目前這一段動作，進入下一段。 */
    fun calEndSegment() {
        if (!calibrating || !_calMarking.value) return
        recorder.endMark(relNow())
        _calMarking.value = false
        _calStep.value = _calStep.value + 1
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
            publishSessions()
        }
    }

    /** 讀入全部記錄，更新列表和「今日已完成」的箭數。請在背景線程呼叫。 */
    private fun publishSessions() {
        val list = store.listSessions().sortedByDescending { it.startMs }
        _sessions.value = list
        _todayDone.value = todayDone(list, System.currentTimeMillis(), excludeId = _activeId.value)
    }

    /** 恢復預設設定：不動記錄、主畫面箭數和閾值。 */
    fun resetSettings() {
        settings.resetDefaults()
    }

    /** 本應用的套件資訊（含簽章），給版本與簽章顯示用。讀不到時是 null。 */
    private val packageInfo: android.content.pm.PackageInfo? = try {
        appContext.packageManager.getPackageInfo(
            appContext.packageName,
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES,
        )
    } catch (_: Exception) {
        null
    }

    /** 版本名稱，給「關於」顯示。 */
    val versionName: String = packageInfo?.versionName ?: "?"

    /** 版本號碼（versionCode），讀不到時是 -1。 */
    val versionCode: Long = try {
        packageInfo?.longVersionCode ?: -1L
    } catch (_: Exception) {
        -1L
    }

    /** 版本顯示文字，例如 v1.7（8） */
    val versionLabel: String = AppInfo.versionLabel(versionName, versionCode)

    /** 簽章指紋前 4 組（SHA-256），用來核對是不是同一把金鑰。讀不到時是「?」。 */
    val signatureShort: String = try {
        val signers = packageInfo?.signingInfo?.apkContentsSigners
        if (signers.isNullOrEmpty()) "?" else AppInfo.fingerprintShort(signers[0].toByteArray())
    } catch (_: Exception) {
        "?"
    }

    /** 記錄資料夾在手錶上的完整路徑，用 adb pull 取出。 */
    val dataPath: String =
        ((appContext.getExternalFilesDir(null) ?: appContext.filesDir).absolutePath) + "/sessions"


    fun selectSession(id: String) {
        _selectedId.value = id
    }

    fun selectShot(index: Int) {
        _selectedShot.value = index
    }

    /**
     * 事后在記錄詳情裡標記／恢復誤判。進行中的記錄不能改（記錄器正在寫它）。
     * 回傳時存檔已經排進佇列，之後再讀取一定讀到新內容。
     */
    /** 事後修改這次記錄的條件標籤。進行中的記錄不能改。 */
    suspend fun setSessionTags(id: String, tags: List<String>): Unit =
        updateSession(id) { it.copy(tags = tags) }

    /** 記錄佔用的空間。 */
    suspend fun storageUsage(): StorageUsage = withContext(Dispatchers.IO) { store.usage() }

    /** 只刪除原始資料 CSV（進行中的那一筆除外），保留記錄。 */
    suspend fun deleteRawData(): RawDeleteResult = withContext(Dispatchers.IO) {
        val r = store.deleteRawData(keepId = _activeId.value)
        publishSessions()
        r
    }

    suspend fun setShotCancelled(id: String, index: Int, cancelled: Boolean): Unit =
        updateShot(id, index) { it.copy(cancelled = cancelled) }

    /** 事後輸入／清除某一箭的環數（0～10，null 為清除）。進行中的記錄不能改。 */
    suspend fun setShotScore(id: String, index: Int, score: Int?): Unit =
        updateShot(id, index) { it.copy(score = score?.coerceIn(0, 10)) }

    /** 單箭自評（1 差、2 普通、3 好，null 清除）。進行中的記錄交給錄製器，其他的改存檔。 */
    suspend fun setShotFeel(id: String, index: Int, feel: Int?): Unit {
        val v = Feel.valid(feel)
        if (id == _activeId.value) {
            recorder.setFeel(index, v)
        } else {
            updateShot(id, index) { it.copy(feel = v) }
        }
    }

    private suspend fun updateShot(id: String, index: Int, change: (ShotEntry) -> ShotEntry): Unit =
        updateSession(id) { s ->
            if (index !in s.shots.indices) {
                s
            } else {
                val shots = s.shots.toMutableList()
                shots[index] = change(shots[index])
                s.copy(shots = shots)
            }
        }

    private suspend fun updateSession(id: String, change: (Session) -> Session): Unit =
        withContext(Dispatchers.IO) {
            if (id == _activeId.value) return@withContext
            val s = store.load(id) ?: return@withContext
            store.saveAsync(change(s))
            publishSessions()
        }

    /** 記錄頁管理模式：一次刪除多筆（進行中的那一筆會被略過）。 */
    fun deleteSessions(ids: Set<String>) {
        val active = _activeId.value
        val targets = ids.filter { it != active }
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            store.deleteMany(targets)
            publishSessions()
        }
    }

    /** 匯出 summary.csv 和 shots.csv 到 sessions 資料夾。回傳是否成功。 */
    suspend fun exportSummary(): Boolean = withContext(Dispatchers.IO) { store.exportAll() }

    /** 匯出網頁報告 report.html（v1.16）：頁首含作者、版本、簽章與 App 圖示。 */
    suspend fun exportReport(): Boolean = withContext(Dispatchers.IO) {
        store.exportReport { ReportBuilder.build(it, reportMeta()) }
    }

    private fun reportMeta(): ReportMeta {
        val icon = try {
            appContext.resources.openRawResource(R.raw.report_icon).use {
                android.util.Base64.encodeToString(it.readBytes(), android.util.Base64.NO_WRAP)
            }
        } catch (_: Exception) {
            null
        }
        return ReportMeta(
            author = AppInfo.AUTHOR,
            versionLabel = versionLabel,
            signature = signatureShort,
            signatureNote = AppInfo.signatureStatus(signatureShort),
            generatedAtMs = System.currentTimeMillis(),
            iconBase64 = icon,
            arrowsPerEnd = settings.arrowsPerEnd.value,
        )
    }

    // ── 改版內容（v1.17.1）──
    private val _changelog = MutableStateFlow(ChangelogUi())
    val changelog: StateFlow<ChangelogUi> = _changelog.asStateFlow()

    /** 載入改版內容：先顯示上次存下來的，再抓 GitHub 上的 CHANGELOG.md；只列已發布的版本。 */
    fun loadChangelog() {
        if (_changelog.value.loading) return
        val cached = prefs.getString("changelog_cache", "") ?: ""
        val cachedTags = prefs.getString("changelog_tags", null)?.split(',')?.filter { it.isNotBlank() }?.toSet()
        if (cached.isNotEmpty()) {
            _changelog.value = ChangelogUi(loading = true, entries = Changelog.onlyPublished(Changelog.parse(cached), cachedTags), offline = true)
        } else {
            _changelog.value = ChangelogUi(loading = true)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val mgr = UpdateManager(appContext)
            try {
                val md = mgr.fetchText(Changelog.RAW_URL, json = false)
                val tags = try {
                    Changelog.publishedTags(mgr.fetchText(Changelog.RELEASES_URL, json = true))
                } catch (e: Exception) {
                    null
                }
                val ed = prefs.edit().putString("changelog_cache", md)
                if (tags != null) ed.putString("changelog_tags", tags.joinToString(",")) else ed.remove("changelog_tags")
                ed.apply()
                _changelog.value = ChangelogUi(entries = Changelog.onlyPublished(Changelog.parse(md), tags))
            } catch (e: Exception) {
                val old = _changelog.value.entries
                _changelog.value = ChangelogUi(entries = old, offline = old.isNotEmpty(), failed = old.isEmpty())
            }
        }
    }

    // ── 從別台匯入與報告檢視（v1.17）──
    private val _import = MutableStateFlow(ImportUi())
    val importUi: StateFlow<ImportUi> = _import.asStateFlow()

    /** 上次輸入的對方位址（方便下次）。不含 PIN。 */
    var lastImportAddress: String
        get() = prefs.getString("import_addr", "") ?: ""
        set(v) { prefs.edit().putString("import_addr", v.take(40)).apply() }

    fun startImport(address: String, pin: String) {
        if (_import.value.running) return
        val base = ImportAddress.parse(address)
        if (base == null) {
            _import.value = ImportUi(result = ImportResult(error = ImportError.BAD_ADDRESS))
            return
        }
        lastImportAddress = address.trim()
        _import.value = ImportUi(running = true)
        viewModelScope.launch(Dispatchers.IO) {
            val importer = SessionImporter(store, HttpSource(base, pin.trim()))
            val r = importer.run(settings.importOverwrite.value, settings.importRaw.value) { d, t ->
                _import.value = ImportUi(running = true, done = d, total = t)
            }
            _import.value = ImportUi(result = r)
            publishSessions()
        }
    }

    fun clearImportResult() {
        if (!_import.value.running) _import.value = ImportUi()
    }

    /** 在這台裝置上直接看的網頁報告（全部記錄）。 */
    suspend fun reportHtml(): String = withContext(Dispatchers.IO) {
        ReportBuilder.build(store.allSessions(), reportMeta())
    }

    // ── 區網分享（v1.16.1）──
    private var shareServer: ShareServer? = null
    private var shareTimer: kotlinx.coroutines.Job? = null
    private val shareData by lazy { ShareData(prefs) }
    /** 已信任的裝置與今天的連線記錄（v1.16.3）。 */
    val trustedDevices: StateFlow<List<TrustedDevice>> get() = shareData.devices
    val shareEvents: StateFlow<List<ShareEvent>> get() = shareData.events
    fun renameTrusted(id: String, name: String) = shareData.rename(id, name)
    fun removeTrusted(id: String) = shareData.remove(id)
    fun clearTrusted() = shareData.clearDevices()
    fun clearShareLog() = shareData.clearLog()
    fun onlineTrusted(): List<TrustedDevice> = shareData.online()
    private val _share = MutableStateFlow(ShareState())
    val share: StateFlow<ShareState> = _share.asStateFlow()

    /** 開始分享：產生 PIN、啟動伺服器，10 分鐘後自動關閉。 */
    fun startShare() {
        if (_share.value.running) return
        val ip = ShareServer.localIp()
        if (ip == null) {
            _share.value = ShareState(error = ShareError.NO_WIFI)
            return
        }
        val pin = String.format("%04d", java.security.SecureRandom().nextInt(10000))
        val gate = object : TrustGate {
            override val enabled: Boolean get() = settings.shareRemember.value
            override fun verify(token: String) = shareData.verify(token)
            override fun issue(ip: String, agent: String) = shareData.issue(ip, agent)
            override fun record(event: ShareEvent) {
                if (settings.shareLog.value) shareData.log(event)
                if (event.result == ShareResult.WRONG_PIN && settings.shareAlert.value) {
                    buzzer.pattern(longArrayOf(0, 120, 80, 120))
                }
            }
        }
        val router = ShareRouter(
            pin = pin,
            listFiles = { store.shareNames() },
            lookup = { store.shareFile(it) },
            buildReport = { ReportBuilder.build(store.allSessions(), reportMeta()) },
            zh = !I18n.english,
            gate = gate,
        )
        val server = ShareServer(router)
        if (!server.start()) {
            _share.value = ShareState(error = ShareError.PORT)
            return
        }
        shareServer = server
        _share.value = ShareState(running = true, url = "http://$ip:${ShareServer.PORT}", pin = pin)
        shareTimer?.cancel()
        shareTimer = viewModelScope.launch {
            kotlinx.coroutines.delay(ShareState.AUTO_STOP_MS)
            stopShare()
        }
    }

    fun stopShare() {
        shareTimer?.cancel()
        shareTimer = null
        shareServer?.stop()
        shareServer = null
        _share.value = ShareState()
    }


    /** 即時波形：最近幾秒的加速度合力大小（m/s²），由舊到新。 */
    fun liveSnapshot(): FloatArray = live.snapshot()

    /** 即時晃動圓盤：最近約 2 秒的 x、y（由舊到新）。 */
    fun liveTraceSnapshot(): Pair<FloatArray, FloatArray> = liveTrace.snapshot()

    suspend fun loadSession(id: String): Session? = withContext(Dispatchers.IO) { store.load(id) }

    fun deleteSession(id: String) {
        if (id == _activeId.value) return
        viewModelScope.launch(Dispatchers.IO) {
            store.delete(id)
            publishSessions()
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            store.deleteAll(keepId = _activeId.value)
            publishSessions()
        }
    }

    // ---- 唤醒锁与震动 ----

    private fun updateWakeLock() {
        val want = _running.value && settings.wakeLock.value
        if (want) {
            val wl = wakeLock ?: powerManager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ArcheryAssistant:detect")
                .apply { setReferenceCounted(false) }
                .also { wakeLock = it }
            if (!wl.isHeld) wl.acquire(MAX_WAKE_LOCK_MS)
        } else {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
    }

    private fun autoPauseNow() {
        if (!_running.value) return
        val min = settings.autoPauseMin.value
        buzzEnd(EndVibration.LONG)
        stop()
        _notice.value = tr("已自動暫停：超過 $min 分鐘沒有放箭", "Auto-paused: no arrow for over $min min")
    }

    // ---- 震動 ----

    /** 每一箭：一次很短的輕震。 */
    private fun buzzShot() = buzzer.shot()

    /** 滿一趟的震動；樣式為「關閉」時什麼都不做。設定頁選樣式時也用它試震。 */
    fun buzzEnd(style: Int) = buzzer.end(style)

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
            detector.onGyro(sqrt(lastGx * lastGx + lastGy * lastGy + lastGz * lastGz))
            if (settings.live2d.value && settings.traceGyro.value) liveTrace.add(timeMs, lastGx, lastGy)
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
        rateMeter.onSample(event.timestamp)
        val measured = rateMeter.hz
        if (measured != _measuredHz.value) _measuredHz.value = measured
        finishFollowThrough(timeMs)

        // 自動暫停：太久沒有放箭（校準錄製不適用）
        if (lastActivityMs < 0) lastActivityMs = timeMs
        if (!calibrating && !competing && !autoPausing && settings.autoPause.value &&
            AutoPause.shouldPause(timeMs, lastActivityMs, settings.autoPauseMin.value)
        ) {
            autoPausing = true
            viewModelScope.launch { autoPauseNow() }
        }
        if (settings.liveWaveform.value) {
            live.add(timeMs, sqrt(ax * ax + ay * ay + az * az))
        }
        if (settings.live2d.value && !settings.traceGyro.value) liveTrace.add(timeMs, ax, ay)
        recorder.writeRaw(timeMs - baseElapsedMs, ax, ay, az, lastGx, lastGy, lastGz)
        detector.onSample(ax, ay, az, timeMs)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onCleared() {
        shareServer?.stop()
        sensorManager.unregisterListener(this)
        battery.stop()
        if (_running.value) {
            _running.value = false
            recorder.stop(System.currentTimeMillis())
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        sound.release()
        sensorThread.quitSafely()
    }

    private companion object {
        const val END_FEEDBACK_MS = 5000L
        const val KEY_COUNT = "count"
        const val KEY_THRESHOLD = "threshold"
        const val COMP_TICK_MS = 200L
        const val COMP_DONE_DELAY_MS = 1500L
        const val GRAVITY_ALPHA = 0.9f
        const val MIN_THRESHOLD = 6f
        const val MAX_THRESHOLD = 40f
        const val RAD_TO_DEG = 57.29578f
        const val MAX_WAKE_LOCK_MS = 4L * 60 * 60 * 1000 // 安全上限 4 小时
    }

    // 要放在所有屬性之後，這時 versionName 都已初始化
    init {
        runAutoClean()
        updates.start()
    }
}
