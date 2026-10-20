# 隱私權政策 / Privacy Policy

**Archery Assistant**（套件名稱 `com.zgrcc.archeryassistant`）　作者：ZGrCc

最後更新 / Last updated: 2026-10（v1.13：加入檢查更新 / adds update check）

## 繁體中文

Archery Assistant 是一個在 Wear OS 手錶上運作的射箭輔助工具，用手錶的加速度與陀螺儀感測器偵測放箭、計數，並分析手臂穩定度。

**我們不蒐集你的資料。**
- 應用沒有廣告、分析或追蹤程式，也不會上傳你的任何資料。
- 網路只用在「檢查更新」（v1.13 起，預設不自動檢查，要在設定開啟「啟動時自動檢查更新」，或自己按「檢查更新」）：應用只會連到 GitHub（`api.github.com` 與 GitHub 的下載位址），讀取本專案公開的發布版本號與說明，你選擇「下載並安裝」時才下載更新檔。請求裡只有應用名稱，不含你的記錄、設定或任何個人資料。
- 所有資料（箭數、設定、每次射擊的記錄與感測器數值）都只存在你的手錶本機，位於應用專屬的儲存空間：`/sdcard/Android/data/com.zgrcc.archeryassistant/files/`。
- 開發者與任何第三方都無法取得這些資料。你可以隨時在應用內刪除記錄，或解除安裝應用一併清除。
- 應用使用的權限：震動（放箭與提醒）、喚醒鎖（設定開啟時，讓偵測在螢幕熄滅時繼續）、網路（只用於檢查更新與下載更新檔）、安裝應用（把下載好的更新檔交給系統安裝程式，安裝時仍需你在系統畫面確認）。感測器為手錶內建，不需要額外授權。
- 應用不會讀取你的位置、聯絡人、相片、麥克風或相機。

你若用 adb 或其他方式把記錄複製出手錶，那份複本由你自己保管。

如有疑問，請到本專案的 GitHub 頁面開 Issue：https://github.com/pl168959903/ArcheryCounter

## English

Archery Assistant is an archery helper for Wear OS watches. It uses the watch's accelerometer and gyroscope to detect arrow releases, count them and analyse arm steadiness.

**We do not collect your data.**
- The app contains no ads, analytics or trackers and never uploads any of your data.
- The network is used only for "Check for updates" (since v1.13; automatic checking is off by default - turn on "Check for updates on start" in Settings, or tap "Check for updates" yourself). The app connects only to GitHub (`api.github.com` and GitHub's download addresses), reads the public release version and notes of this project, and downloads the update file only when you choose "Download and install". Requests carry only the app name - no records, settings or personal data.
- All data (arrow counts, settings, session records and sensor values) stays on your watch in the app's private storage: `/sdcard/Android/data/com.zgrcc.archeryassistant/files/`.
- Neither the developer nor any third party can access it. You can delete records inside the app at any time, or uninstall the app to remove everything.
- Permissions used: vibration (release and reminder buzzes), wake lock (only when you turn it on, to keep detecting with the screen off), internet (only to check for updates and download the update file) and install packages (hands the downloaded update to the system installer, which still asks you to confirm). The sensors are built into the watch and need no extra permission.
- The app does not access your location, contacts, photos, microphone or camera.

If you copy records off the watch (for example with adb), that copy is yours to manage.

Questions: open an Issue at https://github.com/pl168959903/ArcheryCounter
