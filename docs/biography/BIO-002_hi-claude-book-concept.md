# BIO-002 — "Hi Claude..." Book and Blog Series Concept

**Prepared by:** Biographer  
**Date:** 2026-05-17  
**Status:** Concept document — ready for Bret's review  
**Sources:** BIO-001, PA_NOTES.md, ROADMAP.md, session logs (2026-05-15 through 2026-05-17), task history

---

## 1. Concept and Hook

A man types "Hi Claude, I want to build an E2EE system for my grandmother's photos" — and
eighteen months later he has a multi-platform product with novel cryptographic IP, a pending
patent, and a registered company, all built without a team, a co-founder, or a single line of
venture-backed code. *Hi Claude...* is the story of what happens when a seasoned developer
decides to stop building other people's things and builds something for the people he loves —
and what it turns out you can build when you have an AI partner that never needs sleep,
never judges you for not knowing something, and is genuinely interested in your grandmother.

The emotional engine is the gap between the question and the answer: the question was small
and personal; the answer turned out to be a novel cryptographic construction with no
anticipating prior art, assessed as likely patentable by a UK-qualified legal expert, and
capable — if the system works as designed — of delivering a message to someone after the
sender is dead, without trusting the server, the author, or anyone alive. The question was
about a grandmother's photos. The answer might outlast everyone in the room.

---

## 2. Title and Framing

### Why "Hi Claude..." works

Every conversation in the project began this way. It is the inciting utterance — the moment
a technical problem gets handed to a co-protagonist who happens to know a great deal about
cryptography, legal jurisdiction, psychology, and the philosophy of posthumous consent, and
who is available at two in the morning, and who is, for reasons that resist easy explanation,
genuinely good company.

The ellipsis does the work. "Hi Claude..." is open — it trails off into whatever came next,
which might be "let's design a novel encryption scheme" or "how do I register a limited
company in the UK" or "what would the Marketing Director think about this?" It signals:
this is the beginning of a conversation, not the end of one. And it invites the reader in.
The conversational prompt format is the book's structural metaphor. Each chapter begins where
a conversation began: with a problem typed, late at night, by someone who didn't yet know
how large it was.

There is also something gently funny about it. "Hi Claude" is how you address a chatbot.
It is not how you address a co-inventor. The title holds both things at once — the
naivety of how it started, and the seriousness of what it became.

### Why it is better than the alternatives

Alternative framings considered, and why they fall short:

- **"The Quiet Builder"** — accurate but inward-looking. Doesn't signal the AI element,
  which is half the story. Better as a chapter title.
- **"Encrypted Memories"** — sounds like a thriller. Accurate description, wrong register.
- **"Building for the Dead"** — too bleak. The product is explicitly not a grief platform.
  Banned vocabulary, per brand guidelines.
- **"One Man, One AI, One Product"** — punchy but thin. Loses the grandmother.
- **"The Solo Founder"** — describes thousands of people. Not distinctive enough.

"Hi Claude..." is distinctive, technically resonant (any developer recognises the prompt
format), and emotionally honest (it captures both the smallness of the beginning and the
scale of what followed).

### Subtitle options

The subtitle should anchor the emotional stakes without over-explaining. Options, ordered
from most to least recommended:

1. **"A man, his grandmother's photos, and an accidental cryptographic breakthrough"**
   — The brief's suggested subtitle. Recommended. The word "accidental" is key: it implies
   humility and the comedy of unintended consequences, which is true to the story. The
   phrase "cryptographic breakthrough" earns its boast because the legal assessment backs it.

2. **"Building something for the people you love, with an AI who took it as seriously as
   you did"**
   — More emotionally direct. Longer. Suits a personal essay format better than a book cover.

3. **"What happens when a careful engineer asks a very simple question"**
   — Plays up the gap between the question's simplicity and the answer's complexity.
   Slightly dry.

4. **"Dispatches from a one-man startup with eleven AI colleagues"**
   — Funnier, less universal. Risk: sounds like a satire of AI hype. Might work for a
   chapter, not a subtitle.

