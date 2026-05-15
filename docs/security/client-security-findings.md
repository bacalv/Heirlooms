# Client Security Findings — SEC-003

**Date:** 2026-05-15
**Author:** Security Manager
**Status:** Active — maintained at `docs/security/client-security-findings.md`

---

## Summary

This report covers a code-audit of the Android (`HeirloomsApp/`), Web (`HeirloomsWeb/`), and iOS (`HeirloomsiOS/`) clients against the threat model in `docs/security/threat-model.md`. All three clients implement end-to-end encryption correctly at the cryptographic layer. The most serious findings concern session-token persistence in plaintext storage (Android SharedPreferences, Web localStorage) and the absence of a Content-Security-Policy header on the web client.

---

## Android (`HeirloomsApp/`)

### A-01 — Session token stored in plaintext SharedPreferences

| Field | Value |
|---|---|
| **Location** | `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/EndpointStore.kt` L49–50 |
| **Severity** | HIGH |
| **Description** | `EndpointStore.setSessionToken()` writes the 32-byte session token into `MODE_PRIVATE` SharedPreferences as a plaintext base64 string under the key `session_token`. On Android 8 and below, SharedPreferences XML files are readable by any process with physical access to the `/data/data/` directory (root, ADB backup exploits, forensic extraction). Even on Android 9+ with file-based encryption, extracting the token from a rooted or forensic-unlocked device is straightforward. A stolen session token grants full API access until the 90-day TTL expires. The master key itself is properly Keystore-wrapped; however the session token is not. |
| **Recommended fix** | Store the session token in an Android Keystore-backed `EncryptedSharedPreferences` (Jetpack Security library), or write it into the same Keystore-encrypted blob as the wrapped master key in `DeviceKeyManager`. The Keystore AES key used for vault material should also wrap the session token. |

---

### A-02 — Keystore key not gated behind biometric or user authentication

| Field | Value |
|---|---|
| **Location** | `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/crypto/DeviceKeyManager.kt` L63 |
| **Severity** | MEDIUM |
| **Description** | `getOrCreateKeystoreKey()` sets `.setUserAuthenticationRequired(false)`. This means the Keystore AES key (which wraps the vault master key) can be used without any biometric or PIN challenge. Any code running in the app process — including code injected via a debug session on a compromised device — can call `loadMasterKey()` without requiring the user to prove presence. The master key is thereby available whenever the app process is live, without an additional authentication gate. |
| **Recommended fix** | Set `.setUserAuthenticationRequired(true)` with `.setUserAuthenticationValidityDurationSeconds(-1)` to require per-operation biometric confirmation, or at minimum `.setUserAuthenticationValidityDurationSeconds(30)` to require authentication once per 30 seconds. Tie this to a `BiometricPrompt` before vault unlock. |

---

### A-03 — No certificate pinning

| Field | Value |
|---|---|
| **Location** | `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/api/HeirloomsApi.kt` L21–24 |
| **Severity** | MEDIUM |
| **Description** | The `OkHttpClient` is constructed with default settings — no `CertificatePinner` is configured. An attacker who can intercept TLS (via a rogue CA installed on the device, or a MITM proxy on a corporate network) could observe API traffic including session tokens sent in `X-Api-Key` headers. Because all media is E2EE, content itself would remain protected; however the session token and metadata requests (upload lists, tags, plot membership) would be exposed. |
| **Recommended fix** | Add an `OkHttpClient.CertificatePinner` for `api.heirlooms.digital` and pin the leaf or intermediate certificate SHA-256 hash. Rotate the pin in a new app release before rotating the certificate. Certificate pinning is a meaningful defence-in-depth measure even though content is E2EE. |

---

### A-04 — No root/compromised-device detection

