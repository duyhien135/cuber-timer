import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Keystore ký debug CỐ ĐỊNH: trước đây mỗi lần build trên GitHub Actions
// tự sinh key mới nên Android không cho cài đè (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
// Keystore lưu dạng base64 trong repo (debug.keystore.b64) vì file nhị phân
// không đẩy qua connector được; giải mã ra debug.keystore ngay lúc cấu hình
// (trước mọi task, để AGP đọc được khi ký). Đây là key DEBUG
// (alias androiddebugkey, mật khẩu android), hạn tới 2054.
// Lưu ý (build-26 đã fail vì vậy): KHÔNG đặt code này trong khối android {}
// vì `java.util.Base64` không phân giải được ở đó.
val debugKeystoreFile = layout.projectDirectory.file("../debug.keystore").asFile
if (!debugKeystoreFile.exists()) {
    val b64 = layout.projectDirectory.file("../debug.keystore.b64").asFile.readText()
    debugKeystoreFile.writeBytes(Base64.getMimeDecoder().decode(b64))
}

android {
    namespace = "com.example.cubetimer"
    compileSdk = 35

    signingConfigs {
        // getByName: config debug đã có sẵn do AGP tạo, chỉ sửa lại key;
        // dùng create("debug") sẽ lỗi trùng tên.
        getByName("debug") {
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
        versionCode = 21
        versionName = "3.1"
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
