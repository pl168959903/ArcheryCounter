package com.zgrcc.archeryassistant

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 區網分享、自動尋找與信任裝置（從 CounterViewModel 拆出，v1.21.1）。 */
class ShareController(
    private val scope: CoroutineScope,
    private val appContext: Context,
    prefs: SharedPreferences,
    private val settings: AppSettings,
    private val store: () -> SessionStore,
    private val buzzer: Buzzer,
    private val reportMeta: () -> ReportMeta,
) {
    // ── 區網分享（v1.16.1）──
    private var shareServer: ShareServer? = null
    private var shareTimer: kotlinx.coroutines.Job? = null
    private val nsd by lazy { NsdHelper(appContext) }
    private val _found = MutableStateFlow<List<FoundDevice>>(emptyList())
    /** 區網自動尋找到的裝置（v1.18）。 */
    val foundDevices: StateFlow<List<FoundDevice>> = _found.asStateFlow()

    fun startDiscovery() {
        if (!settings.importDiscover.value) return
        _found.value = emptyList()
        nsd.startDiscovery { d -> _found.value = (_found.value.filter { it.address != d.address } + d) }
    }

    fun stopDiscovery() = nsd.stopDiscovery()

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
            override fun revoke(token: String): Boolean = shareData.revoke(token)
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
            listFiles = { store().shareNames() },
            lookup = { store().shareFile(it) },
            buildReport = { ReportBuilder.build(store().allSessions(), reportMeta()) },
            zh = !I18n.english,
            gate = gate,
            deviceName = android.os.Build.MODEL ?: "",
            zip = if (settings.shareZip.value) ({ store().shareZip() }) else null,
        )
        val server = ShareServer(router)
        if (!server.start()) {
            _share.value = ShareState(error = ShareError.PORT)
            return
        }
        shareServer = server
        _share.value = ShareState(running = true, url = "http://$ip:${ShareServer.PORT}", pin = pin)
        if (settings.importDiscover.value) nsd.advertise(android.os.Build.MODEL ?: "watch", ShareServer.PORT)
        shareTimer?.cancel()
        val autoMin = settings.shareAutoStopMin.value
        if (autoMin > 0) {
            shareTimer = scope.launch {
                kotlinx.coroutines.delay(autoMin * 60_000L)
                buzzer.pattern(longArrayOf(0, 200, 100, 200))
                stopShare()
            }
        }
    }

    fun stopShare() {
        shareTimer?.cancel()
        shareTimer = null
        shareServer?.stop()
        shareServer = null
        nsd.stopAdvertise()
        _share.value = ShareState()
    }


    fun shutdown() {
        shareServer?.stop()
    }
}
