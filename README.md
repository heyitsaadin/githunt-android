<div align="center">

<img src="app/src/main/res/drawable/githunt_logo.png" alt="GitHunt logo" width="96" />

# GitHunt

**Discover trending GitHub repos on Android.**
Native app built with Kotlin and Jetpack Compose.

[![Release](https://img.shields.io/github/v/release/heyitsaadin/githunt-android?label=release&color=brightgreen)](https://github.com/heyitsaadin/githunt-android/releases/latest)
[![Android CI](https://img.shields.io/github/actions/workflow/status/heyitsaadin/githunt-android/android-ci.yml?branch=main&label=build)](https://github.com/heyitsaadin/githunt-android/actions/workflows/android-ci.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)

[![Stars](https://img.shields.io/github/stars/heyitsaadin/githunt-android?style=flat&logo=github&label=stars&color=yellow)](https://github.com/heyitsaadin/githunt-android/stargazers)
[![Forks](https://img.shields.io/github/forks/heyitsaadin/githunt-android?style=flat&logo=github&label=forks)](https://github.com/heyitsaadin/githunt-android/network/members)
[![Watchers](https://img.shields.io/github/watchers/heyitsaadin/githunt-android?style=flat&logo=github&label=watchers&color=blue)](https://github.com/heyitsaadin/githunt-android/watchers)
[![Issues](https://img.shields.io/github/issues/heyitsaadin/githunt-android?style=flat&label=issues)](https://github.com/heyitsaadin/githunt-android/issues)
[![Last commit](https://img.shields.io/github/last-commit/heyitsaadin/githunt-android?label=last%20commit)](https://github.com/heyitsaadin/githunt-android/commits/main)
[![Downloads](https://img.shields.io/github/downloads/heyitsaadin/githunt-android/total?label=downloads&color=green)](https://github.com/heyitsaadin/githunt-android/releases)

[Website](https://githunt-aadin.vercel.app) ·
[Releases](https://github.com/heyitsaadin/githunt-android/releases) ·
[Issues](https://github.com/heyitsaadin/githunt-android/issues) ·
[Contributing](#contributing)

</div>

---

## About

GitHunt is a feed of GitHub repositories that learns what you like. This is the native Android app. It talks to the same Next.js API and Neon database as the [GitHunt website](https://githunt-aadin.vercel.app), so your account, clips and feed are identical on both.

Browse trending and long-tail repos, read their READMEs without leaving the app, ask AI questions about any repo, and get notified when something starts trending.

## Contents

1. [Features](#features)
2. [Screenshots](#screenshots)
3. [Download](#download)
4. [Tech stack](#tech-stack)
5. [Repository layout](#repository-layout)
6. [Building locally](#building-locally)
7. [Configuration](#configuration)
8. [CI and releases](#ci-and-releases)
9. [How auth works](#how-auth-works)
10. [Contributing](#contributing)

## Features

**Feed and discovery**
- Infinite-scroll **Home feed** from `GET /api/feed/default`, trending first, then long-tail.
- **Discover** tab for exploring beyond your personal feed.
- **Repo cards** with language and topic chips, star and fork counts, share, clip and "not interested".
- Full **README viewer** inside the card (headings, tables, task lists, inline HTML and images via Markwon).
- **Clipped repos** page to save repos for later.

**AI**
- **Ask AI** chat on every repo, backed by the `/api/ai-chat` route.
- **AI search** in the feed.

**Personalization**
- Every like, clip, expand and dismiss is sent to `POST /api/engagement`, so the interest model that powers your feed keeps learning on mobile too.
- **Personalization** settings to see what GitHunt learns from your usage.

**Notifications**
- Background check about every 15 minutes (WorkManager) for repos that start trending.
- In-app **notification inbox** covering the last 24 hours. Tapping a system notification opens it.

**Account**
- Email and password login or signup, plus native **Google sign-in** (Credential Manager).
- Persistent session, email verification resend, edit profile, change password, security and delete account.
- **Bring your own GitHub API token** to avoid rate limits.

**App polish**
- Light, dark or system theme.
- Translation language setting.
- Searchable settings, in-app guides (What is this, How to use) and an Info screen with the build number.
- Animated page transitions, shimmer loading skeletons, and a top bar and dock that hide while scrolling.

## Screenshots

> Add screenshots to `docs/screenshots/` and link them here.

<!--
<p align="center">
  <img src="docs/screenshots/home.png" width="23%" />
  <img src="docs/screenshots/discover.png" width="23%" />
  <img src="docs/screenshots/repo-readme.png" width="23%" />
  <img src="docs/screenshots/settings.png" width="23%" />
</p>
-->

## Download

Every push to `main` that passes CI publishes a new release containing a debug APK.

- **Obtainium (recommended):** add `https://github.com/heyitsaadin/githunt-android` as an app and it will update itself on every new build.
- **Manual:** grab `githunt-debug.apk` from the [latest release](https://github.com/heyitsaadin/githunt-android/releases/latest) and install it. You may need to allow "Install unknown apps" for your browser or file manager.

Requires **Android 8.0 (API 26)** or newer.

## Tech stack

| Area | Library |
| --- | --- |
| Language | Kotlin 1.9, Java 17 |
| UI | Jetpack Compose, Material 3, Compose BOM `2024.09.00` |
| Networking | Retrofit 2.11, OkHttp 4.12, kotlinx.serialization |
| Async | Kotlin Coroutines and Flow |
| Images | Coil 2.7 (with SVG support) |
| Markdown | Markwon 4.6 (tables, tasklists, strikethrough, HTML, images) |
| Auth | Credential Manager and Google ID, cookie session via OkHttp `CookieJar` |
| Background | WorkManager 2.9 |
| Fonts | Plus Jakarta Sans and Inter, downloaded on first build |

`compileSdk` 34 · `targetSdk` 34 · `minSdk` 26

## Repository layout

```
app/src/main/java/com/githunt/android/
├── data/
│   ├── ai/           Ask AI repository
│   ├── auth/         Login, signup, Google sign-in
│   ├── clips/        Clipped repos
│   ├── discover/     Discover API + repository
│   ├── engagement/   Engagement events for the interest model
│   ├── feed/         Feed API, models, repository
│   ├── net/          ApiResult, persistent session cookie jar
│   ├── profile/      Profile API, models, images
│   ├── push/         Device push + notification worker
│   ├── readme/       README fetching
│   └── settings/     Preferences, settings API
├── di/               AppContainer (manual dependency injection)
└── ui/
    ├── auth/         Auth screen, validation, animated background
    ├── clips/        Clipped repos screen
    ├── components/   RepoCard, README view, Ask AI, icons, shimmer, nav
    ├── discover/     Discover screen + view model
    ├── feed/         Home feed, AI search
    ├── home/         Home screen
    ├── profile/      Profile screen + view model
    ├── settings/     Settings host and all sub-pages
    └── theme/        Colors, typography, theme
```

## Building locally

1. Install **Android Studio** (Koala or newer) and JDK 17.
2. Clone and open the project:
   ```bash
   git clone https://github.com/heyitsaadin/githunt-android.git
   ```
3. Let Gradle sync. Fonts are downloaded automatically on the first build.
4. Run on a device or emulator, or from the terminal:
   ```bash
   ./gradlew assembleDebug
   ```
   The APK lands in `app/build/outputs/apk/debug/`.

Other useful tasks:

```bash
./gradlew lint
./gradlew testDebugUnitTest
```

Testing against a local `next dev` server? From an emulator use `http://10.0.2.2:3000/` as the API base URL. It is already allowed for cleartext in `network_security_config.xml`.

## Configuration

Build settings are read from a Gradle property (`-P`), then an environment variable, then a default.

| Setting | Gradle property | Env var | Purpose |
| --- | --- | --- | --- |
| API base URL | `apiBaseUrl` | `GITHUNT_API_BASE_URL` | Where the GitHunt backend lives |
| Google web client ID | `googleWebClientId` | `GITHUNT_GOOGLE_WEB_CLIENT_ID` | Enables Google sign-in |
| Version code | `appVersionCode` | `GITHUNT_VERSION_CODE` | Set by CI to the run number |

Example:

```bash
./gradlew assembleDebug -PapiBaseUrl=https://your-domain.com/
```

Google sign-in only works when the app's signing SHA-1 is registered in Google Cloud. The repo ships a fixed debug keystore (`app/githunt-debug.keystore`) so CI builds always match.

## CI and releases

Workflow: [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml)

1. **build**: lint, assemble debug APK, run unit tests, upload the APK as an artifact.
2. **release** (pushes to `main` only): publishes a GitHub Release tagged `build-<run number>` with `githunt-debug.apk` and optionally pings Discord.

Pull requests run the build job only and never get write access.

## How auth works

The website keeps you signed in with an `httpOnly` session cookie. Instead of inventing a separate token system, the app stores and re-sends that same cookie using a persistent OkHttp `CookieJar` (`data/net/SessionCookieJar.kt`). It behaves like a browser that stays logged in, even across app restarts, and needs no backend changes.

## Contributing

Issues and pull requests are welcome.

1. Fork the repo and create a branch from `main`.
2. Make your change and run `./gradlew lint testDebugUnitTest`.
3. Open a pull request describing what and why.

If you like the project, a ⭐ and a 👀 **watch** on the repo go a long way.

---

<div align="center">

Made by [@heyitsaadin](https://github.com/heyitsaadin)

</div>
