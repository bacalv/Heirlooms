---
id: IOS-002
title: iOS Share Extension target
category: iOS
priority: Medium
status: queued
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
