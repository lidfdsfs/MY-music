import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
}

// 本机配置（local.properties 不进仓库）：高德 Key 等敏感值由此注入 BuildConfig
val localProps: Properties = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
}
val amapKey: String = localProps.getProperty("AMAP_KEY").orEmpty()

// 云端模型全部由用户自行接入，不在安装包内注入任何内置 AI Key。

android {
    namespace = "com.example.my"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // 独立音乐 App 的包名，可与主 App 同时安装
        applicationId = "com.example.my.music"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        // 仓库不内置高德 Key：本机 local.properties 写 AMAP_KEY=xxx 才会注入，
        // 为空时 App 仍可在设置页填自己的 Key（见 AmapApi.KEY）
        buildConfigField("String", "AMAP_KEY", "\"$amapKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 只保留 arm64-v8a：2017 年后的安卓设备几乎全是 64 位，v7a 包纯属体积浪费。
        // 砍掉 v7a 可省约 55MB native 库（translate/vosk/mlkit 各少一份）。
        // 若确实要支持极老的 32 位设备，把 "armeabi-v7a" 加回来即可。
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    packaging {
        jniLibs {
            // libc++_shared.so 在 jniLibs 与其它原生依赖里可能各有一份，保留一份即可
            pickFirsts += listOf("**/libc++_shared.so")
        }
    }

    signingConfigs {
        // 用本机 debug keystore 给 release 签名，保证可直接 install（非上架用途）
        create("localRelease") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("localRelease")
            // 关闭 R8 收缩/混淆：本项目依赖 MNN(libllm.so)、Vosk、MLKit、osmdroid 等
            // native 库，Java 层方法名被重命名后 JNI 找不到符号，表现为安装后一打开就闪退。
            // 本 App 不走应用商店，用体积换稳定性。
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // 用于把 local.properties 里的 AI_BUILTIN_KEY 注入 BuildConfig
        buildConfig = true
    }
    lint {
        // release 包的 lintVital 会去拉 desktop 变体依赖，离线环境解析不到，跳过
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // OSMDroid 地图（OpenStreetMap，免 Key；Maven Central 分发）
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    // 购票专区的 MCP 响应解析：只做 JSON 树解析，不反射、不反序列化任意类型
    // 用 2.10.1：离线缓存完整，且不引入当前环境缺失的 error_prone_annotations
    implementation("com.google.code.gson:gson:2.10.1")
    // 超级加密通信：Ed25519 身份签名、X25519 协商、BLAKE3 安全码、Argon2 预留。
    // 只用轻量 API（org.bouncycastle.crypto），不走 JCA Provider，避免与 Android 自带 BC 冲突。
    implementation("org.bouncycastle:bcprov-jdk18on:1.80.2")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.lottie.compose)
    // 视频压缩：Media3 Transformer（Google 官方，Apache-2.0）。纯 Java 实现、不带 ffmpeg 原生库，
    // 依赖系统 MediaCodec 硬编硬解；media3-exoplayer/muxer/container 由它传递引入。
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    // 新版足迹拍照入口：CameraX 取景/拍照/录像（系统相机仍保留在旧版入口）
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.camerax.video)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.workmanager)
    implementation(libs.mlkit.labeling)
    // 视觉：OCR（中英）、人脸、条码；翻译/语言识别均为端侧模型（首次使用时下载模型包）
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.face.detection)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    // MediaStyle 通知：大卡片封面+进度条+控制按钮
    implementation("androidx.media:media:1.7.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Vosk 离线中文语音识别（AAR 自带 native libs，模型按需下载）
    implementation("com.alphacephei:vosk-android:0.3.47")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}