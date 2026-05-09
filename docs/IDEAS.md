# Heirlooms — Ideas & Brainstorms

This file captures product thinking, feature ideas, and design discussions that
aren't yet ready to become roadmap items. Maintained by the PA.

---

## Deferred from Milestone 5 (Capsules, Increment 1) — 8 May 2026

### 1. Tag-rule capsules (Milestone 10)

The v1 schema uses a fixed upload list per capsule. A natural extension: capsule contents
become a *predicate* (a tag query) rather than a fixed set, evaluated at delivery time.
A new `capsule_content_rules` table referencing tags, plus a flag on `capsules` indicating
which mechanism applies. Cleanly stackable on the v1 schema.

### 2. Per-recipient delivery state (Milestone 10)

Each `capsule_recipients` row gains `opened_at TIMESTAMPTZ` (nullable). Required for the
recipient-side UX showing who has and hasn't opened their capsule.

### 3. Recipient timezone resolution (Milestone 10)

`unlock_at` is currently anchored to the sender's timezone. Milestone 8 may want to
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
a Milestone 8 conversation, possibly built fresh rather than evolved from the developer
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

### What changes at Milestone 8
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
Milestone 8 session doesn't have to reverse-engineer why the endpoint
isn't there.

---

## Heirlooms is about time, not just death (recalibration, May 2026)

The founding session framed Heirlooms as a digital legacy product — what
happens to your photos after you die, who gets the letter, when does the
capsule unlock. That framing was emotionally powerful and got the project
started. It is also narrower than the product actually is.

The product is about *time*. The gap between now and a future moment when
something will mean more. Death is one shape that gap takes. Most of the
shapes the gap takes do not involve death at all.

### Examples of capsules that have nothing to do with death

- A video to your daughter on her 18th birthday, sealed when she's 8.
  Both of you are alive when it opens; the capsule was waiting ten years.
- A photo album of your dad's 60s, sealed for his 70th.
- A silly inside joke between siblings, opened five years from now because
  that's when it will be funniest again.
- A letter to your future self, opened in a decade.
- A capsule for your wife that opens on your 25th anniversary.
- A capsule for your child the day they leave for university, sealed when
  they're 12.

Most of these involve both the sender and the recipient being alive at the
moment of delivery. The product is *better* under this framing, not worse,
because the sender gets to be there for the unlock too.

### Implications for the brand

The brand voice stays solemn and dignified — that's the register, the way
the product speaks. The *content* users put into capsules can be anything
they want, including humour, in-jokes, and lightness. The discipline:

- The product itself doesn't crack wise. The voice doesn't joke. *Compost*
  doesn't get a smiley face.
- The product doesn't constrain users to seriousness either. A capsule of
  cursed photos for a daughter's wedding day gets the same care as a
  deathbed letter, because both are gifts.
- The waiter at a good restaurant doesn't tell jokes, but if the diners are
  laughing, the waiter doesn't shush them. The room is theirs.

### Implications for the roadmap

Several entries elsewhere in IDEAS.md and ROADMAP.md were written under the
narrower *legacy* framing and should be read with this recalibration in
mind:

- *Engagement without gamification* (below): "the garden remembers you"
  works under either framing, but the test cases now include lighter
  moments, not only grief moments. *A photo from this day five years ago*
  is just as likely to be a holiday snap as a photo of someone now gone.
- *M10 milestone delivery*: the design space is *moments worth marking*,
  not specifically *posthumous delivery*. Easier to design, less morbid,
  broader applicability, and the recipient-resolution UI applies equally
  to capsules sent to alive recipients.
- *Print-on-delivery integrations*: stops being grief-coded. *"The capsule
  arrives as a beautiful object on the day it should arrive"* is the
  framing, not *"the capsule arrives after the sender's death."*
- *Trust posture and encryption*: the framing softens. Privacy is not only
  about *what if I die and you read my final letter*. It's also about
  *I want to write something private to my wife and not have a stranger
  read it, full stop*.

