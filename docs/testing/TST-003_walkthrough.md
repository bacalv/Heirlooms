# TST-003 — Staging Manual Test Walkthrough

**For:** Bret (CTO)
**Estimated time:** 30–60 minutes
**Date generated:** 2026-05-15
**Depends on:** TST-002 (first user already registered in staging)

---

## Environment

| | Value |
|--|--|
| API base | `https://test.api.heirlooms.digital` |
| Web | `https://test.heirlooms.digital` |
| Android app | **Heirlooms Test** (burnt-orange icon, staging flavor) |
| API key | `k71CFcf59rdvmFqfV_nZhBd4W7DUao4jAvRvmTE4neA` |

Throughout this document:
- `$KEY` = `k71CFcf59rdvmFqfV_nZhBd4W7DUao4jAvRvmTE4neA`
- **User A** = the first user created in TST-002 (already exists)
- **User B** = the second user you will create in Journey 2

All curl commands send `X-Api-Key: $KEY` unless stated otherwise. Once you have session tokens for User A and User B, substitute them where indicated. Copy-paste the full commands as-is.

---

## Quick-reference: auth header formats

```bash
# API-key auth (admin/setup calls)
-H "X-Api-Key: k71CFcf59rdvmFqfV_nZhBd4W7DUao4jAvRvmTE4neA"

# Session-token auth (user calls — substitute the token from login/register)
-H "X-Api-Key: <session_token>"
```

---

## Journey 1 — Upload and view a photo

**Goal:** confirm photo upload works end-to-end on Android and is visible on web.

### Step 1.1 — Upload a photo on the Android app

- Open **Heirlooms Test** on your Android device.
- You should land on the **Garden** screen (bottom nav: olive-branch icon, leftmost tab).
- Tap the **share sheet** trigger on your device (share a photo from the Photos app, or from the Android share menu, selecting "Heirlooms Test"). Alternatively, if there is a camera/upload FAB on the Garden screen, use that.
- Select or take a test photo. It does not need to be meaningful.
- On the **upload screen** (shown after selecting the photo), leave tags blank or add a tag such as `test`.
- Confirm the upload.

**Check:**
- [ ] The upload screen appears without errors.
- [ ] After confirming, you are returned to the Garden or an upload-progress screen.

### Step 1.2 — Photo appears in garden on Android

- On the **Garden** screen, pull-to-refresh (swipe down).

**Check:**
- [ ] The uploaded photo appears as a thumbnail in the garden grid.
- [ ] Thumbnail renders without a broken-image indicator.

### Step 1.3 — Tap to load full-resolution on Android

- Tap the photo thumbnail to open the **Photo Detail** screen.

**Check:**
- [ ] Full-resolution image loads (not blurry or broken).
- [ ] No error toast or "couldn't load" message appears.

### Step 1.4 — Photo appears in garden on web

- Open `https://test.heirlooms.digital` in a browser.
- Log in as User A (if not already logged in).

**Check:**
- [ ] The photo uploaded in step 1.1 is visible in the garden on the web UI.
- [ ] Thumbnail loads correctly.

### Step 1.5 — Full-resolution on web

- Click the photo thumbnail.

**Check:**
- [ ] Full-resolution image loads in the detail view.

**Verify via API (optional sanity check):**

```bash
curl -s \
  -H "X-Api-Key: k71CFcf59rdvmFqfV_nZhBd4W7DUao4jAvRvmTE4neA" \
  "https://test.api.heirlooms.digital/api/content/uploads?limit=5" \
  | python3 -m json.tool
```

Expected: JSON object with an `items` (or `uploads`) array containing at least one entry. HTTP status 200.

---

## Journey 2 — Create a second user (invite flow)

**Goal:** invite and register a second account (User B). TST-002 already created User A — you need a session token for User A to generate the invite.

### Step 2.1 — Log in as User A on Android to get a session token

On Android (Heirlooms Test, already logged in as User A), go to:
**More (burger menu) → Diagnostics**