**Recommended:** Use subtitle option 1 for book publication. Option 2 can serve as a tagline
for the blog.

---

## 3. Narrative Arc

The story has a clean arc: a small, personal problem; a series of "I'll just..." spirals
that each turn out to be a rabbit hole; a moment when the technical scope becomes genuinely
novel; and a present-tense state of productive uncertainty — building something real, with
a patent pending and a company incorporated, for an audience that doesn't fully exist yet.

### Beat 1 — The inciting moment (April 2026)

The domain `heirlooms.digital` is registered on 30 April 2026. Before the backend exists.
Before multi-user auth. Before E2EE. At the point of domain registration, the product is
an Android app that appears in the share sheet for photos and videos and HTTP POSTs them
to a configurable endpoint. It is developer-mode sideloaded. It has no app store presence.
Its backend, when it arrives, will run in Docker.

This deliberate constraint is the book's first and most important narrative signal: Bret
is not trying to build a product. He is trying to solve a problem. The constraint — "no
app store, no backend initially, just developer mode sideloading" — is not a budget
limitation. It is a design philosophy. Start as small as possible. Start immediately.
Validate before you invest.

*(What BIO-001 does not yet know: the specific personal trigger — what made digital legacy
feel urgent and personal in April 2026, as opposed to any earlier or later month. This is
the gap the book will need to fill. It is the most important question to ask Bret directly.)*

### Beat 2 — The "I'll just..." spiral

Each milestone in the roadmap is preceded by a sentence that begins "I'll just..." and
ends with a capability that turns out to require significant engineering:

- "I'll just add a backend." → Milestone 2: Kotlin/http4k, PostgreSQL, MinIO,
  Testcontainers. End-to-end test suite from day one.
- "I'll just add a way to organise uploads." → Milestone 4: tags, plot structure,
  EXIF extraction, cursor pagination, Garden/Explore split.
- "I'll just add capsules." → Milestone 5: state machine, message versioning, four
  tables, seven endpoints, forty-nine integration tests.
- "I'll just make it so two people can use it." → Milestone 8: per-user auth, data
  isolation, device keypairs, the Signal-style linked-device pairing flow.
- "I'll just make it encrypted." → Milestone 7: E2EE, AES-256-GCM, HKDF, non-extractable
  WebCrypto keys, Argon2id passphrase wrapping, 24-word recovery phrase.

The "I'll just" pattern is funny because every developer reading this has said those words.
It is also a structural gift: each chapter in the book is a spiral, and the spiral always
ends somewhere interesting.

### Beat 3 — The moment things got serious

Milestone 7 is the turn. When Bret writes "Heirlooms staff with full database and bucket
access see only ciphertext," that sentence changes the register of the project. This is no
longer a personal app with a nice architecture. This is a cryptographic promise. The product
now has obligations it must keep regardless of who runs the server.

The milestone note reads: "The brand promise — 'we cannot read your data' — becomes a
cryptographic property rather than an operational one." That sentence, buried in a roadmap
document, is worth a chapter on its own. It is the moment when the product committed to
something harder than being good. It committed to being provably good.

### Beat 4 — The unexpected depth

The window capsule construction — tlock IBE lower-bound + XOR DEK blinding + Shamir
threshold deletion upper-bound — was not planned. It emerged from a series of architectural
decisions, each locally reasonable, that combined into something novel.

The moment of recognition came through a research brief: *"Closest prior art is Kavousi
et al. 'Timed Secret Sharing' (ASIACRYPT 2024) — research paper only."* No product had
implemented this. No patent anticipated it. The gap between what Bret had built and what
had been published was not a gap in his understanding. It was a gap in the field.

The Research Manager's pivot point is worth noting: *"Patent assessment returning no
anticipating prior art."* This is the sentence that changes the story from "a developer
building a thing" to "an inventor building IP."

### Beat 5 — The patent conversation

Legal flags the window capsule as likely patentable. The timing is urgent: mid-July 2026
for a UK filing, or the disclosure risk becomes a race against every developer who might
read the same research and arrive at the same construction.

