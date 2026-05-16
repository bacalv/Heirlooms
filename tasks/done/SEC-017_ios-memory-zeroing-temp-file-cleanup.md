---
id: SEC-017
title: iOS — zero DEK byte buffers after use and delete decrypted video temp files
category: Security
priority: High
status: queued
assigned_to: Developer
depends_on: [SEC-013]
touches:
  - HeirloomsiOS/HeirloomsApp/Views/
  - HeirloomsiOS/HeirloomsShare/
  - HeirloomsiOS/Sources/HeirloomsCore/Crypto/
estimated: half day
---

## Goal

1. Explicitly zero `Data` buffers that hold raw DEK and master key bytes after use.
2. Delete the decrypted video temp file when `FullScreenMediaView` is dismissed.

## Background

SEC-013 (iOS client security review) identified two related findings:

**iOS-05 (HIGH):** In multiple locations, raw key bytes are extracted from `SymmetricKey` instances into `Data` buffers via `withUnsafeBytes { Data($0) }`. These buffers are released to ARC rather than explicitly zeroed. On a jailbroken device, a memory forensics tool (Frida, etc.) can scan the process heap for 32-byte AES key patterns while the buffer is alive. This window extends for seconds to minutes during upload and view operations.

Affected locations:
- `HeirloomsApp/Views/ActivateView.swift` L196 (master key), L252 (plot key)
- `HeirloomsApp/Views/HomeView.swift` L229, L235 (DEK bytes)
- `HeirloomsShare/ShareUploadCoordinator.swift` L125, L131, L197
- `Sources/HeirloomsCore/Crypto/EnvelopeCrypto.swift` L216 (DEK bytes in wrapDEK)

**iOS-11 (LOW):** `FullScreenMediaView` writes decrypted video plaintext to `FileManager.default.temporaryDirectory` for `AVPlayer` playback but never deletes the file when the view is dismissed. The cleartext video persists on disk until the OS purges temp files.

Affected location:
- `HeirloomsApp/Views/FullScreenMediaView.swift` L134–138

## Acceptance criteria

### DEK zeroing (iOS-05)

1. All `Data` buffers created by `withUnsafeBytes { Data($0) }` that hold DEK or master key bytes include a `defer { buffer.resetBytes(in: 0..<buffer.count) }` guard immediately after creation.
2. Alternatively (preferred where possible): use `SymmetricKey` directly in CryptoKit operations without materialising to `Data`, eliminating the need for the buffer entirely.
3. `EnvelopeCrypto.wrapDEK` — the `let dekBytes = dek.withUnsafeBytes { Data($0) }` line should zero `dekBytes` after the encrypt call.

### Temp file cleanup (iOS-11)

4. `FullScreenMediaView` registers an `onDisappear` modifier that:
   a. Explicitly deletes the video temp file from disk.
   b. Sets `media = nil` to release the `UIImage` or `AVPlayer` URL from the SwiftUI state store.

## Notes

- `Data.resetBytes(in:)` is the standard Swift API for zeroing a contiguous byte range in-place. It has been available since iOS 8.
- Swift `SymmetricKey` does not guarantee zeroing on deallocation — explicit zeroing of any materialised `Data` is required.
- The `media = nil` assignment on disappear also addresses the secondary memory concern for decrypted images (held as `UIImage` in the `MediaContent.image` case).
- For the video temp file, the file URL path uses `item.id` as the filename — be careful not to collide if the same item is opened twice; consider using `UUID().uuidString` as the temp filename instead (this is already done in the upload paths).

## Completion notes

**Completed:** 2026-05-16
**Agent:** developer-11 (branch agent/developer-11/SEC-017)

### Fix 1 — DEK zeroing (iOS-05 HIGH)

Applied `var` + `defer { buffer.resetBytes(in: 0..<buffer.count) }` immediately after each `Data` buffer creation holding raw key bytes. All six affected call sites were patched:

1. **`HeirloomsiOS/HeirloomsApp/Views/ActivateView.swift`** (L196, L253):
   - `masterKeyData` (master key → Keychain save): changed `let` → `var`, added defer zeroing.
   - `plotKeyData` (plot key → Keychain save): changed `let` → `var`, added defer zeroing.

2. **`HeirloomsiOS/HeirloomsApp/Views/HomeView.swift`** (L229, L235):
   - `contentDekBytes` and `thumbDekBytes` (DEK wrapping for upload): changed `let` → `var`, added defer zeroing for each.

3. **`HeirloomsiOS/HeirloomsShare/ShareUploadCoordinator.swift`** (L125, L131, L197):
   - `contentDekBytes` and `thumbDekBytes` in `encryptAndUploadImage`: changed `let` → `var`, added defer zeroing for each.
   - `contentDekBytes` in `encryptAndUploadVideo`: changed `let` → `var`, added defer zeroing.

4. **`HeirloomsiOS/Sources/HeirloomsCore/Crypto/EnvelopeCrypto.swift`** (L216 in `wrapDEK`):
   - `dekBytes` (raw DEK bytes before AES-GCM encrypt): changed `let` → `var`, added defer zeroing.

Note: Eliminating the `Data` materialisation entirely was not possible here because `wrapSymmetric` and `KeychainManager.saveMasterKey/savePlotKey` accept `Data` parameters, not `SymmetricKey` directly. The defer-zeroing approach is the correct pragmatic fix.

### Fix 2 — Video temp file cleanup (iOS-11 LOW)

**`HeirloomsiOS/HeirloomsApp/Views/FullScreenMediaView.swift`** (L134–139 and body):

- Changed temp file name from `item.id` to `UUID().uuidString` to prevent collisions if the same item is opened twice.
- Added `.onDisappear` modifier chained after `.sheet(isPresented:)` that:
  - Pattern-matches `.video(let url)` from `media` and calls `try? FileManager.default.removeItem(at: url)` to delete the cleartext file.
  - Sets `media = nil` to release the `AVPlayer` URL (and any `UIImage` in the image case) from SwiftUI state, triggering ARC release sooner.

### Files changed

- `HeirloomsiOS/HeirloomsApp/Views/ActivateView.swift`
- `HeirloomsiOS/HeirloomsApp/Views/HomeView.swift`
- `HeirloomsiOS/HeirloomsShare/ShareUploadCoordinator.swift`
- `HeirloomsiOS/Sources/HeirloomsCore/Crypto/EnvelopeCrypto.swift`
- `HeirloomsiOS/HeirloomsApp/Views/FullScreenMediaView.swift`

No new tasks spawned. No tests were run (iOS — no Xcode build environment in agent context; changes are additive defer blocks and a modifier addition, no logic change).
