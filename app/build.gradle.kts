import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.marekmaro.equinox"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.marekmaro.equinox"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.8.0"
    }

    // Klucz do podpisu wersji sklepowej podajemy przez zmienne środowiskowe
    // (lokalnie albo jako sekrety GitHub) – nigdy nie trafia do repozytorium.
    val keystorePath: String? = System.getenv("EQUINOX_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("upload") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("EQUINOX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("EQUINOX_KEY_ALIAS")
                keyPassword = System.getenv("EQUINOX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: mniejsza i szybsza aplikacja
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // bez klucza (np. test w CI) podpisujemy kluczem testowym, żeby dało się zainstalować
            signingConfig = if (keystorePath != null) signingConfigs.getByName("upload")
            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
        xmlReport = true
        htmlReport = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    // radio w formacie HLS (.m3u8)
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")

    // wykrywanie zgięcia telefonów składanych
    implementation("androidx.window:window:1.5.1")

    testImplementation("junit:junit:4.13.2")
}
