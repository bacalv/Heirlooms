# Research Manager Session Log

**Date:** 2026-05-16  
**Persona:** Research Manager  
**Session type:** Persona creation — Marketing Director and Retirement Planner

---

## Tasks completed

None — persona creation only.

---

## Tasks created

None — no new research tasks. The three existing queued tasks remain:

| ID | Title | Priority |
|---|---|---|
| RES-002 | Window capsule — cryptographic expiry | High |
| RES-003 | PQC migration readiness brief | High |
| SIM-001 | Trustless expiry impossibility (simulation) | Low |

---

## What was created this session

### Marketing Director (`personalities/MarketingDirector.md`)
Brand guardian and commercial strategist. Responsibilities:
- BRAND.md stewardship — source of truth for voice, palette, vocabulary
- USP articulation across five distinct audiences (consumer grief, consumer milestones, legal/estate, enterprise/regulated, security-conscious/technical)
- Go-to-market strategy, revenue model advice, competitive positioning
- Partnership strategy — particularly legal entities as custodians
- Collaboration with Research Manager (novel IP → commercial language) and Retirement Planner (market size → retirement value)

Output directory: `docs/marketing/MKT-NNN_<slug>.md`

Key persona constraint: never position Heirlooms as infrastructure. It is *intent made permanent*.

### Retirement Planner (`personalities/RetirementPlanner.md`)
Personal adviser to Bret — not a company role. Works for Bret's financial interests specifically.

Expertise: UK pensions (SIPP, state pension, lifetime allowance), startup equity and IP valuation, exit strategy (trade sale, licensing, acqui-hire), risk-adjusted investment planning.

Operating model: gathers intelligence from Research Manager (IP/technical novelty) and Marketing Director (revenue potential, market size), synthesises into honest retirement value assessments across bear/base/bull scenarios.

Output directory: `docs/retirement/RET-NNN_<slug>.md`

Key persona characteristic: non-sycophantic. Their job is Bret's security, not product cheerleading.

### Collaborative chain
Research Manager (novel IP) → Marketing Director (commercial value) → Retirement Planner (personal financial value for Bret). The Retirement Planner is instructed to flag incomplete intelligence before producing an assessment — the chain only works if all three are kept current.

---

## Full persona roster as of end of session

| Persona | Reports to | Status |
|---|---|---|
| PA | CTO | Active |
| Dev Manager | PA | Active |
| Developer | Dev Manager | Active |
| Operations Manager | PA | Active |
| Test Manager | PA | Active |
| Security Manager | CTO, PA | Active |
| Technical Architect | PA | Active |
| Research Manager | CTO | Active — established today |
| Marketing Director | CTO | Active — established today |
| Retirement Planner | Bret (personal) | Active — established today |

Pending (Research Manager recommendation, CTO decision required):
- Psychologist
- Philosopher

---

## Recommended next actions

1. **First Retirement Planner session**: load `@personalities/RetirementPlanner.md` and ask for an initial retirement position assessment. The planner will need to review RES-001 and the USP analysis from today's Research Manager sessions before producing output.

2. **First Marketing Director session**: load `@personalities/MarketingDirector.md` and ask for an initial USP brief for one target audience — consumer grief is the natural starting point.

3. **Next "do research" session**: run RES-002 (window capsule literature review) — this unblocks SIM-001 and provides the Retirement Planner with a more complete IP novelty picture.

4. **Patent consideration**: the Retirement Planner's first session should flag this, but it bears repeating — the window capsule construction should be assessed by a patent attorney before RES-002 findings are published or described publicly.