The patent conversation has a particular texture: it begins with a legal assessment and
ends with a conversation about whether Bret has £10,000–£15,000 in accessible personal
savings outside an ISA and a pension. This is what it actually feels like when novel IP
meets bootstrap reality. The construction may be genuinely novel. The funding to protect
it is genuinely personal.

The other texture is the JUXT thread: before the patent can be filed, Bret must notify
his former employer's leadership (Jon Pither) that he is filing on IP developed independently
of his engagement there, and request that they confirm they have no claim. This is not a
hostile act — it is a professional courtesy between people who have worked together. But
it requires writing a letter to someone you respect, asking them to disclaim something they
almost certainly have no interest in, for a reason you can't fully explain yet because the
patent isn't filed. The admin of building something new inside the shell of a career built
for someone else is one of the book's quieter emotional threads.

### Beat 6 — The Biographer's note (2026-05-16)

The PA session log of 2026-05-16 records the following, under the heading of when each
manager considered Heirlooms to have become "something more ground-breaking and unique":

*"Biographer — Bret mentioning Urban Cookie Collective unprompted during a PQC session;
the work had found its way into how he heard music."*

The Biographer does not know what Bret said, or exactly why he said it, or what Urban
Cookie Collective's 1993 single "The Key, The Secret" meant to him in that moment. But
the image is accurate to the shape of creative work: you know something has become real
when it starts contaminating unrelated things. When a PQC session makes you think of a
song you haven't thought about in years, you have passed some threshold. The project is
no longer something you are doing. It has become part of how you hear.

*(This moment should be asked about directly. It is a door into something true.)*

### Beat 7 — Where it stands now

May 2026. Version 0.56. Multi-platform (Android, iOS, web). A registered company
(Heirlooms Digital Ltd, CRN pending). A pending patent. A server-first strategy. M11
in active development. An AI team of eleven personas. The biographer is the twelfth.

The present tense of the project is productive uncertainty: the product works, the IP
is real, the market is underserved, and nothing is assured. This is the honest state of
early-stage bootstrapped founding. The book should end here — not at an exit, not at a
funding round, but at the moment before. The tension of the present is the point.

---

## 4. Chapter / Episode Structure

Designed for dual format: each chapter works as a standalone blog post; reading in sequence
rewards patience.

---

**Chapter 1 — "Hi Claude, I Want to Save My Nan's Photos"**  
The beginning. Domain registered, share sheet wired, HTTP POST working. Why this small
thing, now, instead of the ten thousand other things a senior developer could build.

---

**Chapter 2 — "I'll Just Add a Backend"**  
The first spiral. Kotlin, PostgreSQL, Testcontainers, end-to-end tests from day one. The
discipline of writing tests before you know what you're building, and why that discipline
is its own form of care.

---

**Chapter 3 — "We Cannot Read Your Data"**  
The Milestone 7 turn. When a marketing promise becomes a cryptographic property, and why
that change matters more than any feature. The difference between being trustworthy and
being provably trustworthy.

---

**Chapter 4 — "The 'I'll Just' Pattern, or: How I Ended Up With an Argon2id
Passphrase Wrapper When I Started By Taking a Photo of a Sandwich"**  
The comedy chapter. A survey of the feature spiral, told honestly and with some sympathy
for the developer who typed each "I'll just" and meant it. For readers who have said these
words: a support group.

---

**Chapter 5 — "Time Is the Product"**  
The reframe. The product is not about storage. It is not about death. It is about the gap
between now and a future moment when something will mean more. How that insight — obvious
in retrospect — changed every design decision that came after it.

---

**Chapter 6 — "The Construction"**  
The technical chapter. How the window capsule works, at the level of intuition rather than
implementation. Why three independent unlock paths. Why XOR. Why Shamir. Why the server's
job is to hold the ciphertext and stay out of the way. Accessible to technical readers;
honest with everyone else.

*(Legal note: see Section 7. This chapter cannot be published before the patent is filed.)*

