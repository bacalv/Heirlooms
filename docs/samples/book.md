# Book Outline: End-to-End Encryption in Practice

*Key Management, Device Pairing, and Designing Systems for Long Horizons*

---

## Title

**End-to-End Encryption in Practice**
*Key Management, Device Pairing, and Designing Systems for Long Horizons*

Animal cover: probably an elephant (memory, longevity). Or a tortoise.

---

## Reader

A senior engineer who has decided their product needs E2EE and is staring at the abyss. Not a cryptographer — doesn't want to implement AES from scratch. Wants to make good architectural decisions, understand what they can't delegate to a library, and avoid the mistakes that make systems fail five years after they were built.

---

## The pitch

Most books on encryption stop at the cipher. The hard part isn't AES-GCM — it's everything around it: how a key moves from a user's brain to their device and then to their second device; what happens when they lose their phone; how to share an encrypted file with another user without sharing your key; how to structure a system so that algorithm deprecation in 2035 doesn't require a full migration. This book is a practitioner's guide to those problems, built around a complete worked example — a family media vault with E2EE across Android, web, and a server that never sees plaintext.

---

## Structure

---

## Part I — The Problem

---

### Chapter 1: What "end-to-end" actually means

**1.1 The marketing problem**
"End-to-end encrypted" appears on products that let the service provider read your data under certain conditions, products that encrypt only the transport layer, and products with genuine client-side encryption. This section establishes a precise working definition: E2EE means the service operator cannot read the plaintext of stored content, under any circumstances short of a compromised client.

**1.2 Building a threat model**
A threat model is a written-down answer to the question: who are you protecting against, and what are they capable of? This section introduces the threat modelling exercise, walks through the Heirlooms threat model (rogue employee, data breach, government compulsion, compromised server), and distinguishes between threats the architecture addresses and threats it explicitly doesn't — device compromise, malicious client code, cryptographic breaks at civilisational scale.

**1.3 The boundary: what the server can and cannot know**
In a well-designed E2EE system the server handles storage, routing, and metadata — but sees only ciphertext. This section maps the boundary precisely: what metadata is unavoidable (file sizes, upload timestamps, user identifiers), what metadata can be minimised, and what the server must never receive (plaintext content, raw keys, decrypted DEKs).

**1.4 Writing the honest claim**
The system's privacy guarantee should be expressible in plain language that a non-technical user can evaluate. This section works through the Heirlooms formulation — *"Heirlooms staff cannot read your photos, your messages, or your sealed capsules. The only people who can unlock your archive in your absence are the people you nominated"* — and shows how to derive the claim from the threat model rather than marketing instincts.

**1.5 Introducing the case study**
A tour of the Heirlooms system: what it is, the tech stack (Kotlin server, Android client, React web client, PostgreSQL), the milestones that introduced each piece of the E2EE architecture, and how the book will use it as a running worked example. Readers who don't use Kotlin or React can treat it as a platform-agnostic reference; the encoding and protocol decisions generalise.

---

### Chapter 2: The key management problem

**2.1 Why the cipher isn't the hard part**
AES-256-GCM is well-understood, well-tested, and available in every platform's standard library. Getting it wrong at the cipher level is rare; getting it wrong at the key management level is common. This section reframes the problem: choosing an encryption algorithm is a ten-minute decision; deciding where the key lives, how it moves, and what happens when it's lost is the months-long architectural work.

**2.2 The key lifecycle**
Keys are created, stored, used, rotated, and eventually deleted — and each stage has distinct security requirements. This section maps the full lifecycle and introduces the questions each stage forces: Who generates the key? What entropy source? Where is it stored at rest? What's the exposure surface during use? When should it be rotated, and can rotation happen without re-encrypting everything?

**2.3 The two-level hierarchy: master keys and DEKs**
Encrypting every file directly under a single master key creates two problems: rotating the master key requires re-encrypting all content, and sharing a single file requires sharing the master key. The two-level hierarchy — a master key that wraps per-content data encryption keys — solves both. This section explains the design and the tradeoffs: more keys to manage, but rotation and sharing become tractable operations.

**2.4 Where keys live: enclaves, keychains, and the browser**
The security of the key management system depends on where the highest-value key (the master key) is stored. This section surveys the options: Android Keystore (hardware-backed TEE, non-extractable), iOS Secure Enclave (similar, P-256 only), WebCrypto non-extractable keys (software-only, XSS risk), passphrase-derived keys, and server-side encrypted backups. Each offers different tradeoffs between security, recoverability, and cross-platform portability.

**2.5 The key loss problem**
In a system without server-side key copies, losing the master key means losing access to all encrypted content, permanently. This section frames key loss as a design problem that must be addressed before writing any crypto code — not as an afterthought. Introduces the three recovery paths Heirlooms uses (recovery phrase, passphrase-wrapped server backup, Shamir social recovery) and previews how each is implemented in later chapters.

---

### Chapter 3: Designing for failure from the start

**3.1 Failure modes taxonomy**
A structured inventory of the ways an E2EE system can fail: device loss (user loses phone), credential loss (passphrase forgotten), service shutdown (company goes bankrupt, server decommissioned), and algorithm deprecation (cryptographic primitive weakened or broken). Each failure mode has different probability, different severity, and different mitigation strategies. This section establishes the taxonomy; the rest of the chapter addresses each.

