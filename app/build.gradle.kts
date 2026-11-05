plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Reads a build setting from -P<prop>, then the environment, then a fallback.
fun buildSetting(prop: String, env: String, fallback: String): String =
    (project.findProperty(prop) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: fallback

android {
    namespace = "com.githunt.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.githunt.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0-alpha1"

        // Where the GitHunt backend (the Next.js app on Vercel) lives.
        val apiBaseUrl = buildSetting("apiBaseUrl", "GITHUNT_API_BASE_URL", "https://githunt-aadin.vercel.app/")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")

        // The *web* OAuth client id from Google Cloud (same value as the
        // GOOGLE_CLIENT_ID env var on Vercel). Empty = Google button explains
        // that it is not configured yet; password sign-in still works.
        val googleWebClientId = buildSetting("googleWebClientId", "GITHUNT_GOOGLE_WEB_CLIENT_ID", "192191799004-bmumsvcd1c0l2tehfgfmhsg4bn1hkkf3.apps.googleusercontent.com")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    // A fixed debug key. Google sign-in only works when the app's signing
    // SHA-1 matches the one registered in Google Cloud, and CI runners would
    // otherwise generate a brand new debug key on every build.
    signingConfigs {
        getByName("debug") {
            storeFile = file("githunt-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
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
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    lint {
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose (the BOM keeps every compose artifact on matching versions)
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // Native Google sign-in (Credential Manager -> Google ID token)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Images (feed avatars)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