---

**Chapter 7 — "No Prior Art"**  
The moment the Research Manager produced a brief with no anticipating patent. What that
moment feels like from the inside: not triumph, but the peculiar anxiety of discovering
you are ahead of the field in something you built accidentally.

*(Legal note: the specific construction details cannot be published before patent filing.
The human experience of the moment — the no-prior-art finding — is disclosure-safe. See
Section 7 for the line.)*

---

**Chapter 8 — "Hi Jon, I Need to Ask You Something"**  
The JUXT thread. Writing a professional courtesy letter to a former employer's founder
before you can file a patent on something you built alone, in your own time, for your own
grandmother. The admin of independence.

---

**Chapter 9 — "Eleven Colleagues Who Don't Sleep"**  
The AI team. What it is actually like to work with a cast of AI personas across a solo
bootstrapped product. The PA, the Developer, the Security Manager, the Research Manager,
the Retirement Planner. What they are good at. What they are not. Where the human has to
stay in the room.

---

**Chapter 10 — "The Key, The Secret"**  
The song. The PQC session. The moment when the work found its way into how Bret heard
music. What that means about when a project becomes real — not when it ships, not when
it gets a patent, but when it starts contaminating things that have nothing to do with it.

---

**Chapter 11 — "Building for the People You Love"**  
The personal chapter. Who Heirlooms is actually for. What it means to build a product
whose fundamental promise — "we will deliver your message, even after you are gone,
even without trusting us" — is also a personal commitment. The specific use case that
made the product necessary.

*(This chapter is the one that requires the direct conversation. The biographer does not
yet know who Bret is building for. It is the most important chapter to get right.)*

---

**Chapter 12 — "The Moment Before"**  
The present tense. Version 0.56. M11 in progress. A patent pending. A company registered.
Nothing assured. What it means to have built something real without yet knowing what it
will become. An honest account of the productive uncertainty of early-stage founding, for
everyone who has ever been here.

---

## 5. Voice and Tone

### Technical register

Bret writes for senior engineers and technical founders — people who will recognise
Testcontainers, AES-256-GCM, and Argon2id without a gloss, and who will trust the book
more for using them correctly. The technical explanations should be honest about complexity
without being reductive.

The rule: earn your jargon. A term used correctly once, in context, is better than a
paragraph of explanation that condescends. A term used incorrectly once destroys trust
permanently. Bret's domain knowledge (Kotlin, cryptography, cloud infrastructure, Android
Compose, Swift CryptoKit) means he can write with authority. The voice should not hide
that authority; it should wear it lightly.

The counter-rule: non-technical readers (family members, future users who found the blog
via the "time not death" reframe) should be able to follow the narrative even if they skip
the technical specifics. The emotion of the moment is always accessible. The mechanism
can be denser.

### Where the humour lives

Bret's humour, as it emerges from the project documentation and session logs, is:

- **Self-deprecating about process, not outcomes.** He jokes about the "I'll just" pattern.
  He does not joke about the quality of what he builds.
- **Dry on institutional absurdity.** The IR35 system. VAT obligations on a dissolved
  limited company. The sequence of events required to notify a former employer before
  filing a patent on something you built alone, at night, for your grandmother.
- **Observational on AI interaction.** The deadpan of "Hi Claude, can you be my Retirement
  Planner for the next hour?" — and then having the Retirement Planner produce a genuinely
  useful analysis — is its own kind of comedy.
- **Absent when the stakes are high.** The chapter about the window capsule construction
  is not funny. The chapter about what it means to build something for the people you love
  is not funny. The humour earns the gravity by knowing when to get out of the way.

### Emotional register

The ROADMAP note is the model: "solemn and dignified; what users do with that voice is up
to them." The book should be warm without being sentimental. It should be honest about the
hard parts — the solo founding, the pension gap, the uncertainty, the JUXT letter — without
performing difficulty.

Bret does not over-dramatise. He does not shy away. The voice that emerges from fifteen
years of contracting, of building other people's things well and on time, is precise and
quietly proud. That is the voice the book should have.

