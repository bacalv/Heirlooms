---
id: SEC-013
title: iOS client security review — parity with SEC-003
category: Security
priority: Medium
status: queued
assigned_to: SecurityManager
depends_on: []
touches:
  - docs/security/
  - HeirloomsiOS/
estimated: half day (audit only, no remediation required)
---

## Goal

Conduct a security audit of the iOS client equivalent to SEC-003 (Android client security review). Produce a findings document. No immediate remediation required — findings feed into new SEC-NNN tasks.

## Background

SEC-003 audited the Android client and produced findings A-01 through A-05. No equivalent audit exists for `HeirloomsiOS/`. The Swift/SwiftUI client handles:
- Master key operations via CryptoKit (P-256, AES-GCM, HKDF)
- Keychain storage of the master key and session token
- Plaintext media display in memory
- QR scanner (IOS-001 completed)
- Share extension (IOS-002 completed)

## Scope

Audit for:
1. **Keychain access flags**: are keys stored with appropriate `kSecAttrAccessible` values? Should be `.biometryCurrentSet` or `.afterFirstUnlock`, not `.always`.
2. **Background state data protection**: does the app overlay a privacy screen when backgrounding? (SEC-014 tracks the implementation — this audit documents the gap.)
3. **ATS (App Transport Security)**: is the `NSAppTransportSecurity` configuration in `Info.plist` correctly locked to `heirlooms.digital` and `test.heirlooms.digital`?
4. **Share extension isolation**: does the share extension have access to the main app's Keychain group? Is that appropriate?
5. **Memory handling**: does the app clear sensitive data (master key bytes, DEK bytes) from memory after use, or does it let ARC handle it?
6. **Certificate pinning**: is TLS certificate pinning implemented? Should it be?

## Output

A findings document at `docs/security/SEC-013_ios-client-review.md` with:
- Finding ID (iOS-01, iOS-02, …)
- Severity (Critical / High / Medium / Low / Info)
- Description and location in code
- Recommended remediation
- Comparison note to equivalent Android finding where applicable

Spawn follow-on tasks for any Critical or High findings.

## Acceptance criteria

- Findings document produced and committed
- Any Critical or High findings have corresponding SEC-NNN tasks created and queued

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
