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
tasks.register("generateLauncherIcon") {
    doLast {
        val jsonFile = rootProject.file("icon.json")
        val parsed = groovy.json.JsonSlurper().parseText(jsonFile.readText()) as Map<*, *>
        val bytes = java.util.Base64.getDecoder().decode(parsed["icon"] as String)
        val outDir = project.file("src/main/res/mipmap-xxxhdpi")
        outDir.mkdirs()
        java.nio.file.Files.write(outDir.resolve("ic_launcher.png").toPath(), bytes)
    }
}
tasks.named("preBuild") { dependsOn("generateLauncherIcon") }
