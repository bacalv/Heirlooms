# Present Only When Needed
## Time-guaranteed access for healthcare research data

**Sector:** Healthcare Biobanks & Research Data Access  
**Audience:** Biobank directors, research data governance leads, IRBs, NHS data access bodies  
**Version:** 1.0 — May 2026 | Pre-patent safe — may be distributed freely

---

A researcher at a university completes a study. The ethics approval has expired. The data access agreement has lapsed. The biobank sends a reminder about deletion. The researcher replies that the dataset has been deleted.

It has not. It sits on a university server, on a backup drive, possibly on a personal laptop that was last synced two years ago. Nobody is being dishonest, exactly. The researcher believes the working copy was removed. The biobank has a signed confirmation. The patient whose tissue and genetic information is in that dataset consented to a specific study, for a defined period. That period ended. The data did not.

This is not an edge case. It is the default outcome of every research data access arrangement in operation today.

---

## The gap at the centre of research data governance

Ethics committees and IRBs work hard to set appropriate access windows. Data access agreements specify them in detail. GDPR Article 17 creates a legal obligation to honour them. Biobank governance frameworks at UK Biobank, NIH dbGaP, and their counterparts worldwide all require researchers to destroy or return data at the end of their approved access period.

And then the access period ends, and nothing happens automatically. Because nothing can.

Deletion is a policy. An honour code. An administrative control that depends on researchers, their institutions, and their IT systems all acting correctly — and on nobody ever moving data to a location the checklist did not anticipate. The biobank has no mechanism to reach into a researcher's computing environment when the window closes and make the data stop working. No such mechanism has existed.

What remains is patient data — identifiable, sensitive, genetic — persisting indefinitely in research environments scattered across universities, hospitals, and institutes worldwide. Data that was consented to for one study, one window, one purpose. Data that participants reasonably expected to be present only for as long as science required it.

The patients trusted the system. The system has no way to keep that trust technically.

---

## What a sealed research archive does differently

A time-bounded data access platform changes this at a structural level.

When a biobank releases a dataset under an approved access window, the data is sealed with a time guarantee. During the approved window, researchers can access and work with the dataset normally. The seal is transparent during the period it is meant to be. When the access window closes, the means of decryption are permanently destroyed. Not archived. Not held in escrow pending a renewal request. Destroyed — as a technical fact, not a policy decision.

What this means in practice: a dataset downloaded on day one of an approved access window becomes permanently inaccessible on day one after the window closes. A local copy on a university server. A backup on a research institution's storage. A file that migrated to a collaborating institution in another country during the study. All of them become ciphertext with no recoverable key. Not because anyone went and deleted them. Because the means of reading them no longer exists anywhere.

Patient data that participants consented to use for a specific study window is present only during that window. When it ends, the data is not deleted — it is mathematically gone. A downloaded copy is indistinguishable from a server-side copy: equally inaccessible to anyone, forever.

Throughout the access period, the platform stores only encrypted content. The operator — the biobank, the access governance body — holds only ciphertext. There is no readable copy to be breached, accidentally disclosed, or subpoenaed by a jurisdiction the data was never intended to reach.

---

## Why this matters for GDPR compliance

GDPR Article 17 — the right to erasure — is routinely invoked in research contexts and routinely unenforceable once data has left controlled infrastructure. A data access agreement that requires deletion is a contract. Contracts bind willing parties who remain aware of their obligations. They do not reach into research computing environments and make data unreadable.

The consequence is a systemic compliance gap that data governance teams understand but cannot close with the tools currently available. Audit programmes, deletion attestations, and institutional policies all require the right people to act correctly. They do not provide a technical guarantee that the data is gone.

Cryptographic destruction provides that guarantee. When the access window expires, erasure is not a process that needs to be triggered, audited, and attested. It is a property of the architecture. The data ceases to be readable as a consequence of the window closing, not as a consequence of anyone acting on an obligation. Regulators, ethics boards, and data protection officers can verify the guarantee by examining the architecture, not by auditing a checklist.

For biobanks managing compliance obligations across hundreds of simultaneous data access agreements, in dozens of jurisdictions, with research institutions that vary enormously in their governance maturity, this is a qualitatively different kind of assurance than any audit programme can provide.

---

## The patient trust dimension

Research biobanks exist because patients consent to contribute. That consent is specific — to a study, to a purpose, to a window. When patients read that their data will be used for a defined period and then destroyed, they are making a decision based on a description of what will happen. The description is accurate; the mechanism to enforce it does not exist.

Patient and public involvement in biobank governance consistently identifies data retention and deletion as a significant concern. Participants want to know that their data will not persist beyond what they agreed to. Biobanks want to be able to tell them it will not. The honest answer today is that the biobank will do its best, will require deletion by contract, and will rely on researchers complying.

A time-bounded access platform makes a different answer possible: the data cannot persist beyond the approved window, because the means of reading it will not exist. This is not a stronger version of the same assurance. It is a different category of assurance entirely.

For biobanks engaged in public engagement and participant communication — and for ethics bodies advising participants on what their contribution entails — that distinction is meaningful.

---

## What this is not

This is not a data repository replacement, a biobank management system, or an access request processing platform. It is a structural access layer applied to the datasets that data access agreements govern: a way of making the time boundary in an ethics approval a technical property rather than an administrative one.

It does not require replacing existing biobank infrastructure. It requires treating the datasets that participants have entrusted to research — the assets that carry the greatest obligation — as deserving a guarantee commensurate with that obligation.

---

The ethics approval specifies a window because patient data should be present only during the window of scientific necessity. For the first time, it is possible for that specification to be technically true, not just administratively intended.

That is what this platform makes possible.
