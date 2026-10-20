plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zgrcc.archeryassistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zgrcc.archeryassistant"
        minSdk = 30
        targetSdk = 35
        versionCode = 19
        versionName = "1.13"
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
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
