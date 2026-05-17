---
id: BUG-030
title: createFriendInvite uses GET instead of POST
category: Bug Fix
priority: Low
status: queued
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/api/HeirloomsApi.kt
assigned_to: Developer
estimated: 30 minutes
---

## Problem

`HeirloomsApi.createFriendInvite()` (line 667) calls the internal `get()` helper
instead of `post()`, so it issues `GET /api/auth/invites` rather than
`POST /api/auth/invites`.

The `CreateFriendInviteTest.createFriendInvite sends POST to correct path` unit test
captures this — it fails with a method mismatch.

Found during BUG-028 test run (2026-05-17).

Note: the companion `getInvite()` function immediately above it (line 661) also calls
`get("/api/auth/invites")` and appears to be a duplicate. Clarify with the PA whether
`createFriendInvite` is intended to supersede `getInvite`, and remove the duplicate
once the method is fixed.

## Fix

Change `createFriendInvite()` to use `post("/api/auth/invites")` (with an empty body
if the server expects one). Confirm the server endpoint accepts POST, then remove or
deprecate `getInvite()` if it is redundant.

## Acceptance criteria

- `CreateFriendInviteTest` passes (all 4 tests green)
- `getInvite()` is either removed or clearly marked as the GET variant if both are needed