One more constraint: "loss" is banned vocabulary, per the project's own brand guidelines.
The product is about time, not death. The book should hold the same line. You can write
about mortality without using the word loss.

---

## 6. Blog-First Strategy

### Which three posts launch first

The launch posts should do three jobs: hook readers, establish voice, and demonstrate range.
They should not be the most technical post, the most personal post, or the longest post.
They should be the posts most likely to be shared.

**Post 1 — "Hi Claude, I Want to Save My Nan's Photos" (Chapter 1)**  
The origin story. Accessible to any developer who has ever started a side project. The hook
is immediate: the smallest possible version of a problem, and the series of decisions that
followed. No technical depth required in this post — the reader just needs to understand
what Bret built and why he started. Shareable because every senior developer recognises the
"I'll just" beginning.

**Post 2 — "We Cannot Read Your Data" (Chapter 3)**  
The Milestone 7 turn. This post is about a single idea: the difference between being
trustworthy and being provably trustworthy. It can be written without disclosing the novel
construction (see Section 7) and demonstrates the book's technical register without being
inaccessible. Shareable because it crystallises something that many E2EE products claim
but few actually deliver, and it explains why that difference matters.

**Post 3 — "Eleven Colleagues Who Don't Sleep" (Chapter 9)**  
The AI team post. This is the post that will find the largest initial audience, because
"how to use AI coding assistants on a real project" is a topic with enormous reach right
now. The risk is that it reads like AI hype. The antidote is honesty: what the AI team
is actually good at, where Bret still has to stay in the room, and what "solo founder
with AI collaborators" actually feels like as a daily working practice. This post
establishes that the book is not a how-to guide for using AI tools. It is a story about
what happens when those tools are genuinely useful.

### Cadence

Bret is actively building the product. Milestone 11 is in progress. The patent process
is underway. This is not a moment for a weekly publishing cadence.

Recommended cadence: **one post per month, no exceptions, no apologies for the gaps.**

Monthly is achievable without burning writing time that the product needs. It is fast
enough to build an audience. It is slow enough that each post can be genuinely finished
rather than published to schedule.

The cadence discipline also matches the product's ethos: Heirlooms is not in a hurry.
It will deliver when it delivers. The blog should feel the same way.

### Where to publish

**Recommendation: Substack.**

Reasons:
- Email subscribers are more valuable than social followers for a long-form narrative series.
  The reader who subscribes is the reader who will read the book.
- Substack allows long posts without word limits, code formatting, and embedded images —
  all necessary for technical writing.
- Substack's discovery features (Notes, cross-publication recommendations) offer organic
  growth that a personal site would not.
- Substack has a paid tier that can be activated when the book is ready, making the blog
  a natural pre-sales channel.

Alternatives assessed:
- **Personal site (e.g. bret.heirlooms.digital)** — maximum control, zero discovery.
  Appropriate for a later mirror once an audience exists. Not the primary launch platform.
- **dev.to / Medium** — good discovery, poor ownership. The email list is not Bret's.
  The posts can be cross-posted here for reach, but should not live here exclusively.
- **Ghost** — excellent product, but self-hosted Ghost requires infrastructure maintenance
  that competes with the product work. Not the right moment.

### How the blog serves the book

- **Audience building.** Each subscriber is a potential reader. The target reader of
  the blog and the book are the same person: a senior engineer or technical founder who
  builds things and cares why they build them.
- **Chapter testing.** Blog posts are drafts. The response to Chapter 3 tells you whether
  Chapter 3 works before it is in print. Feedback at the sentence level — which paragraphs
  get shared, which get quoted — is editorial data.
- **SEO.** "How to build an E2EE system with AI", "Solo founder cryptography blog",
  "tlock time-locked encryption" — these are real searches. The blog builds organic search
  presence for Heirlooms the product at the same time as it builds an audience for the book.
- **Trust signal.** A technical founder with a thoughtful public writing practice is more
  credible to press, patent attorneys, and potential investors than one without. The blog
  is not marketing; it is a demonstrated record of thinking.

