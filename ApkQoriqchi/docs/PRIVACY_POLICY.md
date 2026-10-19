# Privacy Policy — APK Qo‘riqchi AI (TEMPLATE)

> **Template:** this text requires review by a qualified lawyer before publication. It describes the behaviour of the app as implemented.

_Last updated: 2026-10-18_

## What the app does
APK Qo‘riqchi AI helps you assess APK files, links and installed-app permissions using third-party threat intelligence services and on-device analysis. It does not replace Google Play Protect or Android's built-in security protections.

## Data processed on your device only
- **Selected APK files.** These are copied temporarily to private app storage so the app can compute hashes and read metadata (package name, version, permissions, signing certificate). The copy is deleted after analysis.
- **Installed-app list.** It is read only while you use the audit screen and is never sent anywhere. A summary record is saved to history only for apps whose details you open.
- **Scan history.** It is stored in the app's private database and contains hashes, package names, minimized URLs (scheme, host and path only), verdicts and provider status. It never contains file contents.

## Data sent to third parties
| Service | What is sent | When |
|---|---|---|
| VirusTotal (Google) | SHA-256 hash of an APK; a URL you check | When you run a scan and have added a VirusTotal key |
| VirusTotal (Google) | The full APK file | Only after you enable uploads in Settings **and** confirm for that specific file. Uploaded samples are shared with the security community under VirusTotal's terms and may be retained. |
| Google Safe Browsing | The URL you check | When you run a URL check and have added a key |
| Google (Gemini API) | A text summary of scan evidence (verdict, detections, permissions, package name, URL host) | Only when you tap “Explain with AI”. On Gemini's free tier, Google may use this data to improve its services. |

Each service processes the data under its own privacy policy. The app has no server of its own and collects no analytics.

## API keys
Your API keys are stored encrypted on the device with Android Keystore and are sent only to the matching provider.

## Permissions
- `INTERNET` and `ACCESS_NETWORK_STATE`: used for provider lookups.
- `QUERY_ALL_PACKAGES`: used only for the installed-app audit you start yourself.
- `POST_NOTIFICATIONS`: requested only if you turn on scan notifications.

## Deleting your data
- **Settings → Privacy:** clear all history, delete individual records, and delete all API keys.
- **Android Settings → Apps → APK Qo‘riqchi AI → Storage → Clear data:** removes everything.
- **Uninstalling the app** removes all local data.
- Data already sent to third parties (for example, files uploaded to VirusTotal) is governed by those services. Contact them directly for deletion.

## Contact
[Developer name / email — fill in before publishing]
