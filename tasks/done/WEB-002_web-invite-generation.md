---
id: WEB-002
title: Web — generate and share invite links
category: Feature
priority: Medium
status: queued
assigned_to: Developer
depends_on: []
touches:
  - HeirloomsWeb/src/pages/FriendsPage.jsx
  - HeirloomsWeb/src/api/api.js
estimated: 2–3 hours
---

## Goal

Allow web users to generate invite links from the web app, equivalent to the Android
"Invite a friend" flow. Currently web users can only receive invites — they cannot
generate them.

## Background

WEB-001 (friends list page) is complete — web now shows the friends list. But a web
user with no Android device has no way to invite someone. The server already supports
invite token generation (`POST /api/auth/invites`).

## Deliverables

- An "Invite a friend" button or section on `FriendsPage.jsx`
- Calls the existing invite token API endpoint to generate a token
- Constructs the invite URL: `https://heirlooms.digital/join?token=<token>` (or
  `https://test.heirlooms.digital/join?token=<token>` for staging flavor)
- Provides a copy-to-clipboard button and/or a shareable link display
- Clear UX: the invite link is single-use, expires after redemption

## Acceptance criteria

- Logged-in web user can generate an invite link
- Link copies to clipboard with one click
- Correct domain used for prod vs staging
- Error state handled if API call fails

## Completion notes

Completed by Developer agent on 2026-05-16.

### Changes made

**`HeirloomsWeb/src/api.js`**
- Added `createInvite(sessionToken)` — POST `/api/auth/invites`, returns `{ token, expires_at }`
- Added `buildInviteUrl(token)` — detects staging vs prod via `API_URL` (checks for `test.heirlooms.digital`), returns the full join URL

**`HeirloomsWeb/src/pages/FriendsPage.jsx`**
- Added `InviteSection` component rendered above the friends list
- "Generate invite link" button calls `createInvite` then `buildInviteUrl`
- Generated URL is displayed in a readable box with `select-all` styling
- "Copy link" button uses `navigator.clipboard.writeText`, shows "Copied!" for 2.5 s
- "New link" button allows regeneration without leaving the page
- Error state shown if API call fails (inline italic message)
- Generating state disables buttons with visual feedback

### Acceptance criteria

- [x] Logged-in web user can generate an invite link
- [x] Link copies to clipboard with one click
- [x] Correct domain used for prod vs staging (via `API_URL` env var check)
- [x] Error state handled if API call fails
