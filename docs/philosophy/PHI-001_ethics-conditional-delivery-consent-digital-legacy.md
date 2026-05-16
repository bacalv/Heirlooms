# PHI-001 — Philosophical Ethics: Conditional Delivery, Consent, and the Long-Horizon Promise

**Author:** Philosopher  
**Date:** 2026-05-16  
**Status:** Final

---

## The reframe — philosophy of avoidance

The CTO's instinct to move the word "loss" out of Heirlooms' brand vocabulary is not merely a marketing decision. It is a choice about how the product relates to human psychology around death. That choice deserves scrutiny.

**Is designing around avoidance ethically neutral?**

The honest answer is no — but "not neutral" does not mean "wrong." Every design choice takes a position. A product that foregrounds death — "you will die; prepare" — is not more honest than one that foregrounds memory and time. It is differently positioned. The question is whether the position is one Heirlooms can stand behind with integrity.

Ernest Becker's work on mortality salience (*The Denial of Death*, 1973) establishes that much of human cultural activity is a structured response to the awareness of death. We do not design for an imaginary person who is at peace with their mortality — we design for actual humans who use busyness, beauty, narrative, and connection to hold that awareness at arm's length. Meeting users where they are is not complicity in avoidance; it is honesty about who the users are.

However, there is a meaningful line between accommodation and exploitation. A product that profits from the psychological avoidance without offering any route through it is more troubling than one that begins with the comfortable (memories, time, gifts) and allows the user to approach the harder territory at their own pace. Heirlooms' design — where a birthday video and a posthumous letter live in the same product — has this virtue: users are never misled about what the platform ultimately serves. The solemn brand voice holds the door open without forcing anyone through it.

**Is the reframe honest?**

This is sharper. When a parent seals a capsule knowing they may not survive to see their child at eighteen, reframing that act as "planting happy memories" is not quite a lie, but it is a softening. There is something worth preserving in refusing to soften it completely.

The position I would defend: the reframe is honest at the product level (Heirlooms genuinely is about time, not only death) but would become dishonest if it was used to avoid having explicit mortality-facing copy at the moments where that honesty is owed. A parent sealing a posthumous capsule should encounter product copy that acknowledges what they are doing — not brutally, but plainly. "This capsule will be delivered if you're not there" is more honest than "plant this memory for the future." The reframe is a brand posture, not permission to sanitise every interaction.

**Recommendation:** The reframe is defensible. The ethical obligation is to preserve honesty-at-the-moment, especially at capsule sealing. The brand can be warm; the UX at emotionally critical moments must be direct.

---

## Conditional delivery — ethics of contingency

### The morality of conditional intent

When a sender creates a chained capsule whose second compartment is accessible only to whoever solves the puzzle first, they are doing something more complex than giving a gift. They are encoding a conditional: not "I want you to have this" but "I want someone who can do this to have this, and I want the attempt to matter."

Is that morally coherent?

Yes. Conditional intent is familiar in ethics and law: inheritance contingent on surviving a testator, a bequest conditional on education being completed, trust distributions contingent on reaching majority. The philosophical tradition does not treat conditionality as a corruption of intention; it treats it as a refinement. The sender is not withholding arbitrarily — they are specifying the conditions under which they judge the gift to be appropriate.

The more interesting question is whether this form of conditionality belongs in a product whose default emotional register is gift-giving and legacy. The ethical concern is not that conditional intent is wrong; it is that the product's framing may create a mismatch between the sender's actual intent (the puzzle is the point) and the recipient's expectation (I am receiving something precious from someone I loved). If the mechanic is presented as a game and received as an inheritance ritual, the mismatch can feel exploitative — recipients competing over a loved one's words while grieving.

The ethical safeguard is disclosure. Senders should be required to understand, before sealing, that they are creating a competition, not simply a time-delayed gift. And the delivery UX must make the mechanic explicit to recipients before they begin — they should know what they are entering, not discover it mid-process.

### Content that is designed to die

If neither recipient solves the puzzle before C₁'s window closes, C₂ and its contents become permanently inaccessible. The sender created something that may never be experienced by anyone.

Is this ethically analogous to destroying the content?

