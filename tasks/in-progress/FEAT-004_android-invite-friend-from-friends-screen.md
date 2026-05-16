---
id: FEAT-004
title: Android — invite a friend from the Friends screen
category: Feature
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/social/FriendsScreen.kt
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/social/FriendsViewModel.kt
assigned_to: Developer
estimated: 2–3 hours
---

## Background

Discovered during v0.54 staging smoke test (2026-05-15). The Android Friends screen
has no way to invite a new friend. The web app has an invite-link flow (FEAT-001), but
that feature was never brought to Android.

This means an Android-only user cannot invite friends from their device — they must
use the web app, which requires a web pairing session. For users who have not set up
the web client this is a dead end.

## Goal

Add an "Invite a friend" button to the Android Friends screen that:

1. Calls `POST /api/auth/invites` to generate a short-lived invite token
2. Constructs the invite URL: `https://heirlooms.digital/invite?token={token}`
3. Opens the Android share sheet so the user can send it via any app (Messages,
   WhatsApp, email, etc.)

When the recipient opens the link:
- If they are an existing Heirlooms user: they land on the web invite-connect flow
  (already implemented via FEAT-001) and become friends with the inviter
- If they are a new user: they land on the registration page with the token
  pre-filled and become friends with the inviter on registration

## Acceptance criteria

- "Invite a friend" button is visible on the Android Friends screen
- Tapping it generates an invite token and opens the Android share sheet with the
  invite URL
- A recipient who opens the link and registers (or connects) becomes a friend of
  the inviter
- Invite token expiry (48 hours) is shown to the user before sharing

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
