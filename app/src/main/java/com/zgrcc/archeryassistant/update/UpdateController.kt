package com.zgrcc.archeryassistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 檢查更新、下載、略過的狀態管理（v1.13，v1.14.1 從 CounterViewModel 拆出）。
 * [currentVersion] 用函式取得，因為建立這個物件時版本名稱還沒初始化。
 */
class UpdateController(
    private val scope: CoroutineScope,
    private val settings: AppSettings,
    private val updater: UpdateManager,
    private val currentVersion: () -> String,
) {
    private val _update = MutableStateFlow(UpdateUi())
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    /** 建立後呼叫一次：監聽安裝失敗，並在設定開啟時自動檢查（每次開啟或一天一次，見 [UpdatePolicy.shouldAutoCheck]）。 */
    fun start() {
        // 安裝程式回報失敗（被取消、簽章不符等）
        scope.launch {
            var seen = UpdateBus.installFailed.value
            UpdateBus.installFailed.collect { n ->
                if (n != seen) {
                    seen = n
                    _update.value = UpdateUi(UpdateKind.ERROR, _update.value.info, error = UpdateError.INSTALL_FAILED)
                }
            }
        }
        // 自動檢查更新：開啟時，預設每次開啟查一次；關掉「每次開啟」則距上次超過一天才查
        if (UpdatePolicy.shouldAutoCheck(settings.updateMode.value, settings.updateLastCheck, System.currentTimeMillis())) {
            checkUpdate(manual = false)
        }
    }

    /**
     * 檢查 GitHub 最新發布。[manual] 為 false 是啟動時的自動檢查：沒有新版本或失敗都不吭聲，
     * 也不提示使用者已略過的版本。
     */
    fun checkUpdate(manual: Boolean) {
        val kind = _update.value.kind
        if (kind == UpdateKind.CHECKING || kind == UpdateKind.DOWNLOADING || kind == UpdateKind.INSTALLING) return
        if (manual) _update.value = UpdateUi(UpdateKind.CHECKING)
        scope.launch(Dispatchers.IO) {
            try {
                val info = updater.fetchLatest()
                settings.updateLastCheck = System.currentTimeMillis()
                _update.value = when {
                    info == null -> if (manual) UpdateUi(UpdateKind.ERROR, error = UpdateError.NO_APK) else UpdateUi()
                    UpdatePolicy.shouldOffer(info.tag, currentVersion(), settings.updateSkipped, manual) ->
                        UpdateUi(UpdateKind.AVAILABLE, info)
                    manual -> UpdateUi(UpdateKind.UP_TO_DATE)
                    else -> UpdateUi()
                }
            } catch (e: UpdateException) {
                _update.value = if (manual) UpdateUi(UpdateKind.ERROR, error = e.error) else UpdateUi()
            } catch (e: Exception) {
                _update.value = if (manual) UpdateUi(UpdateKind.ERROR, error = UpdateError.SERVER) else UpdateUi()
            }
        }
    }

    /** 下載並交給系統安裝程式。只有在有新版本、且系統允許安裝時才會動作。 */
    fun downloadAndInstall() {
        val state = _update.value
        val info = state.info
        if (state.kind != UpdateKind.AVAILABLE || info == null) return
        if (!updater.canInstall()) {
            _update.value = UpdateUi(UpdateKind.ERROR, info, error = UpdateError.NOT_ALLOWED)
            updater.openInstallSettings()
            return
        }
        _update.value = UpdateUi(UpdateKind.DOWNLOADING, info, percent = 0)
        scope.launch(Dispatchers.IO) {
            try {
                val file = updater.download(info) { p -> _update.value = UpdateUi(UpdateKind.DOWNLOADING, info, percent = p) }
                _update.value = UpdateUi(UpdateKind.INSTALLING, info)
                updater.install(file)
            } catch (e: UpdateException) {
                _update.value = UpdateUi(UpdateKind.ERROR, info, error = e.error)
            } catch (e: Exception) {
                _update.value = UpdateUi(UpdateKind.ERROR, info, error = UpdateError.INSTALL_FAILED)
            }
        }
    }

    /** 略過目前提示的版本：之後自動檢查不再提示這個版本。 */
    fun skipUpdate() {
        val info = _update.value.info ?: return
        settings.updateSkipped = info.tag
        _update.value = UpdateUi()
    }
}
