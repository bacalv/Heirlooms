# Heirlooms — Strategic Concepts from the M8 Implementation Session

**Date:** 11 May 2026
**Context:** A session that started as M8 E5 brief preparation and turned into
a wide-ranging strategic conversation about what Heirlooms is and what it
could become. The technical work that triggered the conversation is captured
in `M8_E5_brief.md`; this document captures the *product and strategic
concepts* that emerged alongside it.

The themes are loosely ordered but not strictly sequential — they're more like
threads that wove around each other through the session. Each is captured
faithfully enough that a future session can pick any of them up cleanly.

---

## Concept 1 — The MCP server (read-only Heirlooms integration in Claude)

**Origin:** A question about whether interacting with Heirlooms could become
a Claude skill, and whether users could buy AI agents to perform tasks for
them.

**What it is:** A read-only MCP server exposing the Heirlooms API to Claude,
so that when a user is in a Claude conversation, Claude can answer questions
about their vault — what's planned, who's getting what, what hasn't been
touched in a while, whether anything is approaching a delivery date.

**What it deliberately is not:** A way for Claude to *compose* capsule
content, write tender messages, or generate artifacts on the user's behalf.
The brand voice is "the user, on their own, putting something into a box
for someone they love." If Claude wrote your granddaughter's birthday letter,
the product has eaten itself. The value of Heirlooms comes from human
intention behind every artifact.

**Critical security boundary:** Claude must never have access to the user's
master key. The MCP server exposes metadata only — capsule list, recipients,
scheduled unlock dates, photo counts, tags — never decrypted content.
Vault contents stay e2e encrypted under the existing model. The user can
still ask Claude things like "summarise what I've prepared for my daughter's
21st" but the summary is over the metadata, not the contents.

**Why it's on-brand:** It signals seriousness without compromising the
product's quietness. "Heirlooms works in Claude" is the kind of small,
dignified integration that fits the voice. It's not a feature anyone shouts
about; it's a feature that the right user discovers and appreciates.

**Roadmap placement:** M10 or M11. After multi-user (M8) and contacts (M9)
have settled and the API surface is stable enough to expose externally.

---

## Concept 2 — Artifact care (the right framing for "AI agents")

**Origin:** A counter-question about whether users could buy agents to "process
this video and add subtitles."

**The reframing:** Don't sell agents. The product isn't an agent marketplace —
it's a thoughtful service. The *capability* the user wants (subtitles,
transcripts, colour correction, audio cleanup on a 45-minute message to a
grandchild) is real and valuable. The *framing* matters enormously.

- "Buy an AI agent for £3 to add subtitles" → App Store. Wrong product.
- "Heirlooms keeps your memories well-cared-for; transcripts and subtitles
  are included for Family plans" → the actual product, expressed honestly.

The underlying compute is identical. The relationship to the user is completely
different.

**The deeper insight:** Heirlooms is uniquely positioned to *quietly improve
artifacts over time* so that the recipient, 30 or 50 years from now, gets
the best possible version — not the phone-shot version the user submitted
in 2026. This is something the user couldn't or wouldn't bother to do
themselves. It's exactly the kind of thing that justifies the recurring
subscription that any cloud-storage-for-precious-things business needs.

**Concrete possibilities (technical pieces are mostly off-the-shelf):**
- Whisper-class transcription → searchable transcripts of every video
- Speaker-diarisation → "this is Grandma talking from 0:00 to 2:14"
- Audio cleanup (noise reduction, leveling) on voice recordings
- Colour and exposure correction on photos and videos
- Tilt correction on phone videos shot vertically and held wonky
- Subtitle generation (mostly free once transcripts exist)
- Quiet upscaling of older / smaller media when models improve

**Critical UX shape:** The user doesn't ask for these. They just happen.
The user gets a gentle notification: *"we added subtitles to your video
from 14 May."* The decision-making is invisible. This is the opposite of
agent UX, where the user delegates a task and watches it execute.

**Roadmap placement:** A milestone of its own — probably the moment a paid
tier launches. Could be M12-M14. Each capability ships individually; users
slowly notice their archive getting quietly better.

