# SEC-013 — iOS Client Security Review

**Date:** 2026-05-16
**Author:** Security Manager
**Scope:** `HeirloomsiOS/` — full source audit across six areas defined in SEC-013 task
**Reference:** SEC-003 / `docs/security/client-security-findings.md` (Android + web)

---

## Executive Summary

The iOS client has a strong cryptographic foundation: Keychain storage uses
`kSecAttrAccessibleWhenUnlockedThisDeviceOnly` throughout, the P-256 sharing key
targets the Secure Enclave, and the envelope crypto layer is correctly implemented.
No critical findings were identified.

Three **High** findings were raised:

- **iOS-02**: No privacy overlay when the app enters the background — vault content
  is visible in the iOS App Switcher.
- **iOS-03**: ATS configuration is absent entirely. There is no main-app `Info.plist`
  in the repository; ATS domain-pinning to `heirlooms.digital` is not enforced.
- **iOS-05**: No explicit zeroing of DEK and master-key byte buffers in memory after
  use; Swift ARC/CryptoKit do not guarantee deterministic zeroing.

Two **Medium** findings were raised (no certificate pinning; no biometric gate on
vault unlock). These match equivalent Android findings (A-03, A-02).

No **Critical** findings were identified.

---

## Findings

### iOS-01 — Keychain access flags are correctly set

| Field | Value |
|---|---|
| **Finding ID** | iOS-01 |
| **Severity** | INFO (no finding) |
| **Location** | `Sources/HeirloomsCore/Crypto/KeychainManager.swift` L267, L45–49 |
| **Android comparison** | Corresponds to Android INFO finding A-06 (allowBackup) — both platforms handle baseline storage security correctly |

All generic-password Keychain items (session token, master key, plot key, user ID,
plot ID) are created with:

```swift
addQuery[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
```

This is the strongest appropriate flag for this use case:
- Items are inaccessible when the device is locked.
- Items do not migrate to a new device via iCloud Keychain backup.
- `ThisDeviceOnly` prevents restoration to a different device from an encrypted backup.

The P-256 sharing private key additionally uses `SecAccessControlCreateWithFlags`
with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` and targets the Secure Enclave
via `kSecAttrTokenIDSecureEnclave`. The `.privateKeyUsage` flag restricts usage to
ECDH key agreement operations — the raw key bytes are never exported.

**No remediation required.** This is the correct design.

---

### iOS-02 — No privacy overlay on backgrounding

| Field | Value |
|---|---|
| **Finding ID** | iOS-02 |
| **Severity** | HIGH |
| **Location** | `HeirloomsApp/App.swift`, `HeirloomsApp/Views/HomeView.swift`, `HeirloomsApp/Views/FullScreenMediaView.swift` — no backgrounding handler found in any file |
| **Android comparison** | Android finding A-05 (FLAG_SECURE not set) — now partially remediated by SEC-009; iOS equivalent is not implemented |

The app does not subscribe to `UIApplication.willResignActiveNotification`,
`UIApplication.didEnterBackgroundNotification`, or the SwiftUI `.onChange(of: scenePhase)`
lifecycle hook to overlay a privacy screen before the OS takes an App Switcher snapshot.

When the user double-presses the Home button (or swipes up on Face-ID devices) while
viewing decrypted vault content, iOS captures a screenshot of the current frame and
displays it in the App Switcher. Any person with brief physical access to an unlocked
device can view full-resolution decrypted images this way — without any further
authentication.

Additionally, `UIScreen.main.isCaptured` is not monitored, so screen recording or
AirPlay mirroring of the vault screen is not detected or masked.

SEC-014 (`tasks/queue/SEC-014_ios-vault-background-privacy-screen.md`) was pre-created
to track the implementation. This finding documents the current gap.

**Recommended remediation:** In `App.swift`, attach an `.onChange(of: scenePhase)`
modifier to the root `WindowGroup`. When `scenePhase == .inactive` (the moment before
an App Switcher snapshot is taken), push a full-screen blur/lock view on top of the
navigation stack. Remove it when `scenePhase == .active`. Separately, subscribe to
`UIScreen.capturedDidChangeNotification` and overlay a privacy mask in `FullScreenMediaView`
when `UIScreen.main.isCaptured` is `true`.

---

### iOS-03 — No main app Info.plist — ATS domain restriction absent

| Field | Value |
|---|---|
| **Finding ID** | iOS-03 |
| **Severity** | HIGH |
| **Location** | `HeirloomsiOS/` — no main app `Info.plist` exists in the repository; only the Share Extension `Info.plist` files are present |
| **Android comparison** | Android finding A-07 (cleartext traffic for broad private IP ranges) — iOS equivalent is more serious because domain-specific ATS pinning is entirely absent |

The repository contains `Info.plist` files only for the two Share Extension targets
(`HeirloomsApp/ShareExtension/Info.plist` and `HeirloomsShare/Info.plist`). Neither
contains an `NSAppTransportSecurity` dictionary.

The main `HeirloomsApp` target has no `Info.plist` checked into the repository at all.
Without a controlled `Info.plist`:

1. The default Xcode-generated `Info.plist` may lack an explicit
   `NSAppTransportSecurity` block, which leaves ATS in its default state (HTTPS
   enforced with system CAs, but no domain restrictions).
2. There is no `NSExceptionDomains` configuration locking the app to
   `heirlooms.digital` and `test.heirlooms.digital` only — any HTTPS URL can be
   reached, including attacker-controlled hosts if code is ever injected.
3. There is no `NSAllowsLocalNetworking` or `NSTemporaryExceptionAllowsInsecureHTTPLoads`
   entry to restrict local development URLs to debug builds only.

The task brief specified verifying that ATS is "correctly locked to `heirlooms.digital`
and `test.heirlooms.digital`". That configuration does not exist.

**Recommended remediation:** Add a committed main app `Info.plist` to the repository
(tracked under `HeirloomsiOS/HeirloomsApp/Info.plist`). Include:

```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <false/>
    <key>NSExceptionDomains</key>
    <dict>
        <key>heirlooms.digital</key>
        <dict>
            <key>NSExceptionMinimumTLSVersion</key>
            <string>TLSv1.3</string>
            <key>NSExceptionRequiresForwardSecrecy</key>
            <true/>
        </dict>
        <key>test.heirlooms.digital</key>
        <dict>
            <key>NSExceptionMinimumTLSVersion</key>
            <string>TLSv1.3</string>
            <key>NSExceptionRequiresForwardSecrecy</key>
            <true/>
        </dict>
    </dict>
