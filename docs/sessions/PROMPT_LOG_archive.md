# Heirlooms — Prompt Log

Sessions before 14 May 2026 archived in `docs/sessions/2026-01_to_2026-05-13_archive.md`.

---

## Session — 14 May 2026 — v0.53.x: refactor completion, staging environment, task system

### Summary

Large session completing the server refactor and setting up the testing infrastructure.

### What was done

**Server (v0.53.1)**
- Phases 1–8 of server refactor: flat package → `config/ crypto/ domain/ filters/ repository/ routes/ service/ representation/ storage/`
- Database delegation layer removed — services call repository interfaces directly; Database.kt is 37 lines
- `println` replaced with SLF4J/Logback
- Hand-rolled JSON replaced with Kotlin DTO data classes + shared `ObjectMapper`
- V29 Flyway migration: seeds founding user + system plot for fresh databases
- Auth route bug fixed: `getInviteRoute`, `pairingInitiateRoute`, `pairingCompleteRoute` now use `request.authUserId()` not `resolveSession()`
- 326 unit tests passing. Integration test coverage: 53.3%

**Infrastructure**
- `heirlooms-test` DB on existing Cloud SQL instance
- Cloud Run: `heirlooms-server-test` + `heirlooms-web-test` in us-central1
- Domains: `test.api.heirlooms.digital` + `test.heirlooms.digital` (SSL active)
- Test API key in Secret Manager

**Android (v0.53.0, versionCode 59)**
- `staging` product flavor: `.staging` app ID, burnt-orange icon with TEST badge
- Both APKs installed on device R9HR102XT8J

**Task system**
- `tasks/` created with `queue/`, `in-progress/`, `done/`, `brainstorming/`
- 11 tasks queued; see `tasks/progress.md`

### Deployed

| Component | Version | URL |
|-----------|---------|-----|
| Production server | v0.53.1 | https://api.heirlooms.digital |
| Test server | v0.53.1 | https://test.api.heirlooms.digital |
| Production web | v0.53.0 | https://heirlooms.digital |
| Test web | v0.53.0 | https://test.heirlooms.digital |
| Android prod | 0.53.0 (59) | installed |
| Android staging | 0.53.0-staging (59) | installed |

### Outstanding

- `git push` (4 commits ahead of origin/main)
- iOS QRScannerView AVFoundation (IOS-001)
- Manual production sanity test (TST-001)