Not quite — but it is close. The moral difference between "I destroyed this" and "I created conditions under which this was never seen" is one of intent and timing, not of outcome. The practical result is the same: the content does not reach anyone. The philosophical question is whether the sender had a moral obligation to consider this outcome at creation time.

I think they did. Here is why: the sender is not only the author of the content. They are also the architect of the conditions of its delivery. When those conditions are designed such that permanent inaccessibility is a real possibility (not a remote edge case), the sender has designed an outcome they should own morally, not merely technically. The content was created for someone. Designing a system in which it reaches no one is a choice about those intended recipients.

This is not a red line — people can make gifts that are contingent on something happening. But it has two ethical implications:

First, the sender must be genuinely informed about what "nobody solves the puzzle" means. If the product allows a sender to create a C₂ containing a final goodbye letter, with a puzzle window that is realistically difficult, without surfacing that the letter may be permanently lost, the product is facilitating a decision the sender may deeply regret — or may never know they should regret.

Second, there is a class of content for which this mechanic is ethically unsuitable, and the product should take a position on that. A trivial prize (a secret recipe, a fun memory) designed to die is different from a final personal message designed to die. The product may not be able to enforce this distinction, but it should at least surface it.

**This is an ethical concern that marketing and legal have not captured.** Legal will note that the user consented to the mechanic. Marketing will note that the mechanic is a differentiator. Neither discipline will raise the question of whether the product has an obligation to surface the genuine possibility of permanent loss before the sender seals — particularly for content of deep personal significance.

### Competition over inheritance

The "first solver wins" mechanic introduces competition between recipients. In a family context, this may feel alien and uncomfortable, or it may feel like exactly what a mischievous grandparent would have wanted. The ethics here track the ethics of the content.

For trivial or playful content, competition is morally unproblematic. The philosophy of games (Suits, Rawls on fair play) asks that games be entered freely, with rules understood, and conducted fairly. If the mechanic is clearly framed as a game — and experienced as one — it raises no deep ethical concern.

For significant personal content, competition is more troubling. The concern is not that competition is wrong per se, but that inheriting access to a final personal message from a parent or partner through a race introduces a relational wound that the sender may not have intended and the product should not enable carelessly. The first solver gets something; the second solver gets something too — the knowledge that they were second, that they lost, in a context where "losing" has emotional weight beyond the typical game context.

There is also a fairness dimension that the philosophy of games illuminates. A fair competition requires roughly equal opportunity. Among siblings, cognitive speed, geographic circumstance (time zones, notification delivery), or life situation (one sibling is caring for children; the other has more free time) may systematically advantage one over another in ways the sender did not intend. The product is enforcing a rule that may produce outcomes the sender would not have chosen.

**Position:** Competition over legacy content is not indefensible, but it should be opt-in with explicit sender acknowledgement that the mechanic may create relational consequences. The product should not present it as simply a feature without surfacing the emotional stakes.

### Ownership of the sealed capsule

Once a capsule is sealed and the author cannot open it — as Milestone 11's cryptographic sealing is designed to ensure — a genuinely interesting question of ownership arises.

Locke's account of property is grounded in labour: a person owns what they have mixed their labour with. By this account, the sealed capsule is unambiguously the sender's property — they created it. But Locke's framework was not designed for objects that are created by one person, locked against the creator, and conditionally accessible to another. The capsule has entered a kind of liminal state: not possessed by the sender (who cannot open it), not yet received by the recipient (who may never see it).

The more useful framing, I think, is custodianship rather than ownership. The platform holds the ciphertext as custodian, on behalf of both the sender's intent and the recipient's future interest. Neither party owns it in the Lockean sense; both have a claim to it. The platform's obligation is to honour both claims faithfully — the sender's intent (the conditions of delivery) and the recipient's interest (actually receiving what was intended, if the conditions are met).

This has practical consequences. If the platform were to shut down, the sealed capsule is not simply an asset to be disposed of; it is a trust obligation. The platform has accepted custodianship of something created for someone. Heirlooms' commitment to encrypted exports is a partial answer to this — it ensures the sender retains the ability to retrieve the ciphertext even if the platform fails. But it does not resolve the underlying question: the recipient's interest in receiving what was intended to them is not met by a ciphertext they cannot decrypt.

