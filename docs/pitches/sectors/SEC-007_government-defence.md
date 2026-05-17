# Sealed Orders, Renewed
## Time-guaranteed access control for government and defence

**Sector:** Government & Defence  
**Audience:** Government IT procurement, MoD Digital, Cabinet Office, defence primes  
**Version:** 1.0 — May 2026 | Pre-patent safe — may be distributed freely

---

In the age of sail, a naval commander departing on a sensitive mission would be handed a sealed envelope. Inside were his operational orders. He was under strict instruction not to open it until he reached a specified latitude, or until a specific date, or until a signal was received. The seal was physical evidence of tamper — wax impressed with an Admiralty signet. If it was broken too early, everyone would know.

The tradition persisted into the twentieth century. Special forces units have received mission parameters in sealed envelopes opened only at the point of insertion. The logic has never changed: there are things an operator must not know before the moment they need to know them. Premature disclosure changes behaviour, creates security vulnerabilities, and allows adversaries who have penetrated the command chain to learn what is coming.

That tradition has no credible digital equivalent. The digital systems used to manage classified operational briefings today are access-controlled, not time-controlled. An administrator can open a file before the operation begins. A compromised account can do the same. A misconfigured permission grants access that was never intended. The envelope is still there — but it is made of policy, not physics.

---

## The problem is not access control. It is time control.

Government and defence organisations understand information security. Zero Trust Architecture — the model now mandated by NCSC guidance and increasingly embedded in MoD Digital Strategy — removes the assumption that anything inside a perimeter can be trusted. Every access request is verified, every session is audited, every privilege is minimised.

Zero Trust is an excellent answer to the question: *who should be able to access this right now?*

It is not an answer to the question: *how do we ensure this cannot be accessed until a specific moment in the future, regardless of who holds credentials?*

Nor does it answer: *how do we ensure this can never be accessed after a certain date, regardless of what has been copied, retained, or forwarded?*

These are different problems. They require a different kind of guarantee.

The first is a confidentiality problem. The second is a time-binding problem. No version of access control — however sophisticated, however well-audited — can provide a time-binding guarantee, because access control works by restricting what credentialled users can do. A credentialled user with the right permissions, at the wrong moment, breaks the guarantee. An adversary who has compromised those credentials breaks it entirely.

---

## The classified material that should already be gone

The second problem is, in operational terms, the larger one — and the less visible.

Classified material has a natural lifecycle. Operational intelligence is sensitive before and during an operation. Its sensitivity may diminish rapidly once the operation concludes, the window for adversarial action closes, or the intelligence source is retired. Force dispositions, mission parameters, signals intelligence summaries, and communications plans all have a moment at which their classification has decayed: the information they contain is no longer operationally relevant, and the harm from disclosure has largely passed.

In practice, that material persists. It persists on classified networks. It persists in archives. It persists on removable media that was not returned, on devices that were not sanitised, in email threads that were not deleted. It persists because deletion requires someone to act — someone to remember, to prioritise, to authenticate, to execute the deletion, and to verify it completed.

When it eventually leaks — and over time, classified material does leak — the damage is compounded by how long it has been retained. Documents that would have been low-value intelligence two years after the operation are high-value historical records five years later. The case officer networks, operational methods, and human intelligence assets that would have been largely academic by year two become the subject of serious national security exposure by year ten.

This is not a new problem. The Chilcot Inquiry, the Snowden disclosures, and numerous smaller incidents have illustrated the same structural failure: classified material that should have been destroyed long ago was instead retained, and its retention created the conditions for a leak.

The standard answer — mandatory review cycles, declassification schedules, secure destruction procedures — fails at the operational level because it depends on human action at a future date, against the inertia of retention. It is a policy answer. Policy can be overridden, forgotten, or simply not executed under operational pressure.

---

## What a time-guaranteed classified document system provides

A sealed operations platform applies time-bounded guarantees to classified material at the point of creation, rather than relying on procedural compliance at the point of deletion.

When a document is created and sealed with a sunset date, what happens after that date is not a policy outcome. It is a mathematical one. The means of decryption — distributed across independent infrastructure that no single party controls — are permanently destroyed when the window closes. The ciphertext that remains on government servers is not locked away. It is genuinely unreadable. There is no key to compel, no administrator to coerce, no audit log to suppress.

For operational briefings, the same mechanism provides the lower bound: the means of decryption does not exist until the window opens. The document is sealed against a future moment. An adversary who compromises the server before the window opens recovers ciphertext and nothing else. There is no key to steal, because the key has not been generated. It will come into existence independently, at the moment defined when the document was created — through a process that nobody in the command chain can accelerate, suppress, or interfere with.

These two properties together are what the sealed orders tradition was trying to achieve physically. The lower bound enforces the embargo — the operational briefing cannot be opened before the mission begins. The upper bound enforces the sunset — the classified material that has passed its operational relevance is not merely scheduled for deletion. It is gone.

Throughout the lifecycle of the document, the platform operator stores only ciphertext. They cannot read the content. They cannot be compelled to decrypt it. They cannot comply with a disclosure request that relates to content they never had access to.

This architecture is compatible with existing Zero Trust environments and does not require replacement of classified network infrastructure. It operates at the document layer, not the network layer, and can be integrated with existing identity management and audit frameworks.

---

## Relevance to the current policy environment

NCSC guidance on cloud security and data handling increasingly distinguishes between systems where the provider has technical access to plaintext and systems where they do not. The latter is structurally preferable for sensitive government workloads.

MoD Digital Strategy has set explicit objectives around reducing legacy data risk and modernising classified information management. The persistent retention of operationally expired classified material is precisely the category of legacy data risk that strategy is designed to address — but the tools currently available to address it rely on procedural controls rather than technical enforcement.

The Cabinet Office Data Ethics Framework and the government's broader Zero Trust adoption programme both point in the same direction: move from policy-based assurance to technical assurance wherever the sensitivity of the data warrants it.

A sealed operations platform provides technical assurance at the point where policy has historically been weakest: the time dimension. Not who can access this, but when it can be accessed — and when it becomes permanently inaccessible.

---

## The patent urgency

The specific approach behind these guarantees — the construction and combination of time-bounded lower and upper properties without relying on a trusted escrow holder — has been checked carefully against published research and patent databases. It has not been protected.

UK patent law operates without a grace period. If the mechanism is described in sufficient technical detail before a priority date is filed, or if any other party files first, the right to patent is permanently and irrecoverably lost. This document describes only the effects of the platform — what it does and why it matters — and is safe to distribute. The mechanism behind those effects is what is being filed.

Defence prime contractors, government technology suppliers, and sovereign capability programmes considering this problem should be aware of where the patent process currently stands. The priority date is the clock that is running.

---

The naval tradition of sealed orders was never purely ceremonial. It was a practical answer to a real operational problem: some information must be protected against early disclosure by more than a lock, because locks can be picked. The physical seal was the best available answer for its time. It is no longer the best available answer.

The technology to provide a time-binding guarantee — before the window, nothing can be read; after it closes, nothing can ever be read again — exists now. It has not been applied to this problem at the programme level. The window to establish that is open.

For MoD Digital, Cabinet Office, and the intelligence community considering what classified information management looks like under a genuinely modern security architecture: this is the capability that has been missing.