| Field | Value |
|---|---|
| **Location** | Entire `HeirloomsApp/` — no root detection found |
| **Severity** | MEDIUM |
| **Description** | The app performs no check for rooted devices (no SafetyNet/Play Integrity attestation, no filesystem checks for `su` binary or test-keys). On a rooted device, `adb pull /data/data/digital.heirlooms.app/` can extract SharedPreferences (including the plaintext session token per A-01). More critically, root access allows extracting the Keystore-backed wrapped master key and defeating the Keystore protection via Magisk bypass techniques. While the Android Keystore provides hardware-backed protection on most modern devices, the absence of any root check means there is no user-visible warning and no API attestation that the device integrity is intact. |
| **Recommended fix** | Integrate the Play Integrity API (successor to SafetyNet) to perform device attestation at vault setup and on each login. Display a warning (or refuse operation) when the device verdict indicates compromised integrity. Fall back gracefully on devices that lack attestation (e.g. Fire OS, emulators). |

---

### A-05 — FLAG_SECURE not set on activity windows

| Field | Value |
|---|---|
| **Location** | `HeirloomsApp/app/src/main/kotlin/digital/heirlooms/app/MainActivity.kt` |
| **Severity** | MEDIUM |
| **Description** | `MainActivity.onCreate()` does not call `window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)`. Without this flag, the system's recent-apps thumbnail and screenshot APIs can capture decrypted vault content. A malicious background app with `READ_FRAME_BUFFER` (Accessibility services, screen capture apps) can capture live frames from the vault UI on older Android versions. The recent-apps switcher also caches a thumbnail of the last app frame, exposing plaintext images to anyone who briefly has physical access to the unlocked device. |
| **Recommended fix** | Add `window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)` in `MainActivity.onCreate()` (and `SettingsActivity` if sensitive data is shown there). This prevents screenshots and recent-apps thumbnails. Note: this also prevents users from taking screenshots intentionally, so consider limiting it to screens that display decrypted vault content. |

---

### A-06 — `android:allowBackup="false"` is correctly set

| Field | Value |
|---|---|
| **Location** | `HeirloomsApp/app/src/main/AndroidManifest.xml` L17 |
| **Severity** | INFO (no finding) |
| **Description** | `android:allowBackup="false"` is correctly set. ADB backup and Android Auto Backup are both disabled, preventing backup extraction of SharedPreferences (including the session token). |

---

### A-07 — Cleartext traffic correctly restricted to local/private ranges only

| Field | Value |
|---|---|
| **Location** | `HeirloomsApp/app/src/main/res/xml/network_security_config.xml` |
| **Severity** | LOW |
| **Description** | Cleartext HTTP is permitted only for private IP ranges (192.168.x.x, 10.x.x.x, 172.16.x.x, localhost, 127.0.0.1). This is appropriate for local development and LAN testing. Production traffic to `api.heirlooms.digital` uses HTTPS. There is no `<base-config cleartextTrafficPermitted="true">` override, so the default (cleartext blocked for all other hosts) applies. This configuration is acceptable. However, it is worth noting that the local development IP ranges are quite broad (e.g. all 192.168.0.0 and 10.0.0.0 addresses, not just a specific local host). On a shared WiFi network, this could theoretically expose local API traffic, but in practice local development servers are not exposed on shared networks. |
| **Recommended fix** | Low priority. Optionally narrow the allowlist to `10.0.2.2` (Android emulator host) and `localhost` only for staging/debug builds, and use a build-type override to exclude these from release builds entirely. |

---

### A-08 — No sensitive data logged

| Field | Value |
|---|---|
| **Location** | All `HeirloomsApp/` production Kotlin sources |
| **Severity** | INFO (no finding) |
| **Description** | A full search for `Log.`, `Timber`, `println`, and `System.out` in production code found no logging of auth tokens, session tokens, master keys, or other cryptographic material. Only non-sensitive error messages are logged (staging approve/reject failures). |

---

## Web (`HeirloomsWeb/`)

### W-01 — Session token stored in localStorage