The platform does have an obligation to the sealed content itself — not as an owner, but as a custodian. Custodianship is a weaker claim than ownership, but it is a real one, and it survives the platform's operational continuation.

---

## Consent before capacity loss

### Epistemic limits of anticipatory consent

The person with Alzheimer's who consented to Care Mode monitoring while they had full capacity faces an epistemic limitation that is real but not disqualifying.

The bioethics literature on anticipatory consent (advance directives in medical care, Ulysses contracts in psychiatric care) addresses precisely this situation. The standard account is this: consent is valid when it is given by a person with capacity, who understands the nature of what they are consenting to, and who gives that consent voluntarily. The fact that the consenting person cannot fully know what their future experience of the consented state will be like is a feature of all meaningful consent to future-state decisions, not a defect unique to this case.

When a person consents to a medical procedure with uncertain outcomes, we do not void the consent on the grounds that they could not know which outcome they would experience. We ask whether they understood the range of possible outcomes and chose to accept them. Anticipatory consent to monitoring is structurally similar: the person with capacity knew what monitoring would involve, understood that their future self might not prefer it, and chose to establish it anyway.

However, there is a morally significant asymmetry between medical consent and Care Mode consent. Medical consent typically involves a professional who is required to explain the procedure, its risks, and alternatives. The consenting person can ask questions. The consent process is documented and witnessed.

Care Mode consent — as currently envisioned — is a unilateral user action. The platform cannot ensure that the person has understood what they are consenting to in the same way a clinical consent process can. This matters more, not less, when the person is already facing cognitive decline and may be under family pressure (however well-intentioned) at the time of consent.

**Position:** Anticipatory consent to Care Mode is ethically valid in principle. The ethical burden falls on the consent process being adequately informed and free from coercion — and the platform's interface must take that burden seriously.

### Personal identity and continuity

Parfit's account of personal identity in *Reasons and Persons* (1984) poses the hardest challenge here. Parfit argued that what matters in survival is not strict identity — the persistence of the same metaphysical entity — but psychological continuity and connectedness: the overlapping chains of memories, intentions, beliefs, and character traits that link a person across time.

On a Parfitian view, a person with advanced dementia may have very weak psychological continuity with the person who gave consent. Memories are gone; personality may have changed dramatically; the person may no longer recognise family members, or be capable of forming the kind of intentions that ground preferences. If the degree of psychological continuity is low enough, Parfit would say that the earlier person and the current person are connected, but not the same person in any robust sense.

What follows from this for Care Mode?

The Lockean view — which grounds personal identity in memory — reaches a similar conclusion: the person who cannot remember giving consent is, in a meaningful sense, not the same person who gave it. And Locke's account of consent is explicitly tied to personal identity: consent is binding on the person who gave it because they are the same person who now lives under it.

These are genuinely difficult implications, but I do not think they void anticipatory consent as a concept. They do, however, place significant weight on the person's earlier self having genuinely represented their later self's interests — not just their own preferences at the time of consent, but what they would want for the person they would become.

This is the ground on which Care Mode can be ethically defended: the person with capacity is making a judgment about their future self's interests ("I want to be found if I wander; I trust my son to respond appropriately; I would rather be monitored than lost"). The ethical legitimacy of that consent rests on how well the product supports that judgment being well-informed.

**The irresolvable tension:** There is a genuine philosophical tension here that cannot be dissolved. The person experiencing the monitoring may, in some stages of dementia, be distressed by the surveillance in a way that they cannot articulate or that caregivers dismiss as "confusion." The consent of the earlier self cannot be fully binding on the welfare of the later self in all circumstances — it can only give strong presumptive authority to the POA holder to act in what they judge to be the monitored person's best interests. The platform cannot adjudicate this. What it can do is design Care Mode so that the POA holder's access is proportionate to the stated purpose (safety, not surveillance), and so that the platform takes no position on what the monitored person "would have wanted" beyond what the consenting person explicitly stated.

### Whom does the platform serve?

