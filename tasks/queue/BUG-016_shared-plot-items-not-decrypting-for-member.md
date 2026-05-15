---
id: BUG-016
title: Shared plot items uploaded from web not decrypting for Android member
category: Bug Fix
priority: High
status: queued
assigned_to: Developer
depends_on: []
touches:
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/crypto/
  - HeirloomsApp/app/src/main/kotlin/digital/heirlooms/api/
  - HeirloomsWeb/src/crypto/
estimated: 3-4 hours (agent)
---

## Steps to reproduce

1. User A (web) is the owner of shared plot "v054 Test share".
2. Fire 1 (Android) is a member of "v054 Test share".
3. User A uploads a photo from the web and it gets approved into the shared plot.
4. Fire 1 opens the shared plot on Android — items are visible in the list but do not
   decrypt (photos fail to load / show placeholder or error).

## Expected behaviour

Fire 1 can view and decrypt all items in the shared plot that User A uploaded.

## Actual behaviour

Items appear in the shared plot list on Fire 1's device but the content does not decrypt.

## Investigation

The shared plot E2EE flow:
1. Plot owner holds the plot key.
2. When a new member joins, a `pending_plot_key_requests` entry is created so the owner
   can wrap the plot key under the member's sharing public key and deliver it.
3. Member receives the wrapped plot key and can then decrypt plot items.

## Findings (2026-05-15)

`plot_members` for Fire 1 on "v054 Test share": `wrapped_plot_key` is **present**
(`plot_key_format = p256-ecdh-hkdf-aes256gcm-v1`). The key was delivered server-side.
Decryption fails on **both** Android and web for Fire 1 → client-side failure.

## Root cause hypothesis — sharing key mismatch

1. Fire 1 registers on Android → Android generates sharing keypair A, uploads pubkey A.
2. Bret (web) wraps the plot key under pubkey A → stored in `plot_members`.
3. Fire 1 pairs Android to web → web generates a **new** sharing keypair B, uploads
   pubkey B, overwriting pubkey A on the server.
4. The plot key is still wrapped under pubkey A (now gone from the server).
5. Fire 1's web session holds privkey B → can't unwrap.
6. Fire 1's Android holds privkey A → may also fail if key resolution uses server pubkey.

## Fix

When pairing/registering a new device, do NOT overwrite an existing sharing public key
if one is already registered for the user. Either skip the upload (preferred) or perform
a proper key rotation that re-wraps all existing wrapped plot keys under the new key.
Also verify whether the web pairing flow transfers the sharing private key from the
originating device to the new session.

## Notes

Discovered during TST-007 Journey 4/5 (2026-05-15).
User A's account: `475484d3-7175-49c3-9907-94a90d04cbb0`.
Fire 1's account: `2e435222-94c0-4849-9a63-b475b934fb82`.
Shared plot: "v054 Test share".
