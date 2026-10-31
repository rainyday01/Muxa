import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// LogHub upload token for official builds. Kept out of the public source:
// set `loghub.uploadToken=` in local.properties (gitignored) or the
// MUXA_LOGHUB_UPLOAD_TOKEN env var. Without it the app offers GitHub issues only.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val logHubUploadToken: String =
    (System.getenv("MUXA_LOGHUB_UPLOAD_TOKEN") ?: localProps.getProperty("loghub.uploadToken") ?: "").trim()

android {
    namespace = "app.mtmux.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.mtmux.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 128
        versionName = "0.3.18"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "LOGHUB_UPLOAD_TOKEN", "\"$logHubUploadToken\"")
    }

    // Shared debug keystore (same as historical dist/*.apk / elli machine) so
    // updates install over existing m-tmux without "signature mismatch".
    // File is gitignored: android/keystore/mtmux-debug.keystore
    val sharedDebugKs = rootProject.file("keystore/mtmux-debug.keystore")
    signingConfigs {
        getByName("debug") {
            if (sharedDebugKs.isFile) {
                storeFile = sharedDebugKs
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            // Keep same key as debug for sideload continuity until a release key exists.
            if (sharedDebugKs.isFile) {
                signingConfig = signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.md",
                "META-INF/NOTICE.txt",
            )
            pickFirsts += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":ssh"))
    implementation(project(":termux-emulator"))
    implementation(project(":termux-view"))
    implementation(project(":autocomplete"))
    implementation(project(":desktop-push-api"))
    implementation(project(":command-palette-api"))
    implementation(project(":feature:sessions"))
    implementation(project(":feature:hosts"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:terminal"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    val bom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