The Diagnostics screen shows the current session token. Copy it.

Alternatively, issue the SRP login flow via curl (see note below). The simplest path is to read it from Diagnostics.

> **Note:** The login flow requires an SRP challenge exchange (`POST /api/auth/challenge` → compute auth_key → `POST /api/auth/login`). This is done automatically by the Android app. Use the Diagnostics screen to capture the live session token rather than re-implementing SRP in curl.

Set a shell variable for convenience:

```bash
USER_A_TOKEN="<paste session token from Diagnostics here>"
```

### Step 2.2 — Generate an invite token for User B

```bash
curl -s \
  -H "X-Api-Key: $USER_A_TOKEN" \
  "https://test.api.heirlooms.digital/api/auth/invites"
```

Expected response (HTTP 200):

```json
{"token":"<invite_token>","expiresAt":"<ISO timestamp>"}
```

**Check:**
- [ ] HTTP 200 returned.
- [ ] `token` field is non-empty.
- [ ] `expiresAt` is in the future.

Save the token:

```bash
INVITE_TOKEN="<paste token value here>"
```

### Step 2.3 — Register User B on the web

- Open `https://test.heirlooms.digital` in a browser (use a private/incognito window to avoid being logged in as User A).
- Look for a **Sign up** or **Register with invite** option on the login page.
- Enter the invite token when prompted, then choose a username and passphrase for User B.

**Check:**
- [ ] Registration completes without error.
- [ ] User B is redirected to their garden.
- [ ] The garden is empty (no photos).

### Step 2.4 — User B logs in and sees empty garden

- Log out if necessary, then log back in as User B.

**Check:**
- [ ] Login succeeds.
- [ ] Garden is empty (no "couldn't load" errors, just an empty state message).

---

## Journey 3 — Friend connection

**Goal:** User A and User B become friends. This uses the in-app sharing key / pairing flow.

### Step 3.1 — Get User A's sharing key (Android)

- On Android (logged in as User A), go to:
  **More (burger) → Devices & Access**
- Look for a **Sharing Key** or **Add friend** / pairing option.

> The app uses a sharing-key mechanism. User A shares their public sharing key, and User B registers it on their side. The exact UI entry point is in **Devices & Access** or via the social pairing flow.

**Check:**
- [ ] You can see or copy User A's sharing key / pairing code.

### Step 3.2 — Verify sharing key is stored on the server

```bash
curl -s \
  -H "X-Api-Key: $USER_A_TOKEN" \
  "https://test.api.heirlooms.digital/api/keys/sharing/me"
```

Expected: HTTP 200, JSON with `pubkey` and `wrappedPrivkey` fields.

**Check:**
- [ ] HTTP 200 with pubkey present.

### Step 3.3 — User B adds User A as a friend

- On the web (logged in as User B), look for a **Friends** or **Add friend** section.
- Enter User A's sharing key / username.

> Alternatively: the `PUT /api/keys/sharing` and social friend-add flow can be exercised from the Android app for User B. The app's **Devices & Access** screen drives this.

**Check:**
- [ ] Friend add request completes without error.

### Step 3.4 — Both users see each other in friends list

**User A (curl):**

```bash
curl -s \
  -H "X-Api-Key: $USER_A_TOKEN" \
  "https://test.api.heirlooms.digital/api/friends"
```