</dict>
```

Use a Xcode build configuration override to add a local-network exception in
Debug builds only (e.g. allow `localhost` and `192.168.x.x` for local development).

---

### iOS-04 — Share extension Keychain access is appropriate

| Field | Value |
|---|---|
| **Finding ID** | iOS-04 |
| **Severity** | INFO (no finding) |
| **Location** | `HeirloomsApp/HeirloomsApp.entitlements`, `HeirloomsApp/ShareExtension/HeirloomsShare.entitlements`, `HeirloomsShare/HeirloomsShare.entitlements`, `HeirloomsShare/ShareExtensionKeychain.swift` |
| **Android comparison** | No direct Android equivalent; Android does not have Share Extensions |

The share extension shares a Keychain access group (`digital.heirlooms.keychain`)
with the main app. The extension reads three items: session token, plot key, and
plot ID.

**This access is appropriate and correctly scoped:**

1. **Functional necessity**: The share extension must authenticate API calls (session
   token) and encrypt content (plot key). Without read access to these items,
   encrypted upload from the share sheet is impossible.

2. **Access constraint is maintained**: Items remain
   `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`. The share extension can only
   activate when the device is unlocked (user initiated the share action) — this
   accessibility level is compatible.

3. **Read-only access**: `ShareExtensionKeychain` only reads items. It does not write
   to or delete Keychain entries. The main `KeychainManager` class manages writes.

4. **Fallback path risk**: Both copies of `ShareExtensionKeychain.swift` include a
   fallback query that omits `kSecAttrAccessGroup` and will read items from the app's
   private Keychain container if the access group query fails. This fallback exists
   for simulator/development scenarios where the Keychain Sharing entitlement may not
   be provisioned. In production builds with correctly configured entitlements, the
   primary query (with the access group) will always succeed. The fallback represents
   a development convenience, not a production security gap, but it should be
   conditionally compiled out of release builds to eliminate dead code.

5. **Master key not shared**: `ShareExtensionKeychain` does not read the master key —
   only the plot key and session token. The master key is used only during web
   pairing, which is a main-app-only flow. This is the correct design.

**Minor observation (not a finding):** The fallback query without `kSecAttrAccessGroup`
should be wrapped in `#if DEBUG` to prevent it from being present in App Store builds.

---

### iOS-05 — DEK and master key bytes not explicitly zeroed after use

| Field | Value |
|---|---|
| **Finding ID** | iOS-05 |
| **Severity** | HIGH |
| **Location** | `HeirloomsApp/Views/ActivateView.swift` L196–197, L252–253; `HeirloomsApp/Views/HomeView.swift` L166, L229–235; `HeirloomsApp/Views/FullScreenMediaView.swift` L84–88; `HeirloomsShare/ShareUploadCoordinator.swift` L125–131, L197 |
| **Android comparison** | No direct Android finding raised in SEC-003, but the same pattern applies on Android |

