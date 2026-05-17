---
id: BIO-002
title: "Hi Claude..." — book and blog series concept development
category: Biography
priority: Medium
status: queued
assigned_to: Biographer
depends_on: [BIO-001]
touches:
  - docs/biography/
estimated: 1 session
---

## Brief

Bret has proposed a book and companion blog series tentatively titled **"Hi Claude..."**

The hook: a man who typed "Let's build an entire E2EE system and design a novel
encryption scheme so I can save my Nan's photos" into Claude — and then actually did it.

The target audience is technical: senior engineers, technical founders, people who
use AI coding assistants. The tone should be entertaining, honest about wrong turns,
and human-first — not a how-to guide, but a story about building something real.

## Goal

Produce a concept document at `docs/biography/BIO-002_hi-claude-book-concept.md`
covering the following:

---

### 1. Concept and hook

Develop the core pitch in 2–3 sentences. The contrast between the technical depth
(E2EE, Shamir secret sharing, novel cryptographic constructions, patent-eligible IP)
and the deeply personal, modest motivation (Nan's photos) is the emotional engine of
the book. Make it sing.

---

### 2. Title and framing

Explore the "Hi Claude..." title:
- Why it works (the conversational prompt format, Claude-as-co-protagonist, the
  naive optimism of someone starting something enormous without knowing it)
- Alternative framings if any seem stronger
- Subtitle options (e.g. "A man, his grandmother's photos, and an accidental
  cryptographic breakthrough")

---

### 3. Narrative arc

Map the story beats using what you know from BIO-001 and the project history
(git log, session logs, task files). The arc should include at minimum:

- The inciting moment: what was the actual starting motivation?
- The "I'll just..." spiral: each "simple" feature that turned out not to be
- The moment things got serious: when did it become real software?
- The unexpected depth: when did Bret realise he'd built something novel?
- The patent conversation: when legal flagged the window capsule as potentially
  patentable — what does that moment feel like from the inside?
- Where it stands now: an ongoing project, a potential product, a retirement plan

---

### 4. Chapter / episode structure (dual format)

Design a structure that works both as book chapters AND as standalone blog posts.
Each unit should be readable independently but reward reading in sequence.

Suggest 8–12 chapter/episode titles with a one-line description of each.
Think about what would make someone share a specific post: the title, the hook,
the moment of recognition ("yes, I've done exactly this").

---

### 5. Voice and tone

Bret is writing in his own voice. Based on BIO-001 and any session logs in
`personalities/session-logs/`, characterise that voice:
- How technical should the explanations get? (The audience can handle it, but
  jargon should be earned)
- Where is the humour — self-deprecating? Observational? Dry?
- What should the emotional register be? (Warm, not maudlin; honest, not boastful)

---

### 6. Blog-first strategy

The recommendation is to launch the blog series before the book. Address:
- Which 3 posts should launch first? (Hook readers, establish voice, demonstrate range)
- What cadence is realistic given Bret is also actively building the product?
- Where should the blog live? (Substack, personal site, dev.to, Medium — pros/cons)
- How does the blog serve the book? (Audience building, chapter testing, SEO)

---

### 7. Legal / disclosure flag

**IMPORTANT:** The window capsule construction (tlock IBE lower-bound + XOR DEK
blinding + Shamir threshold deletion upper-bound) has been assessed as likely
patentable by Legal (LEG-001). The UK filing target is mid-July 2026.

Any blog post or book content that discloses the specific cryptographic construction
should be held until AFTER the patent application is filed. This includes:
- The XOR blinding scheme (DEK_client XOR DEK_tlock)
- The first-solver-wins chained capsule mechanism (ARCH-008)
- The HMAC tag scheme details

The human interest story, the journey narrative, and general descriptions of
"time-locked encryption" are fine to publish at any time. Identify which planned
chapter/episode topics are disclosure-safe vs. should wait.

---

### 8. Sample opening

Write the opening 3–5 paragraphs of Chapter 1 (or Episode 1) in Bret's voice.
This is the most important output — it should demonstrate that the concept works.
Make the reader laugh in the first paragraph. Make them feel something by the
third.

---

## Output

`docs/biography/BIO-002_hi-claude-book-concept.md` covering all 8 sections above.

## Completion notes

<!-- Biographer appends here and moves file to tasks/done/ -->