In Care Mode, the platform serves three interests simultaneously: the monitored person's safety (finding them if they wander), the POA holder's peace of mind (knowing where they are), and implicitly, the broader family's interests in the monitored person's wellbeing.

The monitored person benefits directly from the safety function. But they are also subject to surveillance they may not currently understand or prefer. The question is whether this is serving the monitored person, or serving their family at the monitored person's expense.

This is not a cleanly resolvable tension. The honest answer is that Care Mode serves both — but that the justification for the monitored person's contribution to the arrangement depends entirely on the consent being genuine and the POA holder exercising their role in the monitored person's interests, not in their own convenience.

The platform's ethical exposure here is this: it cannot verify that the POA holder is acting in the monitored person's interests. It cannot verify that the original consent was free from coercion. It cannot verify that the monitoring is being used only for safety. It is enforcing a consent cryptographically without being able to audit the quality of that consent or the use of its outputs.

**Position:** The platform serves the monitored person only if the consent was genuine and the POA holder is acting in good faith. Both of those conditions are outside the platform's control. The ethical obligation is to minimise the scope of monitoring to what the stated purpose (safety) requires, to make the revocation mechanism clear and easily accessible during any period when capacity is retained, and to document at consent time what the monitoring covers and does not cover. The platform should not market Care Mode as "protecting" the monitored person without acknowledging that protection and surveillance are not the same thing.

---

## The long-horizon promise

### Obligations across decades

Heirlooms accepts a capsule sealed today with a delivery date in 2045. That is a promise that extends nineteen years into the future — across technology generations, company ownership changes, and potential insolvency. Is it ethical for a startup to accept this obligation?

The honest answer is that it is ethically justifiable only if the startup has taken genuine structural steps to honour the promise under realistic failure scenarios. The moral weight of the promise is not diminished by the fact that it might be broken; it is exactly the difficulty of keeping it that makes the structural preparations ethically required.

The "encrypted export that works without the app" is not a cop-out. It is the platform acknowledging, honestly, that a twenty-year promise cannot be made solely on the basis of organisational continuity. By ensuring the user retains an independently usable form of their data, the platform is distributing the obligation rather than concentrating it entirely in its own survival.

But there is a gap. The encrypted export preserves the sender's data. It does not preserve the delivery mechanism — the notification, the unlock, the recipient experience. A sender who has died cannot use the export to deliver the capsule manually. The export is recovery infrastructure for the sender's control over their data; it is not a substitute for the delivery promise.

The ethical position: the platform should be explicit about what its long-horizon promise covers and what it does not. "We will keep your data safe and return it to you if we fail" is different from "we will deliver your capsule in 2045." Both are meaningful. Only the second is the product's deepest promise, and the platform should be honest that the second requires organisational continuity it cannot guarantee.

**Practical implication:** The executor model (Milestone 13) partially addresses this by distributing the delivery obligation to trusted humans. This is philosophically sound — it replaces a fragile institutional promise with a human network, which is more resilient to organisational failure. But it shifts the reliability question to the executor cohort's continuity, which has its own failure modes.

There is no clean resolution to the ethics of multi-decade promises. The honest position is: we accept this obligation; we have taken these structural steps to honour it; we cannot guarantee it. That honesty is itself an ethical act.

### Obligations to future recipients

Recipients of capsules are people who have not agreed to any terms of service. They may receive content decades after it was created. What does Heirlooms owe them?

This is philosophically unusual. Most platform ethics discussions concern the rights of users — people who have agreed to terms and entered a relationship with the platform. Future recipients are third parties. They have no contractual relationship with Heirlooms. They may not know Heirlooms exists until the capsule arrives.

Yet the platform has, by accepting the capsule, acquired obligations to these people. It holds content destined for them. It will (or should) notify them when it is delivered. It will present them with an experience at a moment of potentially significant emotional weight.

The philosophical basis for obligations to future recipients is not contractual. It is grounded in the ethics of custodianship and in the nature of the act the platform has enabled: someone trusted the platform to do something for the benefit of another person. The platform accepted that trust. That acceptance creates obligations to the beneficiary, not merely to the trustor.

What does that mean concretely?

