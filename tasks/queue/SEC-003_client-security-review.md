---
id: SEC-003
title: Client security flaw testing plan
category: Security
priority: Medium
status: queued
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