**3.2 Device loss and the recovery path**
Device loss is the most common failure mode. This section covers the design of a recovery path that doesn't require the server to hold a plaintext key: passphrase-derived keys, server-side encrypted blobs, and the recovery flow that reconstructs vault access from a new device. Includes the Heirlooms `setup-existing` and fresh-browser login flows as worked examples.

**3.3 Credential loss and the recovery phrase**
A passphrase can be forgotten; a 24-word BIP-39 recovery phrase, written on paper, cannot be silently lost without the user noticing. This section covers the design of a recovery phrase scheme, the UX of presenting it to users, the storage recommendations, and the key derivation path from phrase to master key. Also covers the harder question: what do you say to a user who didn't write it down?

**3.4 Crypto agility: surviving algorithm deprecation**
An E2EE system built today should be able to migrate to stronger cryptographic primitives in 2035 without requiring users to re-encrypt everything. This section introduces crypto agility as a design property: algorithm identifiers in every envelope, a re-wrap path that changes the wrapping key without touching the plaintext, and a migration strategy that can be executed lazily as users access their content.

**3.5 Versioned envelopes**
The versioned envelope is the concrete mechanism that implements crypto agility. This section specifies the envelope format: a version byte, algorithm identifiers for both the symmetric cipher and the key wrapping scheme, the nonce, the wrapped DEK, and the ciphertext. Explains why every field needs to be present even when there's currently only one version — retrofitting this is expensive; building it in is cheap.

**3.6 Service shutdown and the data portability question**
If the service shuts down, can users export their content? This section covers the design of a data export format that lets users decrypt their archive independently of the running service. The implications for key storage design: anything that lives only in a server-side HSM cannot be exported. Introduces the principle that key material must ultimately be derivable from something the user holds, not only from server-side state.

---

## Part II — The Cryptographic Architecture

---

### Chapter 4: Master keys and data encryption keys

**4.1 The per-content key principle**
Every piece of encrypted content — every photo, video, thumbnail, metadata blob — has its own unique data encryption key. This section explains why: it makes selective sharing possible (re-wrap one DEK, not the master key), limits the blast radius of a DEK compromise (one file, not the vault), and makes deletion cryptographically clean (discard the DEK, the content is unrecoverable).

