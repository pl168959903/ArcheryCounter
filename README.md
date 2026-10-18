# 射箭计数 (ArcheryCounter) — Wear OS

戴在手腕上，自动侦测每次放箭并计数。侦测到时手表会震动一下，也可以用 − / + 手动修正。

## 运行方式

1. 安装最新版 Android Studio，用 **Open** 打开本文件夹 `ArcheryCounter`，等 Gradle 同步完成（需要网络）。
2. 连接手表（Wear OS 3 / API 30 以上）：
   - 手表：设定 → 系统 → 关于 → 版本，连点「版本号」7 次开启开发者选项，然后在「开发者选项」里打开 ADB 调试和无线调试。
   - Android Studio：Device Manager → Pair Devices Using Wi-Fi，按提示配对。
3. 选择手表为运行目标，按 Run。

## 不装 Android Studio：用 GitHub 云端编译 APK

1. 注册并登录 GitHub，新建一个仓库（Private 即可）。
2. 把本文件夹里的所有文件（包括隐藏的 `.github` 文件夹）上传到仓库。
3. 打开仓库的 **Actions** 页面，等 `Build APK` 跑完（约 3～5 分钟，绿色勾表示成功）。
4. 点进这次运行，在页面底部 **Artifacts** 下载 `ArcheryCounter-debug-apk`，解压得到 `app-debug.apk`。
5. 把 APK 装到手表：电脑下载 Google 的 platform-tools（只有 adb，很小），手表开启「无线调试」后执行：
   ```
   adb pair <手表显示的配对地址:端口>
   adb connect <手表显示的 IP:端口>
   adb install app-debug.apk
   ```

## 使用与调整

- 点「开始侦测」后屏幕会保持亮起；放箭时计数 +1 并震动。
- 屏幕上的「上次峰值」是最近一次放箭的冲击强度（m/s²）。
- **阈值**决定多大的冲击算放箭，数值越大越不灵敏。先射几箭看峰值，再把阈值设在典型峰值的一半到七成左右。
  - 漏计 → 调低阈值；误计（比如拉弓、取箭时被算进去）→ 调高阈值。
- 「清零」只在暂停侦测时出现，避免射箭时误触。

## 代码结构

| 文件 | 作用 |
| --- | --- |
| `ShotDetector.kt` | 放箭判定：冲击 + 之前手腕稳定 + 3 秒冷却。纯 Kotlin，有单元测试 |
| `CounterViewModel.kt` | 读取传感器、保存计数和阈值、震动 |
| `MainActivity.kt` | Compose for Wear OS 界面 |

判定参数（稳定阈值、时间窗口、冷却时间）都在 `ShotDetector.kt` 的 `companion object` 里。

## 已知限制

- 目前只在屏幕亮着时可靠工作。想让它熄屏后也持续计数，需要加前台服务和 Ongoing Activity。
- 默认阈值 15 只是估计值，必须用你自己的弓和手腕实测调整。