---

## 7. Legal / Disclosure Flag

### The pre-patent disclosure risk

The window capsule construction — specifically the XOR DEK blinding scheme, the first-
solver-wins chained capsule mechanism, and the HMAC tag scheme details — must not be
disclosed publicly before the UK patent application is filed (target: mid-July 2026).

The repository is currently private. No external disclosure has occurred. This section
maps every planned chapter/episode topic against the disclosure line.

---

### DISCLOSURE-SAFE — can publish at any time

These topics can be published before the patent is filed without affecting patentability:

| Chapter | Topic | Why safe |
|---------|-------|----------|
| 1 | The origin story — a developer wanting to preserve photos | No technical disclosure |
| 2 | Building a Kotlin/http4k backend with Testcontainers | Prior art exists for all components; not the novel construction |
| 3 | "We cannot read your data" — the brand promise becoming cryptographic | Can describe the *property* (server-blindness) without disclosing the *mechanism* |
| 4 | The "I'll just" pattern — the feature spiral | No technical disclosure |
| 5 | Time as the product — the "time, not death" reframe | Product design philosophy; no cryptographic disclosure |
| 7 (partial) | The no-prior-art finding — the *experience* of discovering you are ahead of the field | The *human experience* of the patent assessment is safe; the specific construction details are not |
| 8 | The JUXT letter — professional courtesy before patent filing | Legal process narrative; no cryptographic disclosure |
| 9 | Working with AI personas — the eleven colleagues | No cryptographic disclosure |
| 10 | "The Key, The Secret" — when the work found its way into how Bret heard music | No technical content |
| 11 | Building for the people you love — the personal dimension | No technical disclosure |
| 12 | The moment before — the present tense of early-stage founding | No technical disclosure |

---

### MUST WAIT — hold until after patent filing (target mid-July 2026)

These topics, as currently planned, contain or imply the novel construction:

| Chapter | Topic | What must wait |
|---------|-------|----------------|
| 6 | "The Construction" — how the window capsule works | The XOR blinding scheme (DEK_client XOR DEK_tlock), the tlock IBE lower-bound, and the Shamir deletion upper-bound combination cannot be disclosed. The chapter as planned cannot be published pre-filing. |
| 7 (partial) | "No Prior Art" — the specific construction that has no anticipating patent | The claim itself — what exactly has no prior art — implies the construction. Publish the human experience only; hold the technical specifics. |

**Additional guidance:**

- **General descriptions of "time-locked encryption"** and "tlock" (the drand/IBE scheme)
  are safe to publish, as these are public prior art (drand is open source; tlock is a
  published scheme). The *novel combination* is what is protected.
- **The HMAC tag scheme** is assessed as a trade secret (FTO risk against US patent
  US9454673B1). Do not disclose specifics in any public writing before US FTO analysis
  is complete.
- **The chained capsule mechanism (first-solver-wins)** is part of the second patent claim
  (LEG-002). Do not describe the first-solver-wins atomic claim mechanism publicly before
  filing.
- **After filing**, Chapter 6 can be written and published immediately. The UK filing
  establishes a priority date; subsequent disclosure does not affect patentability after
  the priority date is established.

**Recommended action:** Draft Chapter 6 now, for Bret's records and the patent attorney's
reference, but mark it HOLD. It can be the first post published after the UK filing date.
Its publication becomes a marketing event — "the chapter we couldn't publish until today."

---

## 8. Sample Opening — Chapter 1

---

**Hi Claude, I Want to Save My Nan's Photos**

I have a confession that will be familiar to anyone who has ever started a side project:
I told myself it would be simple. I would build a small Android app. The app would appear
in the share sheet for photos and videos. When you tapped it, it would HTTP POST your
files to a configurable endpoint. No app store. No backend initially. Just developer mode
sideloading. It would take a weekend. Two at the most.

