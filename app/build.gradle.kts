plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.githunt.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.githunt.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.13.4" // kept in step with the web app's package.json version

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Base URL of your deployed GitHunt Next.js app. Override at build
        // time with -PapiBaseUrl=https://your-domain.com/ or set the
        // GITHUNT_API_BASE_URL env var (used by the CI workflow).
        val apiBaseUrl = (project.findProperty("apiBaseUrl") as String?)
            ?: System.getenv("GITHUNT_API_BASE_URL")
            ?: "https://githunt-psi.vercel.app/"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
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
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose (BOM keeps all compose artifact versions aligned)
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // WebView OAuth: android.webkit.CookieManager is used for Google/GitHub
    // sign-in instead of Custom Tabs, because the session cookie set by
    // /api/auth/*/callback needs to be readable by the app afterward (a
    // Custom Tab's cookie jar belongs to the system browser, not the app —
    // see ui/auth/OAuthWebViewScreen.kt for the full explanation). No extra
    // Gradle dependency needed; WebView + CookieManager are both in the
    // Android SDK itself.

    // Networking: Retrofit + OkHttp (with a persistent cookie jar for the
    // httpOnly session cookie the API sets) + kotlinx.serialization
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // DataStore (persisted lightweight app prefs, e.g. theme/session hints)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Markdown rendering for READMEs (Ask AI panels, repo cards)
    implementation("com.github.jeziellago:compose-markdown:0.5.4")

    // Paging for infinite-scroll feed
    implementation("androidx.paging:paging-runtime-ktx:3.3.0")
    implementation("androidx.paging:paging-compose:3.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
