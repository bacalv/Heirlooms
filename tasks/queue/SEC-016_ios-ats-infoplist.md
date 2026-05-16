---
id: SEC-016
title: iOS — add committed Info.plist with ATS domain restriction
category: Security
priority: High
status: queued
assigned_to: Developer
depends_on: [SEC-013]
touches:
  - HeirloomsiOS/HeirloomsApp/
estimated: 1 hour
---

## Goal

Add a committed main app `Info.plist` to the `HeirloomsiOS/HeirloomsApp/` directory and configure `NSAppTransportSecurity` to restrict the app to `heirlooms.digital` and `test.heirlooms.digital` only.

## Background

SEC-013 (iOS client security review) identified finding **iOS-03 (HIGH)**: the main `HeirloomsApp` target has no `Info.plist` checked into the repository. The only `Info.plist` files present are for the two Share Extension targets. Without a committed `Info.plist`, App Transport Security (ATS) is not explicitly configured — there is no `NSExceptionDomains` block restricting the app to the two Heirlooms domains, and ATS settings can silently regress.

The Android equivalent (`HeirloomsApp/app/src/main/res/xml/network_security_config.xml`) correctly restricts cleartext traffic to private IP ranges only.

## Acceptance criteria

1. `HeirloomsiOS/HeirloomsApp/Info.plist` exists and is committed.
2. The file contains an `NSAppTransportSecurity` block with:
   - `NSAllowsArbitraryLoads` set to `false`.
   - `NSExceptionDomains` entries for `heirlooms.digital` and `test.heirlooms.digital` with `NSExceptionMinimumTLSVersion` = `TLSv1.3` and `NSExceptionRequiresForwardSecrecy` = `true`.
3. A `#if DEBUG` build configuration override (separate scheme or xcconfig) allows `localhost` and local IP ranges for development builds only.
4. The Xcode project target's **Info.plist File** build setting points to the new committed plist.
5. The app builds and runs on device/simulator without ATS violation warnings.

## Notes

- The `NSExceptionDomains` block does not weaken ATS for these domains — it tightens it by mandating TLS 1.3 and forward secrecy rather than relying on the system default (TLS 1.2 minimum).
- Do not set `NSAllowsArbitraryLoads: true` in any build configuration.
- See SEC-013 findings document at `docs/security/SEC-013_ios-client-review.md` for the full finding and recommended XML.

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