**Strategic note:** The artifact-care framing might be the cleanest
articulation of *why Heirlooms is a recurring-subscription business rather
than a one-time-purchase business*. Storage alone is a commodity. Storage
plus continuous improvement of the stored thing is not.

---

## Concept 3 — Physical fulfilment (bridge from digital vault to physical world)

**Origin:** A second example of "agent task": "deliver a physical birthday
card to my niece every year for the next 5 years with unlock QR codes."

**What it actually is:** Not an agent. A *fulfilment service*. It answers a
question the product hasn't yet asked: when a delivery moment arrives 20 or
50 years in the future, how does the recipient actually find out? The current
implicit assumption is email or push notification, but a posthumous capsule
to a child now 18 might not have an email address that the original user
could have known about.

**The QR-via-physical-mail idea:** A printed birthday card, lovingly designed,
mailed at the right moment, carrying a single-use QR code that resolves to
the unlocked capsule. The physical envelope acts as the delivery channel
where digital identity can't be predicted decades ahead.

**Adjacent things this opens up:**
- Print-and-mail of selected photos alongside the capsule
- Physical objects with the capsule encoded (USB stick, sealed package)
- A lawyer or executor holding a sealed envelope with a recovery code
- Memorial-style physical deliveries for posthumous capsules
- Recipient-side identity verification before unlock (you have to
  authenticate against something only the intended recipient would
  reasonably possess)

**Why it's defensible:** This is the *least replicable* feature any digital
competitor could match. Software companies don't ship physical objects well;
fulfilment partnerships are slow, painstaking work; printed materials require
real design taste. If Heirlooms builds this credibly, it becomes a moat that
gets stronger over time, not weaker. The whole product's pitch is *time*,
and physical delivery is one of the few mechanisms that genuinely clears
the 20-year horizon reliably.

**Roadmap placement:** A major post-launch milestone. Probably M15+ — once
there are paying users with real delivery dates approaching, the use case
will assert itself naturally. Don't build it speculatively; build it when
users start asking.

---

## Concept 4 — The trust-network reframing (why "agents" is the wrong word)

**Origin:** Bret's idea about spawning agents that hold part of his key
and ensure cryptographic integrity over decades, even if he dies.

**The crucial reframing:** What Bret described isn't an AI agent. It's a
**cryptographic custodian** — a long-lived process or party that holds a key
share, executes a deterministic protocol when conditions are met, and
otherwise sits quietly. It doesn't need to think. It needs to *not lose the
share*, *prove it still has the share*, and *release it correctly*.

**Why the terminology matters:** Custodians want to be small, boring,
auditable, and limited. Agents want to be flexible, language-understanding,
ambiguity-handling. These are opposite design pressures.

- "Agent" → flexible, modern, AI-ish, *fragile*.
- "Trustee" / "Custodian" / "Guardian" → old, boring, well-understood,
  *durable*.

Reach for the older language. People understand trustees intuitively because
human civilisations have used trustees around death and inheritance for
centuries. The word does work the product can lean on.

**Where AI does fit (narrowly):** A custodian needs to *evaluate release
conditions* — "is this person actually dead?", "is this court order
legitimate?", "is this scanned death certificate from a country I've never
heard of consistent with that country's official documents?" LLM-in-the-loop
is useful as a *judge helping a human reviewer*, not as the custodian
itself. Intelligence sits *beside* the custodian, augmenting human judgment.
The custodian itself stays dumb.

---

## Concept 5 — The five properties of a trustworthy digital custodian

A framework for evaluating any proposed custodian design.

**1. Longevity.** It must credibly outlive what it's holding things for.
For Heirlooms that might be 80 years (a 30-year-old user, capsule for a
child to open at 18, user lives to 90, capsule continues past their death).
Most software services don't last that long. Most companies don't. Most
*countries* don't, for that matter. This is the hardest property and the
one that gates everything else.

**2. Independence.** No party with a stake in the outcome can control the
custodian. Not Heirlooms-the-company (or e2e is broken). Not the recipient
(they'd unlock early). Not the user themselves (they've gained nothing).
Custodians must be *separate parties* with no incentive to defect.

