---
id: BUG-025
title: Web Friends page has no navigation link — route exists but unreachable
category: Bug Fix
priority: Low
status: queued
depends_on: []
touches:
  - HeirloomsWeb/src/App.jsx
assigned_to: Developer
estimated: 30 minutes
---

## Problem

The Friends page exists at `/friends` and WEB-001 implemented it, but there is no
navigation link to reach it from the main app UI. Users can only access it by typing
the URL directly.

Found during TST-010 (2026-05-16), Journey 3.

## Fix

Add a "Friends" link to the main navigation (sidebar, hamburger menu, or bottom nav)
pointing to `/friends`.

## Acceptance criteria

- Friends page is reachable from the main navigation without typing the URL