| Field | Value |
|---|---|
| **Location** | `HeirloomsWeb/src/App.jsx` L37, L55, L97 |
| **Severity** | HIGH |
| **Description** | The session token (`heirlooms_session_token`) is written to and read from `localStorage`. Unlike `httpOnly` cookies, `localStorage` is accessible to any JavaScript executing in the same origin. In the event of an XSS vulnerability (now or in the future), an attacker can exfiltrate the session token with a single `localStorage.getItem('heirlooms_session_token')` call. The token grants full API access until expiry. `localStorage` also persists across browser restarts, meaning a shared-computer user who does not explicitly log out leaves their session token readable by the next browser user. |
| **Recommended fix** | The safest option is to move session token storage to an `httpOnly`, `Secure`, `SameSite=Strict` cookie managed by the server. The server would need to accept the cookie in addition to (or instead of) the `X-Api-Key` header. A lower-effort mitigation is to use `sessionStorage` instead of `localStorage` — this at least scopes the token to the browser tab lifetime and prevents persistence across browser restarts. It does not protect against XSS. |

---

### W-02 — No Content-Security-Policy header

| Field | Value |
|---|---|
| **Location** | `HeirloomsWeb/nginx.conf` |
| **Severity** | HIGH |
| **Description** | The nginx configuration serving the web client sets no `Content-Security-Policy` response header. Without a CSP, any injected JavaScript executes with full origin trust — there is no browser-enforced barrier against inline script injection or loading scripts from attacker-controlled origins. This is a defence-in-depth layer that the OWASP Top 10 (A03:2021 Injection) and the Heirlooms threat model specifically call for. Given that the session token lives in `localStorage` (W-01), an XSS + CSP absence combination is especially dangerous. |
| **Recommended fix** | Add a strict CSP to `nginx.conf`: `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data:; connect-src 'self' https://api.heirlooms.digital https://storage.googleapis.com; frame-ancestors 'none'`. Note: Vite-built React apps may require `'unsafe-inline'` for styles (or use nonces). Audit carefully before enforcing. |

---

### W-03 — Vault master key held in memory only (good)

| Field | Value |
|---|---|
| **Location** | `HeirloomsWeb/src/crypto/vaultSession.js` |
| **Severity** | INFO (no finding) |
| **Description** | The master key is held as a module-level `let _masterKey` variable — never written to localStorage, sessionStorage, or IndexedDB. It is zeroed on `lock()`. The device private key is stored as a non-extractable `CryptoKey` in IndexedDB (cannot be read as bytes by JavaScript). The wrapped master key (ciphertext) is stored in IndexedDB for pairing persistence; the raw master key is not. This is the correct design. |

---

### W-04 — Vault device private key is non-extractable (good)

| Field | Value |
|---|---|
| **Location** | `HeirloomsWeb/src/crypto/deviceKeyManager.js` L53 |
| **Severity** | INFO (no finding) |
| **Description** | `crypto.subtle.generateKey()` is called with `extractable: false` for the device private key. The key is stored as a `CryptoKey` object in IndexedDB and can never be exported as raw bytes by JavaScript. This is the correct design. |

---

### W-05 — No XSS vectors found in rendered content

| Field | Value |
|---|---|
| **Location** | All `HeirloomsWeb/src/` JSX and JS files |
| **Severity** | INFO (no finding) |
| **Description** | No usage of `dangerouslySetInnerHTML`, `innerHTML`, or unescaped template literals rendering user-controlled data was found. All rendering goes through React's JSX interpolation, which HTML-escapes by default. |

---

### W-06 — No mixed content found

| Field | Value |
|---|---|
| **Location** | `HeirloomsWeb/src/api.js` L1 |
| **Severity** | INFO (no finding) |
| **Description** | All API calls use relative paths or the `VITE_API_URL` environment variable. In production, `VITE_API_URL` is expected to point to `https://api.heirlooms.digital`. No hardcoded `http://` URLs to production endpoints were found. |

---

### W-07 — No production console logging of sensitive values

| Field | Value |
|---|---|
| **Location** | All `HeirloomsWeb/src/` sources |
| **Severity** | INFO (no finding) |
| **Description** | `console.log` calls found in production code (`encryptedVideoStream.js`, `PhotoDetailPage.jsx`, `GardenPage.jsx`) log only operational/error messages (video stream strategy, download failure, preview clip failure). No auth tokens, keys, or cryptographic material are logged. |

