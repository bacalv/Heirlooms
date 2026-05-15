---
id: SEC-008
title: Web — add Content-Security-Policy header and migrate session token off localStorage
category: Security
priority: High
status: queued
depends_on: []
touches: [HeirloomsWeb/]
assigned_to: Developer
estimated: 1 day
---

## Background

Two HIGH findings from `docs/security/client-security-findings.md` (SEC-003 audit):

**W-01:** The session token (`heirlooms_session_token`) is written to `localStorage`. Any XSS
vulnerability — now or introduced in future — can exfiltrate it with a single line of JavaScript.

**W-02:** The nginx configuration serving the web client sets no `Content-Security-Policy` response
header. Without CSP, injected JavaScript has full origin trust and can exfiltrate `localStorage`
contents freely.

Together these two findings mean a single XSS bug leads directly to full session compromise.

## Goal

1. Add a Content-Security-Policy header that prevents execution of injected scripts.
2. Reduce the persistence lifetime of the session token to the browser-tab/session lifetime only
   (sessionStorage migration, as a minimum step that does not require server changes).

## Approach

### Part 1 — CSP (HeirloomsWeb/nginx.conf)

Add the following `add_header` directive to `nginx.conf`:

```nginx
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data:; connect-src 'self' https://api.heirlooms.digital https://storage.googleapis.com; media-src 'self' blob:; frame-ancestors 'none';" always;
```

Verify the CSP does not break the production build by running the Vite dev server and checking all
features (image load, video playback, API calls). Adjust `connect-src` if additional CDN or GCS
endpoints are used.

Also add `X-Frame-Options: DENY` and `X-Content-Type-Options: nosniff` while touching nginx.conf.

### Part 2 — Session token migration to sessionStorage (HeirloomsWeb/src/App.jsx)

Change `localStorage` to `sessionStorage` for `heirlooms_session_token`:

```javascript
const LS_TOKEN = 'heirlooms_session_token'
// Change:
//   localStorage.getItem(LS_TOKEN)   →  sessionStorage.getItem(LS_TOKEN)
//   localStorage.setItem(LS_TOKEN)   →  sessionStorage.setItem(LS_TOKEN)
//   localStorage.removeItem(LS_TOKEN) →  sessionStorage.removeItem(LS_TOKEN)
```

Note: `heirlooms_username` and `heirlooms_display_name` contain non-secret data and may remain in
`localStorage` (they are only used for display, not authentication).

On migration, detect and clear any stale `heirlooms_session_token` from `localStorage` on first
load (one-time cleanup):
```javascript
if (localStorage.getItem(LS_TOKEN)) {
  const stale = localStorage.getItem(LS_TOKEN)
  sessionStorage.setItem(LS_TOKEN, stale)
  localStorage.removeItem(LS_TOKEN)
}
```

This means users will need to log in again per browser tab (not per browser restart). The
pairing material in IndexedDB allows seamless vault re-unlock without passphrase re-entry.

## Long-term recommendation (not in scope here)

The ideal fix for W-01 is an `httpOnly` + `Secure` + `SameSite=Strict` cookie managed by the server,
eliminating session token access from JavaScript entirely. This requires server-side changes to
`AuthRoutes.kt` (Set-Cookie on login, Cookie header read on auth). Track as a future task.

## Acceptance criteria

- `nginx.conf` includes a `Content-Security-Policy` header on all responses.
- The session token is no longer readable from `localStorage` after login.
- The web app continues to function: login, vault unlock, photo display, upload, capsule creation.
- CSP does not produce console errors in Chrome/Firefox devtools during normal use.
- Vitest test suite passes unchanged.

## References

- Findings W-01 and W-02 in `docs/security/client-security-findings.md`
- `HeirloomsWeb/nginx.conf`
- `HeirloomsWeb/src/App.jsx` L37, L55–111

## Completion notes

Implemented 2026-05-15.

**Part 1 — CSP (nginx.conf):** Added `Content-Security-Policy`, `X-Frame-Options: DENY`, and `X-Content-Type-Options: nosniff` headers with `always` flag so they are sent on all responses including error pages.

**Part 2 — sessionStorage migration (App.jsx):** All `localStorage` reads/writes/removes for `heirlooms_session_token` (LS_TOKEN) migrated to `sessionStorage`. One-time migration logic in the `useState` initializer detects a stale token in localStorage, copies it to sessionStorage, and removes it from localStorage — so users logged in before the change remain logged in for the current tab. Non-secret fields (`heirlooms_username`, `heirlooms_display_name`) remain in localStorage as permitted by the task spec.

Build verified clean (`npm run build`).
