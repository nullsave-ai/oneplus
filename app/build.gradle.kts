plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.oneplus.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.oneplus.app"
        minSdk = 24 // required by youtubedl-android 0.18.1
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // yt-dlp ships Python + native code per ABI: keep only the phone ABIs (x86 emulators are dropped) to limit APK size
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    // The bundled Python/yt-dlp binaries are executed from the app's native-lib directory, so they must be extracted
    packaging { jniLibs { useLegacyPackaging = true } }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
// No Material dependency on purpose: all components live in ui/system.
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Player: only the three modules actually needed. No media3-ui (controls are drawn in Compose), no session/datasource-okhttp.
    // 1.5.x is the last line that supports minSdk 21 with the current AGP/compileSdk; newer lines need minSdk 23.
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")

    // Extractor for page links (YouTube, X, ok.ru, ...): yt-dlp running on the device. GPL-3.0 licensed (see SECURITY_AUDIT.md).
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
}
