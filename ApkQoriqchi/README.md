# APK Qo‘riqchi AI — Android

> "Detect with trusted security engines. Analyze with evidence. Explain with AI. Protect user privacy by default."

This is a native Android app (Kotlin, Jetpack Compose, Material 3). It checks APK files, links and installed apps using VirusTotal, Google Safe Browsing and on-device analysis. Google Gemini explains the results in Uzbek, Russian or English. It is **not** a replacement for Google Play Protect.

The backend (FastAPI) is in `../backend` (see its README).

## Stack (versions verified against Maven, 2026-10)

| Area | Choice |
|---|---|
| Build | Gradle 9.6.0, AGP 9.4.1 (built-in Kotlin), Kotlin 2.3.20, KSP 2.3.12 |
| SDK | `compileSdk`/`targetSdk` 37 (current stable in this environment), **`minSdk` 26** |
| UI | Compose BOM 2026.09.00, Material 3, Navigation Compose 2.10.2 |
| DI and state | Hilt 2.60.1, ViewModel + StateFlow, coroutines 1.11 |
| Data | Room 2.8.5 (history), DataStore 1.2.1 (prefs), EncryptedSharedPreferences (API keys) |
| Network | OkHttp 5.5.0, Retrofit 3.0.0, kotlinx.serialization 1.11 |
| APK verification | `com.android.tools.build:apksig` 9.4.1 (v1–v4 signature schemes) |

**Why `minSdk` 26 (Android 8.0):** it covers about 97% of active devices, gives a consistent SAF picker, `getPackageArchiveInfo` with signing info, and notification channels. Features that need API 28+ (signing-certificate history) or 30+ (install-source info) are gated at runtime.

## Build and run

Requirements: Android Studio (bundled JBR 21+) and Android SDK platform 37.

```bash
cd ApkQoriqchi
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug            # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest        # unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the `ApkQoriqchi` folder in Android Studio and press **Run**.

### Release build and signing

1. Create an upload key once and keep it **out of the repository**:
   `keytool -genkeypair -v -keystore ~/keys/qoriqchi-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
2. Add the following to `~/.gradle/gradle.properties` (not the project's):
   `QORIQCHI_STORE_FILE=/Users/you/keys/qoriqchi-upload.jks`, `QORIQCHI_STORE_PASSWORD=…`, `QORIQCHI_KEY_ALIAS=upload`, `QORIQCHI_KEY_PASSWORD=…`
3. Add a `signingConfigs { create("release") { … } }` block to `app/build.gradle.kts` that reads those properties, and set `signingConfig` on the `release` build type.
4. Run `./gradlew :app:bundleRelease` to get an AAB for Play (with Play App Signing), or `:app:assembleRelease` for an APK. R8 minification is already enabled for release builds.
5. **Google Play:** `QUERY_ALL_PACKAGES` needs the Play Console permission declaration (the core purpose is the installed-app security audit), and `REQUEST_DELETE_PACKAGES` must also be declared.

## Configuration (in the app)

**Settings → API keys.** Keys are stored encrypted on the device and sent only to their own provider.
- **VirusTotal** (virustotal.com → profile → API key). The free tier allows 4 requests/min and 500/day and is non-commercial.
- **Google Safe Browsing** (Cloud Console → enable the Safe Browsing API → API key). Non-commercial; commercial apps should use Web Risk (the adapter is included).
- **Google Gemini** (aistudio.google.com → Get API key). Optional, for AI explanations; the app calls it only when the user taps "Explain with AI".

No keys are built into the app or the repository: every user enters their own keys.

Without keys the app still runs its local checks. Those results are marked **UNKNOWN** or "local checks only", never "clean".

## Architecture

```
core/        models (RiskLevel, ScanRecord, ProviderResult), Room history, settings, encrypted keys, DI, share-intent bus
core/network/virustotal/   VirusTotal v3 client (files, upload, analyses)
feature/apk/   SAF picker → streaming hashes → PackageManager + apksig → RiskEngine → VerdictPolicy → VT lookup/upload(consent) → report
feature/url/   normalizer → lexical/lookalike heuristics → Safe Browsing v5 / Web Risk + VT URL → UrlVerdict (never fetches the URL)
feature/apps/  InstalledAppAuditor (PackageManager, install source, special access) → AppRiskScorer
feature/ai/    GeminiExplainer (structured JSON output, schema-validated, verdict cannot be overridden) + AiExplanationCard
feature/home, history, settings (service status + test connection, privacy controls, notifications, language)
```

Every risk decision is made by pure-Kotlin, unit-tested policy classes (`VerdictPolicy`, `RiskEngine`, `UrlVerdict`, `AppRiskScorer`). The model only explains it.

## Documentation
- `docs/STATUS.md`: implemented, partially implemented and not-implemented features, and test results
- `docs/THREAT_MODEL.md`: assets, attackers, trust boundaries, mitigations
- `docs/PRIVACY_POLICY.md`: privacy policy **template (requires legal review)**
- `docs/openapi.json`: backend API schema snapshot