**3. Verifiability.** While the user is alive, they must be able to check
the custodian still holds the share and is operating correctly. A custodian
that silently lost your share 8 years ago is worse than no custodian. There
has to be a *heartbeat* — a regular cryptographic proof-of-share-still-held
that the user or their trustees can verify. ("Proof of custody" — the same
primitive Filecoin uses to keep storage providers honest — is the right
shape here.)

**4. Replaceability.** The user must be able to remove and replace any
custodian without ever exposing the master key. This is what makes the
trust network *tendable* — people die, institutions get acquired, services
get gutted, and the network has to rebalance over time. Threshold schemes
support this naturally (re-sharing among a new set of holders without
changing the underlying secret), and this property is the entire reason
the design works at long horizons.

**5. Auditability.** When a release happens, there must be a paper trail.
Not for security reasons (the crypto already handles those) but for
*legitimacy*. The great-grandchild receiving an unlock in 2087 sees:
"your great-grandfather Bret sealed this on 11 May 2026; here's how the
trustees verified the release; here are the conditions that triggered
it." That trail makes the unlock feel like an *honourable handover*,
not a technical glitch. It also makes the system answerable to courts.

**The institutional consequence:** These five properties don't just describe
a custodian — they describe a *class of institution*. Building this properly
isn't really a software feature. It's institution-building. Software is
necessary but not sufficient.

---

## Concept 6 — The five categories of custodian (and why you need all of them)

No single category satisfies all five trustworthiness properties. The robust
design *diversifies across categories* so no single failure mode brings down
the network.

### Family trustees
Other humans the user designates — spouse, sibling, adult child, close
friend. Each has a Heirlooms account; their share is wrapped to their
account's master key. They authenticate normally to produce their share
when asked; they never see it directly.

- **Strengths:** High trust. Zero infrastructure cost. No new institutions.
  Intuitive — "choose three people who'll stand for you" makes sense.
- **Weaknesses:** Human lifespan < 100 years. Relationships strain.
  People become unreachable. Trustees can collude. Good for 5-30 year
  horizons; insufficient for 50+.
- **Critical rule:** Capsule *recipients* must never be allowed as
  trustees. Their incentive is to unlock early; making them trustees
  creates a conflict the user probably doesn't see coming. The product
  should detect this and refuse.

### Institutional trustees
A law firm, a notary, a bank, a national archive, a probate registry. Real
institutions with legal continuity, participating in the protocol as a
service.

- **Strengths:** Long-lived (a partner-track law firm has 50+ year
  continuity; the British Library has 300+). Legitimate in courts.
  Already familiar — "my lawyer has a sealed envelope" is something
  people already do, this is the digital version.
- **Weaknesses:** Expensive. Not internationally portable. Slow to
  act when the moment comes. Staff turnover means the *people* who
  knew about your arrangement may not be there; the institution needs
  internal protocols so no single staff member needs to know.
- **Practical move:** Partner with two or three across jurisdictions
  (UK, US, continental Europe). Real legal work — contracts, escrow
  agreements, SLAs that survive partnership dissolution.

### Software custodians on dedicated infrastructure (the Foundation)
A long-running service whose only job is holding shares and executing the
protocol. Crucially, *not Heirlooms-the-company* — Heirlooms can't credibly
run something that has to outlast it. Must be separate, with its own funding,
legal entity, and continuity plan.

**The Heirlooms Foundation idea, made structural:**
- Separate legal entity, with a charter mandating the custodial function
  as primary purpose.
- Endowment funding (~£3-10M would buy decades even at conservative
  drawdown).
- Its own infrastructure, separate from Heirlooms-the-company. Different
  cloud accounts, domains, keys, operations team.
- Board including outside trustees — legal experts, cryptographers,
  possibly customers — with the explicit duty to keep it running
  independently of the company.
- A protocol for orderly shutdown: if it ever has to wind down, shares
  get re-shared to other custodians in the network before the lights
  go off.

**Strategic role:** Not the *only* custodian. The *backstop* custodian
— the one that exists specifically because every other category has
lifespan limits.

**Cultural model:** The Long Now Foundation does this for the 10,000
Year Clock. The Internet Archive does it for the web. Separate the
long-horizon mission from the company's quarterly P&L.

