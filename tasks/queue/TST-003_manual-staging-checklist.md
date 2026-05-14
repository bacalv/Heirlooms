---
id: TST-003
title: Manual staging test checklist
category: Testing
priority: High
status: queued
depends_on: [TST-002]
touches: []
estimated: 30-60 minutes (manual)
---

## Goal

Walk through the key user journeys on the staging environment to validate the full stack before writing automated tests. This becomes the reference for the Playwright suite (TST-004).

## Environment

- API: https://test.api.heirlooms.digital
- Web: https://test.heirlooms.digital
- Android: Heirlooms Test (burnt-orange icon)
- API key (for setup): `k71CFcf59rdvmFqfV_nZhBd4W7DUao4jAvRvmTE4neA`

## Journeys

### Journey 1: Upload and view a photo
- [ ] Upload a photo via Android
- [ ] Photo appears in garden on Android
- [ ] Photo appears in garden on web (https://test.heirlooms.digital)
- [ ] Thumbnail loads on both
- [ ] Full-resolution loads on both

### Journey 2: Create a second user (invite flow)
- [ ] Generate invite from first user's account: `GET /api/auth/invites`
- [ ] Register second user on web (https://test.heirlooms.digital) using invite token
- [ ] Second user can log in and sees empty garden

### Journey 3: Friend connection
- [ ] User A shares their sharing key (Settings → Sharing Key on Android)
- [ ] User B adds User A as a friend
- [ ] Both users see each other in friends list

### Journey 4: Shared plot
- [ ] User A creates a private plot
- [ ] User A generates a plot invite QR for User B
- [ ] User B scans QR and joins the plot
- [ ] User A uploads to the plot — appears in staging queue for User B
- [ ] User B approves staging item
- [ ] Photo appears in shared plot for both users

### Journey 5: Flows / Trellises
- [ ] Create a flow with tag criteria
- [ ] Upload a photo with matching tag
- [ ] Photo auto-routes to the target plot

### Journey 6: Web-specific
- [ ] Drag-and-drop upload works on web
- [ ] CMD+V paste upload works on web
- [ ] Auto-approve toggle on flow create works

## Notes

Document any failures or unexpected behaviour. Each failure becomes a bug-fix task in `queue/`.

## Spawned tasks (to be filled in after completion)

Any bugs found here should become BUG-XXX tasks in queue/.
