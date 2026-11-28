plugins {
    id("com.android.application") version "8.5.0"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

val appVersionName = "1.1.0-beta.5"

/** 默认主题：-PpulseDefaultTheme=ink 打默认墨水屏的包（只影响首次安装） */
val defaultInkTheme = providers.gradleProperty("pulseDefaultTheme")
    .map { it.equals("ink", ignoreCase = true) }
    .orElse(false)
    .get()

fun versionCodeOf(version: String): Int {
    val separator = version.indexOf('-')
    val base = if (separator >= 0) version.substring(0, separator) else version
    val prerelease = if (separator >= 0) version.substring(separator + 1) else null

    val parts = base.split(".")
    require(parts.size == 3) { "appVersionName 必须是 major.minor.patch 或 major.minor.patch-beta.N 格式，例如 1.0.0" }
    val (major, minor, patch) = parts.map { it.toInt() }
    require(minor in 0..99 && patch in 0..99) { "minor 与 patch 必须小于 100" }

    // 同一版本的预发布号必须小于正式版：base*10 + (1..8)，正式版固定为 base*10 + 9
    val slot = if (prerelease == null) {
        9
    } else {
        val segments = prerelease.split(".")
        require(segments.size == 2 && segments[0] in setOf("beta", "rc")) {
            "预发布号必须是 beta.N 或 rc.N，N 取 1..8"
        }
        val index = segments[1].toIntOrNull()
        require(index != null && index in 1..8) { "预发布序号必须取 1..8" }
        index
    }
    return (major * 10_000 + minor * 100 + patch) * 10 + slot
}

android {
    namespace = "com.pulse.tts"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pulse.tts"
        minSdk = 26
        targetSdk = 34
        versionCode = versionCodeOf(appVersionName)
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "DEFAULT_INK_THEME", defaultInkTheme.toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            // 开启代码与资源裁剪：icons-extended 这类整包依赖只有裁剪后才会瘦身
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += "META-INF/*"
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Compose（BOM 统一版本）
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    // 相机 + 二维码识别（bundled 模型，不依赖 GMS）
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // 网络
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 协程 + 序列化
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    // 调试工具
    debugImplementation("androidx.compose.ui:ui-tooling")
    // 单元测试
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // 系统 TTS 真机集成测试（仅测试期依赖，不进入 APK）
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