### Federated custodians
The most ambitious option, probably the most resilient long-term.
Heirlooms publishes an open protocol. Other organisations run nodes that
comply. Users choose which nodes hold their shares. Nodes can be other
digital-legacy companies, archives, libraries, blockchain-anchored
services, government registries.

**The killer property:** *Share migration*. If a node degrades, the share
migrates to another node without exposing the secret. The network heals
itself.

**The problem:** Bootstrapping. A federation needs federation members.
The first email server was useless. Not a v1 feature — something you
*grow into* once the protocol is proven and other organisations want in.

**The design move available now:** Make the protocol open from day one
even if Heirlooms is the only node initially. That single decision keeps
the door open without forcing you through it.

### Cryptographic time-locks
A category requiring no human or institutional custodian: a share encrypted
under a condition that can only be evaluated by physics or maths passing.
Time-lock puzzles, verifiable delay functions, threshold beacons (drand).

- **Strength:** No trust required. Just maths.
- **Weakness:** The cryptography is young. drand has been running ~6
  years, not 100. Long-horizon properties not proven.
- **Right use:** *Secondary* layer for date-based capsules. Custodial
  network is primary; time-lock is the backup if every other share is
  lost. Wouldn't lead with this in v1, but it's a really good
  defence-in-depth layer.

### A reasonable default trust network
For a typical user, a 3-of-5 scheme like:
- 2 family trustees
- 1 institutional trustee (Heirlooms partner notary or your own lawyer)
- 1 Foundation node (opted in by default)
- 1 federated node *or* cryptographic time-lock

Any 2 can be compromised, lost, or unavailable; the user is still
recoverable. Concurrent failure across *categories* is much rarer than
within one. Different defaults for different user profiles — 25-year-old's
default trust network looks different from an 80-year-old's.

---

## Concept 7 — Tending the trust network as a continuous practice

The most quietly important insight from the trust-network thread.

**The wrong metaphor:** Split the key once, hand out shares, forget it.

**The right metaphor:** *Tend a garden of trust over time.*

- Shares get refreshed periodically.
- Trustees get replaced as people drift, die, or institutions fail.
- The user reviews the network on a gentle cadence — annual or quarterly.
- The product nudges: "Your trust network is still healthy. Three of
  four trustees responded to the most recent heartbeat. Want to review?"

**Why this is the deep idea:** This is what makes the trust network
genuinely a 100-year mechanism rather than a 100-year hope. Without
re-sharing and rebalancing, the network ossifies into whatever you set
up at 25 and is wrong by the time you're 60. With it, the network adapts.

**Brand alignment:** This framing fits the existing voice perfectly.
Heirlooms isn't a service you configure once and ignore — it's a
relationship you have with your own future. The trust network is part
of that relationship.

---

## Concept 8 — The user experience: how the trust network *feels*

The technical structure only matters if users can use it. The flows that
make it work:

**Onboarding (or dedicated setup later):**
*"Heirlooms uses a network of trustees to make sure your account can be
recovered if something happens to you. Choose at least three. Some can be
people you trust; some can be institutions; one is a Heirlooms Foundation
node that exists specifically for this purpose."*

User picks trustees. Some are email-invites to friends/family. Some are
institutional partners from a list. One is the Foundation, opted in by
default. The user sets the threshold (default: majority).

**Quarterly or annual nudge:**
*"Your trust network is still healthy. Three of four trustees responded
to the most recent heartbeat. Want to review them?"* User can replace
unresponsive trustees. The network re-shares.

**Release event:**
The trustees who need to cooperate get a notification. They authenticate.
They review the release conditions in plain language: *"Bret has been
inactive for 24 months and his death has been verified by [registry]."*
They approve. The shares reconstruct. The capsules deliver.

**At the recipient end:**
The recipient sees a paper trail: *"This capsule was sealed by Bret Adam
Calvey on 11 May 2026. It was released on 14 March 2087 after verification
by [trustees]. Here's what's inside."* The cryptography is invisible; the
legitimacy is felt.

That moment — the recipient understanding that this is a careful,
deliberate handover and not a database lookup — is *what the whole
system is for*.

---

## Concept 9 — Blockchain as one layer, never the load-bearing one

A specific thread on whether to use blockchain in the custodian network.

