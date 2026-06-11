import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing config is read from a gitignored keystore.properties (never committed).
// If it's absent (e.g. a fresh clone), the release build is simply left unsigned.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.eeter"
    compileSdk = 34
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.eeter"
        minSdk = 26
        targetSdk = 34
        versionCode = 21
        versionName = "3.0"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Playback + Android Auto
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    // HLS support so .m3u8 stations (e.g. Kaguraadio) can be built into the queue;
    // without it, setting any queue containing an HLS item fails and nothing plays.
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    // Persistence (favorites / last station)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Station logos / artwork
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Pager for the swipeable player + Palette for per-station brand colors
    implementation("androidx.palette:palette-ktx:1.0.0")
}
