---
id: DONE-M9
title: Milestone 9 — Friends, item sharing, Android plot management
category: Feature
completed: 2026-05-12
completed_at: b096091
version: v0.45.0–v0.46.2
---

M9 introduced the account-level P-256 sharing keypair (stored server-side as a wrapped key in `account_sharing_keys`), automatic friendships on invite redemption, and encrypted item sharing on Android — the sender unwraps the DEK, re-wraps it to the recipient's sharing pubkey, and posts to `POST /api/content/uploads/{id}/share`. Android also gained inline plot management (create, rename, delete) from the garden. Post-deploy iterations (v0.45.1–v0.45.9) fixed shared item decryption on both platforms, a rotation-save race, web auto-unlock paths failing to load the sharing key, and EXIF orientation on Android. v0.46.0 extended sharing to the web with a ShareModal component; v0.46.2 fixed garden thumbnail display (object-contain vs object-cover).
