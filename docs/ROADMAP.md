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

**Milestone 5 — The capsule mechanic**
The ability to mark a set of uploads as a named capsule with access rules:
who can see it, and when. Time-locked delivery. Trusted contact designation.

**Milestone 6 — Milestone delivery**
Scheduled delivery of a capsule on a specific date — a child's 18th birthday,
a graduation, a wedding. The feature that makes this more than cloud storage.

**Milestone 7 — Multi-user access**
Beneficiary accounts: people who can receive but not upload. Notification on unlock.
Dignified, personal delivery experience rather than a password reset email.

---

## Philosophy

Start personal, stay simple, add trust slowly.

The product earns the right to handle something as sensitive as a person's digital
legacy by being reliable, honest about its limitations, and giving users full control
over their data at every step. An encrypted export that works without the app is not
a fallback — it is a core feature.