**4.2 Key generation: entropy and determinism**
DEKs should be random and unique. This section covers the right sources of entropy on each platform (Android's `SecureRandom`, WebCrypto's `getRandomValues`, Java's `SecureRandom` on the server), the pitfall of deterministic key generation from content hashes, and why a DEK should never be reused across content items even if the content is identical.

**4.3 The master key and key derivation**
The master key is derived from a high-entropy seed using HKDF, producing separate keying material for different purposes (content encryption, authentication, metadata). This section covers the Heirlooms key derivation scheme: Argon2id from passphrase → 64-byte output → split to auth key + master key seed → HKDF for per-purpose keys. Explains why the authentication key and the encryption key must be separate.

**4.4 The envelope format in detail**
The complete byte layout of the Heirlooms envelope: version identifier, algorithm identifier for the wrapping scheme, algorithm identifier for the symmetric cipher, IV/nonce, the wrapped DEK, the ciphertext length, the ciphertext, and the authentication tag. Explains each field's purpose and the consequences of getting the layout wrong (most commonly: the auth tag covers the wrong bytes).

**4.5 Worked implementation: Android (Kotlin)**
Full implementation of DEK generation, content encryption, and envelope construction in Kotlin using the Android crypto APIs. Covers `KeyGenerator`, `Cipher`, the byte array layout, and the wrapper class (`VaultCrypto`) that Heirlooms uses to keep the low-level operations encapsulated. Includes the subtle differences between running on a device (hardware AES) and running in tests (JVM).

**4.6 Worked implementation: Web (JavaScript)**
The same operations using the WebCrypto API. Covers the async nature of WebCrypto (every operation returns a Promise), the `CryptoKey` object and its non-extractability property, ArrayBuffer handling, and the encoding to/from base64 for transport. Notes the places where the WebCrypto API makes things easier (key non-extractability is enforced by the runtime) and harder (no synchronous API anywhere).

**4.7 Testing cross-platform compatibility**
A ciphertext produced by the Android client must be decryptable by the web client and vice versa. This section covers the cross-platform test strategy Heirlooms uses: a set of known-good test vectors (plaintext, key, IV, expected ciphertext) verified against all three implementations (Android, web, server), run as part of CI. Covers the encoding mismatches that typically cause cross-platform failures before you have test vectors.

---

### Chapter 5: Device pairing and key wrapping

**5.1 The second-device problem**
A user's vault is accessible from their phone and their laptop. Both need the master key. The server cannot hold the master key in plaintext. How does device 2 get the key? This section frames the problem and introduces the two general approaches: server-mediated encrypted transfer, and direct device-to-device key exchange. Explains why Heirlooms uses the latter.

**5.2 P-256 ECDH: the mechanism**
Elliptic-curve Diffie-Hellman: two parties each have a keypair, they exchange public keys, and both independently derive the same shared secret. The shared secret is used as input to HKDF to derive a symmetric wrapping key. This section covers the mathematics at one page of depth — enough to understand what's happening and why it's secure, not enough to implement the curve arithmetic from scratch (you shouldn't).

**5.3 The pairing handshake**
The Heirlooms pairing protocol step by step: the new device generates an ephemeral P-256 keypair and encodes the public key as a QR code; the existing device scans it; the existing device wraps the master key under the derived shared secret and posts the wrapped blob to the server; the new device polls, retrieves the blob, and unwraps it. The server sees only ciphertext. Neither device ever sends the master key in plaintext.

**5.4 Ephemeral keys and why they matter**
The new device's keypair is ephemeral — generated for this pairing session, discarded immediately after. This section explains why: if the pairing private key were stored, an attacker who obtained it later could decrypt the pairing blob if they also intercepted the server traffic. Ephemeral keys provide forward secrecy for the pairing exchange.

**5.5 The Android Keystore and hardware binding**
On Android, long-lived device keypairs (used for the device's permanent registration, distinct from the pairing ephemeral) can be generated inside the hardware-backed Keystore, making the private key non-extractable even to the application. This section covers the `KeyPairGenerator` API, the `KeyGenParameterSpec` configuration for non-extractable P-256, and what "hardware-backed" actually means in terms of security properties.

**5.6 WebCrypto non-extractability**
The web platform doesn't have a hardware enclave in the same sense, but WebCrypto keys can be marked non-extractable: the raw key bytes are never exposed to JavaScript, only to the WebCrypto operations that use them. This section covers the `generateKey` API, the `extractable: false` parameter, storing `CryptoKey` objects in IndexedDB (they survive `structuredClone`), and the security properties and limitations compared to hardware binding.

**5.7 Cross-platform key encoding: the traps**
P-256 public keys have three common serialisation formats: uncompressed SEC1 (65 bytes, prefix 0x04), compressed SEC1 (33 bytes), and SubjectPublicKeyInfo (SPKI, the DER-encoded format used by WebCrypto). Android produces SEC1; WebCrypto expects SPKI. This section covers the transformations, the `sec1ToSpki` conversion function Heirlooms uses, and the class of bugs (silent decryption failures, usually) that result from getting this wrong.

---

### Chapter 6: Envelope versioning and crypto agility

**6.1 The case for building this now**
It's tempting to defer versioning until it's needed. This section argues against deferral: adding version bytes to an existing envelope format requires migrating all existing ciphertexts or maintaining two parallel code paths indefinitely. Adding them from the start costs two bytes per envelope and zero ongoing complexity. The argument is made concrete with a hypothetical: AES-256-GCM is weakened in 2031 and you need to migrate 10 million stored ciphertexts.

**6.2 What goes in the version header**
A versioned envelope header contains: an envelope format version (allows the entire interpretation of the following bytes to change), an asymmetric scheme identifier (identifies the key wrapping algorithm), and a symmetric scheme identifier (identifies the content encryption algorithm). This section explains each field and why all three need to be present even when there is currently only one value for each.

**6.3 Algorithm identifiers in practice**
Concrete identifier strings (or bytes) for the schemes Heirlooms uses: `p256-ecdh-hkdf-aes256gcm-v1` for the standard DEK wrapping envelope, `master-aes256gcm-v1` for passphrase-wrapped master key blobs. Explains the naming convention, the versioning within the identifier string, and the lookup table that maps identifiers to decryption code paths.

**6.4 The re-wrap path**
When an algorithm is deprecated, existing DEKs wrapped under the old scheme need to be migrated. The re-wrap path: decrypt the DEK using the old scheme (requires the master key), re-encrypt the DEK under the new scheme, store the new envelope. Critically: the content itself is not re-encrypted. This section covers the protocol, the migration trigger (version identifier in the envelope), and the atomicity requirements (the old envelope must not be deleted until the new one is confirmed written).

**6.5 Lazy vs eager migration**
Two migration strategies: eager (migrate all envelopes at once, possibly as a background job) and lazy (migrate an envelope when the user next accesses the content). This section covers the tradeoffs. Eager migration requires server-side re-wrap capability (or a client-side migration tool run on every device). Lazy migration is simpler but leaves old-format envelopes in the store indefinitely. Heirlooms's approach and why.

**6.6 The Heirlooms envelope format as a complete reference**
The full byte layout of every envelope variant used in the system, with field names, sizes, and notes on each. Presented as a reference table. Covers: the standard content envelope, the thumbnail envelope (which uses the same DEK as the content but different nonce), the passphrase-wrapped master key blob, and the pairing blob. Intended as a template readers can adapt.

---

### Chapter 7: Cross-platform E2EE

**7.1 The compatibility surface**
An E2EE system spanning multiple platforms has to get the same answer from different cryptographic implementations. This section maps the full compatibility surface for Heirlooms: Android encrypts, web decrypts; web encrypts, Android decrypts; server validates integrity but never decrypts. Lists the specific operations that must produce identical results across platforms and the encoding conventions that need to be pinned.

**7.2 Android: BouncyCastle, the Keystore, and JVM crypto**
Android's cryptographic runtime is layered: the Android Keystore for hardware-backed operations, the standard JCA/JCE providers (usually BouncyCastle under the hood) for software operations, and the `android.security.keystore` package bridging them. This section covers which operations belong in each layer, the subtle differences between Android's BouncyCastle and the upstream library, and the testing challenge: the Android Keystore APIs are stubs in JVM unit tests, requiring careful abstraction.

**7.3 WebCrypto: the API and its quirks**
WebCrypto is a well-designed API with a few sharp edges. This section covers: the fully asynchronous interface (and how to manage the resulting Promise chains), the key import/export format restrictions, the algorithm parameter objects, the ArrayBuffer type and the conversion utilities you'll write many times if you don't centralise them, and the browser support matrix for the specific algorithms Heirlooms uses.

**7.4 Server-side: processing without decrypting**
The server handles content in three ways: storing opaque ciphertext blobs in GCS without touching them, validating envelope integrity (checking that required fields are present and correctly formed without decrypting), and re-wrapping DEKs for sharing (wrapping an existing DEK under a new recipient's public key). This section covers each and the important constraint: the server never needs the master key.

**7.5 The encoding translation layer**
The central source of cross-platform bugs. This section documents every encoding convention that Heirlooms pins: base64 standard vs URL-safe, with/without padding, big-endian vs little-endian field lengths, the SEC1-to-SPKI conversion, JSON field naming conventions for key material. Presents the translation utilities and the unit tests that validate them.

**7.6 Building a cross-platform test suite**
A test strategy for a system where "it works" means "Android and web produce mutually decryptable ciphertexts." Covers: the test vector approach (known plaintext + key + nonce → expected ciphertext, tested on all platforms), the integration test that runs the full encrypt/decrypt round trip across platforms, and the CI configuration that runs all three test suites together so cross-platform regressions are caught before code is merged.

---

## Part III — Multi-User and Sharing

---

### Chapter 8: Multi-user key isolation

**8.1 The isolation invariant**
In a multi-user E2EE system, each user's content must be inaccessible to every other user — including the server operator. The isolation invariant: user B must not be able to retrieve or decrypt user A's content under any request pattern, and the server must enforce this. This section defines the invariant formally and explains what "server enforcement" means when the server never sees plaintext.

**8.2 Per-user key ownership**
Every user has their own master key, completely independent of every other user's master key. DEKs for content belong to the user who uploaded it. This section covers the schema implications (owner_user_id on uploads, per-user key storage), the auth model (session tokens scoped to a user, validated on every request), and the mapping from HTTP request to authorised user to authorised content.

**8.3 Server-side access control without seeing content**
The server enforces isolation by checking ownership on every read and write operation: does the authenticated user own this resource? This section covers the filter/middleware pattern for extracting the authenticated user ID from the session token and making it available to route handlers, the ownership check pattern on every content endpoint, and the test coverage required to be confident the enforcement is correct.

**8.4 404 not 403: the privacy-preserving response**
When user B requests a resource owned by user A, the correct response is 404 (not found), not 403 (forbidden). A 403 response confirms the resource exists, which is itself information leakage — a probing attacker can enumerate valid resource IDs. This section explains the principle, the implementation (a single access-checking function that returns 404 for both missing and unauthorised resources), and why this matters more than it initially seems.

**8.5 The isolation test suite**
Access control code is only trustworthy if it's tested by someone trying to break it. This section covers the isolation test pattern used in Heirlooms: create two users (Alice and Bob), create content owned by Alice, attempt access as Bob, verify 404 on reads and appropriate failures on writes. Estimates the scope: roughly 20–30 test cases across uploads, tags, plots, and capsules for a system of Heirlooms's scale.

**8.6 Backfilling isolation onto an existing schema**
Heirlooms started as a single-user system. This section covers the migration path: the Flyway migrations that added `owner_user_id` columns, the backfill that assigned existing content to the founding user, and the lessons learned about migration ordering (nullable columns first, backfill second, NOT NULL constraint third). Useful pattern for teams retrofitting multi-user isolation onto an existing product.

---

### Chapter 9: DEK re-wrapping for sharing

**9.1 The sharing problem**
User A wants to share a photo with user B. The photo is encrypted under a DEK that is wrapped under A's master key. A cannot share the photo by sending B the ciphertext alone — B cannot decrypt it. A cannot share the master key — that would give B access to everything. The solution is DEK re-wrapping: take the DEK, re-encrypt it under B's public key, and store the result alongside the original. B can unwrap the shared DEK using their private key and decrypt the photo.

**9.2 The re-wrap protocol step by step**
The complete sharing flow: A's client fetches B's sharing public key from the server (B uploaded it at vault setup); A's client unwraps the content's DEK from A's master key; A's client re-wraps the DEK under B's P-256 sharing public key using ECDH-HKDF-AES-GCM; A's client posts the wrapped DEK and a reference to the original content to the server; the server creates a recipient upload record linking the shared DEK to B's account.

**9.3 Key formats for sharing: the sharing keypair**
The sharing keypair is distinct from the device pairing keypair: it's a long-lived keypair specifically used for DEK re-wrapping, registered with the server so other users can encrypt to it. This section covers the keypair generation, the SPKI public key upload, the server storage model, and the access control rule (only friends can retrieve your sharing public key).

**9.4 The recipient's perspective**
When B opens the shared photo, their client fetches the recipient upload record, finds the wrapped DEK, and unwraps it using B's sharing private key. The content ciphertext is identical to what A's client produced. This section walks through the unwrap path, the `unwrapWithSharingKey` implementation, the envelope format for re-wrapped DEKs (`p256-ecdh-hkdf-aes256gcm-v1`), and the error cases (missing sharing key, corrupted wrapped DEK).

**9.5 The connection graph as a key graph**
The set of users who can receive re-wrapped DEKs from a given user is exactly the user's connection graph (friends list). This section explores the deeper structure: the friends relationship, the sharing key lookup, and the deletion guard (content cannot be hard-deleted from storage if a live shared reference to it exists, because the recipient's re-wrapped DEK would become invalid). The compost cleanup logic that checks for live sharing references before deleting GCS objects.

**9.6 What the server knows about shared content**
The server stores: the original ciphertext (opaque), the original wrapped DEK (opaque), the re-wrapped DEK (opaque), and the metadata linking them (uploader ID, recipient ID, `shared_from_upload_id`). It knows who shared with whom and when, but not what was shared. This section discusses the metadata privacy implications and the design choices that minimise what the server can infer.

---

### Chapter 10: Social recovery with Shamir secret sharing

**10.1 The mathematics in one page**
Shamir secret sharing encodes a secret S as a random polynomial of degree k-1 over a finite field, where S is the polynomial's constant term. Any k of the n evaluation points (shares) suffice to reconstruct the polynomial and recover S; fewer than k shares reveal nothing. This section covers the intuition, the threshold property, and why it's the right primitive for distributing a master key across trusted contacts.

**10.2 Threshold selection**
Why 3-of-5 rather than 2-of-3 or 5-of-7? This section works through the tradeoffs: a lower threshold is easier to reconstruct but easier to reconstruct without the user's consent; a higher threshold is more resistant to collusion but more fragile against trustee loss. Covers the practical guidance for different use cases and the default recommendation for a personal vault with a modest trust network.

**10.3 Generating and distributing shares**
The implementation of share generation: split the master key seed into n shares using a Shamir implementation, wrap each share under the corresponding trustee's public key, send each wrapped share to the server for the trustee to retrieve. This section covers the implementation, the UX of the share distribution flow (what does the user tell each trustee?), and the choice of Shamir library.

**10.4 The recovery flow**
When the user needs to reconstruct their master key from shares: notify the designated trustees, each trustee authenticates and approves the release of their share, the client collects k shares, reconstructs the polynomial, recovers the master key seed. This section covers the protocol in detail, the server's role (hold wrapped shares, mediate the approval flow), and the UX of the approval step from the trustee's perspective.

**10.5 Re-sharing when trustees change**
Trustees become unavailable — they leave the platform, die, become estranged. The user needs to replace a trustee without the master key being exposed. The re-sharing protocol: while the user still has access, generate a new set of shares, distribute to the new set of trustees, revoke the old shares. This section covers the implementation and the timing invariant: old shares must be revoked only after new shares are confirmed delivered.

**10.6 The trustee UX**
Shamir secret sharing requires explaining to non-technical people that they're holding "a piece of something" without telling them what it is or making them feel responsible for understanding it. This section covers the UX language, the trustee onboarding flow, the heartbeat notification ("Your Heirlooms recovery role is still active"), and the release-approval UX when a recovery event occurs.

**10.7 Testing recovery paths**
Recovery is the code path that runs least often and matters most. This section covers the test strategy: unit tests for the share generation and reconstruction mathematics, integration tests for the full distribute-and-recover round trip, and the specific test for re-sharing after trustee replacement. Notes the common failure modes: off-by-one in the threshold, share corruption, key format mismatch between share generation and reconstruction.

---

## Part IV — The Long Horizon

---

### Chapter 11: Five properties of a trustworthy custodian

**11.1 The custodian problem**
A vault designed for 50-year delivery windows requires key custody that outlasts the user. Who holds the key? The user's family? The company? A third-party service? This section frames the custodian problem precisely and explains why it requires a structured evaluation framework rather than an ad hoc choice.

**11.2 Longevity**
A custodian must credibly outlast what it's holding. For a digital vault this may be 80 years — a 30-year-old user, a capsule for a child to open at 18, the user lives to 90, the capsule continues past their death. This section examines what "credible longevity" means for different custodian types: individual humans (bounded by lifespan), institutions (bounded by organisational continuity), software services (historically short-lived), and cryptographic mechanisms (bounded by algorithmic longevity).

**11.3 Independence**
A custodian with a stake in the outcome cannot be trusted to execute the protocol faithfully. Recipients have an incentive to unlock early. The service operator has an incentive to satisfy government requests. Family members have incentives that may conflict with the user's intentions. This section covers the independence requirement, the specific conflict-of-interest cases to design against, and the structural separation that independence requires.

**11.4 Verifiability: the heartbeat primitive**
A custodian that silently lost a share eight years ago is worse than no custodian. The verifiability requirement: while the user is alive, they must be able to confirm that each custodian still holds a valid share and is operating correctly. This section introduces the proof-of-custody primitive — a regular cryptographic challenge/response that proves share possession without revealing the share — borrowed from Filecoin's storage verification model.

**11.5 Replaceability**
The user must be able to remove and replace any custodian without ever exposing the master key. This is what makes a trust network tendable over decades rather than a fixed configuration set in 2026 and never revisited. This section covers the replaceability requirement, the re-sharing protocol that implements it, and the invariant that must hold throughout: the master key never appears in plaintext outside the user's own devices.

**11.6 Auditability**
When a release event occurs decades in the future, there must be a paper trail that makes the release feel like an honourable handover rather than a technical glitch. This section covers the auditability requirement: what the audit log contains (sealed-at timestamp, release conditions, trustee identities, approval timestamps), why it matters for legal legitimacy, and how to design the log so it can be verified by parties who weren't present at sealing time.

**11.7 Evaluating any mechanism**
The five properties as an evaluation rubric. This section applies the rubric to five custodian types — family trustees, institutional trustees, software custodians, federated nodes, and cryptographic time-locks — showing which properties each satisfies and which it doesn't. The conclusion: no single mechanism satisfies all five, which implies the robust design diversifies across mechanisms.

---

### Chapter 12: Diversifying the trust network

**12.1 No single category is sufficient**
The failure modes of different custodian categories don't overlap: human lifespans, institutional acquisitions, software company bankruptcies, and cryptographic algorithm breaks are largely independent. This section makes the case for diversification: a network spanning categories is far more resilient than a network with five members of the same type.

**12.2 Family trustees**
The most intuitive custodian type: people the user trusts, each holding a wrapped share. This section covers strengths (high trust, zero infrastructure cost, intuitive), weaknesses (human lifespans cap at ~100 years, relationships change, family members can collude), and the right horizon (good for 5–30 years, insufficient for 50+). Covers the technical implementation: share wrapped under the trustee's Heirlooms account public key.

**12.3 The recipient-as-trustee trap**
Capsule recipients have an incentive to release the capsule early — they want to see what's inside. Naming a recipient as a trustee creates a conflict of interest the user may not recognise. This section covers the detection logic (the product should identify when a proposed trustee is also a capsule recipient and refuse gently), the UX language for the refusal, and the other safety rules a trust-network UI needs.

**12.4 Institutional trustees**
Law firms, notaries, national archives, probate registries. This section covers strengths (long legal continuity, court legitimacy), weaknesses (expensive, slow to act, staff turnover means internal protocols must be watertight), and the practical implementation (what a notary actually receives, how they authenticate to release a share, the SLA that needs to be negotiated). Notes the jurisdictional complexity: an institution that's reliable in the UK may not be accessible to a user's heirs in 2070 if circumstances change.

**12.5 The Foundation: a software custodian with a continuity mandate**
A separate legal entity — distinct from the product company — whose sole purpose is holding shares and executing the release protocol. This section covers the Foundation design: separate charter, separate infrastructure, endowment funding, outside board, and an orderly shutdown protocol that re-shares to other custodians before closing. The Long Now Foundation and the Internet Archive as cultural models.

**12.6 Federated nodes and the open-protocol move**
The most resilient long-term design: other organisations run custodian nodes that comply with an open protocol. Users choose which nodes hold their shares. The network heals itself when nodes fail. This section covers the bootstrapping problem (a federation needs members; the product can't wait for them), the design move available immediately (publish the protocol openly even when the product is the only node), and what share migration looks like in a federated network.

**12.7 Cryptographic time-locks**
A share locked until a specific date using a verifiable delay function or threshold beacon (drand) requires no human or institutional custodian. This section covers the mechanism, the maturity of the technology (drand has been running ~6 years as of writing, not the decades needed to establish long-horizon confidence), and the right role for time-locks: a secondary layer rather than a primary custodian, best as the backstop when other shares are unavailable.

**12.8 A default trust network**
A concrete recommendation for a typical user: a 3-of-5 scheme with two family trustees, one institutional trustee, one Foundation node, and one federated node or time-lock. Any two can fail without affecting recovery. This section works through the default, the configuration UI that helps a user build it, and the different defaults appropriate for different user profiles (age, family situation, geography).

---

### Chapter 13: Blockchain as one layer, not the load-bearing one

**13.1 The question**
"Will any blockchain last 30 years?" is the question this chapter addresses honestly rather than optimistically. It sets up the four senses in which a blockchain can be said to "last" and examines current candidates against them.

**13.2 Four senses of lasting**
A blockchain lasts in different ways: the ledger is still verifiable; active validators still produce blocks; the security model remains intact; transactions are still economically viable and tooling still works. You can have the first without the second (a frozen chain), the second without the third (a chain with degraded security incentives), or all of the above without the fourth (a chain where transactions cost $50 each). For a custodian, you need all four.

**13.3 Current candidates evaluated**
Bitcoin: strongest for raw longevity, simple design, no central foundation, deliberately limited programmability. Ethereum: more programmable, active development, multiple independent clients (a longevity property), real upgrade risk. Everything else: speculative at 10 years, never mind 30. This section assesses each against the four properties of lasting, without advocacy.

**13.4 The tooling problem**
The Bitcoin ledger from 2009 is still verifiable; the wallets people used in 2009 are mostly gone. Even if the underlying chain protocol survives, the surrounding tooling — wallets, RPC providers, SDKs, UIs — lives on much shorter timescales. This section covers the tooling problem and the design implication: any blockchain integration needs an abstraction layer that can swap out tooling implementations without requiring the underlying share to be re-issued.

**13.5 Design conclusions for blockchain integration**
Four conclusions: use blockchain as one share-holder among several, never as the trust root; use the most established chains for the simplest possible use cases; design for migration so that if a chain shows signs of trouble the share can be moved to a different custodian; plan for cryptographic migration generally (re-sharing can include a re-encryption step). The deeper principle: no single mechanism should be load-bearing.

**13.6 The general principle**
The blockchain analysis generalises to all single-mechanism thinking. This section distils the chapter's lesson: the 100-year promise cannot be made by any single technology, institution, or chain. It is made by the network, the protocol, and the diversity. The design assumes any given member will eventually fail, and builds accordingly. This is the insight that makes the whole trust-network architecture coherent.

---

### Chapter 14: When software is not enough

**14.1 The bankruptcy scenario**
A well-engineered E2EE system can still fail the 100-year promise if the company that runs the server goes bankrupt and the infrastructure is decommissioned. Users' content is inaccessible not because the cryptography failed but because the service that stored the ciphertext no longer exists. This section uses the bankruptcy scenario to establish the chapter's argument: some promises require institutional design, not only software design.

**14.2 What an institution provides that software cannot**
Legal continuity, external accountability, the ability to survive the departure of any individual founder or executive, a mandate that persists through ownership changes. This section covers what distinguishes an institution from a company and why the distinction matters for a 100-year custodian.

**14.3 The Foundation pattern**
A separate legal entity with a charter mandating the custodial function as its primary purpose, an endowment that funds operations independently of the product company's revenue, its own infrastructure and operations team, and a board including external experts with the explicit duty to keep it running regardless of the company's fate. This section covers the design in detail, including the orderly shutdown protocol (if the Foundation itself must wind down, it re-shares to other custodians first).

**14.4 Practical steps and costs**
What it actually takes to establish a Foundation: charity or non-profit registration (jurisdiction-dependent), the endowment quantum (rough estimate: £3–10M buys decades at conservative drawdown), the board composition, the infrastructure setup, the operational protocols that prevent any single staff member from being a single point of failure. Not a legal guide, but an engineering team's orientation to what's involved.

**14.5 When to start thinking about this**
Institution-building has long lead times, but the design implications show up earlier than the legal work. Key decisions made in the early architecture — whether the custodian protocol is open or proprietary, whether the server infrastructure is designed to be transferable, whether key material is ultimately derivable from user-held secrets — are harder to retrofit. This section identifies the decisions that need to be made with the institution in mind even before the institution is real.

**14.6 Cultural models and the long view**
The Long Now Foundation was established in 1996 to maintain the 10,000 Year Clock. The Internet Archive was established in 1996 to preserve the web. Both are still operating and have outlasted dozens of contemporaneous software companies. This section uses these models to make the Foundation pattern feel concrete and achievable rather than speculative, and to draw practical lessons about charter design, governance, and the relationship between the mission organisation and the commercial entity alongside it.

---

## Part V — The Product Dimension

---

### Chapter 15: E2EE and the user

**15.1 The honest constraint**
E2EE creates an asymmetry that most consumer products don't have: the service genuinely cannot help a user who loses their keys. This is a feature, not a bug, but it creates a UX problem with no fully satisfying solution. This section frames the constraint and establishes the principle for this chapter: the UX of an E2EE system must be honest about the constraint, not designed to conceal it.

**15.2 The recovery copy problem**
Telling a user that losing their keys means losing their data permanently — in onboarding copy, during passphrase setup, at the moment of recovery phrase generation — is outside the warm, reassuring register of most consumer apps. This section covers the UX writing challenge, the Heirlooms approach (honest but not alarming), and the principle: the copy must be accurate enough that a user who reads it understands the stakes, without being so frightening that users skip it.

**15.3 The passphrase vs biometric tradeoff**
On mobile, biometric authentication (fingerprint, face ID) is more convenient than a passphrase for vault access. But biometric data is not a key — it's a gate to a key stored in the device's secure enclave. This section covers the layered model (biometric unlocks the device, which holds the wrapped master key seed), the implications for the threat model, and the failure modes biometric introduces that passphrase doesn't.

**15.4 The two-product insight**
A product used daily by a software engineer is a different product from the same codebase used by a non-technical 70-year-old. The developer tool is configurable and assumes technical literacy; the family experience is invisible infrastructure that assumes the opposite. This section covers how to hold both products in mind simultaneously, the specific UX decisions that diverge between them, and the onboarding architecture that shows the right surface to each user type.

**15.5 Onboarding non-technical users to key management**
The onboarding flow must guide a non-technical user through: generating a master key (invisible), writing down a recovery phrase (explained simply), setting a passphrase (without alarming them about what happens if they forget it), and understanding the trust network (without using the word "cryptography"). This section covers the Heirlooms approach and the design principles — one concept per screen, concrete consequences not technical mechanisms, progress preserved if the user is interrupted.

**15.6 Brand voice as an architectural constraint**
Heirlooms's brand register — patient, solemn, not gamified — shaped specific technical decisions: the compost metaphor for deletion (content is not immediately gone), the garden metaphor for the archive (slow, growing, tended), and the decision not to add gamification mechanics that would conflict with the emotional register of preparing a capsule for a dying parent. This section covers the discipline: every feature and every UX surface is tested against the brand register, and technical implementations that would force a brand-register violation are rejected.

---

### Chapter 16: The 100-year promise

**16.1 What changes at long horizons**
Most software is designed for a 2–5 year operational horizon. A system with a 50-year delivery window requires different answers to almost every architectural question. This section surveys what changes: key formats (algorithm identifiers you can't defer), envelope versioning (mandatory, not optional), custody design (you need all five properties), data portability (users must be able to decrypt independently of the service), and the honest claim (more specific, more auditable, more legally accountable).

**16.2 The key hierarchy at long horizons**
At a 50-year horizon, the asymmetric scheme used to wrap master keys today (P-256) may be deprecated or broken. The key hierarchy must be designed so the wrapping scheme can be migrated without re-encrypting content. This section revisits the Chapter 4 hierarchy through the long-horizon lens, covers the specific changes required (algorithm identifiers mandatory from day one, re-wrap path designed in not retrofitted), and addresses the post-quantum horizon.

**16.3 The envelope format at long horizons**
A ciphertext produced in 2026 must be decryptable in 2076 by software that doesn't exist yet. This section covers the envelope format properties that make this possible: self-describing (contains everything needed to decrypt without external reference), versioned (allows algorithm migration without re-encryption), and documented (the specification is archived and accessible independently of the running service).

**16.4 Custody at long horizons**
The trust network must be actively tended, not set and forgotten. This section revisits the Chapter 12 trust network through the long-horizon lens: the heartbeat primitive as a continuous practice, the regular re-sharing to replace ageing or unavailable trustees, the audit log as a persistent record of the custody chain, and the Foundation as the backstop that survives everything else.

**16.5 Writing the honest claim**
The chapter closes by returning to the honest claim established in Chapter 1, now enriched by everything the intervening chapters have built. The claim Heirlooms can make: specific, auditable, legal-grade. What it doesn't claim: immunity to cryptographic breaks, government compulsion with appropriate legal process, or the user's own failure to maintain their trust network. The discipline of the honest claim: it's stronger and more trustworthy for being limited than a vaguer promise would be.

**16.6 Closing: infrastructure for a kind of promise**
The book ends where the M8 session concepts document ended. Heirlooms is not a software product that happens to store files. It is infrastructure for a specific kind of promise — that something sealed today will reach the right person at the right time, intact, readable, and accompanied by a clear record of the handover. The software is necessary. The cryptography is necessary. The institution is necessary. None of them alone is sufficient. The 100-year promise is made by the combination, and by the discipline of not pretending that any single layer of it can carry the whole weight.

---

## Appendices

**Appendix A: Cryptographic primitive reference**
One-page summaries of AES-256-GCM, HKDF, P-256 ECDH, Argon2id, and Shamir secret sharing. What each does, when to use it, what not to implement yourself, and the standard library bindings on Android, Web, and JVM.

**Appendix B: Cross-platform encoding cheat sheet**
A reference table of every encoding convention pinned in the Heirlooms codebase: base64 variants, SEC1 vs SPKI vs JWK, field byte order, JSON key naming conventions for key material. Each row shows the Android form, the WebCrypto form, and the conversion utility.

**Appendix C: Flyway migration sequence**
The complete Heirlooms schema evolution annotated: V1 (single-user, API key), V7 (DEK columns), V17 (users and sessions), V22 (per-user isolation), V23 (sharing keys, friendships, shared upload references). Shows how a schema designed for one user evolves to support E2EE multi-user sharing without a rewrite.

**Appendix D: The envelope format specification**
Complete byte-level specification of every envelope variant: the content envelope, the thumbnail envelope, the passphrase-wrapped master key blob, the pairing blob, and the shared DEK envelope. Pseudocode for the seal and unseal operations. Intended as a template and as a reference for readers implementing interoperable systems.

**Appendix E: The five-property evaluation rubric**
A single-page summary of the five trustworthy-custodian properties as a scoring rubric, with a worked example applying it to each of the five custodian categories from Chapter 12. Intended for readers designing their own custody networks.

---

## Notes

Rough page count estimate: 420–480 pages. Parts I–III are the core technical content a reader buys the book for; Part IV is the material that makes it get reviewed and talked about; Part V is short but earns the book a second readership among technical founders and product architects.

Why this book doesn't exist yet: there are cryptography textbooks (theoretical, implementation-agnostic), security engineering books (broad, not E2EE-specific), and blog posts about individual pieces. There is no book that takes a senior engineer from "I've decided this needs E2EE" to "here is the complete working architecture, cross-platform, multi-user, with a credible long-horizon design." That gap is what this book fills.
