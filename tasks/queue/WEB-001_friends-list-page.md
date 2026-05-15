---
id: WEB-001
title: Web: friends list page
category: Feature
priority: Medium
status: queued
depends_on: []
touches:
  - HeirloomsWeb/src/pages/FriendsPage.jsx
  - HeirloomsWeb/src/App.jsx
assigned_to: Developer
estimated: half day
---

## Goal

Surface the friends list on the web app. The API already supports it (`getFriends` in
`api.js`) — this is purely a UI task.

## Background

Discovered during TST-003 (2026-05-15). Android has a Friends screen under the burger
menu; the web has no equivalent. Friends are created automatically when an invite is
redeemed, so there's no add-friend flow to build — just display.

## Deliverables

- A `FriendsPage.jsx` showing the current user's friends (display name + username)
- Reachable from the web nav (settings menu, sidebar, or similar — match existing web UX)
- Empty state: "No friends yet" with a note that friends are added via invite

## Acceptance criteria

- Logged-in user can see their friends list on web
- Empty state is handled gracefully
- Consistent with existing web visual style

## Completion notes

<!-- Agent appends here and moves file to tasks/done/ -->
