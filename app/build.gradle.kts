plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.cubetimer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cubetimer"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    buildTypes {
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

// Giải mã icon launcher từ icon.json (base64) thành PNG trước khi build.
// (File nhị phân không đẩy qua GitHub connector được nên phải nhúng base64.)
// Đường dẫn được tính lúc cấu hình, trong doLast không chạm tới project,
// để tương thích configuration cache.
val iconJsonFile = rootProject.file("icon.json")
val iconOutDir = file("src/main/res/mipmap-xxxhdpi")
tasks.register("generateLauncherIcon") {
    doLast {
        val text = iconJsonFile.readText()
        val b64 = Regex("\"icon\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
            ?: error("icon.json khong co truong icon")
        iconOutDir.mkdirs()
        java.nio.file.Files.write(
            iconOutDir.resolve("ic_launcher.png").toPath(),
            java.util.Base64.getDecoder().decode(b64)
        )
    }
}
afterEvaluate {
    tasks.named("preBuild") { dependsOn("generateLauncherIcon") }
}
