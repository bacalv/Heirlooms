# Research Manager Session Log

**Date:** 2026-05-16  
**Persona:** Research Manager  
**Session type:** Working conventions + strategic discussion + task creation

---

## Tasks completed

None — this session was primarily discussion and task creation.

---

## Tasks created

| ID | Title | Priority | Notes |
|---|---|---|---|
| [RES-002](../../../tasks/queue/RES-002_window-capsule-expiry-cryptography.md) | Window capsule — cryptographic expiry, literature review and construction brief | High | CTO-originated concept; construction proposed in session |
| [SIM-001](../../../tasks/queue/SIM-001_trustless-expiry-impossibility.md) | Simulation: trustless expiry — weakest possible construction without custodians | Low | Throw-away; depends on RES-002 |

---

## Working conventions agreed this session

**Research loop ("do research"):**
- If RES- tasks in queue: work through in priority order
- If queue empty: horizon scan (4–5 sources, produce digest to `docs/research/horizon/`)
- Research Manager self-dispatches; no worktree needed (documents only)
- Report to PA at task completion; PA routes decisions to CTO

**Simulation tasks (SIM-):**
- Initiated by CTO as speculative "what if" thought experiments
- Branch freely from current knowledge; not constrained to realistic scenarios
- Must NOT create follow-on RES-, ARCH-, or SEC- tasks
- Wrap up with short summary; save to session log only, not `docs/research/`

**Mandatory wrap-up steps (every RES- task):**
1. Append sources to `docs/research/REFERENCES.md` under task section
2. Update `docs/research/GLOSSARY.md` with new terms
3. Write PA Summary section at end of brief

---

## Key discussions and findings

### Additional personas

The CTO raised the question of adding philosopher, psychologist, brand, marketing, and HR personas. Research Manager opinion:

- **Psychologist** — add now. Grief-aware UX, executor psychology, posthumous message reception are real product decisions with no current owner.
- **Brand guardian** — add now. BRAND.md exists but has no persona defending it. Lightweight but fills a real gap.
- **Philosopher** — add soon. Feeds Research Manager simulation work and the product's hardest unanswered questions (ethics of posthumous consent, obligations to deceased users).
- **Marketing** — hold until public launch. No real users or channels to test against yet.
- **HR** — hold until real humans join the team.

### Strategic discussion — USPs and competitive positioning

CTO identified that Heirlooms has outgrown the grief framing and is more accurately described as a **cryptographically enforced digital trust network**. Key findings:

- The grief entry point is correct and should be retained as the consumer wedge
- The underlying infrastructure (multi-path time-lock, threshold key custody, cryptographic agility, E2EE) is general-purpose and novel
- No existing product combines: cryptographic time enforcement + quorum key custody + E2EE + identity-aware delivery + multi-decade horizon design
- Closest competitors (dead man's switches, password manager emergency access, Google Inactive Account Manager) use database flags and operational promises, not cryptographic guarantees
- The legal entity custodian concept (law firms, banks, notaries holding Shamir shares) is a specific moat that requires legal agreements and institutional relationships — not copyable from code alone
- Post-quantum readiness as a trust signal: Heirlooms is the only consumer product designed to survive quantum computing. This narrative should begin to surface in product communications.

Recommended competitive positioning priorities (Research Manager view):
1. Document and defend the multi-path time-lock design philosophy (potential academic publication / patent)
2. Be first to formally integrate legal entities as key custodians
3. Engage with drand/League of Entropy community; have a voice in their PQC roadmap
4. Begin quiet post-quantum narrative in product communications now, before Q-Day discussion goes mainstream

### Window capsule concept (RES-002 origin)

CTO proposed a capsule with both unlock time (lower bound) and expire time (upper bound) — after expire_time, the capsule becomes permanently undecryptable by anyone.

Proposed construction (to be validated in RES-002):
- K_window split (2-of-2): K_a (tlock, trustless lower bound) ⊕ K_b (Shamir threshold custodians)
- Custodians release K_b shares only during [unlock_time, expire_time] to authenticated receiver
- Custodians destroy K_b shares at expire_time
- After expire_time: K_a is public (tlock has rung) but K_b is gone → K_window irrecoverable

**Trustless impossibility:** CTO's intuition that trustless expiry is impossible is correct. The precise reason is information-theoretic: anything derivable from information that later became public is permanently derivable. tlock's unlock key becomes permanently public at the unlock round; you cannot un-publish it. The custodian construction avoids this by ensuring K_b never becomes public — it is enforced secrecy with a death date, not cryptographic expiry. SIM-001 will formalise where the theoretical line sits.

---

## Recommended next actions

1. **CTO to consider adding Psychologist and Brand personas** — these address real current gaps and are lightweight to add.

2. **Run RES-002** next "do research" session — the window capsule construction has sufficient novelty that a proper literature review is warranted before any architect brief is written.

3. **CTO to answer the four open questions from RES-001** — particularly whether the Technical Architect should begin specifying the hybrid P-256+ML-KEM migration path now or after M11 ships. This is time-sensitive given the compressed quantum timeline.