**Honest answer to "will any blockchain last 30 years":** Almost certainly
some will, but probably not the ones you'd bet on today, and "lasting" is
doing a lot of work in that sentence.

**Four senses of "lasting":**
1. The ledger still exists and is verifiable.
2. Active validators or miners still produce blocks.
3. It's still *secure* (incentives intact, cryptography still strong).
4. It's still *useful* (transactions cost something sane, tooling
   still works, wallets still exist).

You can have (1) without (2). You can have (3) without (4). For a digital
custodian, you need all four.

**What we know from longevity research:** Things last when they're
*protocols* rather than products, *embedded in institutions* rather than
standalone, have *multiple independent implementations*, and are
*foundational* enough that other things depend on them. TCP/IP, SQL,
Unix, AES — these survive. Individual applications from the same era
mostly don't.

**Current candidates evaluated against those criteria:**
- **Bitcoin** — strongest case for raw longevity. Simple design, no
  central foundation that can fail, deep economic security, monetary
  embedding. But deliberately not very programmable; awkward as a
  conditional-release platform. Security model depends on miner
  incentives that may degrade.
- **Ethereum** — much more programmable, active development, multiple
  independent clients (a real longevity property), institutional
  adoption. But upgrade risk is real — major architectural changes
  every few years, each one a chance to break older contracts.
- **Everything else** — speculative. Base rate for crypto projects is
  brutal at 10 years, never mind 30.

**The Y2K-applied-to-crypto problem:** Even if the chain itself lasts,
the surrounding software might not. Wallets, RPC providers, SDKs,
UIs — all live on much shorter timescales than the chain protocol.
The Bitcoin ledger from 2009 is still verifiable; the wallets people
used in 2009 are mostly gone.

**Design conclusions:**
1. **Never load-bearing.** A blockchain is one share-holder among
   several, not the trust root. If the chain dies, one share is lost
   and the others reconstruct.
2. **Use the most established chains for the simplest use cases.**
   Bitcoin or Ethereum for date-based time-locks, no exotic
   dependencies, no esoteric L2s.
3. **Design for migration.** When a chain shows signs of trouble, the
   share moves to a different custodian via the re-sharing protocol.
   No chain is a permanent home; it's the current best instance of
   its category.
4. **Plan for cryptographic migration generally.** Any 100-year
   custodian has to plan for algorithm replacement. The cryptography
   used in 2026 is not the cryptography used in 2087. Re-sharing
   already handles this if it includes a "re-encrypt under current
   primitives" step.

**The deeper point that generalises beyond blockchain:** *Never put all
of a user's trust in any single mechanism, no matter how durable it
looks today.* The 100-year promise can't be made by any single
technology, institution, or chain. It's made by the network and the
protocol and the diversity, and the design assumes any given member
will eventually fail.

This is freeing — it means you don't have to bet correctly on which
blockchain (or which law firm, or which jurisdiction). You just have
to ensure no single bet is load-bearing.

---

## Concept 10 — The reframing of what Heirlooms is

This is the meta-insight running underneath the conversation, and the
thing most worth writing down.

**The current self-description:** Heirlooms is a software product. A
vault. A delivery system. Useful, well-built, but ultimately a piece of
software hoping to outlive its creators.

**The reframing the conversation surfaced:** Heirlooms is **infrastructure
for a kind of promise** — and that infrastructure is part-software,
part-institution, part-protocol, part-fulfilment-network.

The software (the vault, the capsules, the delivery system) is the same
in both framings. What changes is everything around it: the Foundation,
the partner network, the trust protocol, the artifact-care service, the
physical fulfilment, the paper trail. The 100-year promise stops being
marketing language and starts being a structural commitment with named
parties answerable for keeping it.

**The consequence for the customer:** Different customer too. Right now,
the customer is someone who wants their photos and videos safe and
eventually delivered. With the full picture, the customer is someone who
wants their *intentions honoured* — and is willing to pay more for that
honouring to be credible. It's the difference between a backup service
and a *legacy service*. Same software, different product, very different
willingness to pay.

**The strategic claim worth examining:** *Heirlooms is a
subscription-and-institution business, not a software product.* This
might be the most important sentence to come out of the session. The
software is necessary but not sufficient. The institution-building is
where the durable value lives.

