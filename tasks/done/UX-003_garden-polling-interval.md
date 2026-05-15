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

## Completion notes

**Completed:** 2026-05-15  
**Branch:** `agent/developer-5/UX-003`  
**Commit:** `bc98c9a`

### What was done

**Web (`HeirloomsWeb/src/pages/GardenPage.jsx`)**
- Changed `setInterval` from `10_000` ms to `5_000` ms. Single line change.

**Android**

1. `EndpointStore.kt` — Added `getGardenRefreshIntervalMs()` / `setGardenRefreshIntervalMs(ms)` backed by the existing `EncryptedSharedPreferenceStore` (task said plain SharedPreferences is fine; the project already migrated all settings to encrypted prefs in SEC-007, so this followed the same pattern rather than introducing a second prefs file). Key: `garden_refresh_interval_ms`, default: `5_000L`.

2. `GardenScreen.kt` — Added optional `store: EndpointStore?` parameter (default `null` for backward compat). The `LaunchedEffect` polling loop now reads `store?.getGardenRefreshIntervalMs()` at the top of each iteration, so any change made in Settings takes effect on the next tick without an app restart.

3. `AppNavigation.kt` — Passes `store = store` through to `GardenScreen`.

4. `SettingsScreen.kt` — Added "Garden refresh interval" chip group (2s / 5s / 10s / 30s) using the same `FilterChip` pattern as the existing video-threshold setting. The SettingsScreen screen was already accessible from the burger menu; no nav changes needed.

### Tests

- Web: `npm test` — 115 passed (2 pre-existing failures: smoke API test requires live server; one auth IDB test has a missing `data-testid` on GardenPage that predates this task).
- Android: `./gradlew :app:testStagingDebugUnitTest --no-daemon` — BUILD SUCCESSFUL, all 24 tasks executed, no test failures.

### Decisions

- Used `EncryptedSharedPreferences` (via existing `EndpointStore`) rather than plain `SharedPreferences` for consistency with SEC-007's migration. Non-sensitive data is fine in either store; this avoids a second prefs file.
- `store` is nullable in `GardenScreen` to avoid breaking any existing call sites that don't yet pass a store (e.g., preview composables or tests).
