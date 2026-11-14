plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "app.hush"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.hush"
        minSdk = 26 // NewPipeExtractor needs Java 8 time APIs; API 26+ has them natively
        targetSdk = 34
        versionCode = 9
        versionName = "1.0.9"
    }

    // Release signing is read from the environment (used by the GitHub release workflow).
    // Without it, release builds fall back to the local debug key so anyone can build.
    val releaseKeystore: String? = System.getenv("HUSH_KEYSTORE_FILE")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("HUSH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HUSH_KEY_ALIAS")
                keyPassword = System.getenv("HUSH_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(
                if (releaseKeystore != null) "release" else "debug"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST"
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.core:core-ktx:1.13.1")

    // Playback
    val media3 = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-datasource:$media3")

    // Local storage (liked songs + playlists), never leaves the device
    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Networking + images
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Colour wash on the player, picked from the album art
    implementation("androidx.palette:palette-ktx:1.0.0")

    // YouTube / YouTube Music data, no Google APIs, no account
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
}