---

## iOS (`HeirloomsiOS/`)

### I-01 — No jailbreak detection

| Field | Value |
|---|---|
| **Location** | Entire `HeirloomsiOS/` — no jailbreak detection found |
| **Severity** | MEDIUM |
| **Description** | The app performs no jailbreak detection. On a jailbroken device, the Keychain isolation guarantees may be weakened. Some jailbreak frameworks can extract Keychain items from any app (bypassing `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` constraints), retrieve the session token stored in the Keychain, and read app memory to extract the in-flight plot key. This is analogous to the Android root detection gap (A-04). For an invite-only family vault, the threat model considers physical device compromise realistic. |
| **Recommended fix** | Implement lightweight jailbreak heuristics (check for `/Applications/Cydia.app`, `/usr/bin/ssh`, ability to write outside the sandbox). Additionally (or alternatively), use the DeviceCheck or AppAttest framework for server-side attestation. Show a warning and optionally refuse vault unlock on jailbroken devices. |

---

### I-02 — No screenshot notification handling

| Field | Value |
|---|---|
| **Location** | Entire `HeirloomsiOS/` — no `UIApplicationUserDidTakeScreenshotNotification` handler found |
| **Severity** | LOW |
| **Description** | iOS provides a `UIApplicationUserDidTakeScreenshotNotification` notification when the user takes a screenshot. The app does not register for this notification and does not blur or mask sensitive content when it arrives. While iOS itself cannot prevent screenshots, the app could respond to the notification by logging the event or alerting the user. More usefully, it could use `UIScreen.main.isCaptured` (available since iOS 11) to detect screen recording/mirroring and overlay a privacy mask in real time. |
| **Recommended fix** | Subscribe to `UIScreen.capturedDidChangeNotification` and `UIApplicationUserDidTakeScreenshotNotification`. When `UIScreen.main.isCaptured` is true, overlay a privacy screen on the full-screen media view (`FullScreenMediaView.swift`) to prevent screen recording capture of decrypted images. |

---

### I-03 — Keychain access control is correctly set (good)

| Field | Value |
|---|---|
| **Location** | `HeirloomsiOS/Sources/HeirloomsCore/Crypto/KeychainManager.swift` L45, L243 |
| **Severity** | INFO (no finding) |
| **Description** | All Keychain items (session token, plot key, user ID, plot ID) are stored with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`. This means items cannot be restored to a different device (no iCloud Keychain migration), and are inaccessible when the device is locked. The sharing P-256 private key additionally uses `kSecAttrAccessControl` with `.privateKeyUsage` and Secure Enclave storage where available. This is the correct and secure design. |

---

### I-04 — ATS not exempted — HTTPS enforced (good)

| Field | Value |
|---|---|
| **Location** | `HeirloomsiOS/` — no `Info.plist` or `NSAllowsArbitraryLoads` found |
| **Severity** | INFO (no finding) |
| **Description** | No `NSAppTransportSecurity` dictionary with `NSAllowsArbitraryLoads` or `NSExceptionDomains` entries was found. App Transport Security is not weakened. The iOS app uses `https://api.heirlooms.digital` as its default base URL, consistent with ATS requirements. |

---

### I-05 — No certificate pinning on iOS

| Field | Value |
|---|---|
| **Location** | `HeirloomsiOS/Sources/HeirloomsCore/Networking/HeirloomsAPI.swift` |
| **Severity** | MEDIUM |
| **Description** | `HeirloomsAPI` uses `URLSession.shared` with no custom `URLSessionDelegate` implementing `urlSession(_:didReceive:completionHandler:)` for certificate validation. Certificate pinning is not implemented. The risk is the same as Android (A-03): a rogue CA or MITM proxy can observe session tokens and metadata. Content remains E2EE-protected. |
| **Recommended fix** | Implement certificate pinning by subclassing `URLSessionDelegate` and validating the server certificate against a pinned public key hash in `didReceive challenge`. Alternatively use TrustKit or a similar library. Coordinate certificate rotation with app releases. |

