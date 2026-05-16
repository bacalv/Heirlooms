# Marketing Director Session Log

**Date:** 2026-05-16  
**Persona:** Marketing Director  
**Session type:** Inaugural session — strategic direction and USP analysis  
**Reported to:** CTO (Bret)

---

## Tasks completed

None — this was the Marketing Director's first session. No prior tasks existed.

---

## Tasks created

| ID | Title | Priority | Notes |
|----|-------|----------|-------|
| MKT-001 | Strategic direction thinking report — platform reframe, three segments, chained capsules | Medium | First formal marketing brief; feeds STR-001 synthesis |
| RES-004 | Chained capsule — cryptographic novelty assessment | Medium | Commissioned from Research Manager; depends on RES-002 |
| ARCH-008 | Chained capsule feasibility and Care Mode architecture | Medium | Commissioned from Technical Architect; depends on RES-004 |
| LEG-002 | Care Mode consent framework, chained capsule IP, white-label | Medium | Commissioned from Legal; depends on LEG-001 |
| PSY-001 | Grief reframe, Care Mode dignity, Experience segment psychology | Medium | Commissioned from Psychologist |
| PHI-001 | Ethics of conditional delivery, consent before capacity loss | Medium | Commissioned from Philosopher |
| RET-002 | Three-segment valuation and retirement implications | Medium | Commissioned from Retirement Planner; depends on RET-001 |
| STR-001 | PA synthesis — top 20 features and direction brief | Medium | PA task; depends on all 7 persona reports above |

---

## Key decisions and findings

### Platform reframe (critical)
Heirlooms is a **time-based digital archive**, not a grief or loss platform.
- "Loss" is now banned vocabulary alongside "cloud storage", "backup", "syncing".
- The primary frame: planting happy memories for chosen people at chosen moments.
- Grief/posthumous use cases remain supported but are not the lead.
- This change needs to propagate to `docs/BRAND.md` as part of MKT-001's output.

### Three commercial segments identified
1. **Memory Archive** — consumer, personal archive, time-windowed delivery
2. **Care & Consent** — families with POA for relatives with diminishing capacity (Alzheimer's etc); E2EE geofenced monitoring with cryptographically timestamped consent
3. **Experience** — ARGs, brand campaigns, white-label, serialised storytelling, educational gamification

### Five USPs articulated
1. Intent made permanent (vs infrastructure framing of all competitors)
2. Cryptographic enforcement, not operational promise (E2EE shipped M7)
3. Time-windowed capsules — novel cryptographic primitive (RES-002 confirms novelty)
4. Post-quantum architecture designed in from day one
5. Defensible IP and network-effects moat

### Chained capsules — major new product concept
A DAG of time-windowed capsules where one capsule's unlock is conditional on completing
a task inside a prior capsule. Key mechanics:
- Time windows (open/close bounds per capsule)
- Competitive delivery (first solver wins)
- Expiry-as-death (nobody solves it → downstream capsule never delivered)
- QR-as-capsule-reference (encrypted pointer inside one capsule to another)

Proposed notation: `C₁({A,B}, [T₀+2d→T₀+3d], {puzzle, ref→C₂}) → C₂({winner}, [T₀+4d→T₀+5d], {prize})`

This opens the Experience segment commercially and likely constitutes novel IP (flagged to LEG-002).

### Care Mode — significant legal and ethical complexity
Before any product decisions: Legal (LPA scope, GDPR Article 9, medical device risk),
Psychologist (coercion risk, dignity design, diminishing capacity consent), and
Philosopher (autonomy and personal identity) must all report first.

### Friend-tester / PLG model
Currently organic (free storage for testing). Worth formalising as a deliberate
product-led growth strategy. Documented as scope for MKT-001.

### Two new personas proposed by CTO (at session close)
- **Technical Author** — reports to Technical Architect and PA; end-of-iteration doc sweep
- **Biographer** — non-staff; writes about Bret's personal journey; potential source of
  founder story material for investor and press audiences

Marketing Director recommends: Biographer output should feed into the "Investors/acquirers"
USP narrative. Any customer-facing Technical Author output should pass a brand voice check
before publication.

---

## Recommended next actions

1. **Dispatch PSY-001 and PHI-001 first** — they have no dependencies and their findings
   are prerequisites for Care Mode product decisions. Legal will want their input too.

2. **Dispatch RES-004** — cryptographic novelty assessment of chained capsules unblocks
   ARCH-008 and informs LEG-002's patent strategy.

3. **Create persona files for Technical Author and Biographer** — CTO approval received
   in principle at session close; PA should create `personalities/TechnicalAuthor.md`
   and `personalities/Biographer.md` and add them to PERSONAS.md.

Trigger phrase for the synthesis session when all 7 reports are in: **"Let's review the strategic synthesis."**
