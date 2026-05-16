---
id: PSY-001
title: Psychological assessment — grief reframe, Care Mode dignity, and Experience segment psychology
category: Psychology
priority: Medium
status: queued
assigned_to: Psychologist
depends_on: []
estimated: 1 session
---

## Context

The 2026-05-16 CTO session produced three product directions with significant psychological
dimensions. The Psychologist's role is to ensure that Heirlooms does not harm the people
it is trying to help — and to identify where the platform can do genuine psychological good.

**1. Platform reframe**: The CTO reframed Heirlooms away from "grief and loss" toward a
"time-based digital archive" — a place to plant happy memories for chosen people at chosen
moments. The word "loss" is now banned from brand vocabulary (it triggers avoidance in
users, and connotes data loss for technical users). The grief/posthumous use case remains
supported but is no longer the lead framing.

**2. Care Mode**: E2EE monitoring for Power of Attorney holders caring for someone with
diminishing capacity (e.g. Alzheimer's). A son set up monitoring for his mother after she
was diagnosed. He has LPA. The consent was given before her condition worsened. The
platform provides geofenced alerts visible only to the family. The person can revoke
consent at any time while they retain capacity.

**3. Chained capsules / Experience segment**: Capsules where unlock of one is conditional
on solving a puzzle in another. Includes competitive delivery (first solver wins), and
expiry-as-death (if nobody solves it, the second capsule is never delivered). Commercial
applications include ARGs, brand campaigns, and serialised storytelling.

**4. Friend-tester model**: Free storage in exchange for real-world testing. Bret's friends
are early users who share intimate content with the platform in exchange for free storage.

## Goal

Produce a psychological thinking brief that evaluates these product directions through a
grief-aware, trauma-informed, human-centred lens.

## Questions to address

### Platform reframe

1. **Psychological validity of the reframe**: Is "time-based digital archive for happy
   memories" a psychologically sound reframe? Does moving away from explicit grief language
   help or hinder users who are actually processing bereavement?

2. **Anticipatory grief**: Many users will use Heirlooms while facing the prospect of their
   own death or the death of someone they love. Is a "happy memories" frame appropriate
   for that emotional state, or does it create cognitive dissonance?

3. **Avoidance vs acceptance**: There is a tension between making the product approachable
   (avoiding "loss" language) and being honest about its deepest use case (posthumous
   delivery). What does the psychology of grief and death acceptance say about this tension?

### Care Mode

4. **Anticipatory grief — the Alzheimer's context**: Watching a parent decline through
   Alzheimer's is a form of prolonged, ambiguous loss — the person is still present but
   gradually less themselves. What does dignity-preserving design look like for this
   specific grief experience?

5. **Consent and diminishing capacity**: The person consented while they had full capacity.
   As capacity diminishes, they may no longer fully understand what they consented to.
   At what point does a previously-given consent become ethically problematic to rely on?
   What psychological safeguards should the product build in?

6. **Coercion risk**: Power of Attorney relationships are not always benign. Families are
   complicated. What product design features would make it harder for a coercive family
   member to misuse Care Mode?

7. **The monitored person's experience**: If the person with Alzheimer's knows they are
   being monitored (as they consented), how does that affect their psychological experience
   of daily life? Is there a risk that awareness of monitoring increases anxiety?

### Experience segment

8. **Competitive delivery psychology**: Making content access contingent on solving a puzzle,
   with a winner-takes-all outcome, introduces competition into a platform with a solemn,
   dignified voice. Is competition psychologically appropriate here? What emotional states
   does the "first solver wins" mechanic create in participants?

9. **Expiry-as-death for content**: If nobody solves the puzzle, a capsule is never
   delivered and its contents are permanently inaccessible. What is the psychological
   experience of knowing content has "died" — for the sender? For participants who didn't
   solve it in time?

10. **ARG psychology**: Alternate Reality Games are known to create intense emotional
    engagement, sometimes unhealthy obsession. What are the psychological risks of
    Heirlooms becoming an ARG platform, and what design safeguards should exist?

### Friend-tester model

11. **Reciprocity and trust**: People are sharing intimate content (memories, personal
    photos) in exchange for free storage. What psychological dynamics are at play in this
    exchange? Is "free storage" a psychologically appropriate exchange for intimate content?
    What does research on reciprocity and digital trust say?

## Output

Produce a psychological brief to `docs/psychology/PSY-001_grief-reframe-care-mode-experience-psychology.md`.

Report structure:
```
# PSY-001 — Psychological Assessment: Platform Reframe, Care Mode, and Experience Segment

## Platform reframe — psychological assessment
## Anticipatory grief — what the product touches
## Care Mode — dignity-preserving design
  ### Consent and diminishing capacity
  ### Coercion risk and safeguards
  ### The monitored person's experience
## Experience segment — psychological considerations
  ### Competitive delivery
  ### Expiry-as-death for content
  ### ARG psychology and safeguards
## Friend-tester reciprocity
## Design recommendations
## Red lines (things the product must not do)
## PA Summary
```

The PA Summary must include:
- Whether Care Mode can be built without causing psychological harm (with what conditions)
- Key design safeguards the product must implement
- Any directions the Psychologist recommends the product avoid entirely
- Handoffs needed to other personas (Legal, Philosopher, Marketing)

## Completion notes

Completed 2026-05-16 by Psychologist.

Output: `docs/psychology/PSY-001_grief-reframe-care-mode-experience-psychology.md`

All 11 questions addressed. Key findings:

- **Platform reframe:** Psychologically defensible at acquisition; requires a secondary "witnessing" register for depth-of-use moments (capsule sealing, executor unlock, vault handover). Recommend a dual-voice design system.
- **Anticipatory grief:** Product touches three distinct grief states (healthy planners, facing-own-death, bereaved). Each needs a different product register. Continuing bonds theory supports Heirlooms' core value proposition strongly.
- **Care Mode:** Can be built without causing harm, but requires hard architecture constraints: independent witness at setup, zero-friction revocation for monitored person, 90-day re-confirmation prompts, and scope-expansion as a new consent event.
- **Coercion risk:** PoA abuse is a well-documented pattern (adult children are the most frequent elder financial exploitation perpetrators). Multi-member visibility and independent witness are the primary structural safeguards.
- **Competitive delivery:** Must be whitelisted to commercial Experience capsules only. Applying "first solver wins" to personal posthumous content is a red line — it structurally excludes family members from a final communication.
- **Expiry-as-death:** Appropriate for commercial ARG contexts with clear upfront disclosure. Must be excluded from death-conditioned personal capsules.
- **ARG psychology:** Legitimate commercial direction but must be architecturally isolated from personal/grief use cases. ARG debriefing on campaign close is a concrete gap the product can fill.
- **Friend-tester reciprocity:** Existing friendship + free storage creates a reciprocity dynamic that may inflate content intimacy beyond what testers would otherwise choose. Recommend named exchange, plain-language data rights, and low-intimacy testing tasks.

Handoffs raised:
- Legal: Mental Capacity Act consent analysis; GDPR special category review for Care Mode monitoring data; friend-tester data rights formalisation
- Philosopher: Posthumous consent ethics (capacity diminution); moral status of undelivered content
- Marketing Director: Dual-voice brand architecture; Care Mode audience-specific language guidelines; friend-tester onboarding re-framing
