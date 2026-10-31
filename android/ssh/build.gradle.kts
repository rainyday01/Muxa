plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.mtmux.ssh"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:model"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // AND-020: pure-Java SSH; can swap to libssh2 NDK later
    // 0.38.0: SessionChannel.notifyError NPE when transport dies before
    // stderr stream is assigned (field log 0.2.9). 0.40.0 null-checks it.
    implementation("com.hierynomus:sshj:0.40.0")
    // Full BC required on Android (system "BC" lacks X25519 / curve25519 KEX)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("net.i2p.crypto:eddsa:0.3.0")
    testImplementation("junit:junit:4.13.2")
}
