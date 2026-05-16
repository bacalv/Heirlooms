---
id: PHI-001
title: Philosophical ethics — conditional delivery, consent before capacity loss, and the platform's long-horizon promise
category: Philosophy
priority: Medium
status: done
assigned_to: Philosopher
depends_on: []
estimated: 1 session
---

## Context

The 2026-05-16 CTO session explored three product directions that raise deep ethical questions
the Philosopher is best placed to examine. No other persona is charged with asking these
questions — engineering, legal, and marketing can all proceed without answers, but they
proceed at ethical risk.

**1. Platform reframe**: Heirlooms was reframed from a "grief and loss" platform to a
"time-based digital archive for happy memories." The CTO noted that the word "loss" is
something humans instinctively avoid thinking about — which is precisely why he wants to
move away from it in the product framing.

**2. Chained capsules / conditional delivery**: A capsule C₁ is delivered to two recipients
with a time window. Inside is a puzzle. The first to solve it gains access to a second
capsule C₂. If nobody solves the puzzle before C₁'s window closes, C₂ is never delivered
and its contents are permanently inaccessible.

**3. Care Mode**: A person with Alzheimer's consented (while they had full capacity) to
monitoring by their son, who holds Power of Attorney. The platform enforces that consent
cryptographically. The consent is revocable while capacity remains; once capacity is lost,
it continues in force.

**4. The long-horizon promise**: Heirlooms makes promises across decades — a capsule sealed
today may be intended to open in 2045. The platform's founding philosophy is: "Start
personal, stay simple, add trust slowly." The encrypted export that works without the app
is described as "not a fallback — it is a core feature."

## Goal

Produce a philosophical thinking brief that examines the ethical foundations and tensions
of these product directions. This is not a legal or psychological assessment — it is an
examination of values, obligations, and moral coherence.

## Questions to address

### The reframe — what is being avoided?

1. **Avoidance as design choice**: The CTO chose to move "loss" out of the brand vocabulary
   because humans instinctively avoid it. Is designing around human avoidance of mortality
   ethically neutral, or does it represent a form of complicity in that avoidance? Is there
   a meaningful difference between "we help people preserve memories" and "we help people
   not think about death"?

2. **Is the reframe honest?**: Heirlooms' deepest use case — a message from a parent who
   knows they may die before their child grows up — is inseparable from mortality. Does
   reframing this as "planting happy memories" misrepresent the product to users? Or is
   it simply meeting users where they are?

### Conditional delivery — the ethics of contingency

3. **Can intent be conditional?**: When a sender creates a capsule where the contents
   are only accessible if a recipient solves a puzzle, the sender's intent is not simply
   "I want this person to have this" but "I want this person to earn this." Is that a
   morally coherent form of intent to encode in a product whose language is about gifts
   and inheritance?

4. **The content that dies**: If nobody solves the puzzle, C₂ is permanently inaccessible.
   The sender created something that may never be seen. Is this ethically analogous to
   destroying the content? Does the sender have a moral obligation to consider the
   possibility that the content will never reach anyone? What does it mean to create
   something with the intent that it might not exist?

5. **Competition over inheritance**: The "first solver wins" mechanic introduces competition
   between recipients. In the context of a platform that handles family relationships and
   legacy, is competitive access to someone's intended gift morally defensible? Does it
   matter whether the gift is trivial (a puzzle prize) or significant (a personal message)?

6. **Whose property is the sealed capsule?**: Once a capsule is sealed and the author
   cannot open it, does it still belong to the author? Or does it become something in
   between — committed but not yet delivered? Does the platform have any obligation to
   the sealed content itself?

### Consent before capacity loss

7. **Autonomous consent under uncertainty**: The person with Alzheimer's consented to
   monitoring before their condition worsened. At the time of consent, they could not
   fully know what their future experience of the monitoring would be like. Is consent
   given under this epistemic limitation truly autonomous? Is it materially different
   from consent to a medical procedure with unknown side effects?

8. **The continuity of self**: Philosophical literature on personal identity (Parfit,
   Locke) raises questions about whether a person with advanced dementia is the same
   person who gave consent. If they are not the same person in a philosophically meaningful
   sense, what is the moral status of consent given by their earlier self?

9. **Who does the platform serve?**: In Care Mode, the platform serves the POA holder's
   need for safety information. The person being monitored benefits indirectly (from being
   found if they wander). But they are also subject to surveillance they may no longer
   fully understand. Is the platform serving the monitored person, or serving their family
   at the monitored person's expense?

### The long-horizon promise

10. **Making promises across decades**: Heirlooms makes an implicit promise to users that
    their capsules will be delivered in 2035, 2045, 2055. Startups fail. Technology changes.
    Is it ethical for a startup to accept this obligation? What moral weight does the
    "encrypted export that works without the app" carry — is it sufficient, or does it
    merely shift the burden to the user?

11. **The platform's obligations to future recipients**: The recipients of capsules are
    people who have not agreed to any terms of service. They may receive content decades
    after it was created. What obligations does Heirlooms have to these future recipients
    who are not current users?

## Output

Produce a philosophical brief to `docs/philosophy/PHI-001_ethics-conditional-delivery-consent-digital-legacy.md`.

Report structure:
```
# PHI-001 — Philosophical Ethics: Conditional Delivery, Consent, and the Long-Horizon Promise

## The reframe — philosophy of avoidance
## Conditional delivery — ethics of contingency
  ### The morality of conditional intent
  ### Content that is designed to die
  ### Competition over inheritance
  ### Ownership of the sealed capsule
## Consent before capacity loss
  ### Epistemic limits of anticipatory consent
  ### Personal identity and continuity
  ### Whom does the platform serve?
## The long-horizon promise
  ### Obligations across decades
  ### Obligations to future recipients
## Summary of ethical tensions (with no easy answers)
## Red lines (things that are ethically indefensible regardless of legality)
## PA Summary
```

The PA Summary must include:
- The most important ethical tension the product must resolve before building Care Mode
- Whether the chained capsule "content that dies" mechanic raises ethical concerns
  that marketing or legal have not captured
- Any product decisions the Philosopher recommends the CTO make explicitly rather than
  leaving implicit

## Completion notes

Completed: 2026-05-16

Output written to `docs/philosophy/PHI-001_ethics-conditional-delivery-consent-digital-legacy.md`.

All eleven questions addressed. Clear positions taken on:
- The brand reframe (defensible, with an obligation to preserve honesty-at-the-moment)
- Conditional intent (coherent, but requires disclosure of the competition mechanic)
- Content that dies (raises ethical concerns marketing and legal have not captured — recommendation: add explicit "permanently inaccessible" friction point at sealing)
- Competition over inheritance (opt-in only, with explicit sender acknowledgement of relational stakes)
- Anticipatory consent (valid in principle; burden falls on the consent process being genuinely informed)
- Personal identity continuity (Parfit/Locke both point toward consent as presumptive authority, not absolute authority)
- Platform service in Care Mode (ethically exposed — platform cannot audit POA holder's good faith)
- Long-horizon promises (ethically justifiable only with structural safeguards and honest disclosure)
- Future recipients (non-contractual obligations are real; recipient refusal must be possible)

Five red lines identified. Five explicit CTO decisions flagged.
