plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.cubetimer"
    compileSdk = 35

    // Keystore ký debug CỐ ĐỊNH: trước đây mỗi lần build trên GitHub Actions
    // tự sinh key mới nên Android không cho cài đè (lỗi INSTALL_FAILED_UPDATE_INCOMPATIBLE).
    // Keystore lưu dạng base64 trong repo (debug.keystore.b64) vì file nhị phân
    // không đẩy qua connector được; task dưới giải mã ra debug.keystore trước khi build.
    // Đây là key DEBUG (alias androiddebugkey, mật khẩu android), hạn tới 2054.
    val decodeDebugKeystore by tasks.registering {
        doLast {
            val b64File = layout.projectDirectory.file("../debug.keystore.b64").asFile
            val outFile = layout.projectDirectory.file("../debug.keystore").asFile
            if (!outFile.exists()) {
                outFile.writeBytes(java.util.Base64.getMimeDecoder().decode(b64File.readText()))
            }
        }
    }
    tasks.named("preBuild") { dependsOn(decodeDebugKeystore) }

    signingConfigs {
        create("debug") {
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeType = "PKCS12"
        }
    }

    defaultConfig {
        applicationId = "com.example.cubetimer"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "3.0"
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