---

### I-06 — No sensitive data logged (good)

| Field | Value |
|---|---|
| **Location** | All `HeirloomsiOS/` Swift sources |
| **Severity** | INFO (no finding) |
| **Description** | `print()` calls in production code (`BackgroundUploadManager.swift`, `HomeView.swift`) log only storage keys and error descriptions — no auth tokens, Keychain values, or cryptographic material. |

---

## Findings Summary Table

| ID | Platform | Severity | Title |
|----|----------|----------|-------|
| A-01 | Android | HIGH | Session token stored in plaintext SharedPreferences |
| W-01 | Web | HIGH | Session token stored in localStorage |
| W-02 | Web | HIGH | No Content-Security-Policy header |
| A-02 | Android | MEDIUM | Keystore key not gated behind biometric or user authentication |
| A-03 | Android | MEDIUM | No certificate pinning |
| A-04 | Android | MEDIUM | No root/compromised-device detection |
| A-05 | Android | MEDIUM | FLAG_SECURE not set on activity windows |
| I-01 | iOS | MEDIUM | No jailbreak detection |
| I-05 | iOS | MEDIUM | No certificate pinning on iOS |
| A-07 | Android | LOW | Cleartext permitted for broad private IP ranges |
| I-02 | iOS | LOW | No screenshot notification/capture detection |
| A-06 | Android | INFO | `allowBackup="false"` correctly set |
| A-08 | Android | INFO | No sensitive data logged |
| W-03 | Web | INFO | Vault master key held in memory only |
| W-04 | Web | INFO | Device private key is non-extractable |
| W-05 | Web | INFO | No XSS vectors found |
| W-06 | Web | INFO | No mixed content |
| W-07 | Web | INFO | No production console logging of sensitive values |
| I-03 | iOS | INFO | Keychain access control correctly set |
| I-04 | iOS | INFO | ATS not exempted |
| I-06 | iOS | INFO | No sensitive data logged |

---

## New Tasks Created

| Task | Findings addressed | Priority |
|------|--------------------|----------|
| [SEC-007](../../tasks/queue/SEC-007_android-session-token-keystore.md) | A-01 (Android plaintext session token) | HIGH |
| [SEC-008](../../tasks/queue/SEC-008_web-csp-session-token.md) | W-01 (localStorage session token), W-02 (no CSP) | HIGH |
| [SEC-009](../../tasks/queue/SEC-009_android-biometric-flag-secure.md) | A-02 (no biometric gate), A-05 (no FLAG_SECURE) | MEDIUM |

---

## Overall Security Posture Assessment

The cryptographic core is well-implemented across all three clients. The envelope format (AES-256-GCM with proper random nonces, algorithm IDs, versioned envelopes) is consistently implemented. The iOS Keychain usage is exemplary — `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` throughout, Secure Enclave for the device keypair. The web client's use of non-extractable WebCrypto keys in IndexedDB is correct.

The two HIGH findings on the web client (no CSP, session token in localStorage) are the most actionable because an XSS bug anywhere in the web app would allow immediate session token exfiltration. These should be addressed before public beta.

The Android HIGH finding (plaintext session token in SharedPreferences) is a meaningful gap because the session token is the credential that gates all API access, including fetching wrapped keys. However, because `allowBackup="false"` is set and the device storage is file-based-encrypted (Android 7+), exploitation requires physical device compromise with device unlock — the same prerequisite as most other token-theft attacks.

Certificate pinning on both Android and iOS would close the remaining gap where a compromise of any trusted CA on the device could allow session token interception. Given that content is E2EE, the impact is limited to session-layer attacks, but pinning is recommended before general release.

Root/jailbreak detection on Android and iOS are product decisions as much as security ones — they require careful UX consideration (users with legitimate reasons for root access) and ongoing maintenance.
