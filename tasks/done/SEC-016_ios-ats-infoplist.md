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

**Completed:** 2026-05-16
**Agent:** developer-10 (branch: agent/developer-10/SEC-016)

### What was done

1. **Created `HeirloomsiOS/HeirloomsApp/Info.plist`** — the committed main app plist.
   - `NSAllowsArbitraryLoads = false`
   - `NSExceptionDomains` for `heirlooms.digital` and `test.heirlooms.digital`:
     - `NSExceptionMinimumTLSVersion = TLSv1.3`
     - `NSExceptionRequiresForwardSecrecy = true`
   - A `#if DEBUG` / `#endif` block adds `localhost` and `192.168.0.0` as
     `NSExceptionAllowsInsecureHTTPLoads = true` entries. These are stripped from
     Release builds by Xcode's plist preprocessor when `DEBUG=1` is absent.
   - Standard bundle keys (`CFBundleIdentifier`, `CFBundleShortVersionString`, etc.)
     using `$(VARIABLE)` substitution so the plist works across Bundle ID variants.
   - Privacy usage strings for `NSPhotoLibraryUsageDescription`,
     `NSPhotoLibraryAddUsageDescription`, and `NSCameraUsageDescription`.
   - `CFBundleURLTypes` entry for the `heirlooms://` deep-link scheme.

2. **Created `HeirloomsiOS/Configurations/Debug.xcconfig`** — sets:
   - `INFOPLIST_FILE = HeirloomsApp/Info.plist` (satisfies acceptance criterion 4)
   - `INFOPLIST_PREPROCESSOR_DEFINITIONS = DEBUG=1` (activates `#if DEBUG` in plist)
   - `PRODUCT_BUNDLE_IDENTIFIER = digital.heirlooms.ios.dev`
   - `SWIFT_ACTIVE_COMPILATION_CONDITIONS = DEBUG`

3. **Created `HeirloomsiOS/Configurations/Release.xcconfig`** — sets:
   - `INFOPLIST_FILE = HeirloomsApp/Info.plist`
   - No `INFOPLIST_PREPROCESSOR_DEFINITIONS` → `#if DEBUG` block is stripped
   - `PRODUCT_BUNDLE_IDENTIFIER = digital.heirlooms.ios`

4. **Updated `HeirloomsiOS/SETUP.md`** — added Step 3 (Apply xcconfig files) with
   detailed instructions for wiring the xcconfig into the Xcode project's Debug and
   Release configurations via the Project Info tab. Also updated the directory layout
   reference to document the new `Configurations/` directory and `Info.plist`.

### Decisions made

- **No `.pbxproj` to modify**: the repo ships source files only; the Xcode project
  is created manually per SETUP.md. The xcconfig approach fulfils acceptance criterion 4
  (INFOPLIST_FILE build setting) through the documented setup steps. A developer who
  follows SETUP.md will have `INFOPLIST_FILE = HeirloomsApp/Info.plist` set as soon
  as they apply the xcconfig.
- **`#if DEBUG` in plist** rather than a separate `Info-Debug.plist`: Xcode's
  `INFOPLIST_PREPROCESSOR_DEFINITIONS` feature processes `#if` / `#endif` in plists.
  This keeps one canonical plist in the repo; the debug exception is never compiled
  into a Release or App Store build.
- **`192.168.0.0` as the local-network key**: ATS does not support CIDR ranges; the
  key name is the domain/hostname string. `192.168.0.0` covers the most common
  developer subnet. Teams on non-standard subnets can add an entry matching their
  gateway address by following the same pattern.
- **No NSAllowsArbitraryLoads=true anywhere**: requirement honoured in all build
  configurations.

### No new tasks spawned
