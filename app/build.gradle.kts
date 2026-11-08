import java.net.URL

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

// The app fonts are the website's: Plus Jakarta Sans for text (res/font/plus_jakarta_sans.ttf)
// and Inter for the small labels, times, topic chips and pills (res/font/inter.ttf), which is
// what the site's `font-mono` class actually renders. Both are variable fonts from the Google
// Fonts repo, downloaded once, the first time the project builds. If a download fails the build
// carries on and the app uses the system font for that face.
fun registerFontFetch(taskName: String, resName: String, url: String) = tasks.register(taskName) {
    val target = layout.projectDirectory.file("src/main/res/font/$resName.ttf").asFile
    val scratch = File(temporaryDir, "$resName.part")
    onlyIf { !target.exists() }
    doLast {
        try {
            target.parentFile.mkdirs()
            URL(url)
                .openStream()
                .use { input -> scratch.outputStream().use { out -> input.copyTo(out) } }
            if (scratch.length() > 50_000) {
                scratch.copyTo(target, overwrite = true)
            }
        } catch (e: Exception) {
            logger.warn("Font $resName not downloaded (${e.message}). The app will use the system font.")
        } finally {
            scratch.delete()
        }
    }
}

val fetchPlusJakartaSans = registerFontFetch(
    "fetchPlusJakartaSans",
    "plus_jakarta_sans",
    "https://github.com/google/fonts/raw/main/ofl/plusjakartasans/PlusJakartaSans%5Bwght%5D.ttf",
)
val fetchInter = registerFontFetch(
    "fetchInter",
    "inter",
    "https://github.com/google/fonts/raw/main/ofl/inter/Inter%5Bopsz%2Cwght%5D.ttf",
)
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchPlusJakartaSans, fetchInter) }

android {
    namespace = "com.githunt.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.githunt.android"
        minSdk = 26
        targetSdk = 34
        // CI passes its run number (-PappVersionCode=N) so each published build upgrades the last.
        // Local builds fall back to 2.
        versionCode = buildSetting("appVersionCode", "GITHUNT_VERSION_CODE", "2").toInt()
        // Shown on the Info screen. CI builds append their build number so you can tell
        // which build is installed. Local builds show just the base version.
        val ciBuild = buildSetting("appVersionCode", "GITHUNT_VERSION_CODE", "")
        versionName = if (ciBuild.isNotBlank()) "2.0.0-alpha1 (build $ciBuild)" else "2.0.0-alpha1"

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
    implementation("io.coil-kt:coil-svg:2.7.0")

    // README markdown, rendered inside the repo card (headings, tables, task lists, inline HTML)
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:image:4.6.2")
    implementation("androidx.compose.animation:animation")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Background check for new notifications (runs about every 15 minutes)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
}
