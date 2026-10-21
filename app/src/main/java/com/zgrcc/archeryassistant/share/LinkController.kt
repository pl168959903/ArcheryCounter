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

/** 區域網裝置連線與匯入（從 CounterViewModel 拆出，v1.21.1）。 */
class LinkController(
    private val scope: CoroutineScope,
    private val prefs: SharedPreferences,
    private val settings: AppSettings,
    private val store: () -> SessionStore,
    private val versionName: () -> String,
    private val activeId: () -> String?,
    private val onImported: () -> Unit,
) {
    // ── 從別台匯入與報告檢視（v1.17）──
    private val _import = MutableStateFlow(ImportUi())
    val importUi: StateFlow<ImportUi> = _import.asStateFlow()

    /** 上次輸入的對方位址（方便下次）。不含 PIN。 */
    var lastImportAddress: String
        get() = prefs.getString("import_addr", "") ?: ""
        set(v) { prefs.edit().putString("import_addr", v.take(40)).apply() }

    // ── 區域網裝置頁（v1.18.1）──
    private val _link = MutableStateFlow(LinkDevice())
    val link: StateFlow<LinkDevice> = _link.asStateFlow()

    private val appUserAgent: String
        get() = AgentLabel.userAgent(versionName(), android.os.Build.MODEL ?: "")

    /** 這個位址以前通過 PIN 後，對方發給本 App 的信任憑證（沒有就空字串）。 */
    private fun tokenMap(): LinkedHashMap<String, String> {
        val m = LinkedHashMap<String, String>()
        for (line in (prefs.getString("app_tokens", "") ?: "").lines()) {
            val p = line.split('\t')
            if (p.size == 2) m[p[0]] = p[1]
        }
        return m
    }

    private fun tokenFor(base: String): String = tokenMap()[base] ?: ""

    private fun removeToken(token: String) {
        if (token.isEmpty()) return
        val m = tokenMap()
        m.values.removeAll { it == token }
        prefs.edit().putString("app_tokens", m.entries.joinToString("\n") { it.key + "\t" + it.value }).apply()
    }

    private fun saveToken(base: String, token: String) {
        if (token.isEmpty()) return
        val m = LinkedHashMap<String, String>()
        for (line in (prefs.getString("app_tokens", "") ?: "").lines()) {
            val p = line.split('\t')
            if (p.size == 2) m[p[0]] = p[1]
        }
        m[base] = token
        val text = m.entries.toList().takeLast(10).joinToString("\n") { it.key + "\t" + it.value }
        prefs.edit().putString("app_tokens", text).apply()
    }

    private fun sourceFor(base: String, pin: String) = HttpSource(base, pin, appUserAgent, tokenFor(base))

    /** 從自動尋找的清單點進某台裝置。 */
    fun openLinkDevice(address: String, name: String) {
        _import.value = ImportUi()
        _link.value = LinkDevice(address = address, name = name)
        // 以前連過（存過憑證）就先靜默試一次，成功就直接跳過 PIN 頁（v1.18.4）
        if (tokenMap().isNotEmpty()) verifyLink(address, "", silent = true)
    }

    /** 找不到時手動輸入位址。 */
    fun openManualDevice() {
        _import.value = ImportUi()
        _link.value = LinkDevice(address = lastImportAddress, manual = true)
    }

    /** 驗證 PIN（或已有的信任憑證）。成功後這一頁才出現「開啟網頁報告」和「開始匯入」。 */
    fun verifyLink(address: String, pin: String, silent: Boolean = false) {
        val cur = _link.value
        if (cur.busy) return
        val base = ImportAddress.parse(address)
        if (base == null) {
            _link.value = cur.copy(address = address, error = ImportError.BAD_ADDRESS)
            return
        }
        lastImportAddress = address.trim()
        _link.value = cur.copy(address = address.trim(), busy = true, error = null)
        scope.launch(Dispatchers.IO) {
            var source = sourceFor(base, pin.trim())
            try {
                val name: String
                if (pin.isBlank()) {
                    // 沒填 PIN：把存過的憑證逐一試（位址變了，舊憑證也許對方還認得），哪張通過就記到這個位址
                    var found: String? = null
                    var last: ImportException? = null
                    for (tok in ReportLink.tokenOrder(tokenFor(base), tokenMap().values.toList())) {
                        val s = HttpSource(base, "", appUserAgent, tok)
                        try {
                            found = s.deviceName()
                            source = s
                            break
                        } catch (e: ImportException) {
                            if (e.error != ImportError.WRONG_PIN) throw e
                            last = e
                        }
                    }
                    name = found ?: throw (last ?: ImportException(ImportError.WRONG_PIN))
                } else {
                    name = source.deviceName()
                }
                saveToken(base, source.token)
                _link.value = LinkDevice(
                    address = address.trim(), name = name.ifBlank { cur.name }.ifBlank { base.removePrefix("http://") },
                    pin = pin.trim(), verified = true, manual = cur.manual,
                )
            } catch (e: ImportException) {
                // 靜默嘗試沒通過：只是需要 PIN，不顯示「PIN 不正確」
                val err = if (silent && e.error == ImportError.WRONG_PIN) null else e.error
                _link.value = cur.copy(address = address.trim(), busy = false, error = err)
            } catch (e: Exception) {
                _link.value = cur.copy(address = address.trim(), busy = false, error = if (silent) null else ImportError.UNREACHABLE)
            }
        }
    }

    /** 外部瀏覽器要開的對方報告網址：有憑證先要一次性通行票，否則帶 PIN，再不行開到 PIN 輸入頁。 */
    suspend fun remoteReportUrl(): String? = withContext(Dispatchers.IO) {
        val l = _link.value
        val base = ImportAddress.parse(l.address) ?: return@withContext null
        val token = tokenFor(base)
        var ticket = ""
        if (token.isNotEmpty()) {
            ticket = try {
                HttpSource(base, "", appUserAgent, token).ticket()
            } catch (e: Exception) {
                ""
            }
        }
        ReportLink.url(base, l.pin, ticket)
    }

    /** 請對方取消這台裝置的信任，並刪掉本機存的憑證；之後再連要輸入 PIN。 */
    fun revokeLink() {
        val l = _link.value
        val base = ImportAddress.parse(l.address) ?: return
        val token = tokenFor(base)
        scope.launch(Dispatchers.IO) {
            if (token.isNotEmpty()) {
                try {
                    HttpSource(base, "", appUserAgent, token).revoke()
                } catch (e: Exception) {
                    // 對方連不上或已經不認得：本機憑證照樣刪掉
                }
                removeToken(token)
            }
            _link.value = LinkDevice(address = l.address, name = l.name, manual = l.manual)
        }
    }

    fun leaveLinkDevice() {
        _link.value = LinkDevice()
        if (!_import.value.running) _import.value = ImportUi()
    }

    /** 按「開始匯入」：預覽開著就先預覽（等確認），否則直接匯入。 */
    fun startImport() {
        val l = _link.value
        if (!l.verified || _import.value.running) return
        val base = ImportAddress.parse(l.address) ?: run {
            _import.value = ImportUi(result = ImportResult(error = ImportError.BAD_ADDRESS))
            return
        }
        if (!settings.importPreview.value) {
            runImport(base, l.pin, l.name)
            return
        }
        _import.value = ImportUi(running = true)
        scope.launch(Dispatchers.IO) {
            val source = sourceFor(base, l.pin)
            val p = SessionImporter(store(), source).preview(settings.importRaw.value)
            saveToken(base, source.token)
            _import.value = if (p.error != null) ImportUi(result = ImportResult(error = p.error)) else ImportUi(preview = p)
        }
    }

    /** 預覽後按「確認匯入」。 */
    fun confirmImport() {
        val l = _link.value
        val base = ImportAddress.parse(l.address) ?: return
        if (_import.value.preview != null && !_import.value.running) runImport(base, l.pin, l.name)
    }

    private fun runImport(base: String, pin: String, name: String) {
        _import.value = ImportUi(running = true)
        scope.launch(Dispatchers.IO) {
            val source = sourceFor(base, pin)
            val importer = SessionImporter(store(), source)
            val r = importer.run(settings.importOverwrite.value, settings.importRaw.value, settings.importSettings.value) { d, t ->
                _import.value = ImportUi(running = true, done = d, total = t)
            }
            saveToken(base, source.token)
            if (r.error == null && settings.importMark.value && r.ids.isNotEmpty()) {
                ImportMarks(prefs).put(r.ids, name.ifBlank { base.removePrefix("http://") })
            }
            _import.value = ImportUi(result = r)
            onImported()
        }
    }

    /** 每筆匯入記錄的來源名稱（記錄列表用）；「標示匯入來源」關閉時為空。 */
    fun importOrigins(): Map<String, String> = if (settings.importMark.value) ImportMarks(prefs).all() else emptyMap()

    /** 復原這次匯入（v1.19）：只刪這次新增的記錄；覆蓋掉本機原有的無法還原。 */
    fun undoImport() {
        val r = _import.value.result ?: return
        val ids = r.freshIds.filter { it != activeId() }
        if (ids.isEmpty() || _import.value.running) return
        scope.launch(Dispatchers.IO) {
            store().deleteMany(ids)
            ImportMarks(prefs).forget(ids)
            _import.value = ImportUi(undone = ids.size)
            onImported()
        }
    }

    fun clearImportResult() {
        if (!_import.value.running) _import.value = ImportUi()
    }

}
