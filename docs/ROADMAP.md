# Heirlooms — Roadmap

This document captures the product thinking from the founding session of this project
(April 2026), before any code was written.

---

## Origin

The project began as a brainstorm around digital legacy and inheritance — a problem
that affects everyone with a digital footprint but that most people never plan for.
The research surfaced a clear gap: most people now maintain extensive digital
footprints across countless accounts, yet we still lack universal mechanisms to
manage what happens to that data after death. Many platforms have strict terms of
service that may prohibit account transfer, and what legal frameworks do exist
vary significantly by country.

The personal angle — photos and videos for next of kin — emerged as the most
emotionally powerful entry point into the problem.

A note on framing, added retroactively in May 2026 after a brainstorm
session: the founding session's emphasis on death, inheritance, and
posthumous delivery captured the most emotionally powerful entry point into
the problem, but it isn't the whole product. Heirlooms is more accurately
about *time* — the gap between now and a future moment when something will
mean more. Death is one shape that gap takes. A video to a daughter on her
18th birthday, sealed when she's 8, lives in the same product as a
posthumous letter, and most users will reach for the former more often than
the latter. The brand voice is solemn and dignified; what users do with
that voice is up to them. See IDEAS.md, "Heirlooms is about time, not just
death," for the full reframing.

---

## Concepts explored

Eight product directions were sketched out at the start of the session:

**The digital safe** — a secure vault where you store photos, videos, voice notes,
and letters, with a "will" that specifies who gets access to what, and when. Rated
high demand with strong emotional resonance.

**Dead man's switch** — a feature that monitors account inactivity and, after a set
time, automatically notifies a trusted contact or transfers access. Technically
complex, but eliminates the need for lawyers.

**Digital estate planner** — a guided tool that helps you inventory all your accounts,
assign inheritors for each, and generates documentation that stands up legally.

**The memory book** — an AI-assisted app that turns years of scattered photos and
videos into a curated, organised narrative — a living memoir for children or
grandchildren.

**Time-locked messages** — video or letters scheduled to be delivered on specific
future dates: a child's 18th birthday, graduation, or wedding day. Identified as
the single most emotionally resonant differentiator.

**Collective memory space** — a shared platform where families pool their photos,
videos, and stories about a loved one after death, as a permanent memorial beyond
a social media page.

**Digital rights passport** — a standardised document or app that states your
intentions for every platform: delete it, pass it on, or memorialise it.

**Digital executor service** — a professional service, like a solicitor but for
digital assets, that manages the handover of accounts and data to the right people
at the right time.

---

## Key insight

Cloud storage solves the wrong problem. Google Drive asks "where do I put my files?"
A digital vault asks "who should receive this memory, and when?" That is a completely
different emotional and UX design challenge, and it is why no cloud provider has
filled this gap. They are infrastructure companies, not grief-aware product designers.

---

## The milestone delivery mechanic

The single most powerful differentiator identified was milestone delivery: a video
message from a parent that arrives on a child's 18th birthday — years after their
death — is not something any existing tool can do with intent and dignity. That
feature alone is deeply emotionally resonant and hard to replicate as a bolt-on.

---

## Key risks identified

**The cold start problem** — people know they should do this but don't. Onboarding
must be painless: import from iCloud/Google Photos on day one, not a blank screen.
The emotional trigger matters: "start with one photo."

**Verification of death** — how do you reliably know the person has died without
being intrusive? Options include death certificate upload, trusted witnesses, and
inactivity combined with GP confirmation. Each has tradeoffs between speed and
fraud risk.

**Long-term survivability** — if a capsule is set to unlock in 2039, the company
must still exist. Users need confidence that their data will not vanish if the
startup fails. Escrow, open formats, and encrypted exports are essential, and this
is also the product's moat: a credible answer to "what happens to my data if you
shut down?" is a real trust signal that a large platform cannot easily copy.

**Emotional design** — the product touches grief, mortality, and family conflict.
Every word, from onboarding copy to unlock notifications, must be handled with care.
This is not a typical SaaS product.

**Legal jurisdiction** — digital inheritance law varies by country. The product
needs a clear stance: it is a personal tool, not a substitute for a legal will.