In multiple locations, raw key bytes are extracted from `SymmetricKey` instances into
`Data` buffers using `withUnsafeBytes { Data($0) }` and then passed to the Keychain or
used in encryption operations. Neither the `Data` buffer nor the `SymmetricKey` is
explicitly zeroed after use; both are released to ARC.

Specific patterns:

```swift
// ActivateView.swift L196 — master key bytes materialised into Data
let masterKeyData = masterKey.withUnsafeBytes { Data($0) }
try KeychainManager.saveMasterKey(masterKeyData)
// masterKeyData is never zeroed; it persists in heap until ARC dealloc

// HomeView.swift L229 — DEK bytes materialised for wrapping
let contentDekBytes = contentDek.withUnsafeBytes { Data($0) }
// contentDekBytes lives in heap while the surrounding async task is alive
```

Similarly, `FullScreenMediaView` holds the decrypted plaintext as `Data` in a local
variable and as a `UIImage` in the `@State private var media: MediaContent?` property.
The `@State` property is owned by SwiftUI's view store and will not be released until
the sheet is dismissed. On a jailbroken device, a memory forensics tool could extract
these plaintext bytes from the process address space while the view is open.

Apple's `CryptoKit.SymmetricKey` does not guarantee that its backing memory is zeroed
on deallocation (it is not a `SecureBytes` type). `Data` similarly offers no
deterministic zeroing.

**Impact**: On a device with a jailbreak and a memory forensics tool (e.g. Frida),
an attacker can scan the process heap for 32-byte patterns matching AES keys. The window
of vulnerability extends from the time the key is materialised to the time ARC
deallocates the buffer — this can be seconds to minutes during an active upload or
view session.

**Recommended remediation:**
1. For `Data` buffers holding key bytes: replace raw `Data` with a custom
   `SecureBytes` wrapper backed by `mlock`-protected memory that overwrites its
   contents in `deinit`.
2. For `SymmetricKey`: use the key directly without materialising to `Data` wherever
   possible (many CryptoKit operations accept `SymmetricKey` directly).
3. For decrypted plaintext in `FullScreenMediaView`: nil out the `media` state
   property on view disappearance (`onDisappear` / `.background` scene-phase change)
   to trigger ARC release sooner, and avoid holding the plaintext `UIImage` beyond
   the display lifetime.
4. Explicitly overwrite intermediate `Data` buffers containing DEK bytes before they
   fall out of scope:

```swift
var dekBytes = contentDek.withUnsafeBytes { Data($0) }
defer { dekBytes.resetBytes(in: 0..<dekBytes.count) }
```

Note: `Data.resetBytes(in:)` is available since iOS 8 and fills with zeros.

---

### iOS-06 — No certificate pinning

| Field | Value |
|---|---|
| **Finding ID** | iOS-06 |
| **Severity** | MEDIUM |
| **Location** | `Sources/HeirloomsCore/Networking/HeirloomsAPI.swift` L24–27 |
| **Android comparison** | Directly equivalent to Android finding A-03 (OkHttpClient has no CertificatePinner) |

`HeirloomsAPI` is initialised with `URLSession.shared` and no custom
`URLSessionDelegate`. No `urlSession(_:didReceive:completionHandler:)` override
validates the server certificate against a pinned public key hash:

```swift
public init(
    baseURL: URL = HeirloomsAPI.defaultBaseURL,
    session: URLSession = .shared
) {
```

`BackgroundUploadManager` also uses a `URLSessionConfiguration.background` session
with no certificate validation delegate.

An attacker who installs a rogue CA on the device (easily done on personal devices
via MDM or manual trust store manipulation) can intercept TLS connections to
`api.heirlooms.digital` and observe session tokens in `X-Api-Key` headers, as well
as all metadata requests (upload lists, plot membership, item DEK wrappers in
base64). Because all media is E2EE, ciphertext content remains protected even if TLS
is broken. However, a stolen session token grants full API access until its 90-day
TTL expires, including the ability to fetch wrapped DEKs and attempt offline
decryption if the master key is ever compromised separately.

**Recommended remediation:** Implement `URLSessionDelegate.urlSession(_:didReceive:completionHandler:)`
on a custom session delegate class and validate the server's certificate chain against
a pinned SHA-256 public key hash for `api.heirlooms.digital`. Coordinate certificate
rotation with app releases (publish a rotation notice at least 30 days in advance;
include the new pin alongside the old one). Consider TrustKit as a drop-in library.