First, the platform owes future recipients honesty about what they are receiving. A person who receives a capsule should be told enough about its provenance to understand what it is — who sent it, under what conditions, and what the platform's role was. This is an obligation of transparency, not just UX courtesy.

Second, the platform owes future recipients a notification experience that is proportionate to the emotional weight of the content. A posthumous message from a deceased parent demands different UX treatment than a birthday greeting — and the platform may not know which it is until it is delivered. The ethical minimum is that the delivery experience is designed for the hardest case, not the lightest one.

Third, the platform owes future recipients the ability to refuse. A person who receives a notification that a capsule exists for them should be able to decline to open it. The platform should not assume that every recipient will want to receive the content, particularly if it arrives after a death. Grief is not uniform, and consent to receive applies to recipients, not only senders.

The platform cannot get consent in advance from recipients who do not yet have a relationship with it. But it can design the delivery experience so that the first contact is handled with the care that the absence of prior consent requires.

---

## Summary of ethical tensions (with no easy answers)

**1. Avoidance and honesty.** The product is built to meet users where they are — in avoidance of mortality. The ethical tension is that the most significant use cases are inseparable from mortality. There is no clean line between accommodation and complicity; the product must navigate this case by case, interaction by interaction. The tension does not resolve; it requires ongoing attention.

**2. Content designed to die.** Permanent inaccessibility as a design outcome is not equivalent to destruction, but the practical result is the same. The sender's obligation to consider this, and the platform's obligation to surface it, are real. The tension is between honouring the sender's autonomy to design the conditions of delivery and the platform's obligation to future recipients who may receive nothing. There is no mechanism that fully resolves this; there is only better or worse handling of the sender's decision at creation time.

**3. Competition over legacy.** Competitive mechanics and inheritance rituals sit uncomfortably together. The tension is between feature innovation (the mechanic is genuinely novel and can be used beautifully) and the relational damage that competition can cause among grieving people. The product cannot eliminate this tension; it can only ensure senders are genuinely informed about it.

**4. Anticipatory consent and diminished capacity.** Consent given under uncertainty about a future state is always imperfect. The tension between the earlier self's preferences and the later self's welfare cannot be dissolved by any philosophical account of personal identity — Parfit and Locke both point toward the same honest conclusion: the consent is the best available substitute for the later self's preferences, but it is a substitute, not the thing itself. The platform cannot monitor the quality of that substitution.

**5. Platform service in Care Mode.** The platform serves the monitored person's interests only conditionally, depending on the POA holder's good faith. It has no mechanism to audit that good faith. This is an irreducible ethical exposure: accepting a Care Mode consent is accepting moral responsibility for an outcome the platform cannot fully control.

**6. Multi-decade promises.** The platform cannot guarantee organisational continuity for nineteen years. It can build structural safeguards. The tension is between the honesty of acknowledging this limitation and the commercial pressure not to foreground it. The ethical obligation is toward honesty, even if it is uncomfortable.

**7. Obligations to non-users.** Future recipients have no contractual relationship with the platform but have real interests the platform is obligated to serve. The tension is between the platform's legal relationship (with the sender) and its ethical relationship (with the recipient). Legal and ethics do not align here. The platform should resolve in favour of ethics.

---

## Red lines (things that are ethically indefensible regardless of legality)

**1. Care Mode without a genuine informed consent process.** If the platform allows a POA holder to enable monitoring of a family member without the monitored person going through a consent flow that is clearly separate from any account setup, and without an auditable record of what was consented to, the monitoring is ethically indefensible even if legally permissible. Consent must be real, not buried in terms of service.

**2. Chained capsules with significant personal content, without explicit "content may be permanently lost" disclosure.** A sender who seals a final message to their child inside a competitive puzzle mechanism, without understanding that the message may never be delivered to anyone, has been failed by the product. The product must surface this possibility plainly before sealing, for any capsule where permanent inaccessibility is a realistic outcome. Hiding this behind interface design is indefensible.

**3. Marketing Care Mode as "protecting" the monitored person without acknowledging the surveillance dimension.** The monitored person is subject to tracking they may not currently understand or prefer. Marketing that presents this purely as care, without acknowledging that it involves surveillance of a person who may not currently consent to it, is dishonest. The honesty does not need to be prominent, but it must exist.

