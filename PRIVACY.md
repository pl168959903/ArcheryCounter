# 隱私權政策 / Privacy Policy

**Archery Assistant**（GitHub 版套件名稱 `com.zgrcc.archeryassistant`，Google Play 版 `com.zgrcc.archeryassistant.play`）　作者：ZGrCc

最後更新 / Last updated: 2026-10（v1.23：加入區網分享與匯入的說明、區分 GitHub 版與 Google Play 版 / adds LAN sharing and import, separates the GitHub and Google Play editions）

## 繁體中文

Archery Assistant 是一個在 Wear OS 手錶上運作的射箭輔助工具，用手錶的加速度與陀螺儀感測器偵測放箭、計數，並分析手臂穩定度。也可以裝在手機上，用來匯入與檢視手錶的記錄。

**我們不蒐集你的資料。開發者沒有伺服器，也不會收到你的任何資料。**
- 應用沒有廣告、分析或追蹤程式，也不會把你的資料上傳到網路上的任何服務。
- 所有資料（箭數、設定、每次射擊的記錄與感測器數值）都只存在你的裝置本機，位於應用專屬的儲存空間：`/sdcard/Android/data/<套件名稱>/files/`。兩個版本可以同時安裝，資料各自獨立。你可以隨時在應用內刪除記錄，或解除安裝應用一併清除。

**區域網路分享與匯入（選用，由你主動開啟）**
- 在「連線」設定裡，你可以讓手錶在同一個 Wi-Fi 區域網路內暫時分享記錄（會顯示網址與 4 位數 PIN，預設 10 分鐘後自動關閉），讓你自己的手機或電腦用瀏覽器查看，或讓本應用的手機版匯入。
- 資料只在區域網路內、你輸入 PIN 或已信任的裝置之間傳輸，不經過網際網路，也不會經過開發者。區域網路內使用 HTTP，內容沒有加密，請只在你信任的網路使用。
- 「區網自動尋找」開啟時，應用會用系統的 mDNS 服務在區域網路內廣播與尋找裝置名稱。
- 為了讓你管理，手錶會在本機記下已信任裝置（名稱、連線時間）與今天的連線記錄；你可以隨時在應用內清除。

**檢查更新（只有 GitHub 版）**
- 從 GitHub 下載的版本有「檢查更新」。預設不自動檢查，要在設定開啟，或自己按「檢查更新」。應用只會連到 GitHub（`api.github.com` 與 GitHub 的下載位址），讀取本專案公開的發布版本號與說明，你選擇「下載並安裝」時才下載更新檔。請求裡只有應用名稱，不含你的記錄、設定或任何個人資料。「改版內容」頁也會從 GitHub 讀取本專案公開的更新記錄。
- 從 Google Play 安裝的版本沒有這個功能，更新由 Google Play 負責，「改版內容」使用內建的檔案，不連網。

**應用使用的權限**：震動（放箭與提醒）、喚醒鎖（設定開啟時，讓偵測在螢幕熄滅時繼續）、網路與 Wi-Fi 狀態／多播（區域網路分享、匯入與自動尋找；GitHub 版另外用於檢查更新）、安裝應用（只有 GitHub 版：把下載好的更新檔交給系統安裝程式，安裝時仍需你在系統畫面確認）。感測器為手錶內建，不需要額外授權。應用不會讀取你的位置、聯絡人、相片、麥克風或相機。

你若用 adb 或其他方式把記錄複製出裝置，那份複本由你自己保管。

如有疑問，請到本專案的 GitHub 頁面開 Issue：https://github.com/pl168959903/ArcheryCounter

## English

Archery Assistant is an archery helper for Wear OS watches. It uses the watch's accelerometer and gyroscope to detect arrow releases, count them and analyse arm steadiness. It can also be installed on a phone to import and view the watch's records.

**We do not collect your data. The developer runs no server and receives none of your data.**
- The app contains no ads, analytics or trackers and never uploads your data to any online service.
- All data (arrow counts, settings, session records and sensor values) stays on your device in the app's private storage: `/sdcard/Android/data/<package name>/files/`. The two editions can be installed side by side and keep separate data. You can delete records inside the app at any time, or uninstall the app to remove everything.

**Local-network sharing and import (optional, started by you)**
- In the Connection settings you can let the watch share records temporarily on the same Wi-Fi network (it shows an address and a 4-digit PIN, and stops automatically after 10 minutes by default), so your own phone or computer can view them in a browser, or the phone version of this app can import them.
- Data travels only inside the local network, between devices where you entered the PIN or that you chose to trust. It does not go through the internet or the developer. The local network uses plain HTTP (not encrypted), so use it only on networks you trust.
- When "Network discovery" is on, the app uses the system mDNS service to announce and look for device names on the local network.
- So that you can manage it, the watch keeps a local list of trusted devices (name, connection time) and today's connection log; you can clear them in the app at any time.

**Update check (GitHub edition only)**
- The edition downloaded from GitHub has "Check for updates". Automatic checking is off by default; turn it on in Settings or tap "Check for updates". The app connects only to GitHub (`api.github.com` and GitHub's download addresses), reads the public release version and notes of this project, and downloads the update file only when you choose "Download and install". Requests carry only the app name - no records, settings or personal data. The "What's new" page also reads this project's public changelog from GitHub.
- The edition installed from Google Play has no such feature. Google Play handles updates, and "What's new" uses a built-in file without any network access.

**Permissions used**: vibration (release and reminder buzzes), wake lock (only when you turn it on, to keep detecting with the screen off), internet and Wi-Fi state / multicast (local-network sharing, import and discovery; the GitHub edition also uses internet for the update check), install packages (GitHub edition only: hands the downloaded update to the system installer, which still asks you to confirm). The sensors are built into the watch and need no extra permission. The app does not access your location, contacts, photos, microphone or camera.

If you copy records off the device (for example with adb), that copy is yours to manage.

Questions: open an Issue at https://github.com/pl168959903/ArcheryCounter