**The pressure point:** This direction is going to assert itself whether
or not Bret chooses it. Users at 100-year horizons will start asking the
obvious questions ("what if you go bankrupt?", "how do I know my account
will still be unlockable?", "who can I name as a recovery person?").
Building deliberately for these questions is much cheaper than being
pushed there. The work is easier when it's planned than when it's
forced.

---

## Synthesis — Two themes, four milestones

If the concepts above were filtered down to their commercial and product
essence, two themes emerge:

**Theme A: Heirlooms cares for the artifact, not just stores it.**
This is the artifact-care framing (Concept 2). It's what makes the
business recurring rather than one-time. Subtitles, transcripts, audio
cleanup, colour correction, eventual upscaling. The user doesn't ask;
it just happens; the archive quietly gets better.

**Theme B: Heirlooms is an institution, not just a service.**
This is the trust-network thread (Concepts 4-8) plus the Foundation
(Concept 6) plus the physical fulfilment (Concept 3). It's what makes
the 100-year promise credible. Multiple custodians, multiple categories,
diversified failure modes, paper trail, physical delivery as a bridge
across digital identity gaps.

**Four loose milestone shapes for the future:**

- **M10-M11: The MCP server.** Quiet, on-brand, technically small.
  "Heirlooms works in Claude." Read-only metadata, never decrypted
  content. (Concept 1.)
- **M12-M14: Artifact care.** Probably alongside the launch of a paid
  tier. Each capability ships individually; the archive quietly gets
  better over time. (Concept 2.)
- **M15+: The trust network.** Trustees, the Foundation, the partner
  institutions, re-sharing as a continuous practice. The hardest and
  most brand-defining milestone. (Concepts 4-8.)
- **M16+: Physical fulfilment.** Print-and-mail, QR-card delivery,
  the bridge from digital vault to physical world. Once there are real
  users with real delivery dates approaching. (Concept 3.)

These are not commitments — they're shapes for thinking. The order might
shift; some might collapse together; some might be deferred indefinitely
if Heirlooms decides to be a smaller thing. But they're each worth one
person thinking about for a few hours, and now they exist in writing.

---

## Open threads from this session

Things touched on but not fully developed; worth returning to.

1. **The Heirlooms Foundation as a real legal entity.** Concrete steps:
   what charter, what jurisdiction, what funding mechanism, what board
   composition. Big work, not soon, but the design implications start
   showing up earlier than people expect.

2. **The protocol-openness decision.** Should the custodian protocol
   be published openly from day one (enabling future federation) even
   when Heirlooms is the only node? The cost is small; the option value
   is large. Worth a deliberate decision rather than drifting either way.

3. **Recipient identity verification at decades-out delivery.** A
   posthumous capsule to a child now 4 years old: how does Heirlooms
   know in 2050 that the person claiming to be them really is them?
   This thread connects to the physical-fulfilment concept but also
   needs its own thinking. Identity-over-50-years is *itself* an open
   research problem.

4. **The remaining custodian properties (Independence, Verifiability,
   Replaceability, Auditability) at the level of design detail.** This
   session covered the *what* and the *why* but not the *how* for these
   four. Each deserves its own focused session — what does "verifiable
   custody" actually look like in protocol terms? How is "replaceability"
   implemented in a threshold scheme? Etc.

5. **The artifact-care notification UX.** "We added subtitles to your
   video from 14 May" sounds easy but the tone is hard. Too eager
   (*"Look what we did!"*) breaks the brand. Too quiet and the user
   never notices the value they're paying for. The right cadence,
   tone, and granularity is its own design problem.

6. **The "you can't name capsule recipients as trustees" rule.** What
   other safety rules does the trust-network UX need? This is one
   example of a *foreseeable misuse* that the product should refuse
   gently. There are probably others — name your spouse as the sole
   recipient *and* sole trustee, name your lawyer as trustee for a
   capsule whose recipient is also your lawyer's client, etc. A
   focused session on trust-network safety rules would be worth doing
   before the design lands.

---

*Captured 11 May 2026 alongside M8 E5 brief preparation. Source: a single
extended conversation between Bret and the PA. Companion document:
`M8_E5_brief.md` for the immediate technical work that triggered the
session.*
