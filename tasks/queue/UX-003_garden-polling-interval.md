---
id: UX-003
title: Reduce garden polling interval to 5s — Android configurable
category: UX
priority: Medium
status: queued
assigned_to: Developer
depends_on: []
touches:
  - HeirloomsWeb/src/
  - HeirloomsApp/app/src/main/
estimated: 2-3 hours (agent)
---

## Goal

Reduce the garden polling interval from its current default to **5 seconds** on both web and
Android, so uploads appear in Just Arrived faster. On Android, also expose the interval as a
user setting so battery-sensitive users can dial it back.

## Web

Find the polling interval constant (likely in a hook or service that calls the garden/uploads
endpoint) and change it to 5000ms.

## Android

1. **Change the default** polling interval to 5 seconds.
2. **Add a Settings screen entry** — "Garden refresh interval" — with four options:
   - 2 seconds
   - 5 seconds *(default)*
   - 10 seconds
   - 30 seconds

   Store the selection in `SharedPreferences` (or `EncryptedSharedPreferences` — whichever is
   already used for non-sensitive prefs). Read it wherever the polling interval is consumed.

   The settings entry should sit alongside any existing app preferences. If no Settings screen
   exists yet, create a minimal one accessible from the burger menu.

## Acceptance criteria

- Web garden polls every 5 seconds (verify via DevTools Network tab).
- Android garden polls at the stored interval; default is 5 seconds on fresh install.
- Changing the Android setting takes effect immediately (no app restart required).
- Existing tests still pass.

## Notes

Battery impact: 5s polling is acceptable for a foreground app. Users who are battery-conscious
can select 30s. The 2s option is there for power users / demo scenarios.
Do not apply a configurable interval to web — web users have no equivalent battery concern and
a fixed 5s is the right default.
