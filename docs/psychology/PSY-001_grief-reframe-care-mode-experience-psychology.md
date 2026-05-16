# PSY-001 — Psychological Assessment: Platform Reframe, Care Mode, and Experience Segment

**Author:** Psychologist  
**Date:** 2026-05-16  
**Status:** Final  
**Audience:** CTO, Marketing Director, Philosopher, Legal

---

## Platform reframe — psychological assessment

### Is "time-based digital archive for happy memories" a psychologically sound reframe?

The short answer is: partially. The reframe is commercially astute and psychologically defensible for the majority of users, but it carries a risk of alienating — or worse, harming — a meaningful minority who are already in acute grief.

The marketing case rests on a real insight from Terror Management Theory (Greenberg, Solomon & Pyszczynski): explicit mortality salience triggers avoidance and defensiveness in the general population. If your homepage talks about death, most people close the tab before they process the value. Removing the word "loss" from brand vocabulary reduces that mortality salience trigger. This is legitimate.

The clinical risk lives elsewhere. Research on anticipatory grief consistently finds that **acceptance, not avoidance, is the strongest protective factor**. A 2016 study by Shore (Pitt Bioethics) found that acceptance accounted for 13% of additional variance in anticipatory grief outcomes over and above anxiety and depression — the single largest predictor. Users who are already facing a terminal diagnosis or the impending death of a parent do not need "happy memories" framing; they need framing that acknowledges the weight of what they are doing, without making it morbid.

