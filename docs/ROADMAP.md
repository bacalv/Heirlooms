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

**Milestone 6 — Garden / Explore restructure**
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

The work nips several decisions in the bud: brand register on a workflow-vs-leisure
surface; loading-time issues that compound as the dataset grows; multi-user
implications for plot ownership and pagination that are easier to design correctly
now than to retrofit. Inserted before the originally-planned Milestone 6 (delivery,
now Milestone 8) because delivery deserves to land on a settled foundation rather
than a flat surface that's about to change shape.

**Milestone 7 — Multi-user access**
Per-user accounts and data isolation. The current single-user/shared-API-key model
becomes per-user authentication. Bret backfilled as the existing user during the
migration. Cross-user reads return 404 (privacy-preserving — a probing attacker
can't distinguish "doesn't exist" from "exists but isn't yours"). Two-user
isolation tests across every endpoint are the milestone's non-negotiable
correctness property.

After M7 ships, the first non-Bret human (a friend tester) is onboarded.

**Milestone 8 — Milestone delivery**
Scheduled delivery of a capsule on a specific date — a child's 18th birthday,
a graduation, a wedding. The feature that makes this more than cloud storage.
Designed against real recipient accounts that exist post-M7, including a
resolution UI for capsules sealed with free-text recipients before M7 (see
IDEAS.md).

---

## Philosophy

Start personal, stay simple, add trust slowly.

The product earns the right to handle something as sensitive as a person's digital
legacy by being reliable, honest about its limitations, and giving users full control
over their data at every step. An encrypted export that works without the app is not
a fallback — it is a core feature.