**User B (curl — substitute User B's session token):**

```bash
USER_B_TOKEN="<User B session token>"

curl -s \
  -H "X-Api-Key: $USER_B_TOKEN" \
  "https://test.api.heirlooms.digital/api/friends"
```

Expected: HTTP 200, JSON array containing the other user's `userId`, `username`, and `displayName`.

**Check:**
- [ ] User A's friends list contains User B.
- [ ] User B's friends list contains User A.

---

## Journey 4 — Shared plot

**Goal:** User A creates a shared plot, invites User B via QR/token, User A uploads a photo that lands in User B's staging queue, and User B approves it.

### Step 4.1 — User A creates a shared plot (Android)

- On Android (User A), tap the **Shared** tab in the bottom nav (people icon).
- Tap the **+** FAB (floating action button).
- Enter a name, e.g. `Family`.
- Tap **Confirm**.

**Check:**
- [ ] The plot "Family" appears in the Shared Plots screen under "Joined" with the badge `owner`.

Capture the plot ID via API:

```bash
curl -s \
  -H "X-Api-Key: $USER_A_TOKEN" \
  "https://test.api.heirlooms.digital/api/plots" \
  | python3 -m json.tool
```

Find the entry with `"name":"Family"` and copy its `id`.

```bash
PLOT_ID="<UUID of the Family plot>"
```

**Check:**
- [ ] HTTP 200. Plot with name `Family` and `visibility: "shared"` appears in the list.

### Step 4.2 — User A generates a plot invite for User B

```bash
curl -s -X POST \
  -H "X-Api-Key: $USER_A_TOKEN" \
  "https://test.api.heirlooms.digital/api/plots/$PLOT_ID/invites"
```

Expected: HTTP 201, JSON with `token` and `expiresAt`.

```bash
PLOT_INVITE_TOKEN="<paste token here>"
```

**Check:**
- [ ] HTTP 201 returned.
- [ ] `expiresAt` is ~48 hours in the future.

### Step 4.3 — User B previews invite info (optional verification)

```bash
curl -s \
  -H "X-Api-Key: $USER_B_TOKEN" \
  "https://test.api.heirlooms.digital/api/plots/join-info?token=$PLOT_INVITE_TOKEN"
```

Expected: HTTP 200, JSON with `plotId`, `plotName` ("Family"), `inviterDisplayName`.

**Check:**
- [ ] HTTP 200 returned.
- [ ] `plotName` is "Family".
- [ ] `inviterDisplayName` matches User A.

### Step 4.4 — User B joins the plot (Android or web)

On the **Shared** tab (User B), look for a **Join with invite** / **Scan QR** entry, or on web navigate to `https://test.heirlooms.digital/join?token=<PLOT_INVITE_TOKEN>`.

Alternatively, via API — this registers User B's pubkey and creates a pending join request. The app handles the key-wrap handshake automatically. If testing via the app:

- User B: open the Shared tab → use the QR scanner or paste the invite token.
- User B should see a name prompt; enter a local name for the plot (e.g. `Family`).

After User B joins, User A must **confirm the invite** (supplying the wrapped plot key for User B). The app does this automatically when User A next opens the Shared tab and has a notification about a pending join.

**Check (via API — User A):**

```bash
curl -s \
  -H "X-Api-Key: $USER_A_TOKEN" \
  "https://test.api.heirlooms.digital/api/plots/$PLOT_ID/members/pending"
```

Expected (before confirmation): JSON array with at least one entry for User B.

```bash
curl -s \
  -H "X-Api-Key: $USER_B_TOKEN" \
  "https://test.api.heirlooms.digital/api/plots/shared"
```

After confirmation, User B's membership should have `status: "joined"`.

**Check:**
- [ ] User A sees User B as a pending member (before confirmation).
- [ ] After confirmation (by User A in the app), User B's membership status is `joined`.
- [ ] Both users see each other in the plot's member list.

### Step 4.5 — User A uploads a photo to the shared plot

- On Android (User A), select a photo and share it to Heirlooms Test.
- In the tag / destination screen, tag the photo so that a **Flow** routes it to the "Family" plot. (If no flow exists yet, set one up in Journey 5 first, then return here. Alternatively, add the photo directly to the plot via the plot's "Add item" action in the Garden.)

> If using the direct add path: open the Garden, long-press a photo, and choose **Add to plot → Family**.

**Check:**
- [ ] Upload completes.

### Step 4.6 — Staging item appears for User B

```bash
curl -s \
  -H "X-Api-Key: $USER_B_TOKEN" \
  "https://test.api.heirlooms.digital/api/plots/$PLOT_ID/staging"
```

Expected: HTTP 200, JSON array with at least one item with `plotStatus: "pending"` (or equivalent staging state).

**Check:**
- [ ] HTTP 200.
- [ ] At least one staging item present.
- [ ] Item's thumbnail is visible in User B's Shared tab → plot staging queue on Android.

Save the upload ID of the staging item:

```bash
STAGING_UPLOAD_ID="<upload id from the staging response>"
```

### Step 4.7 — User B approves the staging item (Android)

- On Android (User B), open the **Shared** tab.
- Tap the "Family" plot card.
- The staging review screen appears (screen title: "Review").
- Tap the green checkmark (approve) button on the pending photo.

**Check:**
- [ ] The item moves from "Pending" section to disappear from the review queue (or the queue shows empty).

**Verify via API:**

```bash
curl -s \
  -H "X-Api-Key: $USER_B_TOKEN" \
  "https://test.api.heirlooms.digital/api/plots/$PLOT_ID/staging"
```

**Check:**
- [ ] The previously-pending item is no longer in the staging array.

### Step 4.8 — Photo appears in shared plot for both users

```bash
# User A
curl -s \
  -H "X-Api-Key: $USER_A_TOKEN" \
  "https://test.api.heirlooms.digital/api/plots/$PLOT_ID/items" \
  | python3 -m json.tool

# User B
curl -s \
  -H "X-Api-Key: $USER_B_TOKEN" \
  "https://test.api.heirlooms.digital/api/plots/$PLOT_ID/items" \
  | python3 -m json.tool
```

**Check:**
- [ ] Both calls return HTTP 200.
- [ ] Both arrays contain the approved upload.

---

## Journey 5 — Flows (Trellises)

**Goal:** create a flow with tag criteria, upload a matching photo, and confirm it auto-routes to the target plot.

### Step 5.1 — Create a target plot (if needed)

You can reuse the "Family" shared plot from Journey 4, or create a private plot. To create a private plot via Android:

- On the **Garden** screen, look for a plot management option (long-press an existing plot header or use the plot FAB if present).
- Name it `Flow Target`.

Or via API:

```bash
curl -s -X POST \
  -H "X-Api-Key: $USER_A_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Flow Target","visibility":"private"}' \
  "https://test.api.heirlooms.digital/api/plots"
```

Expected: HTTP 201, JSON with the new plot's `id`.

```bash
FLOW_PLOT_ID="<id of Flow Target>"
```

### Step 5.2 — Create a flow with tag criteria (Android)

- Open **More (burger) → Flows**.
- Tap the **+** FAB.
- In the "New flow" dialog:
  - **Flow name:** `tag-routing-test`
  - **Target plot:** select `Flow Target` (or Family).
  - **Tags:** add `flowtest` (tap the "+" icon next to the tag input).
  - **Auto-approve:** leave **off** (so `requires_staging = true`).
- Tap **Create**.

**Check:**
- [ ] The flow `tag-routing-test` appears in the Flows list.
- [ ] It shows `→ Flow Target` and `Requires approval`.

**Verify via API:**

```bash
curl -s \
  -H "X-Api-Key: $USER_A_TOKEN" \
  "https://test.api.heirlooms.digital/api/flows" \
  | python3 -m json.tool
```

**Check:**
- [ ] HTTP 200. New flow present with correct `targetPlotId` and `criteria`.

Save the flow ID:

```bash
FLOW_ID="<id of tag-routing-test flow>"
```

### Step 5.3 — Upload a photo with the matching tag

- On Android, share a photo to Heirlooms Test.
- In the tag entry field, add the tag `flowtest`.
- Confirm the upload.

**Check:**
- [ ] Upload completes without error.

### Step 5.4 — Verify the photo auto-routed to staging for the flow

```bash
curl -s \
  -H "X-Api-Key: $USER_A_TOKEN" \
  "https://test.api.heirlooms.digital/api/flows/$FLOW_ID/staging" \
  | python3 -m json.tool
```

Expected: HTTP 200, JSON array with at least one staging item.

**Check:**
- [ ] HTTP 200.
- [ ] The uploaded photo (tagged `flowtest`) appears in the staging array.

### Step 5.5 — On Android: review via Flows screen

- Open **More (burger) → Flows**.
- The flow card `tag-routing-test` should show a badge with a pending count (e.g. "1 Review").
- Tap **Review**.
- The staging review screen opens with the pending photo.

**Check:**
- [ ] Badge count on the flow card matches the API staging count.
- [ ] Staging review screen loads the pending photo thumbnail.

---

## Journey 6 — Web-specific features

**Goal:** confirm drag-and-drop upload, CMD+V paste upload, and the auto-approve toggle all work on the web.

### Step 6.1 — Drag-and-drop upload on web

- Open `https://test.heirlooms.digital` in Chrome or Safari (logged in as User A or User B).
- Drag an image file from Finder/Desktop and drop it onto the garden area of the web page.

**Check:**
- [ ] A drop target / overlay appears when dragging over the garden.
- [ ] After dropping, an upload progress indicator or success message appears.
- [ ] The uploaded photo appears in the garden (may require a page refresh).

### Step 6.2 — CMD+V paste upload on web

- Copy an image to your clipboard (screenshot: CMD+SHIFT+4, or copy from Photos).
- Click in the garden area to ensure focus.
- Press **CMD+V**.

**Check:**
- [ ] An upload dialog or progress indicator appears.
- [ ] After paste, the photo appears in the garden.

### Step 6.3 — Auto-approve toggle on flow create

- On the web (logged in as User A), navigate to Flows (if available in the web UI — this may be an Android-only feature currently).
- If the flow creation dialog is available on web: create a new flow with **Auto-approve** toggled **on**.

**Check (web — if UI is present):**
- [ ] Auto-approve toggle is visible and functional in the flow creation dialog.
- [ ] Flow created with `requiresStaging: false` (verify via API):

```bash
curl -s \
  -H "X-Api-Key: $USER_A_TOKEN" \
  "https://test.api.heirlooms.digital/api/flows" \
  | python3 -m json.tool
```

Look for the newly created flow — `requiresStaging` should be `false`.

**Check:**
- [ ] Flow created with `requiresStaging: false`.

> If flow creation is Android-only in the current build, skip the web dialog sub-step and note it as a finding.

---

## Completing the run

For each journey above, mark steps Pass or Fail in the checkboxes above.

**If any step fails:**

1. Note the exact error message and HTTP status (if applicable).
2. Record the repro steps.
3. Create a `BUG-XXX` task file in `tasks/queue/` with the failure details.
4. Tag it with `blocks: TST-004` if it would prevent automated test authoring.

---

## API reference summary

| Route | Method | Base path |
|-------|--------|-----------|
| Get session (from app Diagnostics screen) | — | — |
| Generate user invite | GET | `/api/auth/invites` |
| List uploads | GET | `/api/content/uploads` |
| List plots | GET | `/api/plots` |
| Create plot | POST | `/api/plots` |
| List flows | GET | `/api/flows` |
| Create flow | POST | `/api/flows` |
| Get flow staging | GET | `/api/flows/{id}/staging` |
| Get plot staging | GET | `/api/plots/{id}/staging` |
| Approve staging item | POST | `/api/plots/{id}/staging/{uploadId}/approve` |
| List plot items | GET | `/api/plots/{id}/items` |
| Create plot invite | POST | `/api/plots/{id}/invites` |
| Join-info lookup | GET | `/api/plots/join-info?token=…` |
| List friends | GET | `/api/friends` |
| My sharing key | GET | `/api/keys/sharing/me` |
| List shared memberships | GET | `/api/plots/shared` |
| Pending members | GET | `/api/plots/{id}/members/pending` |

All routes require `X-Api-Key: <token>` header.
