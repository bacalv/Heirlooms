---
id: BUG-029
title: Web pairing session not registered as a device entry — missing from Devices & Access
category: Bug Fix
priority: Medium
status: queued
depends_on: [BUG-023]
touches:
  - HeirloomsWeb/src/pages/PairPage.jsx
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/auth/AuthRoutes.kt
assigned_to: Developer
estimated: 2–3 hours
---

## Problem

After pairing a web browser to an existing account via the pairing code flow, the
web session does not appear as a separate device in Devices & Access. Only the
original Android device shows. This means:
1. The web session cannot be revoked from another device
2. SEC-011 device revocation cannot be fully tested with a paired web session

Found during TST-010 (2026-05-16), Journey 17e.

## Investigation

The pairing flow calls `POST /api/auth/pairing/qr` on the web side. Check whether
this endpoint creates a `wrapped_keys` row for the web browser's device key. If it
does not, the web device is invisible to the device list.

The `POST /api/keys/devices` conflict (BUG-023) may be the same root cause — the
pairing completion is hitting a 409, silently failing to register the device.

## Acceptance criteria

- Pairing a web browser results in a distinct device entry in `wrapped_keys`
- The web device appears in Devices & Access alongside the Android device
- The web device can be revoked from the Android Devices & Access screen
