# PHI-002 — What a Capsule Is: Consent, Futurity, and the Key Ceremony

**Author:** Philosopher  
**Date:** 2026-05-16  
**Status:** Final  
**For:** Technical Author — TAU-001 Part 1: "What a capsule is"

---

## A promise made across time

A capsule is not, at its core, a piece of software. It is an act of will directed at a future the sender cannot fully know.

When someone seals a capsule in Heirlooms — a birthday message for a child not yet born, a letter to a friend to be opened at fifty, words meant only for someone who may be reading them after the sender is gone — they are doing something philosophically unusual. They are making a promise to a future moment, knowing they cannot enforce it. They are giving consent on behalf of their future self, knowing that self may no longer exist. They are directing an act of disclosure to a recipient who has not yet agreed to receive it, and cannot be asked.

This is what distinguishes a capsule from a message. A message is an act of communication. A capsule is an act of trust — trust that time will be crossed, that intentions will survive intact, that the delivery will find its person at the right moment, under the right conditions.

The philosophical tradition has a word for this kind of commitment: a *promissory obligation across time*. It shares structure with a will, with an advance directive, with the letter a soldier writes before combat and leaves unsealed on the desk. What makes it distinctive here is that the capsule, once sealed, is genuinely beyond its creator's control. The sender cannot change their mind by breaking the wax. The seal is cryptographic. It is, in a meaningful sense, irrevocable.

---

## The key ceremony as a symbolic act

*[Technical Author: the following section describes what the cryptographic process means. The technical details of how it works — DEK_client, DEK_tlock, the XOR split, the tlock provider interface — follow in Part 2.]*

When a user seals a capsule, the underlying cryptographic operation does something that deserves to be understood not only technically, but symbolically. The content is encrypted with a key that is itself split in two. One half is sealed by a time-lock: it is mathematically inaccessible until the moment the sender chose, enforced by a public randomness beacon no single party controls. The other half is held by the user's device, never transmitted to the server.

The consequence is precise and important: **Heirlooms cannot read your capsule, ever.** This is not a policy. It is not a promise that the company chooses to honour and could choose to break. It is a cryptographic fact. The server holds a piece of key material that is one half of an XOR pair — and XOR with one half tells you nothing about the whole. The server is not choosing not to look. It is structurally unable to see.

This distinction carries significant ethical weight.

There is a long tradition of institutions that hold sensitive material and promise not to inspect it. Lawyers hold privileged communications. Banks hold safe deposit contents. Mail carriers transmit sealed envelopes. In each case, the institution's restraint is a matter of professional ethics, law, and policy — all real, but all revocable. A policy can change. A law can be amended. Pressure can be applied, legally or otherwise, and access obtained.

Cryptographic blindness is different. It does not ask anyone to exercise restraint. It does not create a rule that could be broken. It eliminates the question. When the server holds half a key to a locked box and the other half genuinely does not exist on any server anywhere, no subpoena, no security breach, no change of corporate ownership, and no policy revision can give the server what it does not have.

This transfers the moral weight of the capsule entirely to the sender and the recipient. Heirlooms is not a guardian who could betray the trust. It is more like a time-locked vault wall: present, structural, and indifferent to the contents in the most complete sense possible.

The obligation this creates for Heirlooms is different from the obligation of a confidentiality-bound custodian. Heirlooms cannot simply be trusted not to look. What it must be trusted to do is maintain the infrastructure through which the key halves will eventually meet — to be present and operational at the moment the time-lock opens, to deliver the server-side component faithfully, and to ensure the recipient can reach what was sealed for them. The ethical duty is not secrecy (the cryptography handles that) but continuity and fidelity of delivery.

---

## Futurity and consent

The person who seals a capsule today may not be alive when it is delivered. This creates what philosophers call the *problem of posthumous consent*: can consent, given by a living person, be morally binding on events that occur after death? And can it be binding on recipients who never agreed to receive anything?

In PHI-001, I examined this question in the context of conditional delivery and Care Mode. The conclusion there, which applies here with equal force, is that anticipatory consent is valid in principle — but its ethical weight depends entirely on how well the consenting person understood what they were consenting to, and whether their instructions genuinely represented their future interests rather than only their present preferences.

For capsules, the weight of posthumous consent falls in two directions.

First, toward the sender's own instructions. When someone seals a capsule with conditions — "deliver this on my daughter's eighteenth birthday, not before" — they are asserting, from the past, a claim about what the future should look like. That claim deserves respect, but it is not absolute. A sealed capsule is not a command. It is an expression of intent by a person who could not know what circumstances would attend the delivery. The platform's role is to honour the instructions faithfully; but the recipient retains the right not to open what was sent to them. Consent to send does not override the autonomy of the person receiving.

Second, toward recipients who are strangers to the platform. A future recipient has not agreed to any terms of service with Heirlooms. They may not know the platform exists until a capsule arrives. The ethical basis for the platform's obligations to them is not contractual; it is grounded in the nature of the act. Heirlooms accepted the capsule on their behalf. That acceptance creates obligations: to deliver faithfully, to present the content with appropriate care, and to give the recipient the ability to decline. The sender's intent does not override the recipient's right not to receive.

There is no clean resolution to the tension between the sender's wishes and the recipient's present autonomy. It is honest to say so. What the platform can do is hold both with equal seriousness: honour the sender's instructions as closely as possible, while ensuring the recipient is genuinely free.

---

## Trust without surveillance

What does it mean to trust an institution that cannot see what it holds?

The usual model of custodial trust is relational: I trust the bank because I believe its employees are honest and its incentives are aligned with mine. That trust can be calibrated — I can investigate the bank's reputation, read its terms, or choose a different custodian. It is trust in people and institutions, with all the conditionality that implies.

Cryptographic server-blindness produces a different kind of trust. It is not trust in the institution's character. It is trust in a mathematical property. The question "can I trust Heirlooms not to read my capsule?" is replaced by "do I understand the cryptographic construction?" and, at a deeper level, "do I trust the mathematics of XOR and public randomness beacons?"

This is, in one sense, a stronger form of trust: it does not depend on the goodwill of any individual, cannot be defeated by a compromised employee, and persists through corporate changes. In another sense, it is a different kind of vulnerability: it depends on the implementation being correct, the time-lock provider remaining operational, and the platform surviving long enough to deliver what was sealed.

*[Technical Author: the implementation correctness properties are addressed in Part 2. The long-horizon operational risk — what happens if Heirlooms ceases to operate before a delivery date — is addressed in Part 4.]*

The philosophical significance is this: a custodian who holds something they cannot open has accepted a responsibility without acquiring the power that usually accompanies it. They cannot extract value from what they hold. They cannot be compelled to disclose what they do not have. But they are still bound — by the structural fact of holding the delivery infrastructure, by the continuity obligation, by the trust the sender placed in the promise that the capsule would reach its person.

Heirlooms' server-blindness does not eliminate moral responsibility. It concentrates it. The responsibility is not secrecy — the cryptography ensures that — but faithfulness. The custodian who cannot see what they hold must, precisely because they cannot see it, take their responsibility to deliver it seriously on the basis of the commitment alone, without the additional grounding of knowing what they are protecting.

That is a harder kind of trust to give, and a harder kind of responsibility to hold. It is also, arguably, the right kind for a platform that handles the things people most want to say to the people they love most, at the moments when saying them matters.

---

*PHI-002 — Philosopher, Heirlooms*
