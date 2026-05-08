# Heirlooms — Ideas & Brainstorms

This file captures product thinking, feature ideas, and design discussions that
aren't yet ready to become roadmap items. Maintained by the PA.

---

## Deferred from Milestone 5 (Capsules, Increment 1) — 8 May 2026

### 1. Tag-rule capsules (Milestone 8)

The v1 schema uses a fixed upload list per capsule. A natural extension: capsule contents
become a *predicate* (a tag query) rather than a fixed set, evaluated at delivery time.
A new `capsule_content_rules` table referencing tags, plus a flag on `capsules` indicating
which mechanism applies. Cleanly stackable on the v1 schema.

### 2. Per-recipient delivery state (Milestone 8)

Each `capsule_recipients` row gains `opened_at TIMESTAMPTZ` (nullable). Required for the
recipient-side UX showing who has and hasn't opened their capsule.

### 3. Recipient timezone resolution (Milestone 8)

`unlock_at` is currently anchored to the sender's timezone. Milestone 7 may want to
resolve to each recipient's timezone at delivery time, given sufficient recipient profile
data.

### 4. Capsules list pagination (deferred)

Current `GET /api/capsules` returns the full list. Add cursor- or offset-based pagination
if any user accumulates more than ~50 capsules and the list becomes slow.


---

## Android daily-use gallery (8 May 2026)

**Planned as part of the combined Android Increment 3 + Daily-Use increment (after v0.20.1).**
The Android app currently does only share-sheet uploads; everything else lives on web.
Add read-only Gallery and Capsules views to Android together with the Android capsule
share-sheet extension. This is a developer-tool increment — same single-user/API-key auth
model as today, just a richer UI for Bret's own use. Not the family-facing app (which is
a Milestone 7 conversation, possibly built fresh rather than evolved from the developer
tool). Capsule web UI (Milestone 5 Increment 2) and compost heap (v0.20.0) both shipped
before this increment, which is the right sequencing — the Android UI will be built against
a settled schema and feature set.

Three reasons for the timing:
- By then the capsule schema is settled (one Android pass, not two).
- The brand work is fresh in the codebase (extending the design language is easier than
  remembering it).
- Milestone 6's delivery design will assume both surfaces exist as places a recipient
  might encounter a bloomed capsule.

The orientation-change fix in v0.17.1 (`android:configChanges`) is tactical — the proper
ViewModel + SavedStateHandle migration is scoped for this increment, when there will be
meaningfully more state worth preserving.

---

## Multi-user and family sharing (5 May 2026)

### Context
The immediate goal is a personal developer tool, but the longer-term vision includes
non-technical family members — specifically Bret's mother, who is not computer
literate but owns an iPhone.

### The core scenario
1. Bret installs the app on his mother's iPhone
2. She uses it to share photos she wants to preserve as heirlooms
3. Bret can share selected photos back with her based on tags

### Tag-based automatic sharing
The most compelling feature idea discussed: rather than manually selecting recipients
for each photo, the user sets up sharing rules once:

> "Share everything tagged 'my children' with Mum automatically"

Any photo tagged "my children" in the future is automatically visible to her —
no manual action required per photo.

**Why this is powerful:**
- Maps to how people naturally think about photos ("these are family photos")
- Set-and-forget — no friction per upload
- Composable — one photo can have multiple tags, triggering multiple sharing rules
- Enables the milestone delivery mechanic naturally — tag "for your wedding day"
  and share with your daughter, time-locked until the right moment

### Implied data model (future, not yet built)
- `users` — identity and authentication
- `tags` — labels applied to uploads
- `upload_tags` — many-to-many join between uploads and tags
- `sharing_rules` — user X shares tag Y with user Z
- Upload feed filtered per recipient based on active sharing rules

Current schema has none of this — uploads have no ownership, no tags, no sharing.
Flyway migrations mean this can be evolved incrementally.

### Requirements before the app can be used by non-technical family members

**Multi-user support**
- Server needs user identity, authentication, and data isolation
- Current shared API key approach replaced with per-user credentials

**App Store distribution**
- iOS App Store required for non-technical iPhone users (no sideloading alternative)
- Apple Developer account needed (£79/year)
- TestFlight is the right intermediate step for family testing before App Store submission
- Android Play Store equivalent needed eventually

**Onboarding**
- Must be a single screen, not a multi-step wizard
- No passwords — magic link or Sign in with Apple/Google
- No configuration — endpoint URL and API key must be invisible to the end user
- The current "enter your server URL" settings screen is developer-only and must
  never be shown to a non-technical user

**Account recovery**
- iCloud Keychain (iOS) / Google backup (Android) for automatic credential restore
  on new phone — invisible and automatic, right for non-technical users
- Email magic link as fallback

### Key insight
Two distinct products share a codebase:
- **Developer tool** — configurable, Bret controls everything, current state
- **Family experience** — invisible infrastructure, zero configuration, designed
  for someone who struggles with TV remotes

Build the developer tool well first. When the time comes to onboard family members,
treat it as a separate product design problem. The backend can be shared; the app
and onboarding need to be designed almost from scratch with the end user in mind.

### Test users
Before publishing to any app store, a multi-user invite/onboarding flow needs
testing with real accounts. Plan for creating test user accounts to validate the
full journey before real family members are onboarded.

---
## Server-side tag listing for multi-user (6 May 2026)

### Context
v0.16.0 implemented tag autocomplete in the web UI by deriving the tag list
client-side from the gallery's existing upload data. For the current single-user
deployment this is correct: the gallery already holds every upload, so a
dedicated `GET /tags` endpoint would be a redundant round-trip.

### What changes at Milestone 7
Once beneficiary accounts arrive, a beneficiary won't see every upload — only
ones shared with them via tag-based sharing rules. Their tag autocomplete
should be scoped to "tags I have access to", not the global tag set. That
makes a server-side endpoint the right shape, returning tags filtered by the
caller's view.

### Likely API
`GET /api/content/tags` — returns distinct tags visible to the calling user,
sorted, possibly with usage counts to drive a "tags you use most" UI later.
Backed by the existing GIN index on `uploads.tags` (added in V6).

### Why this is worth recording now
The Increment 2 plan specified this endpoint but it was deliberately dropped
during build in favour of client-side derivation. That was the right call
for v0.16.0 but means the gap is invisible in the codebase — no TODO, no
stub, nothing pointing at it. Recording the reasoning here so a future
Milestone 7 session doesn't have to reverse-engineer why the endpoint
isn't there.

---
