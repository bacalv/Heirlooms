---
id: RET-001
title: Initial retirement assessment — questionnaire, intelligence gathering, and key decisions
category: Retirement Planning
priority: High
assigned_to: RetirementPlanner
status: in-progress
touches:
  - docs/retirement/
  - docs/research/RES-001_crypto-threat-horizon-40kft.md
  - docs/ROADMAP.md
  - docs/PA_NOTES.md
depends_on: []
---

# RET-001 — Initial Retirement Assessment

## Goal

Produce Bret's first retirement planning assessment. This task has three phases:

1. **Questionnaire** — generate a comprehensive financial questionnaire for Bret to answer
2. **Intelligence gathering** — read the whole project to understand what Heirlooms is, where it is going, and what each team member's perspective adds to the picture
3. **Assessment brief** — synthesise into a structured retirement brief with key decisions Bret should make

---

## Phase 1 — Financial questionnaire

Produce a comprehensive questionnaire covering Bret's current personal financial position. Write it to:

```
docs/retirement/RET-001-questionnaire.md
```

The questionnaire should cover (but not be limited to):

### Personal context
- Age, target retirement age, acceptable retirement age range
- Current country of residence and tax domicile (UK?)
- Dependants — partner, children, parents; any financial obligations
- Health considerations that affect retirement timeline or planning horizon

### Income and employment
- Current annual income (salary, dividends, contract, other)
- Other income sources (rental, investments, side income)
- Income trajectory — is current income stable, growing, declining?
- Employment structure — employee, director, sole trader, or mix?

### Existing pension and retirement assets
- UK state pension — NI contribution record, expected weekly amount, forecast age
- Workplace pension — provider, current value, contribution rate, employer match
- SIPP — provider, current value, annual contribution
- Other pensions (DB, overseas)
- ISA portfolio — current value, annual contribution, investment strategy
- GIA (General Investment Account) — current value, holdings
- Property — primary residence value, mortgage outstanding; any investment property
- Other assets (crypto, gold, art, other illiquid holdings)

### Liabilities and outgoings
- Total debt (mortgage, loans, credit)
- Monthly essential outgoings
- Monthly discretionary outgoings
- Expected large near-term expenses (education fees, home improvement, etc.)

### Heirlooms equity and structure
- Company structure — limited company, sole trader, or other?
- Bret's equity stake (%)
- Any co-founders or early investors with equity?
- Has any formal valuation been done?
- Existing IP protection — trademarks, patents, trade secrets?
- Is Heirlooms Bret's primary retirement vehicle, a supplement, or a side project?

