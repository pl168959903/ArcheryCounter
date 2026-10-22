plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zgrcc.archeryassistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zgrcc.archeryassistant"
        minSdk = 30
        targetSdk = 36
        versionCode = 50
        versionName = "1.23.3"
    }

    // 固定簽章：GitHub 編譯時由 Secrets 提供金鑰（見 README），這樣每次編譯出來的 APK 簽章都一樣，
    // 就能直接覆蓋安裝、保留手錶上的記錄。沒有提供金鑰時（例如在自己電腦上編譯）退回 debug 金鑰。
    val keystorePath = System.getenv("KEYSTORE_FILE")
    val hasFixedKey = !keystorePath.isNullOrEmpty() && file(keystorePath).exists()
    if (hasFixedKey) {
        signingConfigs {
            create("fixed") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    val appSigning = if (hasFixedKey) signingConfigs.getByName("fixed") else signingConfigs.getByName("debug")

    buildTypes {
        debug {
            signingConfig = appSigning
        }
        release {
            // 開啟速度主因：debug 版沒有最佳化。release 版用 R8 縮減，
            // 並自動帶入 Compose 內建的 baseline profile（預先編譯常用程式碼）。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // 個人使用：有固定金鑰就用固定金鑰，否則用 debug 金鑰，這樣 release 版也能直接裝到手錶
            signingConfig = appSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 發行版本：版本號相同。
    // github：APK，內建檢查更新與下載安裝，套件名稱維持原本的，現有使用者照常更新。
    // playWatch／playPhone（v1.23.2）：兩份 AAB 給 Google Play，同一個套件名稱（加 .play）、兩個外形規格，
    //   沒有自我更新（Play 政策不允許），改版內容改讀內建檔案。
    //   手錶版 watch 功能 required=true；手機版不宣告 watch 功能。Play 要求同一個 App 的版本碼不能重複，
    //   所以手錶版用 版本碼×10、手機版用 版本碼×10+1。
    flavorDimensions += "dist"
    productFlavors {
        create("github") {
            dimension = "dist"
            buildConfigField("boolean", "UPDATES", "true")
        }
        create("playWatch") {
            dimension = "dist"
            // 套件名稱加 .play：Play 版與 GitHub 版是兩個不同的 App，可以同時裝在一支手錶上，資料各自獨立
            applicationIdSuffix = ".play"
            versionCode = 50 * 10
            buildConfigField("boolean", "UPDATES", "false")
        }
        create("playPhone") {
            dimension = "dist"
            applicationIdSuffix = ".play"
            versionCode = 50 * 10 + 1
            buildConfigField("boolean", "UPDATES", "false")
        }
    }
    // 兩個 Play 版共用的資源（App 名稱），Manifest 各自一份；CHANGELOG.md 打包進 App，改版內容頁不用連網
    for (name in listOf("playWatch", "playPhone")) {
        sourceSets.getByName(name).res.srcDir("src/play/res")
        sourceSets.getByName(name).assets.srcDir(layout.buildDirectory.dir("generated/changelogAssets"))
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")

    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.wear.compose:compose-navigation:1.4.0")

    // 磁貼（v1.11）
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("com.google.guava:guava:33.3.1-android")

    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // QR Code 編碼（v1.18，區網分享的網址）
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

// 把 CHANGELOG.md 複製到 Play 版的 assets 來源資料夾
val copyChangelog by tasks.registering(Copy::class) {
    from(rootProject.file("CHANGELOG.md"))
    into(layout.buildDirectory.dir("generated/changelogAssets"))
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(copyChangelog) }