**Family disputes** — the platform needs immutable logs and a clear policy: it
executes the user's stated wishes, it does not arbitrate inheritance disputes.

---

## How Heirlooms started

Rather than tackling the full product, the decision was made to start as small as
possible: a personal Android app, just for the author, that adds "My Heirlooms" to the
Android share sheet for any photo or video, and HTTP POSTs it to a configurable
endpoint.

This deliberate constraint — no app store, no backend initially, just developer mode
sideloading — let the project begin immediately and validated the core share-sheet
integration before investing in infrastructure.

---

## Milestone plan

The following milestones were sketched out at the start of the project. This is a
living document and will be updated as the project progresses.

**Milestone 0 — Domain registered (done)**
`heirlooms.digital` registered on 30 April 2026. The project name was finalised as
Heirlooms at the same time, chosen over Heirloom (singular) for its warmer, more
evocative feel — a collection of memories rather than a single object. The .digital
TLD was preferred over .co.uk to avoid a country association.

**Milestone 1 — Personal uploader (done)**
Android app that appears in the share sheet for photos and videos and HTTP POSTs
them to a configurable endpoint. Configurable application name and endpoint URL.
No app store required — sideloaded in developer mode.

**Milestone 2 — Personal server (done)**
Kotlin/http4k backend that receives uploads, stores file metadata in PostgreSQL, and
stores files in MinIO (S3-compatible). Runs in Docker. Full end-to-end test suite
using Testcontainers.

**Milestone 3 — Self-hosted deployment**
A single `docker-compose.yml` that a non-developer could run on a home server or
a cheap VPS to stand up the full stack: server, database, and object storage.
Simple instructions. No Kubernetes, no cloud accounts required.

**Milestone 4 — Settings and organisation (done)**
In-app settings screen to configure the endpoint without editing config files
(simplified in v0.10.0 to API key only after the domain went live).
Tag-based organisation of uploads (v0.16.0). Simple web UI to browse uploads
(v0.7.0). Tag rename, merge, colours/descriptions, exclude-filter UI, and
Android tagging are deferred — see IDEAS.md.

**Milestone 5 — Capsules**
A user can plant photos and a message for someone, to be opened on a chosen date.
A capsule has a shape (open: contents editable until delivery; sealed: contents frozen
at sealing) and a state (open, sealed, delivered, cancelled). Recipients are free-text
in v1, becoming connections at Milestone 7.

- **Increment 1 (v0.18.0, shipped 8 May 2026).** Schema and backend API. Four tables,
  seven endpoints, message versioning, state-machine validation, ~49 integration tests.
- **Brand follow-up (v0.18.2, shipped 8 May 2026).** Visual mechanic for capsule states
  (sealed/open/delivered) added to BRAND.md. Shipped between Increments 1 and 2 — earlier
  than the original plan (between 2 and 3) — to give the web UI a complete visual spec to
  build against.
- **Increment 2 (v0.19.0–v0.19.5, shipped 9 May 2026).** Web UI: Capsules list, capsule
  detail view (four state variants), create form, shared photo picker modal, photo detail
  page's "in N capsules" line and "Add to capsule" flow, confirmation dialogs, sealing
  animation, navigation guard. Bug fixes through v0.19.5; documentation sweep at v0.19.6.
- **Increment 3 (planned, combined with Android Daily-Use).** Android: extend the
  share-sheet flow with "+ start a capsule" as an alternative to plain upload. Capsule
  creation on Android. Planned to ship together with the Daily-Use increment below.
- **Android Daily-Use Increment (planned, combined with Increment 3).** Read-only Gallery
  and Capsules views on Android. Solves the phone/web switching friction. The
  orientation-change fix in v0.17.1 (`android:configChanges`) is tactical; the proper
  ViewModel + SavedStateHandle migration is scoped for this increment. Editing stays on
  web for now.
- **Closes Milestone 5.**

