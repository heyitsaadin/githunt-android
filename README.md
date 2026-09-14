# GitHunt — Android (Kotlin / Jetpack Compose)

A native Android rebuild of the GitHunt web app's user-facing features, talking to
your **existing** Next.js API and Neon database — same accounts, same posts, same
feed. No separate backend, no admin dashboard (by design; see scope below).

## What's included (v1)

- **Auth**: login, signup, session persistence (native equivalent of the web
  app's `pb_session` httpOnly cookie — see "How auth works" below), logout,
  delete account.
- **Google sign-in & GitHub connect**: in-app WebView OAuth flow (see "How
  OAuth works" below) — no external browser hop.
- **Discover feed**: infinite-scroll repo feed via `GET /api/feed/default`,
  matching the web app's trending → long-tail pagination exactly.
- **Repo cards**: language/topic chips, star/fork counts, README expand,
  Ask AI, share, clip (local), "not interested" — each firing the same
  `POST /api/engagement` events the web client does, so your existing
  per-account interest-scoring model keeps working unchanged for native
  users too.
- **Ask AI**: per-repo chat backed by your `/api/ai-chat` (Groq) route.
- **Compose**: new posts with text, image (base64, matching the web app's
  `FileReader.readAsDataURL` exactly), file metadata, link, and repo
  attachments (via `GET /api/github/repo`).
- **Profile & Settings**: view profile, connect GitHub, log out, delete
  account.

## Not yet included

- Auto-translation of feed cards (`/api/translate` model exists, unused).
- Personalization settings screen, feature-request submission, admin
  dashboard (out of scope per your earlier answer).
- Replies/comments UI (models + repository methods exist; no screen yet).

None of these need backend changes — the API already supports all of it.

## How auth works here

The web app signs a session into an `httpOnly` cookie (`pb_session`, see
`lib/auth.js`). Rather than reimplementing auth as bearer tokens (which
would require backend changes), this app uses a `CookieJar`
(`data/api/PersistentCookieJar.kt`) so OkHttp stores and resends that same
cookie automatically — functionally identical to how a browser stays logged
in, persisted across app restarts via `SharedPreferences`.

## How OAuth works here

Google sign-in and GitHub connect are both server-side redirect flows
(`GET /api/auth/google`, `GET /api/auth/github`) that finish by setting the
same httpOnly session cookie and redirecting to your site's root. A Custom
Tab's cookies live in the system browser process and are invisible to the
app, so this project runs the flow in an in-app `WebView` instead
(`ui/auth/OAuthWebViewScreen.kt`). Once the WebView's `onPageFinished`
callback detects the redirect has landed back on your own domain's root
(success) or `/login?error=...` (failure), it reads the cookie out of
`android.webkit.CookieManager` and copies it into the app's own
`PersistentCookieJar` — after that, every normal Retrofit call is
authenticated exactly as if the user had logged in with a password.

This means Google/GitHub sign-in needs **zero backend changes** — it reuses
your existing OAuth routes as-is.

## Project layout

```
android/
  app/src/main/java/com/githunt/android/
    data/api/     Retrofit interface, cookie jar, network client
    data/model/   Kotlin models mirroring the API's JSON exactly
    data/repo/    Repositories wrapping API calls with error handling
    ui/auth/      Login, signup, AuthViewModel
    ui/feed/      Discover screen, RepoCard, Ask AI sheet
    ui/compose/   New-post screen
    ui/profile/   Profile screen
    ui/settings/  Settings screen (logout, delete account)
    ui/nav/       Navigation graph + bottom nav
  .github/workflows/android-build.yml   CI build (see below)
```

## Building locally (Android Studio)

1. Open this `android/` folder as a project in Android Studio (Koala or
   newer recommended).
2. Point it at your deployed API — either:
   - Edit the default in `app/build.gradle.kts` (`apiBaseUrl` fallback), or
   - Pass it at build/run time: **Run → Edit Configurations → add** to
     Gradle args: `-PapiBaseUrl=https://your-domain.com/`
3. Run on an emulator or device. If testing against `next dev` on your own
   machine, use `http://10.0.2.2:3000/` as the base URL from an emulator
   (already whitelisted for cleartext in `network_security_config.xml`) — a
   physical device needs your machine's LAN IP instead, over HTTPS or with
   its own temporary network-security exception.

## Building via GitHub Actions (no local Android Studio needed)

`.github/workflows/android-build.yml` builds both a debug and an unsigned
release APK on every push and on-demand:

1. Push this project to a GitHub repo — either at the repo root (then
   remove the `working-directory: android` lines and the `paths:` filters
   in the workflow) or under an `android/` folder (matches the workflow
   as-is).
2. Actions tab → **Build Android APK** → **Run workflow** — optionally
   override `apiBaseUrl` for that run.
3. Once it finishes, download the `githunt-debug-apk` artifact — that's a
   real installable APK. Sideload it with `adb install app-debug.apk` or
   transfer it to a device and open it (enable "install unknown apps" for
   whatever app you transfer it with).

No Android Studio, SDK, or emulator needed on your end — GitHub's runner
does the actual compiling.

### Signing a release build

The release build in CI is intentionally unsigned (fine for internal
testing/sideloading, not for Play Store distribution). To sign it:

1. Generate a keystore: `keytool -genkey -v -keystore release.keystore -alias githunt -keyalg RSA -keysize 2048 -validity 10000`
2. Base64-encode it and add as a GitHub secret (`RELEASE_KEYSTORE_BASE64`),
   plus secrets for the store password, key alias, and key password.
3. Add a `signingConfigs { release { ... } }` block to
   `app/build.gradle.kts` reading those from environment variables, and a
   step in the workflow to decode the secret back into a keystore file
   before the `assembleRelease` step.

## Known scope decisions carried over from the web app

- Posts are excluded from the Discover feed for now (repos-only) — this
  matches `app/api/feed/default/route.js`'s current documented scope, not
  a limitation introduced here.
- "Not interested" removes a repo from the currently-loaded list
  client-side immediately, same as the web app's optimistic update, ahead
  of the same 21-day/7-day-inactivity expiry rules already enforced
  server-side.
