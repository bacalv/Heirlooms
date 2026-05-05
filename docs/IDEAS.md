# Heirlooms — Ideas & Brainstorms

This file captures product thinking, feature ideas, and design discussions that
aren't yet ready to become roadmap items. Maintained by the PA.

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