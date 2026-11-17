import java.util.Properties

plugins {
    id("com.android.application")
}

// 签名信息只放在本机 local.properties / 环境变量里，不进仓库
val signProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signProp(key: String, def: String = ""): String =
    (signProps.getProperty(key) ?: System.getenv(key) ?: def)

val ksPath = signProp("RELEASE_STORE_FILE")
val ksFile = if (ksPath.isEmpty()) null else rootProject.file(ksPath)
val hasKey = ksFile != null && ksFile.exists()

android {
    namespace = "com.sugardeicing.localserver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sugardeicing.localserver"
        minSdk = 34
        targetSdk = 35
        versionCode = 11
        versionName = "1.1.1"
    }

    signingConfigs {
        create("release") {
            if (hasKey) {
                storeFile = ksFile
                storePassword = signProp("RELEASE_STORE_PASSWORD")
                keyAlias = signProp("RELEASE_KEY_ALIAS", "localhttp")
                keyPassword = signProp("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.1.0")
}