This entry doesn't undo earlier framings; it widens them. The legacy use
case stays. It's just no longer the whole product.

---

## Capsule recipient resolution at delivery (M10)

Capsules sealed before M8 (multi-user) carry free-text recipient strings —
"my daughter", "Sophie". When delivery lands at M10, there is no automatic
way to bind those strings to real accounts. Delivery cannot proceed for
unresolved capsules without a UI to do the binding.

Required as part of the M10 delivery brief:
- For each sealed capsule, show free-text recipients alongside any auto-matched
  connections.
- Let the user resolve unmatched recipients to specific connections (or send
  invites).
- Surface capsules with unresolved recipients past their unlock date in a
  *needs attention* list. Block delivery until resolved.

Without this, pre-M8 capsules either deliver to nobody (broken promise) or
deliver into a void (worse). Worth designing the resolution UI early in M10,
not late.

---

## Per-user view tracking (post-M8, likely M11+)

`last_viewed_at` on `uploads` is a global column today. Per-user view tracking —
a junction table keyed on `(upload_id, user_id)` — is post-M8 work, likely M11+,
not part of M8 itself.

The deferral only stays honest if M8's ownership model never lets a user read
another user's upload outside of capsule delivery. The moment a second viewer
enters the picture, global view tracking starts lying — *Just arrived* no longer
means what it says. The M8 brief must guarantee single-owner reads or this
deferral collapses.

The *Just arrived* predicate currently uses the `idx_uploads_just_arrived`
partial index. When the predicate adds a user-id dimension, the index needs
re-checking — the static conditions (`tags = '{}'`, `composted_at IS NULL`,
`last_viewed_at IS NULL`) are partial-index-friendly; the user-id condition
isn't, and a composite index on `(owner_user_id, uploaded_at DESC) WHERE ...`
is the likely shape.

---

## Friend-tester sequencing and API key rotation

The first non-Bret human in the system is onboarded after M8 ships.
Implications:

- M8's two-user isolation tests are the only thing standing between the tester
  and a privacy incident. Every read endpoint needs a "B reads A's resource →
  404" case; every write endpoint needs the same; every list endpoint needs a
  "B's list excludes A's resources" case. Roughly 20–30 new tests across
  uploads, tags, plots, capsules. Not a handful.