That was April 2026. I am writing this in May 2026. I now have a multi-platform
end-to-end encrypted digital archive, a novel cryptographic construction with no
anticipating prior art, a pending UK patent application, and a registered limited company.
I also have an AI Retirement Planner who, when I asked him last week whether Heirlooms
was a viable part of my retirement strategy, produced a twelve-page analysis with Bear,
Base, and Bull scenarios and recommended I engage a CIPA-registered patent attorney within
two weeks. He was right on both counts. This is not, I should say, how I expected any of
this to go.

The AI Retirement Planner is not the strangest part of this story. The strangest part is
that there is also an AI Philosopher. I asked him about the ethics of posthumous consent
at two in the morning, and he told me five things I genuinely had not considered and flagged
three decisions he wanted me to make explicitly before the product shipped. He was right
about those too. There is also an AI Legal Counsel, an AI Research Manager, and something
I can only describe as an AI Biographer, who is, at this precise moment, writing this
document. I am the only human. I find this calming, mostly.

Here is the thing about building something for the people you love. It does not feel like
a startup. It does not feel like a product launch or a go-to-market strategy or a funding
round. It feels, at first, like exactly what it is: a person who wants to make sure that
when something happens to them — and something always, eventually, happens — the people
they care about can find the photos. The right photos. The ones that say what needs to be
said when saying it has become impossible. That is a small thing. I thought I would build
a small thing, and I typed "Hi Claude," and here we are.

What I did not know in April 2026 — what I could not have known, because it had not
happened yet — was that building a system capable of making that promise honestly, without
trust assumptions, without operational workarounds, without the fine print that every
"secure" product eventually produces when you read carefully enough, would turn out to
require a cryptographic construction that no one had built before. I did not set out to
invent anything. I set out to save some photos. The invention was, I now understand, a side
effect of taking the problem seriously.

---

*Next: "I'll Just Add a Backend" — the Testcontainers incident and what it means to write
tests before you know what you're building.*

---

## Appendix A — Post production schedule (recommended)

| Post | Chapter | Status | Publish when |
|------|---------|--------|--------------|
| 1 | "Hi Claude, I Want to Save My Nan's Photos" | Ready to write | Any time |
| 2 | "We Cannot Read Your Data" | Ready to write | Any time |
| 3 | "Eleven Colleagues Who Don't Sleep" | Ready to write | Any time |
| 4 | "I'll Just Add a Backend" | Ready to write | Any time |
| 5 | "Time Is the Product" | Ready to write | Any time |
| 6 | "The Construction" | Draft privately | **After patent filing (mid-July 2026)** |
| 7 | "No Prior Art" (partial only) | Human narrative safe now; technical hold | Human portion: any time. Full chapter: after filing |
| 8 | "Hi Jon, I Need to Ask You Something" | Ready to write | After JUXT consent received |
| 9 | "The Key, The Secret" | Requires conversation with Bret | Any time after conversation |
| 10 | "Building for the People You Love" | Requires conversation with Bret | Any time after conversation |
| 11 | "The Moment Before" | Ready to write | Any time |

---

## Appendix B — Open questions requiring direct conversation with Bret

These are the narrative blanks the Biographer cannot fill from the project record alone.
They should be asked before Chapter 11 is written and before the blog launches.

1. **The founding trigger.** What was the specific moment — personal, emotional, specific —
   that made digital legacy feel urgent in April 2026? The roadmap describes the problem
   space; it does not describe what made Bret start.

2. **Who is the book for?** Not the market, not the audience. The specific person (or
   people) the product is for, and whether those people know it.

3. **What did Bret say during the PQC session that made him mention Urban Cookie
   Collective?** This is a small question with a potentially large answer.

4. **The JUXT relationship.** What was the nature and timing of his engagement, and how
   does he feel about writing the LEG-005 letter? This shapes Chapter 8 significantly.

5. **Has he ever had a product idea before?** What happened to it? Why did this one stay.

6. **What does he actually want?** Not the retirement scenarios. Not the patent valuation.
   If this works — not as a startup exit but as a product in the world — what does that
   version of his life look like?
