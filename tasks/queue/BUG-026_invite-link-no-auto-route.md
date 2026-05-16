---
id: BUG-026
title: Invite link doesn't auto-route token — user must manually extract and paste it
category: Bug Fix
priority: Low
status: queued
depends_on: []
touches:
  - HeirloomsWeb/src/pages/JoinPage.jsx
assigned_to: Developer
estimated: 1 hour
---

## Problem

When a user opens an invite link (`https://heirlooms.digital/invite?token=...`) in
a browser where they are already logged in, the token is not automatically extracted
from the URL and applied. The user sees the standard page and must navigate to
"Got an invite?" and manually paste the token.

For a logged-out user on a fresh browser, the token IS pre-filled on registration.
The breakage is for already-authenticated users receiving an invite link.

Found during TST-010 (2026-05-16), Journey 3.

## Fix

On the invite/join page, check for a `token` query parameter and automatically
trigger the invite redemption flow for authenticated users (friend-connect path
`POST /api/auth/invites/{token}/connect`).

## Acceptance criteria

- Authenticated user opens invite link → automatically prompted to accept the friend
  connection without manual token copy-paste
- Unauthenticated user opens invite link → registration page pre-filled as before
