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
    compileSdk = 36

    defaultConfig {
        applicationId = "app.mtmux.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 129
        versionName = "0.3.19"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "LOGHUB_UPLOAD_TOKEN", "\"$logHubUploadToken\"")
    }

    // Shared debug keystore (same as historical dist/*.apk / elli machine) so
    // updates install over existing m-tmux without "signature mismatch".
    // File is gitignored: android/keystore/mtmux-debug.keystore
    val sharedDebugKs = rootProject.file("keystore/mtmux-debug.keystore")
    // Store upload key for Google Play / Galaxy Store builds, from local.properties
    // (release.storeFile / release.storePassword / release.keyAlias / release.keyPassword).
    val releaseKs = localProps.getProperty("release.storeFile")?.let { rootProject.file(it) }?.takeIf { it.isFile }
    signingConfigs {
        getByName("debug") {
            if (sharedDebugKs.isFile) {
                storeFile = sharedDebugKs
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (releaseKs != null) {
            create("release") {
                storeFile = releaseKs
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            // Stores reject debug-signed uploads: use the release key when configured,
            // else the shared debug key for sideload continuity.
            signingConfig = when {
                releaseKs != null -> signingConfigs.getByName("release")
                sharedDebugKs.isFile -> signingConfigs.getByName("debug")
                else -> null
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
