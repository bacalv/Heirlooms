---
id: FEAT-003
title: Android account pairing / recovery — pair a fresh Android install to an existing account
category: Feature
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/
assigned_to: Developer
estimated: 3–5 hours
---

## Background

Clearing app data on Android (or a fresh install on a new device) leaves the user on the
registration screen with no visible path to sign in to an existing account. The only current
recovery paths are:

- **Web pairing (QR code)** — requires an active web session on the same account, which in
  turn requires the passphrase-wrapped backup to be configured.
- **Recovery phrase** — 24-word phrase shown at first setup; must be recorded in advance.

If neither was set up (e.g. an Android-only user who never opened the web client), clearing
storage is permanently destructive. This was surfaced during BUG-016 staging retest (2026-05-15)
when Fire 1's app data was cleared and there was no way back in.

## Goal

Investigate and implement a pairing code flow that lets a fresh Android install re-authenticate
to an existing account without needing the web client — the reverse of the current web QR
pairing flow.

## Investigation questions

1. **What does "sign in" mean in our E2EE model?** The master key never leaves a device in
   plaintext, so a username/password login alone cannot restore vault access. Any pairing flow
   needs a trusted device (or the server-held passphrase blob) to hand off the master key.

2. **Existing pairing mechanism (web → Android):** A trusted device (web) generates a
   short-lived QR code; the new device scans it to initiate key exchange. Can this be
   inverted so that the trusted device is Android and the new device shows a code?

3. **Passphrase recovery path:** If the user has a passphrase-wrapped backup on the server,
   should the registration/login screen offer a "Recover with passphrase" option that
   bypasses the need for a trusted device entirely?

4. **Server support:** Does any new endpoint need to be added, or can the existing device
   pairing endpoint be reused?

## Proposed direction

Implement one or both of:

**Option A — "Recover with passphrase" on Android login screen**
Add a secondary button below the registration form: "Already have an account? Recover with
passphrase." This prompts for email + passphrase, derives the master key via Argon2id
(matching the web flow), and restores vault access. Requires the user to have set up a
passphrase previously.

**Option B — Android-initiated pairing code**
On the fresh Android install, show a short-lived numeric code (or QR). The user enters
this on a trusted device (web or another Android) to initiate the key-exchange handshake.
Mirror of the current web QR flow.

Option A is lower effort and covers the most common case. Option B is more robust but
requires both ends to support the new flow.

## Acceptance criteria

- A user who clears Android app data (or installs fresh) can recover their existing account
  without registering a new one
- The recovery path is visible on the Android login/registration screen
- Recovery restores the master key and sharing keypair correctly
- Existing registration flow is unaffected

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