---

### iOS-07 — No biometric gate on vault unlock

| Field | Value |
|---|---|
| **Finding ID** | iOS-07 |
| **Severity** | MEDIUM |
| **Location** | `HeirloomsApp/App.swift`, `HeirloomsApp/Views/HomeView.swift` — no `LAContext` or `LocalAuthentication` import found |
| **Android comparison** | Equivalent to Android finding A-02 (Keystore key not gated behind biometric) |

The app transitions directly from "session token found in Keychain" to displaying
the vault grid (`HomeView`) without requiring any biometric or passcode challenge.
The Keychain items (master key, plot key, session token) are accessible to any code
running in the app process once the device is unlocked — there is no `LAContext`
gate enforcing user presence before vault content is decrypted.

The P-256 sharing key does use `SecAccessControlCreateWithFlags`, but the `.privateKeyUsage`
flag alone does not require biometric confirmation; it restricts the key to signing/ECDH
operations but does not force a user-present challenge. To require biometric
authentication on each use of the Secure Enclave key, the `.biometryCurrentSet` or
`.userPresence` flag must also be included.

**Impact**: On an unlocked device, any code running in the app process (including code
injected via a debug session, Frida, or a compromised dependency) can call
`KeychainManager.getPlotKey()` and `KeychainManager.getMasterKey()` without any
additional authentication. A stolen unlocked device gives full vault access.

**Recommended remediation:**
1. Add a biometric prompt (`LAContext.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics)`)
   on cold launch and after the app returns from background.
2. For the Secure Enclave key specifically, add `.userPresence` or `.biometryCurrentSet`
   to the `SecAccessControlCreateWithFlags` call — this forces a biometric or passcode
   challenge whenever the key is used for ECDH.
3. Track this as part of SEC-015 (`tasks/queue/SEC-015_biometric-gate-account-setting.md`).

---

### iOS-08 — No jailbreak detection

| Field | Value |
|---|---|
| **Finding ID** | iOS-08 |
| **Severity** | MEDIUM |
| **Location** | Entire `HeirloomsiOS/` — no jailbreak detection found |
| **Android comparison** | Equivalent to Android finding A-04 (no root detection) |

This finding was documented in the earlier SEC-003 review as `I-01`. It is
reproduced here for completeness.

The app performs no jailbreak detection. On a jailbroken device:
- The Keychain isolation guarantees may be bypassed by frameworks such as Elcomsoft
  iOS Forensic Toolkit or Frida.
- The Secure Enclave still protects the P-256 private key (hardware-bound), but
  software-stored items (session token, master key bytes, plot key bytes as `Data`)
  can be extracted from process memory.

**Recommended remediation:** Implement lightweight jailbreak heuristics at vault
open time: check for the presence of `/Applications/Cydia.app`, `/usr/bin/ssh`,
`/bin/bash` outside the sandbox, and the ability to write to `/private/`. Additionally
use the DeviceCheck or AppAttest framework for server-side device integrity attestation.

---

### iOS-09 — No screenshot/screen-capture detection

| Field | Value |
|---|---|
| **Finding ID** | iOS-09 |
| **Severity** | LOW |
| **Location** | `HeirloomsApp/Views/FullScreenMediaView.swift` — no `UIScreen.capturedDidChangeNotification` or `UIApplicationUserDidTakeScreenshotNotification` subscription |
| **Android comparison** | Equivalent to Android finding A-05 (FLAG_SECURE not set); SEC-009 partially addresses A-05 on Android |

This finding was documented in SEC-003 as `I-02`. It is reproduced here for
completeness and to note its relationship to iOS-02 (background privacy).

`FullScreenMediaView` does not monitor `UIScreen.main.isCaptured` and does not
subscribe to `UIApplicationUserDidTakeScreenshotNotification`. Decrypted vault
content can be screen-recorded or shared via AirPlay without any in-app response.

iOS cannot prevent screenshots, but it can respond to them. SEC-014 tracks the
background privacy screen implementation; screen-capture detection should be
included in the same implementation task.

---

### iOS-10 — No sensitive data logged

| Field | Value |
|---|---|
| **Finding ID** | iOS-10 |
| **Severity** | INFO (no finding) |
| **Location** | All `HeirloomsiOS/` Swift sources |
| **Android comparison** | Equivalent to Android INFO finding A-08 |

All `print()` calls in production code log only operational/error messages:
- `[BackgroundUploadManager]`: upload success/failure, HTTP status codes, storage keys.
  (`storageKey` is a server-assigned blob reference, not a cryptographic secret.)
- `[HomeView]`: item load failure, video export failure, upload failure descriptions.

