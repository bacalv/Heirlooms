---
id: TST-002
title: Create first user in staging environment
category: Testing
priority: High
status: queued
depends_on: []
touches: []
assigned_to: CTO
estimated: 5 minutes (manual)
---

## Goal

Register the first real user in the `heirlooms-test` database so staging can be used for manual testing.

## Steps

### 1. Generate an invite token

```bash
curl -s -H "X-Api-Key: k71CFcf59rdvmFqfV_nZhBd4W7DUao4jAvRvmTE4neA" \
  https://test.api.heirlooms.digital/api/auth/invites
```

This returns `{"token":"...","expires_at":"..."}`. The token is valid for 48 hours.

### 2. Register via the staging Android app

Open **Heirlooms Test** (burnt-orange icon) on your phone:
- Tap Register
- Enter the invite token from step 1
- Choose a username and password
- Complete device setup (passphrase, key generation)

### 3. Verify

```bash
# Should return 200 with your upload list (empty initially)
curl -s -o /dev/null -w "%{http_code}" \
  -H "X-Api-Key: k71CFcf59rdvmFqfV_nZhBd4W7DUao4jAvRvmTE4neA" \
  https://test.api.heirlooms.digital/api/content/uploads
```

## Acceptance criteria

- Registration completes without error
- App shows an empty garden (no uploads yet — this is a fresh database)
- Login works on subsequent app opens

## Notes

The `heirloms-test` database is completely isolated from production.
The founding user (API key user) has no password — it cannot be logged into via the normal flow.
All subsequent test users need an invite from an existing registered user.
