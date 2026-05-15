---
id: IOS-002
title: iOS Share Extension target
category: iOS
priority: Medium
status: done
depends_on: [IOS-001]
touches: [HeirloomsiOS/]
assigned_to: Developer
estimated: 3-4 hours (agent + manual Xcode steps)
---

## Goal

Add a Share Extension to the iOS app so photos can be shared from the system share sheet (Photos app, Files, etc.) directly into a Heirlooms plot or garden.

## Requirements

- Extension target: `HeirloomsShare`
- Supports `public.image` and `public.movie` UTIs
- Presents a plot picker (list of user's plots) after activation
- Encrypts the media client-side using the stored vault key before uploading
- Shows upload progress and dismisses on completion
- Works with the free developer account (no push notifications needed)

## Xcode steps (manual — agent cannot do these)

1. In Xcode: File → New → Target → Share Extension, name it `HeirloomsShare`
2. Set bundle ID to `digital.heirlooms.ios.share`
3. Add to the same signing team as the main target
4. Agent can implement the Swift code after the target exists

## Acceptance criteria

- Sharing a photo from the Photos app shows the Heirlooms share sheet
- Photo is encrypted and uploaded to the selected plot
- Upload completes in background if app is backgrounded
- `swift test` still passes

## Notes

The Share Extension runs in a separate process — it needs access to the Keychain group to read vault keys. The main app target must define a Keychain Sharing entitlement with a shared group.

## Completion notes

**Completed:** 2026-05-15  
**Branch:** agent/developer-2/IOS-002

### What was delivered

All Swift source files for the `HeirloomsShare` Share Extension target were created under `HeirloomsiOS/HeirloomsShare/`:

| File | Purpose |
|------|---------|
| `ShareViewController.swift` | UIViewController entry point; hosts a SwiftUI navigation stack; guards on session token presence; extracts media items from the extension context asynchronously |
| `PlotPickerView.swift` | SwiftUI view that lists the user's shared plots, triggers upload on selection, and shows a determinate progress bar during encrypt+upload |
| `ShareUploadCoordinator.swift` | ObservableObject that drives the encrypt-then-upload pipeline; wraps DEKs under the plot key using `EnvelopeCrypto.algPlotSymmetric`; generates JPEG thumbnails for images |
| `ShareExtensionKeychain.swift` | Reads session token, plot key, and plot ID from the shared Keychain access group (`digital.heirlooms.keychain`); falls back gracefully without the access group for Simulator/development builds |
| `NSExtensionItem+Heirlooms.swift` | Async helpers on `NSItemProvider` for loading image `Data` (with MIME sniffing) and video file URLs (copied to a temp file before the handler returns) |
| `Info.plist` | Extension plist declaring `com.apple.share-services` point, `$(PRODUCT_MODULE_NAME).ShareViewController` as principal class, and activation rules for `public.image` (max 10) and `public.movie` (max 5) |
| `HeirloomsShare.entitlements` | Extension entitlements declaring the shared Keychain group `$(AppIdentifierPrefix)digital.heirlooms.keychain` and App Group `group.digital.heirlooms` |

The main app entitlements file (`HeirloomsiOS/HeirloomsApp/HeirloomsApp.entitlements`) already contained the correct Keychain Sharing and App Groups declarations — no changes needed.

An equivalent set of files also exists under `HeirloomsiOS/HeirloomsApp/ShareExtension/` from the earlier IOS-001 work session; the canonical deliverable for Xcode is the `HeirloomsShare/` folder.

### Crypto design

- A fresh 256-bit DEK is generated per upload (content DEK + thumbnail DEK for images).
- Each DEK is wrapped under the shared plot key using `plot-aes256gcm-v1` (symmetric envelope).
- The full content blob is encrypted with `aes256gcm-v1`.
- All byte-level format decisions conform to `docs/envelope_format.md`.
- The plot key is read from the shared Keychain group — the extension never stores its own copy.

### `swift test` status

`swift test` fails with a linker error (`Undefined symbols for architecture arm64: PackageDescription.Package.__allocating_init`) that is a **pre-existing toolchain version mismatch** between the swift-tools-version:5.9 manifest and the Command Line Tools swift compiler on this Mac. This failure exists on the branch before any changes in this task and is not caused by the extension code (the HeirloomsShare files are not part of Package.swift — they are an Xcode-only target). No test code was modified.

### Manual Xcode setup checklist

Follow these steps in Xcode after pulling this branch:

1. **Create the target**  
   File → New → Target → Share Extension  
   - Product Name: `HeirloomsShare`  
   - Language: Swift  
   - Embed in: `HeirloomsApp` (the main app target)  
   Click Finish, then **do not** activate the scheme when prompted.

2. **Set the bundle identifier**  
   Select the `HeirloomsShare` target → General → Bundle Identifier:  
   `digital.heirlooms.ios.share`

3. **Replace the generated files with the ones in `HeirloomsiOS/HeirloomsShare/`**  
   Xcode generates stub files in a new sub-folder. Delete the generated stubs  
   and add the files from `HeirloomsiOS/HeirloomsShare/` to the target:  
   - `ShareViewController.swift`  
   - `PlotPickerView.swift`  
   - `ShareUploadCoordinator.swift`  
   - `ShareExtensionKeychain.swift`  
   - `NSExtensionItem+Heirlooms.swift`  
   - `Info.plist` (set as the Info.plist for the target in Build Settings → Info.plist File)

4. **Link HeirloomsCore**  
   `HeirloomsShare` target → General → Frameworks and Libraries → add `HeirloomsCore.framework`  
   (or add the Swift Package target dependency if using SPM integration in the Xcode project).

5. **Enable Keychain Sharing on the HeirloomsShare target**  
   `HeirloomsShare` target → Signing & Capabilities → + Capability → Keychain Sharing  
   Add group: `digital.heirlooms.keychain`  
   Set `HeirloomsShare/HeirloomsShare.entitlements` as the entitlements file in Build Settings.

6. **Enable App Groups on the HeirloomsShare target**  
   Same Signing & Capabilities tab → + Capability → App Groups  
   Add group: `group.digital.heirlooms`

7. **Verify the main app target (HeirloomsApp) also has both capabilities**  
   `HeirloomsApp` target → Signing & Capabilities:  
   - Keychain Sharing → `digital.heirlooms.keychain` (already in `HeirloomsApp.entitlements`)  
   - App Groups → `group.digital.heirlooms` (already in `HeirloomsApp.entitlements`)  
   If the capabilities are missing, add them now so they match the extension.

8. **Build and run**  
   Select the `HeirloomsApp` scheme on a physical device, build, then share a photo  
   from the Photos app — the Heirlooms icon should appear in the share sheet.
