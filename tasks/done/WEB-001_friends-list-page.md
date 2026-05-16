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

Completed 2026-05-16 by Developer (developer-3, branch `agent/developer-3/WEB-001`).

### What was done

- Created `HeirloomsWeb/src/pages/FriendsPage.jsx` — renders a list of friends (display name + username) using the existing `getFriends` API call from `api.js`. Handles loading state with `WorkingDots`, error state, and a graceful empty state ("No friends yet. Friends are added automatically when someone redeems your invite link.").
- Updated `HeirloomsWeb/src/App.jsx` — imported `FriendsPage` and added a `<Route path="friends" element={<FriendsPage />} />` inside the authenticated route group.
- Updated `HeirloomsWeb/src/components/Nav.jsx` — added a "Friends" `NavLink` to both the desktop nav bar and the mobile slide-in menu, consistent with the existing link style.

### Decisions

- Placed the nav link after "Trellises" in the desktop and mobile menus to avoid disrupting the existing link order.
- Matched the card/typography/colour style of `SharedPlotsPage.jsx` (border-forest-15, rounded-card, font-serif italic heading, font-sans body, text-text-muted, WorkingDots for loading).
- No new tasks spawned.
