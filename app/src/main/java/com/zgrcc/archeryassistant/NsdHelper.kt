package com.zgrcc.archeryassistant

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.Inet4Address

/** 區網上找到的另一台正在分享的裝置。 */
data class FoundDevice(val name: String, val address: String)

/**
 * 區網自動尋找（v1.18）：用系統的 NsdManager（mDNS）廣播和尋找 `_archeryshare._tcp`。
 * 分享時廣播自己；匯入時列出找到的裝置。都由「區網自動尋找」開關控制，關閉時什麼都不做。
 * 失敗（系統不支援、網路不允許）一律吞掉，不影響其他功能。
 */
class NsdHelper(context: Context) {
    private val nsd: NsdManager? = try {
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    } catch (e: Exception) {
        null
    }
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null

    fun advertise(name: String, port: Int) {
        val m = nsd ?: return
        stopAdvertise()
        val info = NsdServiceInfo().apply {
            serviceName = "ArcheryAssistant-" + name.take(20)
            serviceType = TYPE
            setPort(port)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
        }
        try {
            m.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
            registration = l
        } catch (e: Exception) {
            registration = null
        }
    }

    fun stopAdvertise() {
        val l = registration ?: return
        registration = null
        try {
            nsd?.unregisterService(l)
        } catch (e: Exception) {
        }
    }

    fun startDiscovery(onFound: (FoundDevice) -> Unit) {
        val m = nsd ?: return
        stopDiscovery()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, code: Int) {}
            override fun onStopDiscoveryFailed(t: String, code: Int) {}
            override fun onServiceLost(i: NsdServiceInfo) {}
            override fun onServiceFound(i: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                try {
                    m.resolveService(i, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(s: NsdServiceInfo, code: Int) {}
                        override fun onServiceResolved(s: NsdServiceInfo) {
                            val host = s.host
                            if (host is Inet4Address && ShareRouter.isLan(host)) {
                                onFound(FoundDevice(s.serviceName.removePrefix("ArcheryAssistant-"), "${host.hostAddress}:${s.port}"))
                            }
                        }
                    })
                } catch (e: Exception) {
                }
            }
        }
        try {
            m.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, l)
            discovery = l
        } catch (e: Exception) {
            discovery = null
        }
    }

    fun stopDiscovery() {
        val l = discovery ?: return
        discovery = null
        try {
            nsd?.stopServiceDiscovery(l)
        } catch (e: Exception) {
        }
    }

    private companion object {
        const val TYPE = "_archeryshare._tcp."
    }
}
