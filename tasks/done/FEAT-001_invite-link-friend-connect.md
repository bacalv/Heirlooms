---
id: FEAT-001
title: Invite link doubles as friend-connect for existing users
category: Feature
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/auth/AuthService.kt
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/routes/auth/AuthRoutes.kt
  - HeirloomsWeb/src/pages/JoinPage.jsx
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/ui/main/InviteRedemptionScreen.kt
assigned_to: Developer
estimated: 1 day
---

## Goal

The invite link (QR code + URL, generated from Device & Access on Android) currently
only works for registering brand-new users. Extend it so that an existing user who
follows the same link is instead connected as a friend with the inviter — no new account
created, no separate "add friend" flow needed.

## Background

Discovered during TST-003 (2026-05-15). There is currently no mechanism for two existing
Heirlooms users to become friends other than one registering via the other's invite.
This leaves no path for users who joined independently (e.g. both via the founding
user's API key) to connect with each other.

The invite link is already a natural "share this with someone" affordance. Reusing it
for friend-connect requires no new UX — just different server-side handling depending
on the recipient's account state.

## Proposed flow

### Recipient is a new user (existing behaviour — unchanged)
1. Follows invite link → registration form → account created → friendship with inviter created

### Recipient is an existing user (new behaviour)
1. Follows invite link → app/web detects they already have a session
2. Server call: `POST /api/auth/invites/{token}/connect` (new endpoint) — redeems the
   token as a friend connection rather than a registration
3. Server: validates token is valid and not expired, creates friendship between
   `invite.createdBy` and the authenticated user, marks token consumed
4. App shows confirmation: "You're now connected with [inviter display name]"

### Detection
- **Web:** if the user is already logged in when they open the invite URL, show
  "Connect with [name]?" instead of the registration form
- **Android:** `InviteRedemptionScreen` — if a session already exists, offer
  "Connect as friend" instead of register

## Server changes

- New endpoint: `POST /api/auth/invites/{token}/connect` (authenticated)
  - Validates token exists, not expired, not already consumed
  - Calls `socialRepo.createFriendship(invite.createdBy, requesterId)`
  - Marks token consumed (same as registration)
  - Returns 200 with inviter's display name
  - Returns 409 if already friends
- Reuse existing invite token table — no schema change needed

## Acceptance criteria

- Existing user follows an invite link → prompted to connect, not register
- Friendship created between inviter and recipient
- Token consumed after use (can't be reused for a second connection)
- New user path is completely unchanged
- Tested on staging: two existing users can connect via invite link

## Completion notes

Completed 2026-05-15 by Developer-5 (agent/developer-5/FEAT-001).

### What was implemented

**Server (HeirloomsServer):**
- Added `connectViaInvite(requesterId, inviteToken)` to `AuthService.kt` with sealed result type covering `Success`, `InvalidInvite`, `AlreadyFriends`, and `SelfConnect`.
- Added `POST /api/auth/invites/{token}/connect` route in `AuthRoutes.kt` using http4k path lens pattern, requires authentication. Returns 200 with `inviter_display_name`, 409 if already friends, 403 on self-connect, 410 on invalid/expired/used token.
- Token is consumed (`markInviteUsed`) on successful connect — cannot be reused for either connection or registration.
- 5 new integration tests in `AuthHandlerTest.kt` covering: success + token consumed, already-friends (409), expired invite (410), unauthenticated (401), self-connect (403). All 36 tests pass.

**Web (HeirloomsWeb):**
- Added `authInviteConnect(sessionToken, inviteToken)` to `api.js`.
- Updated `JoinPage.jsx`: if the user is already logged in (`auth.sessionToken` non-null) and `?token=` is present in the URL, renders a "Connect as friend" confirmation screen instead of the registration form. Shows success message with inviter display name on connect, with a "Go to Heirlooms" button navigating home. Handles 409/403/410 error cases with user-friendly messages.

### What was NOT implemented (follow-up)

- **Android (`InviteRedemptionScreen.kt`)**: Android deep-link handling for the existing-user connect flow was not modified. The screen would need to check for an active session and call the new endpoint instead of showing the registration flow. This should be a follow-up task referencing the new `POST /api/auth/invites/{token}/connect` endpoint.

### Schema changes
None required — reused existing `invites` table with the same `markInviteUsed` mechanism.
