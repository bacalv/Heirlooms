---
id: BUG-001
title: Keychain slot collision — master key vs plot key
category: Bug Fix
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsiOS/HeirloomsApp/Views/ActivateView.swift
  - HeirloomsiOS/Sources/HeirloomsCore/Crypto/KeychainManager.swift
assigned_to: Developer
estimated: 1-2 hours (agent)
---

## Problem

`KeychainManager.savePlotKey()` / `getPlotKey()` is used for two different things:

1. **At friend-invite activation** (`handleFriendInvite`): the user's master key (32-byte symmetric) is stored here with the comment "repurposed slot until plot key is set".
2. **After joining a shared plot** (`handlePlotInvite`): the shared plot key overwrites the same slot.

Consequence: after the user joins a shared plot, `getPlotKey()` returns the **plot key**, not the master key. The web-session pairing flow (`handleWebPairing`) reads `getPlotKey()` expecting the master key — so pairing wraps the wrong key if the user has already joined a plot.

## Fix

Add a dedicated `saveMasterKey()` / `getMasterKey()` pair to `KeychainManager` (separate Keychain item with a distinct `kSecAttrService` / account label).

Update `handleFriendInvite` to store the master key via `saveMasterKey()` instead of `savePlotKey()`.

Update `handleWebPairing` to read from `getMasterKey()` instead of `getPlotKey()`.

## Acceptance criteria

- `KeychainManager` has `saveMasterKey(_:)`, `getMasterKey()`, `deleteMasterKey()`.
- `handleFriendInvite` stores master key to the new slot.
- `handleWebPairing` reads from the new slot.
- The old `savePlotKey` slot is used exclusively for plot keys.
- `swift test` passes (unit tests for the new Keychain methods added).