**4. Delivery UX that does not allow recipients to refuse.** A future recipient who does not want to receive a capsule — for whatever reason, including grief, trauma, or relationship breakdown — must have a clear mechanism to decline. A platform that forces delivery of posthumous content on unwilling recipients has overridden the recipient's autonomy in favour of the sender's. The sender's intent does not override the recipient's right not to receive.

**5. Promising long-horizon delivery without structural safeguards and honest disclosure of failure modes.** Accepting a twenty-year delivery obligation is ethical only if the platform has genuinely prepared for its own failure and disclosed the limits of its guarantee. A startup that accepts this obligation while privately expecting that organisational continuity is someone else's problem is making a promise it does not intend to keep. The encrypted export is a necessary but not sufficient condition.

---

## PA Summary

**The most important ethical tension the product must resolve before building Care Mode:**

The platform cannot verify that the original consent to Care Mode was informed and free from coercion, and cannot audit whether the POA holder is using monitoring in the monitored person's interests rather than their own convenience. This is not a legal problem (consent makes it legal); it is an ethical one. The product must resolve this by: (a) designing a consent flow that is genuinely separate from account setup, clearly described, and documented; (b) specifying that Care Mode access is scoped strictly to safety functions, not general surveillance; and (c) building a clear revocation mechanism that is prominent while capacity is retained. If these three elements are not in place before Care Mode ships, the platform is ethically exposed in a way that reputational or legal events could make highly damaging — not because the law requires it, but because the product is enabling something that depends entirely on trust it cannot audit.

**Whether the "content that dies" mechanic raises ethical concerns that marketing or legal have not captured:**

Yes — clearly. Legal's position is that user consent makes it legally permissible. Marketing's position is that the mechanic is a differentiator. Neither discipline has raised the following: a sender may not understand, at the moment of creating a chained capsule containing significant personal content, that the content could be permanently inaccessible to every intended recipient. The platform has facilitated the creation of a posthumous message that may never be read. This is not malicious, but it may be a decision the sender would not have made if it had been explained plainly. The product's ethical obligation is to surface this consequence explicitly, particularly for capsules containing personal (rather than trivial) content, before sealing. This is a product design recommendation, not a legal one: add a friction point at sealing that says, in plain language, that if the puzzle is not solved within the window, this content will be permanently inaccessible. The sender should own that outcome knowingly.

**Product decisions the CTO should make explicitly rather than leaving implicit:**

1. **Scope of Care Mode monitoring.** The product should make an explicit decision about what data Care Mode exposes to the POA holder and what it does not. Location only? Activity patterns? Communication frequency? This is not a technical question — it is a values question that shapes everything downstream. Leaving it implicit means it will be answered by whoever implements it, not by the CTO.

2. **Consent process for Care Mode.** The product needs an explicit decision on whether Care Mode consent is a separate, documented, standalone flow (the ethically adequate version) or an account-level toggle (not adequate). This decision should be made before any implementation begins.

3. **Class of content eligible for chained capsules.** The product should decide whether to distinguish between trivial content (where "content that dies" is low-stakes) and significant personal content (where it is high-stakes) and whether any friction or disclosure varies by content class. Even if the distinction is unenforceable technically, the explicit decision shapes how the product describes itself and what it surfaces to users.

4. **The long-horizon promise — what it covers and what it does not.** The product should state explicitly what happens to a sealed capsule if Heirlooms ceases to operate. "The encrypted export preserves your data" is true. "Your capsule will be delivered in 2045" may not be — and the gap between these two statements is the ethical exposure. The CTO should decide on the exact promise the product makes and ensure it is reflected in UX copy at the moment of sealing, not buried in terms of service.

5. **Recipient refusal.** The product should decide, explicitly, whether recipients can decline to receive a capsule after notification. This is a values decision: does the sender's intent or the recipient's autonomy take precedence when they conflict? The Philosopher's position is that recipient autonomy takes precedence, but this is a decision that belongs to the CTO, stated explicitly, before Milestone 12 ships.