**v0.20.x — Compost heap (shipped 9 May 2026)**
A non-milestone increment between Milestone 5 and 6: the first user-facing removal
mechanism. Composting is soft and considered — a photo can only be composted if it has
no tags and no active capsule memberships. The 90-day window is the safety net; no public
hard-delete endpoint exists. Removal in Heirlooms is not a trash can: it is the product's
considered way of returning something to the earth.

**Milestone 6 — Garden / Explore restructure (done)**
A re-architecting of the main browsing surfaces before delivery work begins. The single
flat Garden tab becomes two: a *Garden* (work surface — the *Just arrived* plot plus
user-defined plots) and an *Explore* (leisure surface — paginated, filterable, where
you visit memories rather than process them). Adds backend foundations (EXIF extraction,
pagination, plot schema), new web surfaces, and Android adoption. See
`docs/presentations/Garden_Explore_Plan.pptx` for the phased plan.

- **D1 — Tools: re-import utility (done, v0.22.0).** Standalone `tools/reimport/`
  Gradle project. Scans GCS bucket, recreates `uploads` rows for any objects missing
  from the DB. Safety net for the rest of M6's destructive schema work.
- **D2 — Backend + Explore basic (done, v0.23.0).** V9/V10 migrations (exif_processed_at,
  plots schema + seed). ExifExtractionService recovery. Cursor pagination on list
  endpoints. Plot CRUD (4 endpoints). `/explore` page + nav entry.
- **D3 — Web complete (done, v0.24.0).** V11 migration (last_viewed_at + Just arrived index).
  Upload list filters + sort-aware cursor. View tracking endpoint. PATCH /api/plots batch
  reorder. Garden redesigned as plot rows with DnD + gear menu + inline Add form. Explore
  filter chrome + sort. PhotoDetail ?from=garden|explore variants with negative-action
  button separation. IDIOMS.md: Plot, Just arrived, Negative-action button separation.
- **D4 — Android adoption (done, v0.25.0/v0.25.1).** ViewModel + SavedStateHandle migration
  across all seven screens. Four-tab bottom nav (Garden | Explore | Capsules | Burger).
  Explore tab with filter bottom sheet and 4-column grid. Garden plot rows (LazyRow in
  LazyColumn) with interactive titles, long-press actions, and Just arrived 30-second poll
  with OliveBranchArrival animation. PhotoDetail ?from=garden|explore|compost flavours.
  Upload progress screen (one WorkManager job per file, byte-level progress, retry).
  Post-ship fixes: API response key corrections, coroutineScope exception propagation,
  system plot filtering, Garden staleness, Just arrived scroll drift.

The work nips several decisions in the bud: brand register on a workflow-vs-leisure
surface; loading-time issues that compound as the dataset grows; multi-user
implications for plot ownership and pagination that are easier to design correctly
now than to retrofit. Inserted before the originally-planned Milestone 6 (delivery,
now Milestone 8) because delivery deserves to land on a settled foundation rather
than a flat surface that's about to change shape.

**Milestone 7 — Vault E2EE** *(shipped — v0.30.0, 9 May 2026)*
The brand promise — "we cannot read your data" — becomes a cryptographic
property rather than an operational one.

Two-layer envelope encryption applied to the vault: every photo and video is
encrypted under a random per-file data encryption key (DEK); each DEK is
wrapped under the user's master key and stored as metadata. The server never
sees plaintext bytes or unwrapped DEKs. Heirlooms staff with full database
and bucket access see only ciphertext.

The master key is high-entropy, generated on the user's first device, and
never leaves any device in plaintext. Each device — Android (Keystore), web
(non-extractable WebCrypto) — has its own keypair; the master key is wrapped
to each device's public key separately. Adding a device follows a Signal-style
linked-device flow where a trusted device decrypts its master-key copy
locally, re-wraps to the new device's pubkey, and uploads. The server is a
dumb relay throughout.

Open capsules sit in the vault under the same model. Sealed capsules remain
a database-flag state in M7; cryptographic sealing is M9 work.

Recovery is a first-class feature. Three options offered, with the recommended
default shifting at M9 once a connections graph exists:

- *24-word recovery phrase* — generated and shown once at first-device setup,
  with required acknowledgement. The "I don't trust anyone" escape hatch.