No session tokens, Keychain values, master keys, DEK bytes, or cryptographic material
are logged anywhere in the codebase.

---

### iOS-11 — Cleartext temp file handling for video decryption

| Field | Value |
|---|---|
| **Finding ID** | iOS-11 |
| **Severity** | LOW |
| **Location** | `HeirloomsApp/Views/FullScreenMediaView.swift` L134–138 |
| **Android comparison** | No direct Android equivalent found in SEC-003 |

When decrypting a video for playback, `FullScreenMediaView` writes the full decrypted
plaintext to a file in `FileManager.default.temporaryDirectory`:

```swift
let tempURL = FileManager.default.temporaryDirectory
    .appendingPathComponent(item.id)
    .appendingPathExtension(ext)
try plaintext.write(to: tempURL)
media = .video(tempURL)
```

This file is never explicitly deleted. The `media` state variable holds a reference
to the URL but does not trigger cleanup on `onDisappear`. The decrypted video remains
on disk in the temporary directory indefinitely until the OS purges it (which may be
hours or days later).

On a non-jailbroken device, temporary files are protected by Data Protection under
the `.completeUntilFirstUserAuthentication` class by default (not `.complete`). On a
jailbroken device, these files are directly readable.

**Recommended remediation:** Register an `onDisappear` handler in `FullScreenMediaView`
that explicitly deletes the temp file when the view is dismissed:

```swift
.onDisappear {
    if case .video(let url) = media {
        try? FileManager.default.removeItem(at: url)
    }
    media = nil
}
```

---

## Findings Summary Table

| ID | Severity | Title | Follow-on task |
|----|----------|-------|---------------|
| iOS-02 | HIGH | No privacy overlay on backgrounding | SEC-014 (pre-existing) |
| iOS-03 | HIGH | No main app Info.plist — ATS domain restriction absent | SEC-016 (new) |
| iOS-05 | HIGH | DEK and master key bytes not explicitly zeroed after use | SEC-017 (new) |
| iOS-06 | MEDIUM | No certificate pinning | — (accepted, matches A-03) |
| iOS-07 | MEDIUM | No biometric gate on vault unlock | SEC-015 (pre-existing) |
| iOS-08 | MEDIUM | No jailbreak detection | — (accepted, matches I-01/A-04) |
| iOS-11 | LOW | Decrypted video temp file never deleted | SEC-017 (new, included) |
| iOS-09 | LOW | No screenshot/screen-capture detection | SEC-014 (pre-existing) |
| iOS-01 | INFO | Keychain access flags correctly set | — |
| iOS-04 | INFO | Share extension Keychain access appropriate | — |
| iOS-10 | INFO | No sensitive data logged | — |

---

## New Tasks Created

| Task | Findings addressed | Priority |
|------|--------------------|----------|
| [SEC-016](../../tasks/queue/SEC-016_ios-ats-infoplist.md) | iOS-03 (no committed Info.plist, ATS domain restriction absent) | High |
| [SEC-017](../../tasks/queue/SEC-017_ios-memory-zeroing-temp-file-cleanup.md) | iOS-05 (DEK bytes not zeroed), iOS-11 (decrypted video temp file never deleted) | High |

Pre-existing tasks that address other HIGH findings:
- **SEC-014** (`tasks/queue/SEC-014_ios-vault-background-privacy-screen.md`) — iOS-02, iOS-09

---

## Overall Security Posture Assessment

The iOS client's cryptographic foundations are well-designed. The Keychain
configuration (`kSecAttrAccessibleWhenUnlockedThisDeviceOnly` everywhere, Secure
Enclave for the device keypair) is exemplary and exceeds the standard for mobile
consumer apps. The envelope crypto implementation is correct and consistent with
the spec.

The three HIGH findings all have a common theme: the app correctly protects data at
rest (Keychain, on-disk encryption) but does not defend against an attacker with
physical access to an unlocked device. A person who briefly holds an unlocked iPhone
displaying vault content can: (1) see the vault in the App Switcher, (2) screen-record
it, and (3) find decrypted video sitting in the temp directory. These are addressable
with low-complexity changes.

The absent `Info.plist` (iOS-03) is a process gap as much as a security one — it
indicates the main app target's configuration is not under source control, which means
ATS settings could silently regress across Xcode upgrades or when onboarding a new
developer. Adding a committed `Info.plist` is a one-time fix.

Memory zeroing (iOS-05) is a defence-in-depth measure relevant primarily to jailbroken
devices. It requires modest engineering effort and is good security hygiene for a
privacy-first product.

Certificate pinning and biometric gating remain medium-term priorities and are
consistent with the Android posture established in SEC-003.
