---
id: FEAT-004
title: Android — invite a friend from the Friends screen
category: Feature
priority: Medium
status: done
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

Implemented 2026-05-16 on branch `agent/developer-3/FEAT-004`.

### What was done

**`HeirloomsApi.kt`**
- Added `createFriendInvite(): InviteResponse` which issues `POST /api/auth/invites` and parses
  `token` + `expires_at` from the response. The existing `getInvite()` (GET) is retained unchanged.

**`FriendsViewModel.kt`** (new file)
- Sealed `InviteState` hierarchy: `Idle`, `Loading`, `Ready(inviteUrl, expiryLabel)`, `Error(message)`.
- `generateInvite(api, webBaseUrl)` — calls `createFriendInvite()`, constructs
  `$webBaseUrl/invite?token=...`, formats the expiry label, and publishes state via `StateFlow`.
- `resetInvite()` — resets state back to `Idle`.
- `formatExpiry(iso)` — pure JVM helper; converts ISO-8601 timestamp to "Expires d MMM, HH:mm".

**`FriendsScreen.kt`**
- Added `FriendsViewModel` injection via `viewModel()`.
- Added `friendsWebBaseUrl` computed property (mirrors `DevicesAccessScreen` BUG-008 fix):
  staging API URL → staging web URL; prod otherwise.
- "Invite a friend" button (full-width, Forest/Parchment styling) above the friends list.
  Shows a spinner and "Generating…" while the POST is in flight.
- Sub-label "Invite expires after 48 hours." visible at all times (satisfies expiry AC).
- `AlertDialog` shown when invite is `Ready`: displays the 48-hour expiry label and a
  "Share" button that fires `Intent.ACTION_SEND` via the Android share sheet.
- Error `AlertDialog` shown when the API call fails.
- Fixed retry in `LoadError` callback to actually re-fetch friends (the original
  `LaunchedEffect(Unit)` would not re-trigger on a state change).

**Tests**
- `FriendsViewModelTest.kt` — 7 JVM unit tests for `formatExpiry` and URL construction.
- `CreateFriendInviteTest.kt` — 4 MockWebServer unit tests for `HeirloomsApi.createFriendInvite`:
  correct HTTP method, correct path, response parsing, and `X-Api-Key` header.

### Acceptance criteria

- "Invite a friend" button visible on the Android Friends screen. ✓
- Tapping it generates an invite token and opens the Android share sheet with the invite URL. ✓
- A recipient who opens the link and registers/connects becomes a friend of the inviter
  (handled by the existing FEAT-001 server-side invite-connect flow — no Android changes needed). ✓
- Invite token expiry (48 hours) shown to the user before sharing (sub-label always visible;
  exact expiry timestamp shown in the confirmation dialog). ✓
