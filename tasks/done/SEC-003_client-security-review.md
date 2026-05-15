---
id: SEC-003
title: Client security flaw testing plan
category: Security
priority: Medium
status: done
depends_on: [SEC-001]
touches: [HeirloomsApp/, HeirloomsWeb/, HeirloomsiOS/]
assigned_to: SecurityManager
estimated: 1-2 days (research + agent)
---

## Goal

Identify and test for security flaws in the Android, web, and iOS clients. Focus on areas where client-side mistakes could expose user data even if the server is secure.

## Areas to review

### Android
- **Key storage**: Are vault keys stored in Android Keystore (hardware-backed) or SharedPreferences?
- **Cleartext traffic**: Is `android:usesCleartextTraffic` disabled?
- **Screenshot prevention**: Is `FLAG_SECURE` set on activity windows? (Prevents screenshots of photos)
- **Root detection**: Should the app refuse to run on rooted devices?
- **Backup**: Is `android:allowBackup="false"` set? (ADB backup would expose vault keys)
- **Certificate pinning**: Does the app pin the server's TLS certificate?
- **Biometric gate**: Is vault key access gated behind biometric auth?
- **Log scrubbing**: Are auth tokens or keys ever logged (even at DEBUG level)?

### Web
- **Key storage**: Are vault keys in localStorage (bad) or sessionStorage or memory only?
- **XSS surface**: Is all rendered content sanitised? Does the app use `dangerouslySetInnerHTML`?
- **CSP header**: Is a Content-Security-Policy header set on the web app?
- **HTTPS only**: Are all API calls HTTPS? Any mixed content?
- **Token storage**: Is the session token in an httpOnly cookie or localStorage?

### iOS
- **Keychain access control**: Are vault keys stored with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`?
- **ATS (App Transport Security)**: Is any domain exempted from ATS?
- **Jailbreak detection**: Should the app detect jailbroken devices?
- **Screenshot notification**: iOS notifies apps of screenshots — is sensitive content masked?

## Deliverables

1. Findings report: `docs/security/client-security-findings.md`
2. New tasks in `queue/` for any HIGH findings that need fixing
3. Test cases added to relevant test suites for any verifiable invariants

## Notes

This task is research + code audit. No automated penetration testing tools needed.
Focus on the E2EE threat model: the most likely attack surface is client-side key extraction, not server compromise.

## Completion notes

**Completed:** 2026-05-15 by SecurityManager

**Findings report:** `docs/security/client-security-findings.md`

**Summary by client:**

**Android** — Cryptographic architecture is sound. Vault master key and device private key are
Keystore-wrapped. `android:allowBackup="false"` is set. No sensitive data logged. Three MEDIUM and
one HIGH finding: (HIGH) session token in plaintext SharedPreferences; (MEDIUM) Keystore key has no
biometric gate; (MEDIUM) no FLAG_SECURE on windows; (MEDIUM) no root detection; (MEDIUM) no
certificate pinning. Cleartext traffic is scoped to private/loopback addresses only (low risk, noted).

**Web** — Two HIGH findings that together represent a significant XSS-to-session-compromise path:
session token stored in localStorage (not httpOnly cookie), and no Content-Security-Policy header.
The vault master key and device private key are correctly held in memory / non-extractable IndexedDB
CryptoKey objects respectively. No XSS vectors found in current code. No mixed content. No
sensitive data logged.

**iOS** — Exemplary Keychain usage: `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` throughout,
Secure Enclave for device keypair. ATS not weakened. No sensitive data logged. Two MEDIUM findings:
no jailbreak detection; no certificate pinning. One LOW finding: no `UIScreen.capturedDidChange`
handling for screen recording detection.

**Tasks created:**
- [SEC-007](SEC-007_android-session-token-keystore.md) — Android: Encrypt session token with Keystore (HIGH)
- [SEC-008](SEC-008_web-csp-session-token.md) — Web: Add CSP header + migrate session token off localStorage (HIGH)
- [SEC-009](SEC-009_android-biometric-flag-secure.md) — Android: Add FLAG_SECURE + biometric vault-unlock gate (MEDIUM)

**Verifiable invariants for test suite:**
1. Android: `DeviceKeyManager.loadMasterKey()` must return the same bytes that were stored via
   `setupVault()` — round-trip test through Keystore encrypt/decrypt (already testable via unit test
   with a `KeyStore` mock or instrumented test on device).
2. Android: SharedPreferences XML for `heirloom_prefs` must not contain the plaintext session token
   value after SEC-007 is implemented — verify in instrumented test by reading the raw prefs file.
3. Web: `localStorage.getItem('heirlooms_session_token')` must return `null` after login once SEC-008
   is implemented — add assertion to `auth.test.jsx`.
4. Web: CSP header presence — Playwright E2E test (TST-004) should assert `Content-Security-Policy`
   header is set on the HTML response.
5. iOS: All Keychain items must use `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` — enforce via a
   unit test that reads the Keychain item attributes and asserts the accessibility class.
