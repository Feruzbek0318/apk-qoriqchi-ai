# Threat model — APK Qo‘riqchi AI (Android client, no backend)

## Architecture summary

```
[User] → [App UI] → [Local analysis: hashing, PackageManager, apksig, heuristics, risk policy]
                  → HTTPS → VirusTotal v3       (hash / URL lookups; file upload only with consent)
                  → HTTPS → Google Safe Browsing v5 (URL lookups)
                  → HTTPS → Anthropic Messages API (structured evidence → explanation)
          [Room DB: scan history]   [EncryptedSharedPreferences: API keys]   [DataStore: prefs]
```

There is no project-operated server. Each user supplies their own provider API keys.

## Assets

| Asset | Where | Sensitivity |
|---|---|---|
| Provider API keys (VT, Safe Browsing, Claude) | EncryptedSharedPreferences (AES-256-GCM, Android Keystore master key) | High: quota and billing abuse |
| Selected APK files | Temporary copy in `cacheDir`, deleted after analysis | Medium: may be private builds |
| Scan history | Room DB in app-private storage | Medium: reveals browsing and installed software |
| Installed-app list | Memory only; one record per app detail the user opens | Medium: personal data |
| User trust in verdicts | UI | High: a false "safe" causes harm |

## Attackers

1. **Malicious APK author.** Crafts APKs to crash or exhaust the parser, spoofs labels, repackages popular apps, or embeds prompt-injection text in metadata.
2. **Phishing operator.** Uses lookalike or IDN domains, shorteners and redirect chains, and hopes the app "visits" the URL.
3. **Network attacker.** Tries MITM against provider calls.
4. **Malicious app on the same device.** Tries to read keys or history.
5. **Compromised or poisoned AI output.** The model is induced to say "safe" or invent detections.

## Trust boundaries and mitigations

| Boundary | Threat | Mitigation |
|---|---|---|
| APK file → parser | Zip bombs, malformed manifests, huge files | 650 MB limit, ZIP magic check, streaming hashes, `withTimeout` around parsing, all exceptions caught. The APK is never installed or executed. |
| APK metadata / URL → AI prompt | Prompt injection via labels, file names or URLs | The system prompt marks facts as untrusted data. Structured output is schema-validated. The AI cannot change the deterministic verdict. |
| App → providers | MITM, key leakage | TLS only (`cleartextTrafficPermitted=false`). Keys are sent only in headers or query to the official hosts. HTTP logging is BASIC (no headers) and debug only. |
| User URL → network | SSRF or drive-by from visiting the URL | The app never fetches the user's URL. It uses reputation APIs only. There is no WebView. "Open anyway" goes to the external browser after a warning. |
| Device storage | Other apps reading data | App-private storage, encrypted keys, `allowBackup=false`. History stores minimized URLs (no query, fragment or userinfo) and never APK contents. |
| Verdict → user | False sense of safety | No "SAFE" state. Missing reports show "No existing report found." and map to UNKNOWN. Disclaimer shown on clean results. The policy engine is deterministic and unit-tested. |
| File upload → VirusTotal | Privacy leak of private APKs | Off by default. Explicit per-file consent dialog naming the provider and explaining community sharing. Hash lookup always comes first. |

## Residual risks and known limitations

- **API keys on device.** Keys live on the client. This is acceptable only because each user brings their own key. A shared or commercial deployment must move provider calls behind an authenticated backend so the secrets never reach the client.
- **Safe Browsing licensing.** The Safe Browsing API is for non-commercial use. Commercial distribution must switch to Google Web Risk; an adapter is prepared.
- **Root and runtime attacks.** A rooted device or an attacker with runtime instrumentation can extract keys from memory.
- **Package visibility limits.** The installed-app audit is limited by package visibility and does not deep-scan app code.
- **Evasion.** Heuristics and multi-engine reputation can be evaded by new or targeted malware. Zero detections do not prove safety.