The distinction that matters is this: **the reframe is safe in acquisition contexts** (top of funnel, people who haven't yet committed emotionally to the product) and **potentially harmful in depth-of-use contexts** (the moment a person sits down to create a posthumous capsule for their child). A "happy memories" frame at the point of sealing a death-conditioned capsule is a tonal mismatch that will feel to the user like the platform doesn't understand them.

### Anticipatory grief and the "happy memories" frame

Anticipatory grief is not simply sadness about something that hasn't happened yet. It is a clinically recognised form of grief that encompasses simultaneous experience of loss, love, dread, and meaning-making — often for months or years before a death occurs. Researchers at PMC (2025) describe it as "preparatory grief," a mode of processing that helps people build resilience for the death to come.

For users in this state, the appropriate frame is not "plant happy memories" — it is "leave something that will matter." The product's existing instinct about this is good: the milestone delivery concept (a video sealed when a child is 8, opened on their 18th birthday) is emotionally coherent whether the parent is alive or dead at delivery. The task for the Marketing Director is to find language that carries this weight **without invoking grief explicitly**. Words like "preserve," "plant," "leave," and "for when the time comes" are death-aware without being death-named.

### Avoidance vs acceptance — what the research says

There is a genuine tension here, and the product cannot fully resolve it at brand level. What it can do is handle it at the **interface level**:

- At acquisition: use the "time-based archive / happy memories" frame. This is appropriate and will not harm general-population users.
- At onboarding: allow users to self-select their context. A capsule intended for "my daughter's 18th birthday" and a capsule intended for "if I'm no longer here when she turns 18" are the same technical object but very different emotional experiences. The product can ask, gently, which kind of capsule this is — and adjust its tone accordingly.
- At the moment of sealing: shift to a voice that acknowledges the weight of what is happening. "This capsule is sealed. [Name] will receive it on [date]." Not "Great job! Your memory is safely stored."

The underlying psychology is well-established: avoidance of death-related thoughts creates complicated grief when they eventually surface (Shear et al., continuing bonds literature). The product's job is not to force death awareness on users who don't want it — it is to be ready to hold that weight when users bring it.

---

## Anticipatory grief — what the product touches

Heirlooms will be used by people in at least three distinct grief states, each requiring a different product response:

**1. Healthy adults planning for an uncertain future.** These users are the general-population majority. They are creating birthday capsules, anniversary messages, letters to children. Death is a background awareness, not a foreground experience. The "time-based archive" frame serves them well. The product should be warm, light, and free of heavy clinical framing.

**2. People facing their own death.** Terminal diagnosis, serious illness, old age with clear awareness of mortality. These users are doing something profound. They need the product to match the gravity of the act. Language like "if you are no longer here" or "before the time comes" — used carefully, not as a default — acknowledges their reality without making the product feel like a grief support service.

**3. Bereaved people.** People who are already grieving — processing the vault and capsules of someone who has just died, receiving a posthumous message from a parent. These users are in acute grief. Every word in the executor unlock flow, the capsule delivery notification, and the vault handover experience must be written with the care of a grief counsellor. The product's role here is not "efficient execution" — it is witness to something sacred.

The continuing bonds research (Klass, Silverman & Nickman; reviewed in Tandfonline 2023 systematic review) is directly relevant here: **maintaining a connection with the deceased is a healthy and common part of grief**, not a pathology to be resolved. Heirlooms is uniquely positioned to facilitate healthy continuing bonds — a time-locked message from a dead parent is not a morbid artefact, it is exactly what the continuing bonds literature shows bereaved people want and benefit from. This should be part of how the product understands and communicates its own value.

---

## Care Mode — dignity-preserving design

Care Mode is the product's most psychologically complex feature, and it warrants the most careful design attention. Done well, it offers real relief to families in one of the hardest situations a family can face. Done carelessly, it risks becoming a surveillance instrument that adds anxiety and strips dignity from a person who cannot fully advocate for themselves.

### The Alzheimer's context — ambiguous loss

Alzheimer's dementia produces what Dr Pauline Boss first named "ambiguous loss" — the person is physically present but increasingly psychologically absent. Boss describes it as a paradoxical "here, but not here" situation that creates a state of "frozen grief" in carers, because closure is impossible while the person is alive. This form of grief is qualitatively different from bereavement: carers cannot grieve fully while the person is living, but they are already grieving.

The Alzheimer Society of Canada's clinical literature on ambiguous loss identifies several features of carer psychology that are directly relevant to Care Mode:

- Carers experience **anticipatory grief** for every capability loss, every personality change, every moment of non-recognition. The grief is accumulative and continuous.
- Carers often feel guilt about their own desire for "closure," which they cannot have. This guilt can manifest as hypervigilance — the need to monitor, know, control — precisely because the disease is otherwise uncontrollable.
- The **safety monitoring instinct** in carers is therefore not simply surveillance — it is a grief response. Wanting to know where your mother is at 3am is often about fear of losing her suddenly, not about control.

This matters for design because **Care Mode will be adopted primarily by people in a grief state**, not by emotionally neutral rational actors. The product needs to design for that emotional context, not against it.

### What dignity-preserving design looks like in this context

**1. The monitored person must remain legible as a person, not a data source.**

Every interface element in Care Mode should treat the monitored person as an individual with preferences, history, and remaining agency — not as a subject of a monitoring system. Displaying their name prominently, using warm language about their activities, and framing alerts in terms of their wellbeing rather than their location ("Mum appears to be at home and active" rather than "Device detected at home coordinates") preserves dignity in the data presentation layer.

**2. Alerts should be framed in safety terms, not surveillance terms.**

"Your mum hasn't left the house today — she may appreciate a call" is dignity-preserving. "Location unchanged for 18 hours" is not. The underlying data is identical; the framing is everything.

**3. The monitored person should have a visible, accessible record of what is being shared.**

Even if they cannot recall their consent moment, the monitored person should be able to see — in plain language, not legal text — what the system is doing and who can see it. This serves two purposes: it respects their remaining agency, and it creates an observable record that would catch a carer misrepresenting the scope of monitoring.

**4. The system should actively surface capability windows.**

Alzheimer's is not a uniform decline. People with Alzheimer's often have periods of lucidity in the mid-stages. Care Mode should be designed to surface these — for instance, by detecting unusual activity at times when the person is typically inactive, which may indicate a lucid moment. The product could flag this as an opportunity for a meaningful call, not just as a data anomaly.

---

### Consent and diminishing capacity

This is the most ethically charged dimension of Care Mode. The architecture assumes consent given at a point of full capacity, but Alzheimer's is a disease of progressive capacity loss. The consent given when the person was fully capable may not map accurately onto the experience of a person who no longer remembers consenting.

The research on dementia and consent capacity (Alzheimer's Society UK; Psychiatric Times) identifies a spectrum of decision-making capacity that does not collapse suddenly — it erodes gradually. A person who can no longer recall consenting to monitoring may still have preferences about it: they may express discomfort, fear, or resistance that should be treated as meaningful.

**Design safeguards the product must implement:**

**i. Periodic re-confirmation prompts, designed for residual capacity.**

At regular intervals (quarterly is a reasonable default), the system should present the monitored person — in simple language, large text, and without pressure — with a plain-language summary of what Care Mode does, who can see the information, and how to turn it off. This is not a legal formality; it is an ongoing attempt to include the person in their own care arrangement to whatever degree they can participate.

**ii. An accessible, low-friction revocation mechanism.**

The product must offer a revocation pathway that requires no password, no account login, and no memory of previous decisions. The monitored person should be able to express "I don't want this" through a simple, dignified interface — and that expression should immediately notify the caring family member and initiate a review process. The review does not mean the monitoring automatically stops (if the person lacks capacity, their expressed preference may need to be balanced against safety), but it must be visible and documented.

**iii. Capacity milestones — not binary, not automated.**

The product should not attempt to assess capacity — this is a clinical function that belongs to medical professionals. What it can do is create "capacity milestone" events: structured moments where carers are asked "Has your relative's condition changed significantly since you set up Care Mode? If so, we recommend reviewing the arrangement with their GP." This is not an assessment; it is a prompt to seek one.

**iv. The carer must not be the sole actor.**

The consent architecture should require at minimum one additional witness beyond the caring PoA holder — a second family member, a GP, or a nominated independent contact — who is notified when Care Mode is established and when it is modified. This provides a structural check against unilateral decisions by a single carer.

---

### Coercion risk and safeguards

Power of Attorney relationships are not always benign. Research on elder financial abuse (NCBI, DOJ Elder Justice Initiative) consistently finds that adult children are the most frequent perpetrators of elder financial exploitation — in nearly 40% of reported cases. PoA status is described by elder law practitioners as "the most common tool used to commit financial exploitation." The same dynamics apply to monitoring.

A coercive family member who has obtained PoA under pressure, or who obtained it before the person's capacity was fully impaired, could use Care Mode not to protect their relative but to monitor and control them — restricting their movements, isolating them from other contacts, or using location data to prevent contact with other family members.

**The product cannot prevent all misuse — but it can make misuse harder:**

**i. Require independent witness at setup.**

As above: a second person who is not the PoA holder should receive a notification when Care Mode is enabled. This witness should be able to view the consent record and the monitoring scope. Their presence in the system makes covert setup significantly harder.

**ii. Multi-member visibility — the monitored person's data should not be exclusive to the PoA holder.**

Where the monitored person has other close family members (siblings of the carer, etc.), the product should allow the monitored person to designate additional people who can view Care Mode status — not the monitoring data, but the fact that monitoring is active, its scope, and who enabled it. This transparency check makes it very difficult for a single family member to conduct covert surveillance under the guise of Care Mode.

**iii. Alert on scope expansion.**

If a carer attempts to expand the scope of monitoring (e.g., from location-only to content access), this should trigger a re-consent process, not simply be executed. Every expansion of Care Mode scope is a new consent event.

**iv. Anomaly flagging — social isolation patterns.**

The product might consider flagging patterns consistent with isolation: if the monitored person's social contacts (calls, visits detected through device activity) decrease sharply after Care Mode is enabled by a single carer, this is a potential indicator of controlling behaviour. This is a high-engineering-complexity feature, but even a simple flag — "Your relative has not had any visitors in the past 14 days — you may wish to check in with other family members" — directed to the independent witness, not only the PoA holder, creates a meaningful safeguard.

**v. Emergency exit — direct route to external support.**

The monitored person's interface should include a clearly visible, always-accessible link to external support: a national elder care helpline or equivalent. This must be accessible without going through any family member's interface.

---

### The monitored person's experience

The research on dementia and surveillance (PMC 2025, MDPI Sensors) notes a paradox: more obtrusive monitoring is more likely to produce anxiety and stigma, but less obtrusive monitoring (ambient sensors) creates a different risk — the monitored person forgets they are being monitored, so they cannot exercise consent or revocation in practice.

For Alzheimer's patients, the psychological experience of knowing they are monitored is likely to vary significantly across the disease arc:

**Early-to-mid stage (retained awareness):** The person knows they consented, may recall it, and can exercise preferences about it. The primary risk in this stage is **anxiety amplification** — knowing that every movement is tracked may increase the person's anxiety about making "mistakes," leaving the house, or forgetting a routine. Design must ensure Care Mode does not feel like a performance assessment. Framing matters: "Your family can see you're safe" is different from "Your location is being tracked."

**Mid-to-late stage (impaired awareness):** The person may no longer remember consenting or understand what the monitoring involves. In this stage, the psychological experience depends less on the design of the interface (which the person may not access) and more on how family members talk about it. The product cannot control this directly, but its carer interface should model respectful language: "Mum seems to be at home" not "Subject's device is stationary."

**Late stage (severe impairment):** Awareness of monitoring is minimal. Safety monitoring at this stage is closer to standard care technology (fall detectors, wander alarms) and is less fraught ethically, though the data governance questions remain.

**The design recommendation across all stages:** Make the monitored person's experience feel like they are supported, not watched. The emotional difference is significant even if the technical implementation is identical.

---

## Experience segment — psychological considerations

The Experience segment (chained capsules, competitive delivery, expiry-as-death) is a fundamentally different product mode from the rest of Heirlooms. It is gamified, commercial, and designed to create tension and engagement. This is not inherently harmful — but it creates a genuine psychological risk when it shares a platform with the grief-aware, solemn use cases.

### Competitive delivery

Making content access contingent on being the first to solve a puzzle introduces several psychological dynamics that the product should understand before proceeding:

**What the mechanic creates:**
- **Urgency and activation:** Players feel a real time pressure that drives engagement. This is the mechanic's appeal — it creates genuine stakes.
- **Social comparison:** In a competitive frame, other participants are not collaborators in a meaningful experience; they are obstacles. This is fine for an ARG about a brand campaign. It is psychologically inappropriate in a product context where other participants may be grieving family members.
- **Winner's elation and loser's grief:** Research on competitive game psychology (Journal of Sport Psychology; Elite Performance Psychology) consistently finds that losing triggers sadness, anxiety, reduced self-esteem, and in some cases PTSD-adjacent responses in high-investment players. In a grief-adjacent product context, these responses will be amplified.

**The core risk:** If competitive delivery mechanics are applied to real posthumous content — a family member's letter, a personal memory — the losing family member has not simply lost a game. They have been structurally excluded from a final communication from someone they loved. This is a severe and potentially lasting harm. **Competitive delivery must never be available as a mechanic for genuinely personal posthumous content.** It should be constrained to explicitly commercial and non-personal contexts (brand ARGs, fiction, marketing campaigns).

### Expiry-as-death for content

The "nobody solves it, the capsule is never delivered" mechanic is psychologically complex.

**For the sender:** If a person creates content they know will expire unseen if no one solves the puzzle, they are making a deliberate aesthetic choice — the puzzle is part of the meaning. In a purely commercial or artistic context, this is a legitimate creative decision. For a person creating content in anticipation of their own death, it introduces a disturbing element: their final communication to the world may simply not arrive because no one was clever enough. This is not a comfortable thought for someone facing mortality. **Expiry-as-death mechanics should not be available to death-conditioned personal capsules.** The product should enforce this at the interface level, not just policy.

**For participants who didn't solve it:** The research on grief and failure is clear — unresolved losses create persisting psychological distress. A participant who "failed" to solve a puzzle in time and therefore never receives the content experiences a compound loss: the original sense of beingness in the game plus a genuine deprivation of content they believed would be meaningful to them. If that content was positioned as emotionally significant, the psychological harm is real.

**The design recommendation:** Expiry mechanics are appropriate for purely commercial Experience capsules (ARG campaigns, serialised fiction) where participants understand they are in a designed game. They must come with a clear, upfront disclosure: "If no one solves this puzzle before [date], this capsule expires and its contents will never be accessible." This is not a pleasant disclosure — but it is the honest one. The product should not obscure it.

### ARG psychology and safeguards

ARG research (academia.edu, Wikipedia; empirical studies on player psychology) identifies several consistent features of intense ARG engagement:

**What ARGs do well:**
- Create genuine community and collaboration around a shared challenge
- Produce memorable, emotionally resonant experiences through narrative immersion
- Build skills (puzzle solving, research, teamwork) in the context of meaningful engagement

**What ARGs do badly:**
- The blurring of the magic circle (the boundary between game and real life) can become psychologically destabilising for some players. ARGs that permeate everyday spaces — using real-world locations, real-sounding communications, or real social networks — can produce what researchers call "reality leakage"
- Intense ARG communities have shown patterns of collective obsession that can consume participants' daily lives and relationships
- The post-game psychological experience — "the ARG is over, my community is gone" — can produce a form of social grief that is significant in intensity

**Safeguards for Heirlooms as an ARG host:**

**i. Clear context labelling.** Every Experience segment capsule must carry an unambiguous label — at the point of entry, not buried in settings — stating that the participant is entering a structured game experience. "This is a designed experience. The puzzle mechanics, expiry rules, and competitive elements are part of the game." This is the ARG equivalent of informed consent.

**ii. No involuntary participants.** ARGs that involve real-world contact (sending physical objects, using real phone numbers, contacting people who haven't opted in) risk drawing non-consenting people into the game frame. Heirlooms must prohibit Experience capsules from using delivery mechanisms that could reach people who haven't explicitly opted into a specific Experience.

**iii. Post-game support.** When an Experience campaign concludes — whether by a winner solving it or by expiry — the platform should send all participants a closing notification that explains what the campaign was, what the content was (if appropriate to disclose), and provides any information needed for emotional closure. The research finding that ARGs typically lack debriefing is a risk the product can directly mitigate.

**iv. Structural separation.** Experience segment capsules must be architecturally and visually distinct from personal and posthumous capsules within the product. A person in grief should never inadvertently enter an ARG mechanic. The platforms can share infrastructure but should present as meaningfully different experiences.

---

## Friend-tester reciprocity

The friend-tester model — free storage in exchange for real-world testing — activates a well-documented psychological dynamic: reciprocity.

The research on reciprocity and disclosure (Altman & Taylor's social penetration theory; Jiang, Bazarova & Hancock, 2013; Frontiers in Psychology, 2026) consistently finds that when a person receives something of value from another party, they feel a social obligation to give something of equivalent value in return. In the context of intimate digital content, this dynamic creates a specific risk: **the friend-tester may disclose more intimate content than they would otherwise, because the "free storage" has created a felt obligation to give the platform something real and valuable in return**.

This is not a conscious or cynical trade — it is an automatic social-psychological response that operates below the level of deliberate decision-making. The friend-tester may genuinely believe they are making a free choice to share intimate memories. The reciprocity dynamic means that choice is influenced by the exchange context in ways they may not be aware of.

**The specific risks:**

**1. Content depth inflation.** Friends may share more intimate, sensitive, or irreplaceable content than they would with a neutral storage product, because the exchange relationship signals that the product is trusted and that their content is "worth" something. If anything goes wrong — a security incident, a product shutdown, a relationship change with Bret — the depth of what they shared amplifies the harm.

**2. Power asymmetry.** The exchange is between a friend and the person building the platform. This is not a neutral commercial transaction — it is a relationship transaction. The social cost of withdrawing from the arrangement (pulling content, asking for deletion) is much higher than it would be with a commercial service, because doing so implies distrust of the relationship, not just the product. Friends may continue sharing intimate content even if they have doubts, because the exit cost is relational, not just technical.

**3. "Free" is not free.** Research on digital privacy and data exchange (extensively documented in the GDPR policy literature, and in academic privacy psychology) finds that users systematically undervalue their personal data in exchange transactions. The friend-tester believes they are getting a good deal (free storage). They are unlikely to assess their intimate memories at a price that reflects what they would actually demand in a fully informed negotiation.

**What does research on reciprocity and digital trust say?**

The 2026 Frontiers in Psychology study on disclosure decisions finds that reciprocity norms are significantly stronger in relationships with perceived warmth and prior trust. This makes the friend-tester context psychologically higher-risk than a public beta, not lower-risk: the existing friendship relationship amplifies the reciprocity obligation the friends feel to share generously.

The appropriate design response is transparency and explicit re-framing of the exchange:

**i. Name the exchange clearly.** Friends should be told explicitly: "You are an early tester. We are offering free storage in exchange for your feedback and for helping us understand how the product works in practice. There is no expectation that you share anything other than what you would want to store regardless. The product will be better because of what you test, not because of what you share." This breaks the implicit reciprocity framing that "something valuable was given to me, so I should give something valuable back."

**ii. Set explicit data rights expectations.** Friends should know, before they share anything intimate, exactly what happens to their data if the product pivots, if the friendship changes, or if they want to delete everything. A written, plain-language data rights statement — not a privacy policy, but a friend-level commitment — should precede any content sharing.

**iii. Offer structured, low-stakes testing tasks.** Rather than "use the product naturally and share your memories," offer specific testing tasks that don't require intimate content: "Upload three test photos from your camera roll and tell us if the interface feels right." This gives friends a way to participate meaningfully without the reciprocity dynamic pulling them toward intimacy.

---

## Design recommendations

**1. Dual-voice design system.** Develop two register voices: a light, warm "time-capsule" voice for acquisition and non-grief contexts, and a grave, witnessing voice for depth-of-use moments (capsule sealing, executor unlock, vault handover). Switch between them based on context signals, not user demographics.

**2. Context-aware tone at capsule creation.** At capsule creation, ask a simple, optional question: "Is this capsule for a specific moment, or for whenever the time comes?" This single piece of self-report allows the product to adjust its language register for that capsule's entire lifecycle without pathologising or assuming.

**3. Care Mode witness requirement.** Require a second named person — independent of the PoA holder — to be notified when Care Mode is established. This person should receive a copy of the consent record and have read access to Care Mode status (not monitoring data). This is a structural safeguard against coercive setup.

**4. Care Mode periodic re-confirmation.** Every 90 days, prompt the monitored person (if they retain any interface access) with a simple, low-pressure summary of what Care Mode does and how to change it. Log these prompts and their outcomes.

**5. Revocation must be simple and accessible.** The monitored person's interface should have a prominent, always-visible "I want to change what my family can see" button. This triggers a review, not an immediate shutdown — but the trigger must be zero-friction.

**6. Experience segment structural separation.** Experience capsules must be architecturally and visually distinct from personal capsules. No user in a grief context should inadvertently enter an ARG mechanic.

**7. Competitive mechanics — whitelist-only.** "First solver wins" delivery should only be available to capsules that are explicitly flagged as commercial Experience campaigns, not to any capsule touching personal or posthumous content. This is a product constraint, not just a policy.

**8. Expiry disclosure.** Any capsule with expiry-as-death mechanics must display a clear, non-dismissable disclosure at creation time and at the moment of participant entry: "If no one solves this puzzle by [date], this capsule will expire and its contents will be permanently inaccessible."

**9. ARG debriefing.** When an Experience campaign concludes, send all participants a closing notification explaining the campaign outcome and providing emotional closure information.

**10. Friend-tester re-framing.** Restructure the friend-tester conversation to explicitly name the exchange, set data rights expectations in plain language, and offer low-intimacy testing tasks alongside natural use.

---

## Red lines (things the product must not do)

These are not preferences or design recommendations — they are absolute limits. Crossing any of them creates a significant risk of serious psychological harm to real people.

**1. Competitive delivery mechanics must never be applied to genuinely personal posthumous content.**  
Making family members compete for access to a dead relative's final message is not a design decision — it is a harm. The product must enforce this at the architecture level, not rely on creators' discretion.

**2. Care Mode must never be established without independent witness notification.**  
A single PoA holder setting up unobserved monitoring of a person with diminishing capacity is structurally indistinguishable from coercive surveillance. No exceptions.

**3. The monitored person's revocation pathway must never require assistance from the monitoring family member.**  
If the only way to turn off Care Mode is to ask the carer to do it, the product has built a surveillance system, not a safety system. The revocation pathway must be accessible to the monitored person directly, independently.

**4. Expiry-as-death mechanics must never be applied to death-conditioned personal capsules.**  
A person creating content in anticipation of their own death must never be put in the position where their final message might simply not be delivered because no one solved a puzzle. This is not a UX error — it is a moral error.

**5. The product must not use the word "solved" in any context relating to capsule delivery to bereaved people.**  
Language borrowed from puzzle mechanics ("solved," "unlocked," "won") in a grief context is deeply inappropriate. The language of delivery to bereaved recipients must be drawn from the product's witnessing register, not its game register.

**6. Friend-testers must not be placed in a reciprocity dynamic that implicitly pressures intimate disclosure.**  
The friend-tester arrangement must be structured so that friends understand clearly that their value to the product is their feedback and testing, not the intimacy or depth of the content they share.

**7. The platform must not ever present Care Mode as "peace of mind" to the monitored person.**  
"Peace of mind" is the benefit for the carer. For the monitored person, knowing they are monitored may produce the opposite. Marketing language about Care Mode must be audience-specific: carers hear about peace of mind; the monitored person hears about safety and support.

---

## PA Summary

### Can Care Mode be built without causing psychological harm?

**Yes — with conditions.**

Care Mode is psychologically sound and potentially beneficial when:
- Independent witness notification is a hard requirement at setup (not optional)
- The monitored person retains a direct, zero-friction revocation pathway
- Periodic re-confirmation prompts are built into the product cadence (every 90 days minimum)
- Monitoring data is framed in dignity-preserving language throughout the carer interface
- Scope expansion requires a new consent event
- An accessible external support link is always visible in the monitored person's interface

Without these conditions, Care Mode becomes structurally coercive regardless of the good intentions of the family members using it. The product architecture must enforce these safeguards — policy and terms of service are not sufficient.

### Key design safeguards the product must implement

1. Dual-voice design system (light/witnessing registers, context-switched)
2. Care Mode independent witness requirement — hard architecture constraint
3. Care Mode 90-day re-confirmation prompts
4. Zero-friction revocation for monitored person
5. Competitive mechanics whitelisted to commercial Experience capsules only — hard architecture constraint
6. Expiry-as-death excluded from death-conditioned personal capsules — hard architecture constraint
7. ARG debriefing notifications on campaign closure
8. Friend-tester re-framing: named exchange, plain-language data rights, low-intimacy testing tasks

### Directions the Psychologist recommends avoiding

**Proceed with caution, not avoidance:**
- The "happy memories" reframe is fine at acquisition; ensure the product does not maintain that register through the full depth-of-use lifecycle
- ARG mechanics on Heirlooms are a legitimate commercial direction, but must be structurally isolated from personal/grief use cases

**Avoid entirely:**
- Competitive mechanics on personal posthumous content
- Expiry-as-death on death-conditioned personal capsules
- Single-actor Care Mode setup (no independent witness)
- Implicit reciprocity framing in the friend-tester relationship

### Handoffs needed to other personas

**Legal:**
- LPA and Care Mode consent architecture requires legal review, particularly under UK Mental Capacity Act 2005. The consent-given-before-capacity-loss model needs an opinion on its legal robustness and on what documentation Heirlooms should hold.
- GDPR implications of Care Mode monitoring data (special category — health-related behaviour data of a person with diminishing capacity) require explicit legal assessment.
- Friend-tester data rights commitments should be formalised in a legal document, not just a friend-level conversation.

**Philosopher:**
- The posthumous consent question (a person consented while they had capacity — does that consent persist ethically as capacity diminishes?) is precisely the kind of long-horizon ethics question the Philosopher should address.
- The "expiry-as-death" mechanic raises questions about the moral status of content that was created with communicative intent but never delivered. This is worth a philosophical treatment.

**Marketing Director:**
- The dual-voice design system is a brand architecture question as much as a UX one. The Marketing Director needs to be aware that the "happy memories" register is acquisition-only, and that a secondary witnessing register exists for depth-of-use moments.
- Care Mode marketing must be strictly audience-specific: carer-facing and monitored-person-facing language must never be swapped. This should be documented in brand guidelines.
- The friend-tester re-framing (explicit exchange, low-intimacy tasks) needs to be implemented in how Bret talks to and onboards those friends, not just in product UX.