- *Passphrase-wrapped server backup* — mandatory for any user who wants the
  web client. Argon2id derivation, server holds the wrapped blob.
- *Social recovery via Shamir shares* — deferred to M9, since single-user
  has no contacts graph.

Versioned envelope format with explicit algorithm and version identifiers in
every blob. Crypto agility is non-negotiable from v1, not a future-work item.
The DEK rotation path is documented in M7's brief: incremental, file-by-file,
old and new envelopes coexist during migration. AES-256-GCM is the v1
primitive; the format admits successors.

The web client is a session-only peer device: its private key is non-extractable;
its master-key copy is not persisted across sessions; it is re-derived each
login from the passphrase. Sealing happens phone-only (sealing is the
cryptographically heavy moment); reading is allowed on the web. The login
screen says "Type your Heirlooms passphrase," not "Sign in" — the asymmetry
between phone and web is a feature rather than something to hide.

Existing data is discarded; M7 starts from a clean slate. Friend tester does
not arrive yet — they wait for M8.

**Milestone 8 — Multi-user access** *(shipped 11 May 2026 — v0.38.0–v0.41.0)*
Per-user accounts and data isolation. The current single-user/shared-API-key
model becomes per-user authentication. Bret backfilled as the existing user
during the migration. Cross-user reads return 404 (privacy-preserving — a
probing attacker can't distinguish "doesn't exist" from "exists but isn't
yours"). Two-user isolation tests across every endpoint are the milestone's
non-negotiable correctness property.

The users table includes device-pubkey columns from day one so the sharing and
sealing milestones don't have to migrate. The wrapped-keys table M7 introduced
extends naturally to multiple users.

After M8 ships, the first non-Bret human (a friend tester) is onboarded.
M7's E2EE means the tester's data is end-to-end encrypted from first upload —
there is no "we'll add encryption later" caveat.

**Milestone 9 — Friends, item sharing, Android plot management** *(shipped 11–12 May 2026 — v0.45.0–v0.46.2)*

The social layer between multi-user infrastructure (M8) and shared galleries (M10).
Each user has an account-level P-256 sharing keypair. Friendships form automatically
on invite redemption. Individual item sharing: the sender re-wraps the item DEK under
the recipient's sharing pubkey; the recipient gets a new upload record pointing to the
same GCS blob, landing in Just Arrived. Received items show "Shared by [name]"
attribution and are independently taggable and rotatable. Android plot management:
create, rename, delete from the Garden. Web sharing: ShareModal with friend picker,
DEK re-wrapping, and attribution. Garden thumbnails switched from crop to fit.
First non-Bret human tester onboarded 12 May 2026.

**Milestone 10 — Shared plots** *(current — briefed 12 May 2026)*

Named collections visible to multiple users, built on a richer predicate/criteria
system that replaces the tag-only plot model. The predicate system is the foundation:
a boolean expression language (`tag`, `media_type`, `taken_after`, `has_location`,
`is_received`, and more) composable with AND/OR/NOT, stored as JSONB on plots.
Hidden plots (`show_in_garden = false`) serve as reusable predicate building blocks.

Flows route items matching a criteria expression into any collection plot, with an
optional staging review gate. Decisions (approve/reject) are scoped to the plot, not
the flow — multiple flows feeding the same shared plot share one review surface.
Public plots always require staging.

Shared plots carry a per-plot E2EE group key (AES-256-GCM). The plot key is wrapped
to each member's sharing pubkey individually; the server never sees it. Members:
owner (add/remove items, invite, delete) and member (add items, invite). Invitation
via friends list (primary) or 48-hour invite link (fallback). Plot key rotation on
member removal is deferred. See `docs/briefs/M10_brief.md`.

**Milestone 11 — Strong sealing + social recovery** *(was Milestone 9)*
*Sealed* becomes a cryptographic state, not a database flag.

Sealed capsules carry up to three independent unlock paths, each defending
against a different failure mode:

- The per-capsule key is wrapped to the recipient's pubkey at sealing time.
  After sealing, only the recipient can unwrap it — the author cannot, even
  from their own device. This is what makes "sealed" cryptographically
  meaningful.
- For date-conditioned capsules, the per-capsule key is also tlock-encrypted
  against the drand round corresponding to the unlock date. Time itself
  becomes a cryptographic gate; until the round publishes, no party — author,
  recipient, Heirlooms, drand — can decrypt.
- The per-capsule key (or master key, depending on configuration) is split
  via Shamir's Secret Sharing across nominated executors. Threshold of
  executors plus death verification recovers the key.

The redundancy is the design's most important property. Each path defends
against a different failure mode of the others — recipient credential loss,
drand chain retirement or BLS12-381 break, author death without prior
configuration.

The connections data model lands here: identity for capsule recipients,
executor nominations, and recovery shareholders are the same people, modelled
once. Free-text recipients from earlier milestones evolve into named
connections.

Social recovery via Shamir replaces the recovery phrase as the recommended
default for vault master-key recovery — the same connections graph that holds
capsule recipients also holds recovery shares. The 24-word phrase remains as
the deeper escape hatch.

The time-lock layer is pluggable: the capsule format records which scheme
(drand chain ID, or future alternatives) it was sealed under, so new schemes
can be added later. tlock is one tool in the kit, not the foundation — the
multi-layer wrapping is what defends against any single primitive failing
over a multi-decade horizon.

UX commitments: defaults guide users to configurations that tolerate realistic
attrition (3-of-N with N ≥ 7 for multi-decade capsules). Periodic check-ins
prompt users to verify executors are still reachable. Honest copy at the
moment of capsule sealing makes clear which guarantees apply to this specific
capsule.

**Milestone 12 — Milestone delivery** *(was Milestone 10)*
Scheduled delivery of a capsule on a specific date. The feature that makes
Heirlooms more than cloud storage.

Lands on the M11 foundation: recipient pubkey wrapping has happened at sealing,
tlock has held the gate cryptographically, executor shares are configured for
capsules that need them. Delivery is the moment where Heirlooms's role — keep
the ciphertext available, notify the recipient on the date — is exactly what
the cryptographic design asked of it: nothing more.

Includes the resolution UI for capsules sealed with free-text recipients
before M8/M9 (see IDEAS.md). Email is the baseline notification channel;
in-app delivery is the canonical surface. Print-on-delivery and other
physical-world integrations are recorded as a second wave (see IDEAS.md,
"Third-party delivery integrations").

**Milestone 13 — Posthumous delivery** *(was Milestone 11)*
Executor-mediated unlock for capsules whose release is conditioned on the
user's death rather than on a date. The product's deepest promise — a video
for a daughter on her 18th birthday, sealed by a parent who knows they may
not be there — kept honestly through a mechanism where Heirlooms itself never
holds enough material to unlock anything. The executors collectively do.

Death-verification mechanism designed alongside the cryptographic unlock —
they are the same system. Conservative defaults, clear UX about what's
happening, and a periodic check-in that asks living users to confirm their
executor cohort is still reachable.

Onboarding for posthumous-only capsules refuses to seal until executor shares
are configured. Pure cryptographic guarantee meets a UX-level guard: the
product will not let users create capsules they can foresee being unrecoverable.

---

## Retired milestones

**Milestone 3 — Self-hosted deployment (retired May 2026)**
Originally planned as a single `docker-compose.yml` that a non-developer
could run on a home server or cheap VPS. The trust argument — "you don't
have to trust us, you can run it yourself" — was the motivation.

Two things absorbed it. The GCP/Cloud Run deployment path took over the
operational shape; the self-hosted bundle would have been a parallel
maintenance burden with no realistic users. And M7's vault E2EE delivers a
stronger version of the same trust property — the operator cannot read your
data regardless of who runs the server. "Run it yourself" stops being the
strongest version of the trust story when the server can't read your data
anyway.

Could be revived as a much-later enterprise/family-server SKU if the demand
materialises. Off the active roadmap.

---

## Philosophy

Start personal, stay simple, add trust slowly.

The product earns the right to handle something as sensitive as a person's digital
legacy by being reliable, honest about its limitations, and giving users full control
over their data at every step. An encrypted export that works without the app is not
a fallback — it is a core feature.
