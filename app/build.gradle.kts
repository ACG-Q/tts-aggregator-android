plugins {
    id("com.android.application") version "8.5.0"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

val appVersionName = "1.0.3"

fun versionCodeOf(version: String): Int {
    val parts = version.split(".")
    require(parts.size == 3) { "appVersionName 必须是 major.minor.patch 格式，例如 1.0.0" }
    val (major, minor, patch) = parts.map { it.toInt() }
    require(minor in 0..99 && patch in 0..99) { "minor 与 patch 必须小于 100" }
    return major * 10_000 + minor * 100 + patch
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
    }

    buildFeatures {
        compose = true
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
}