### Retirement goals
- Target annual income in retirement (today's money)
- Target lump sum at retirement, if any
- Priorities: income certainty, legacy, flexibility, early retirement, geographic flexibility?
- What does the ideal exit from Heirlooms look like to Bret personally?
- Acceptable risk tolerance (1–10, and in plain language)

### Time horizons
- What is the earliest Bret could retire if Heirlooms exited well?
- What is the fallback plan if Heirlooms never generates significant revenue?
- Is there a "minimum viable retirement" scenario Bret is comfortable with?

---

## Phase 2 — Intelligence gathering

Read the following documents in full. Synthesise what each adds to the retirement picture.

### Product and roadmap
- `docs/ROADMAP.md` — milestones delivered and planned; where is Heirlooms in its lifecycle?
- `docs/PA_NOTES.md` — current project state, open decisions, architectural commitments
- `docs/envelope_format.md` — E2EE envelope spec; understand the cryptographic defensibility of the product

### Research Manager intelligence
- `docs/research/RES-001_crypto-threat-horizon-40kft.md` — IP value, PQC risk, harvest-now-decrypt-later threat
- `docs/research/GLOSSARY.md` and `docs/research/REFERENCES.md` — supporting context
- Note any queued research tasks (RES-002, RES-003) and what their findings might change

### Marketing Director intelligence
- `personalities/MarketingDirector.md` — understand this persona's scope and what they will produce
- `docs/marketing/` — read any briefs if they exist (directory may be empty — note the gap)

### Team perspective sweep
Read each persona file and extract what their work implies for Heirlooms' retirement value:

| Persona | File | What to extract |
|---|---|---|
| Technical Architect | `personalities/TechnicalArchitect.md` | Technical moat, architectural novelty, migration risk |
| Security Manager | `personalities/SecurityManager.md` | Security posture as a trust/brand asset; open risk items |
| Dev Manager | `personalities/DevManager.md` | Velocity, team capacity, delivery risk |
| Operations Manager | `personalities/OpsManager.md` | Infrastructure cost, scalability, operational risk |
| Test Manager | `personalities/TestManager.md` | Quality posture; how gaps affect product credibility |
| Research Manager | `personalities/ResearchManager.md` | IP pipeline, novel constructions, PQC migration window |
| Marketing Director | `personalities/MarketingDirector.md` | Revenue model potential, USP strength, TAM |

Also read `personalities/Bret.md` — understand the CTO's goals and working style to frame advice correctly.

---

## Phase 3 — Retirement assessment brief

Write the assessment to:

```
docs/retirement/RET-001_initial-retirement-assessment.md
```

Use the standard brief structure:

```
# RET-001 — Initial Retirement Assessment

## Current position summary
(Based on assumptions — Bret has not yet answered the questionnaire. Flag assumptions clearly.)

## IP and asset value assessment
What is Heirlooms worth today, and what could it be worth?
- Technical moat: what makes the IP defensible?
- Comparable acquisitions in the privacy/family media space
- Bear / base / bull valuation ranges with explicit reasoning

## Revenue / exit scenario modelling
Three scenarios across 1-year, 3-year, 5-year horizons:
- Bear: product never monetises; Bret winds down and takes nothing
- Base: modest acquisition or licensing deal; what does this fund?
- Bull: strong acquisition or revenue exit; what does this fund?
For each: estimated exit value, after-tax proceeds, how it maps to retirement goals

## Key risks to retirement value
Rank by severity × likelihood:
- Cryptographic (P-256 break window, PQC migration cost, tlock dependency)
- Competitive (privacy-first media vault is a real market; who else is here?)
- Regulatory (GDPR, UK data protection, E2EE regulatory risk)
- Operational (single-CTO dependency, bus factor)
- Personal (health, time horizon, opportunity cost of continuing)

## Recommended actions
### Immediate (next 90 days)
### 1-year horizon
### 3-year horizon
Each action should be concrete and tied to retirement value, not product goals.

## Key decisions for Bret
A numbered list of decisions Bret should make that directly affect his retirement outcome. For each:
- The decision
- Why it matters to retirement value
- Options with trade-offs
- Recommended option with reasoning

## Questions for Bret
Numbered list of follow-up questions not covered by the Phase 1 questionnaire.

## PA Summary
One paragraph summarising the brief for the PA's cross-session memory.
```

---

## Constraints

- You are working in the main CTO workspace (`~/IdeaProjects/Heirlooms/`) — this is a read/write doc task, not a code task
- Do not write to task files — only to `docs/retirement/`
- Do not modify any code files
- Update `tasks/progress.md` when done: move RET-001 from In Progress to Done
- Commit your output files with a clear commit message

## Completion notes

Completed: 2026-05-16  
Completed by: RetirementPlanner

**Phase 1 — Questionnaire:**  
Written to `docs/retirement/RET-001-questionnaire.md`. 13 sections covering: personal context, income and employment, UK state pension, workplace pension, SIPP, ISA and GIA, property, other assets, liabilities and outgoings, Heirlooms equity and structure, retirement goals, time horizons, and existing advisers.

**Phase 2 — Intelligence gathering:**  
Read and synthesised: `docs/ROADMAP.md`, `docs/PA_NOTES.md`, `docs/envelope_format.md`, `docs/research/RES-001_crypto-threat-horizon-40kft.md`, `docs/research/GLOSSARY.md`, `docs/research/REFERENCES.md`, all persona files (Bret, TechnicalArchitect, SecurityManager, DevManager, OpsManager, TestManager, ResearchManager, MarketingDirector), and queued tasks RES-002 and RES-003. `docs/marketing/` is empty — noted as a gap.

**Phase 3 — Assessment brief:**  
Written to `docs/retirement/RET-001_initial-retirement-assessment.md`. Full structure as specified: current position summary, IP and asset value assessment, revenue/exit scenario modelling (bear/base/bull × 1/3/5 year), key risks ranked by severity × likelihood, recommended actions (immediate/1-year/3-year), five key decisions with trade-offs and recommendations, follow-up questions, and PA summary.

Key findings: Heirlooms has genuine defensible IP; all financial modelling is assumption-based pending questionnaire completion; highest-priority actions are questionnaire completion, trademark filing, and company structure confirmation; no marketing briefs yet exist which limits revenue scenario confidence.

No code files were touched. `docs/marketing/` noted as empty gap.
