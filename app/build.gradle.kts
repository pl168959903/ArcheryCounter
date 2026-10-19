plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.archerycounter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.archerycounter"
        minSdk = 30
        targetSdk = 34
        versionCode = 5
        versionName = "1.4"
    }

    buildTypes {
        release {
            // 開啟速度主因：debug 版沒有最佳化。release 版用 R8 縮減，
            // 並自動帶入 Compose 內建的 baseline profile（預先編譯常用程式碼）。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // 個人使用：直接用 debug 金鑰簽名，這樣 release 版也能直接裝到手錶
            signingConfig = signingConfigs.getByName("debug")
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

    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
