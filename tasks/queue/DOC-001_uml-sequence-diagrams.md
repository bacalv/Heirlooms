---
id: DOC-001
title: UML sequence diagrams from test output
category: Docs
priority: Low
status: queued
depends_on: [TST-004, SEC-001]
touches: [docs/security/, HeirloomsTest/]
estimated: 1 day (agent)
---

## Goal

Generate living UML sequence diagrams for the key auth and sharing flows. "Living" means they're generated from actual test execution, not hand-drawn — so they stay accurate as the code evolves.

## Approach

Use a Playwright test hook (or a custom JUnit extension on the integration tests) to emit structured event logs during test execution. A post-processing script converts the logs into PlantUML (or Mermaid) sequence diagram source, which renders to SVG in the docs.

### Example output for the login flow:

```
Client -> Server: POST /api/auth/challenge {username}
Server -> DB: SELECT auth_salt WHERE username=?
DB --> Server: {salt} (or fake salt if user not found)
Server --> Client: 200 {auth_salt}
Client -> Client: derive auth_key = Argon2id(password, salt)
Client -> Server: POST /api/auth/login {username, auth_key}
Server -> DB: SELECT auth_verifier WHERE username=?
Server -> Server: verify HMAC(auth_key) == auth_verifier
Server -> DB: INSERT sessions (token_hash, user_id, expires_at)
Server --> Client: 200 {session_token, user_id, expires_at}
```

## Flows to document

1. Registration (invite → register → first login)
2. Login (challenge → login)
3. Device pairing (Android → Web QR flow)
4. Plot invite (generate QR → scan → join → key exchange)
5. Upload + E2EE envelope (client encrypts → upload → server stores)
6. Staging approval (upload → staging queue → approval → plot item)

## Deliverables

- `docs/security/diagrams/` — SVG renders of each flow
- `docs/security/diagrams/src/` — PlantUML/Mermaid source files
- CI step that regenerates SVGs if source changes
- Link from `docs/security/threat-model.md` to relevant diagrams

## Acceptance criteria

- All 6 flows have accurate, up-to-date sequence diagrams
- Diagrams render correctly in GitHub Markdown preview
- Diagrams are regenerated automatically when integration tests change
