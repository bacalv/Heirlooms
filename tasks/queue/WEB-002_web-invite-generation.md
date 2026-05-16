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

<!-- Agent appends here and moves file to tasks/done/ -->