- 404 not 403 on cross-user reads — privacy-preserving (a probing attacker
  can't distinguish "doesn't exist" from "exists but isn't yours").
- API key rotation: the existing key briefly lives as plaintext in a deployment
  environment during the M8 migration that backfills Bret as the existing user.
  Rotate it post-shipping. Pre-M8 it was infrastructure plumbing; post-M8 it's
  a user credential, and that distinction matters.

---

## iOS strategy and the minimal-app shape

### Context

The Android app exists because share-sheet integration was the cheapest way to
validate the upload flow in M1. It grew Garden and Explore surfaces in v0.21.0
and D4 because the codebase was there and the brand work was fresh — not
because mobile-native browsing was strictly necessary. The web is where the
brand work primarily lives; the Android app trails it.

When iOS enters the picture (post-M8, when the family-experience product
starts being designed for non-technical users), the question is whether to
build a second full-featured native app or take a different shape.

### What native apps actually buy you

Three things, and every option below should be evaluated against them:

1. **Share-sheet integration.** "Share to Heirlooms" appearing when the user
   is in Photos, Camera roll, etc. The path of least resistance for the
   upload action.
2. **Background uploads.** Large videos that keep uploading when the user
   pockets their phone and the screen locks.
3. **Push notifications.** Future delivery moments (M10), capsule unlocks,
   friend-tester invites.

Browsing, filtering, and photo detail — everything in Garden, Explore, and
Capsules — the web does as well or better. Native apps don't add value here;
they only duplicate it.

### iOS-specific constraints

- **iOS PWAs cannot register as a share target.** Android Chrome supports
  the Web Share Target API; iOS Safari does not, and Apple has shown no
  signs of changing this. A pure-web product on iOS cannot put Heirlooms in
  the share sheet.
- **Background uploads in Safari are unreliable.** Service workers exist on
  iOS Safari but background sync is heavily restricted. A 90 MB video
  upload over flaky WiFi will fail more often than it succeeds.
- **Push notifications work on iOS PWAs from iOS 16.4 (March 2023)** but
  only when the PWA is installed to home screen, and the API is more
  limited than native push.

### The four options

**Option 1: Pure web, no iOS app.** PWA installed to home screen. Upload via
`<input type="file">` from inside the PWA. Works fine for a user who's
happy to open the app and tap Upload — fails the share-sheet workflow that
makes uploading frictionless. Viable for a developer; not viable for the
family-experience product.

**Option 2: Minimal iOS app — share extension only.** A Share Extension
registers with iOS to appear as "Heirlooms" in the share sheet. Host app
required by iOS (extensions can't ship standalone) but reduced to three
screens: an "Open in Safari" button, an API key settings screen, an app
version line. Everything else lives in the webapp. ~1 week of work for
someone familiar with iOS, less with brand assets and tag-validation reused
from Android.

**Option 3: Drop Android, single web codebase.** Tear down what's already
shipped. Saves no engineering effort (Android is done) and removes a
working share-sheet flow. App Store presence is also a trust signal for
the family-experience product — "download from the App Store" reads as
more legitimate than "go to a URL" to non-technical users, fairly or not.
Not recommended.

**Option 4: PWA primary + minimal iOS share extension app.** Web is the
primary surface, installed as a PWA on iOS and Android. iOS app exists
*only* to put Heirlooms in the iOS share sheet. Android app stays as-is
through M6/M8; future Android effort favours web over Android-native
parity unless the friction of not having a feature on Android becomes
real. This matches the developer-tool/family-experience distinction
already recorded in IDEAS.md: the developer tool is what Android already
is; the family experience is a tiny invisible iOS app plus a polished web
product.

### Recommendation

Option 4. Don't commit to it now — this is post-M8 work, after multi-user
lands and the friend tester is onboarded — but the shape is worth
recording so it isn't rediscovered cold.

### Things to flag for the eventual iOS brief

- **Share extension UI is memory-constrained.** Apple limits what
  extensions can do. A tag picker with autocomplete is fine; a full photo
  picker or capsule-create flow is risky. Keep the extension tight.
- **Background upload on iOS is a real engineering problem.**
  `URLSession.backgroundSessionConfiguration` exists but uploads still get
  killed under memory pressure or long idle. The Android v0.16.1 streaming-
  from-disk fix has an iOS equivalent that will need to be implemented; do
  not load video bytes into memory before upload.
- **TestFlight is the right intermediate step** before App Store
  submission. The friend tester can be onboarded via TestFlight without
  full review.
- **Apple Developer account is £79/year.** Review can take days. Minimal
  app shape reduces review surface and rejection risk.

### The M10 notification question (open)

The capsule unlock moment is a brand-defining moment of the product. PWA
push works on iOS 16.4+ but requires home-screen install and has a more
limited API than native push. Native push is rock solid.

If the unlock notification needs to feel ceremonial — to actually arrive
with weight on a recipient's phone, on the morning of someone's wedding
day, ten years after their grandmother's letter was sealed — native push
may be worth the extra surface area in the iOS app. That would push the
iOS app slightly past pure-share-extension at M10.

Worth deciding when the M10 delivery brief is being written, not now. But
flag it: the minimal-iOS-app strategy has a known soft spot at the
delivery moment, and that's exactly where the brand has the most to lose
if it lands wrong.

---

## Gamification (considered and rejected)

A brainstorm session in May 2026 explored gamification as a way to encourage
return visits and feature discovery: trophies for first feature use ("first
rotation", "first capsule sealed"), progression ranks (cadet → groundskeeper),
a customisable gardener persona / avatar, levelling mechanics.

**Considered and rejected. The reasoning is recorded here so a future session
doesn't relitigate it cold; if there's a strong new reason to revisit, this
entry is the starting point.**

### Why gamification fails the brand test

The brand voice is patient, considered, slow — *compost* over *delete*,
*seal* over *save*, *plant* over *upload*. Gamification mechanics import the
opposite register:

- **Vertical hierarchy onto a horizontal product.** There is no "advanced"
  way to keep a memory or to send a capsule to someone you love. A user who
  has planted three photos and sealed one capsule has not done a *less
  advanced* thing than a power user with 1,000 uploads.
- **Productivity / urgency emotional mode.** "Level up!" / "Unlock the next
  tier!" is the casino and to-do-app register. It is exactly the register
  the rest of the brand is trying to resist.
- **Activity-as-virtue.** Gamification implies *more is better*. Heirlooms
  partly stands for *less* — the compost mechanic exists because removing
  things is part of the practice. A trophy for "100 photos planted" actively
  contradicts the product's stance on its own scope.
- **Composition failure with serious moments.** A "first capsule sealed!"
  trophy popping up during a session where the user is preparing a capsule
  for a dying parent is not a delight. It is a betrayal of the moment. The
  capsule itself is the moment; nothing should be added on top.

### Specifically rejected

- Trophies, badges, achievements (first-time feature use or otherwise).
- Progression ranks (cadet, novice, groundskeeper, etc.).
- Customisable gardener persona / avatar — the user has no need to be a
  represented character in their own garden.
- XP, levels, streaks, daily-use rewards.
- Leaderboards, comparative features, social-proof mechanics.

### What survives — gentle progress legibility

There is a real product problem under the gamification instinct: how does a
user discover what Heirlooms can do beyond what they already use? A *gardener's
notebook* surface — read-only, low-contrast, non-comparative — could
legibilise the user's own activity over time without rewarding or penalising.

Possible shapes: *In May, you planted 42 photos. Three capsules are growing.*
A *what's new* surface for features written in brand voice. Seasonal
reflections of the user's own garden.

This is recorded as a possible future direction, not a commitment. The
current product doesn't need it. Worth revisiting when the user base grows
past the developer-and-friend-tester stage and feature discovery becomes a
real concern.

The discipline: if the user ever feels rewarded or compared, the surface has
crossed into gamification and should be redesigned. The win condition is the
garden contains what the user wants, when they want it. There is no
leaderboard.

---

## Pricing and monetisation

Heirlooms will need to charge money eventually. The features it charges for
are real costs: long-term storage commitments, end-to-end encryption,
verified-death workflows, original-quality preservation, third-party
integrations.

This entry records the *shape* of monetisation, not specific prices.

### Tokens / in-app currency: rejected

Tokens (named anything — seeds, blooms, sprouts, etc.) as an earnable and
spendable in-app currency are rejected.

The seductive thing about tokens is that they give you a global Heirlooms
currency: clean cross-region pricing, gift mechanics in any direction, a
single number a user understands. The reasons to reject them anyway:

- **They invite gamification.** Once a token exists, "earn tokens by doing X"
  is one product meeting away. The brand register can't survive that.
- **They abstract value at the wrong moment.** *Is this worth 30 seeds?* is
  a different question from *is this worth £15?*. The latter is the
  question the user should be asking when committing a memory to a 50-year
  capsule. Abstraction makes the commitment feel less weighty.

The accepted cost of this rejection: pricing is denominated in real currency,
which means per-region pricing complexity. There is no "global Heirlooms
money." A capsule longevity tier costs £X in the UK and €Y in the EU and $Z
in the US. The tax, payment-processing, and pricing-strategy work this
implies is real, and is accepted as the cost of not having a token system.

### Pricing tiers: brand-voice naming, real underlying costs

Pricing tiers should be named in brand voice and tied to features that
genuinely cost the business more to deliver. Not arbitrary feature gates.

Plausible tier shape (illustrative, not committed):

- A **steward** subscription that keeps the garden tended — ongoing storage,
  ongoing service, the long-term survivability commitment. This is the main
  recurring revenue.
- **Long capsules** as a one-time fee at sealing — a 50-year unlock costs
  more than a 5-year one because it commits the business to 10× the storage
  and survivability cost.
- **Sealed-from-host** encryption as a possible paid feature *eventually*,
  though the trust-posture entry below argues it should be the default rather
  than a paywall.
- **Verified-death workflows** if and when they exist — genuine staff cost,
  genuine value to users.
- **Higher-quality preservation** (original-quality video rather than
  re-encoded) for users who care.

The discipline: every paid feature should have a real cost behind it. *We
charge more because this costs us more, and we want to honour the
commitment.* Not *we charge more because we can extract more.*

### Gift mechanic (recorded as separate future feature)

A gift mechanic — one user paying to give another user a year of stewardship,
a sealed capsule's longevity, a steward subscription — survives the brand
test because it's a gift between people, not earned-and-spent currency.

*"My brother bought me a year of stewardship for my birthday."* That works.
*"I earned 50 seeds by uploading photos this week."* Doesn't.

Recorded here for completeness; not a near-term feature. Likely M9+ once
multi-user accounts and the connection model are real.

---

## Trust posture and encryption (Milestone 7)

Today, the CTO can read any photo any user uploads. This is fine for a
developer tool. It is fundamentally inadequate for the family-experience
product, where the most emotionally loaded content (a parent's last letter
to their child, a sealed capsule for a wedding day, a private message
between spouses) is also the most sensitive.

This section captures the design rationale for end-to-end encryption as the
default product posture. The architectural decisions are now lived in
Milestones 7-11; the implementation specifics will land in the M7 brief.

### The default state

End-to-end encryption — sealed-from-host, where Heirlooms staff cannot read
user content — is the *default* state of the product, not a paid upsell.

This is the inverse of how it would naturally be priced. The gold-tier offering
is not "we encrypt your stuff." If a paid tier exists in this space, it goes
the other way: the user *opts out* of encryption to enable server-side features
that require unencrypted access (search, AI-assisted memory book, auto-tagging,
etc.).

This matches Signal, ProtonMail, and 1Password — the trust-product playbook.
Heirlooms, given what it stores, should follow it.

### Engineering realities, now addressed

The original speculative entry flagged four engineering realities that
"E2EE-by-default" would have to confront. Each now has a home in the
roadmap:

- **Key management.** A high-entropy master key generated per-user, wrapped
  per-device, with three independent recovery paths (24-word phrase,
  passphrase-wrapped server backup, social recovery via Shamir). M7 ships
  the first two; M9 lights up Shamir once the connections graph exists.
- **No server-side processing on encrypted content.** EXIF extraction
  becomes a client-side operation (the device that uploads also extracts
  and supplies the structured metadata, encrypted alongside everything
  else). Thumbnails likewise — generated client-side at upload, encrypted,
  stored under their own DEKs. Tag-rule capsule evaluation operates on
  encrypted-but-indexed metadata. Search defers to the per-encrypted-tier
  posture: included by default, server-side, on the explicit-opt-out
  unencrypted tier.
- **Capsule delivery.** Three independent unlock paths handle this cleanly:
  recipient pubkey at sealing time, tlock-against-drand for date-conditioned
  capsules, and executor-held Shamir shares as the dead-man backstop.
  Heirlooms' role at delivery is to hold the ciphertext and fire the
  notification — no decryption material ever transits Heirlooms' systems.
- **AI-assisted features (the memory book concept).** Genuinely incompatible
  with sealed-from-host content. This is the place the design accepts a real
  product cost. AI features either move client-side (less capable but
  cryptographically clean) or live behind the explicit-opt-out unencrypted
  tier. The memory book, if it ever ships, will likely be the latter.

### Architectural commitments inherited from this design

These shape decisions made now and onwards:

- Server-side content access is not part of the default product. Features
  that require unencrypted access live behind explicit user opt-out.
- Client-side capability is healthy and stays that way. The web is one of
  two equal decryption surfaces alongside Android.
- Versioned envelopes, algorithm identifiers, and a re-wrap path baked in
  from M7 — crypto agility is non-negotiable on a multi-decade product.
- The connections data model in M9 is also the key-sharing graph. Recipients,
  executors, and recovery shareholders are instances of the same thing.

### What this is not

E2EE is not a defence against the user's own device being compromised, a
malicious dependency in the web client, or a cryptographic primitive being
broken on a 20-year horizon. The architecture's job is to degrade gracefully
when these happen, not to pretend they can't.

The honest claim that emerges: Heirlooms staff cannot read your photos, your
messages, or your sealed capsules. The only people who can unlock your archive
in your absence are the people you nominated to do so. That is a defensible,
true, and brand-aligned promise — stronger than what almost any consumer
cloud offers, and weaker (in the right way) than Signal, because Heirlooms
is a product about persistence, not ephemerality.

---

## Open questions for the M7 implementation brief

The Vault E2EE design has settled at the architectural level. Three product
decisions remain open before the M7 implementation brief is drafted; settling
each shapes part of the brief.

### Web posture

The recommended path is *session-only peer device*: the web has its own
keypair, can decrypt content, but its master-key copy is re-derived each
session from a passphrase rather than persisted. This makes passphrase-wrapped
server backup mandatory in M7's scope.

The alternative worth genuinely considering before committing is *web is
read-only metadata only* — the web shows the Garden, lists capsules, edits
tags, manages plots, but cannot decrypt photos or capsule message bodies.
Decryption happens only on Android (and eventually iOS).

The metadata-only path produces the strongest cryptographic property — plaintext
media never lives in a browser, full stop — and removes the XSS-as-fatal class
of risk entirely. The cost is a meaningfully worse product: "I want to look
at my photos on my laptop" is a reasonable user desire and this option says no.

The recommendation stands at session-only peer device; the alternative is
recorded so the decision is conscious, not default.

### Asymmetric scheme for device keypairs

The two realistic candidates:

- **X25519 (with HKDF for ECDH-derived wrapping keys).** Smaller keys, faster,
  modern. Supported in Android Keystore and WebCrypto, though WebCrypto support
  arrived relatively recently — current browser support needs verification at
  brief-writing time.
- **RSA-OAEP.** Older, well-supported everywhere, larger keys and slower.
  No surprises in cross-platform behaviour.

The choice has implications for envelope size (modest), performance (modest),
and post-quantum migration shape (both face the same problem; neither is
quantum-safe). X25519 is the modern default; RSA-OAEP is the safe default.
Worth a deliberate decision rather than a reflexive pick.

### Brand voice on the recovery-failure copy

The architecture means a user who loses every device, forgets their passphrase,
and never wrote down their recovery phrase has lost their data permanently.
Heirlooms cannot help and shouldn't pretend it can. The onboarding copy at the
moment of recovery setup needs to be honest about this in a way that the rest
of the product isn't.

Something close to: *"Heirlooms cannot read your photos or messages, by design.
This means we also cannot help you recover them if you lose your keys."*

That sentence is outside the warm Heirlooms register. The question for the
brand: does it stay outside, on the grounds that recovery setup is the one
moment where the asymmetry is genuinely warranted? Or does the brand find a
way to be honest *and* warm in the same breath?

This is a writing problem more than an engineering one, but it sets the tone
for the recovery surface and several adjacent moments (changing a passphrase,
periodic recovery check-ins). Worth resolving before the M7 brief specifies
any user-facing copy.

---

## Engagement without gamification

Distinct from the gamification rejection: the product should reach out to
users at the right moments, not because of streaks or rewards, but because
that's what the garden does.

### The shape

The garden remembers you. Not the other way around. A user shouldn't feel
guilty for not visiting; they should feel pleasantly surprised when the
garden has something to show them.

Possible shapes (not committed, exploratory):

- *A photo from this day, five years ago.* Quiet, occasional, never daily.
- *The capsule for Sophie's 18th birthday is one year away.* Sent at the
  one-year-out point, not weekly. The moment matters; the countdown doesn't.
- *Seasonal notes.* "It's spring. Last year's spring is in your garden if
  you'd like to see it." Once a season, not weekly.
- *The garden has settled.* Notifications about things that have happened in
  the garden itself — a capsule that's now sealed, a photo that's been in the
  garden for a decade.

### The discipline

The brand test for any of these: would this feel right at any moment in the
user's life? If a notification arrives during a difficult week, does it land
gently or does it feel like the app is being needy? If it arrives on a
joyful day, does it amplify the joy or feel out of place?

The answer for *photo from five years ago* is *it depends on the photo* —
which suggests the timing should be user-controllable, with sensible
defaults that err toward less-rather-than-more.

The answer for *capsule countdown* is *yes, this lands well* — because the
user themself set the unlock date. The garden is honouring a commitment the
user made.

The answer for *streak broken!* is *no, never* — which is why this entry
exists separately from the gamification rejection. The energy that would
have gone into trophies goes here instead.

### Engineering hooks already in the schema

- `uploaded_at` is the basis for *photo from N years ago*.
- `unlock_at` on capsules is the basis for unlock countdowns.
- `last_viewed_at` (when it becomes per-user at M11+) is the basis for *the
  garden has things you haven't seen*.

None of these are immediate features. Recorded as a feature space worth
exploring when the user base reaches the point where reach-out becomes
meaningful — likely post-M10, when delivery has matured and there are real
moments to surface.

---

## Third-party delivery integrations (M10+ second wave)

Capsule delivery at M10 starts simple — a notification, the recipient logs in,
they see their capsule. This is the right first step.

This entry is about the *second wave* of delivery integrations, after that
basic flow has matured. Not part of the M10 brief; recorded so the design
space is visible.

### The shape

Capsules can be delivered in forms the recipient encounters away from the
app:

- **Email delivery.** The capsule arrives as an email with a secure link.
  Probably the actual M10 baseline rather than the second wave — recorded
  here for completeness, but the M10 brief should treat email as the default
  notification channel.
- **Print-on-delivery (Moonpig, Photobox, similar).** The capsule arrives as
  a physical photo book on the recipient's doorstep on the unlock date.
  Genuinely brand-aligned: the capsule becomes a physical heirloom, not
  just a notification in an app. Works equally well for a happy occasion
  (anniversary, milestone birthday) as for a posthumous delivery.
- **Letter delivery** for capsules that are primarily message rather than
  photos — printed and posted on the unlock date.
- **Calendar integration.** A capsule with an unlock date can put a
  placeholder event in the recipient's calendar at the moment of sealing,
  visible from the moment the capsule is sealed (not its contents — the
  *promise*). This is a low-cost integration that significantly increases
  the chance the recipient is paying attention on the day.

### Why these are second-wave

Each integration has per-transaction cost and a per-provider engineering
investment. Each one needs design work to land in brand voice rather than
as a generic e-commerce checkout. The M10 baseline (in-app delivery + email)
needs to be solid first, both as a product surface and as a thing the
recipient has come to trust, before adding physical-world delivery.

### Brand alignment check

The print-on-delivery direction is genuinely strong. *"The capsule arrives
as a physical photo book on her wedding day"* is the kind of moment the
brand exists to deliver. It's also the kind of moment that fails badly if
the printing partner ships a damaged book or the wrong book or a delayed
book. The reliability bar is much higher than for app-only delivery.

When this becomes a real conversation, the partner-vetting work is
substantial. Worth flagging early because the partner choice shapes the
feasible delivery moments.
