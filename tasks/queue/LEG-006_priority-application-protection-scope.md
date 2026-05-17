---
id: LEG-006
title: Priority application — practical protection scope and implementation risk
category: Legal
priority: High
status: queued
assigned_to: Legal
depends_on: [LEG-001, LEG-003]
touches:
  - docs/legal/
estimated: 1 session
---

## Background

The CTO is considering filing a UK priority application (rather than a full application
immediately) as a lower-cost way to establish a priority date for the window capsule
construction while the full filing fees are raised. The Marketing Director has advised
that a priority application establishes the priority date but does not constitute
enforcement rights, and that trade secret protection (keeping the repo private) is the
principal near-term protection.

The CTO wants Legal's precise assessment of what a UK priority application actually
protects, what it does not protect, and what practical risk remains during the gap
between filing and grant.

## Questions for Legal

### 1. Does a priority application prevent someone from implementing the construction?

Does filing a UK priority application prevent a third party from:
(a) independently implementing the window capsule construction in a competing product?
(b) open-sourcing an implementation of the construction?
(c) commercially launching a product based on the construction?

If not, at what stage (publication, grant) do enforceable rights arise?

### 2. Back-dated damages — what is actually recoverable?

If a third party implements the construction after the priority application is filed but
before the patent is granted, and the patent is subsequently granted:

(a) Can Heirlooms claim damages or an account of profits for infringement during
    the "patent pending" period?
(b) From what date — priority date, publication date, or grant date — do damages
    run?
(c) Does the infringer need to have had actual notice of the pending application
    for damages to run from the earlier date?

### 3. The 18-month publication window

The priority application will become public approximately 18 months after filing.
At publication:
(a) Does the published application disclose enough of the construction that a
    sophisticated party could implement it without the codebase?
(b) Does publication start any clock that affects the patentability of the claims?
(c) Should Heirlooms take any protective steps immediately before or after
    publication (e.g., ensuring the full application is in good order, trade mark
    filings, etc.)?

### 4. Trade secret protection in the interim

During the gap between priority filing and grant:
(a) What does UK trade secret law (Trade Secrets (Enforcement etc.) Regulations 2018)
    protect, and under what conditions?
(b) Does keeping the GitHub repo private constitute adequate trade secret protection,
    or are additional steps required (e.g., internal access controls, written secrecy
    policies)?
(c) At what point does trade secret protection end — on publication of the patent
    application, or only on public disclosure of the codebase?

### 5. Risk of a well-resourced third party filing first

If a third party becomes aware of the construction (e.g., through the JUXT outside
interests disclosure or an investor NDA conversation) and files their own application
before Heirlooms' priority application is lodged:
(a) What is the consequence for Heirlooms' patent position?
(b) Is there any recourse if the third party learned of the construction from a
    confidential disclosure?

### 6. Practical recommendation

Given the above, what is Legal's recommended sequencing?

Options considered by Marketing Director:
- File a minimal priority application immediately (low cost, establishes date) then
  prosecute fully within 12 months.
- File the full application immediately (higher cost, stronger position from day one).
- Delay filing until full fees are raised (risk: someone else files first; trade
  secret protection only).

Legal's view on which option best balances cost, protection, and risk for Heirlooms'
specific situation is requested.

## Output

Produce `docs/legal/LEG-006_priority-application-protection-scope.md` covering:

- Plain-language answers to each question above.
- A clear statement of what Heirlooms is and is not protected against at each stage
  (priority filing / publication / grant).
- A recommended sequencing with explicit reasoning.
- Any additional protective steps recommended during the gap period.

## Completion notes

<!-- Legal appends here and moves file to tasks/done/ -->
