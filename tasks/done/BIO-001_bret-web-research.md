---
id: BIO-001
title: Bret — brief web research and biographical profile
category: Biography
priority: Low
status: queued
assigned_to: Biographer
depends_on: []
touches:
  - docs/biography/
estimated: 1 session
---

## Goal

Build a brief biographical profile of Bret from publicly available sources. This
profile becomes the foundation for all future biographical work — presentations,
talks, press, and the narrative of how Heirlooms was created.

## Starting points

- **LinkedIn**: https://www.linkedin.com/in/bret-calvey-45192510/
- **Heirlooms context**: read `personalities/Bret.md` and `docs/PA_NOTES.md` for
  what is already known about Bret from the project perspective
- **Session logs**: browse `personalities/session-logs/` — these capture Bret's
  thinking and decisions in his own words over time

## Research tasks

1. **LinkedIn profile**: read the profile in full — career history, skills,
   recommendations, any published posts or articles
2. **Broader web search**: search for Bret Calvey publicly — any conference talks,
   blog posts, GitHub activity, professional profiles, media mentions
3. **IT contracting background**: the session notes mention a background in IT
   contracting, IR35, and running a limited company — find any public context
   that enriches this part of the story
4. **Cross-reference with project**: where does Bret's professional background
   visibly shape Heirlooms' design choices? (e.g. a security-conscious contractor
   building an E2EE vault)

## Output

Produce a biographical profile to `docs/biography/BIO-001_bret-profile.md`.

Structure:
```
# BIO-001 — Bret Calvey: Biographical Profile

## Professional background (from public sources)
## Career arc summary
## What the public record tells us
## Gaps and unknowns (things not publicly available — to ask Bret directly)
## Narrative threads worth developing
## Suggested first questions for Bret (to fill the gaps)
## PA Summary
```

The "Gaps and unknowns" and "Suggested first questions" sections are important —
the goal is not just to aggregate public information but to identify what a
compelling narrative needs that only Bret can provide.

## Constraints

- Only use publicly available information
- Do not make assumptions or invent biographical details
- Flag clearly when something is inferred vs directly stated
- Keep it brief — this is a foundation document, not a full biography

## Completion notes

**Completed:** 2026-05-16  
**Completed by:** Biographer

### Research conducted

- Searched publicly for "Bret Calvey" across multiple query combinations (developer, UK contractor, JUXT, Colchester, Milton Keynes, GitHub, conference)
- Attempted LinkedIn profile fetch (https://www.linkedin.com/in/bret-calvey-45192510/) — WebFetch denied for authenticated site; key details (recommendation text, location, employer, connection count) extracted from search result snippets
- Discovered and confirmed BAC IT SERVICES LIMITED via Companies House public records (company number 07247888, incorporated 10 May 2010, dissolved 22 April 2025) — aligned with project notes about Bret's dormant limited company
- Confirmed JUXT association and gathered public profile of JUXT as a Clojure/functional programming consultancy in Milton Keynes
- Confirmed University of Essex connection via LinkedIn search result metadata
- Read all PA session logs (pa-2026-05-15T04-31, pa-2026-05-15T09-00, pa-2026-05-15T14-00, pa-2026-05-16T00-00, pa-2026-05-16T08-00, pa-2026-05-16T14-00)
- Read Marketing Director inaugural session log
- Read Research Manager inception session log
- Read docs/PA_NOTES.md, docs/ROADMAP.md, tasks/done/RET-001 completion notes

### Key findings

- BAC IT SERVICES LIMITED is confirmed as Bret's contracting vehicle (initials BAC = Bret Adam Calvey), operational 2010–2025
- JUXT (Clojure consultancy, Milton Keynes) is the only named employer in the public record
- University of Essex confirmed as alma mater; subject not confirmed in public sources
- No public blog, conference talks, or GitHub activity found — consistent with a developer who built privately throughout contracting years
- One strong recommendation found in LinkedIn search results: "highly productive programmer with first class problem solving capabilities… valuable insight for long term architectural changes"
- Heirlooms repo is private (github.com/bacalv/Heirlooms) — no public GitHub presence identified

### Output

Profile written to `docs/biography/BIO-001_bret-profile.md` (seven sections per task spec).

The most critical gap remains the founding story — the specific personal trigger that made Heirlooms feel necessary. Ten direct interview questions prepared to fill this and other narrative gaps.
