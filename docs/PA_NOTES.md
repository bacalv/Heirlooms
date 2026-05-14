# PA Notes — Working Memory

Maintained by the PA across sessions. For task state, see `tasks/progress.md`.
Previous notes archived in `docs/sessions/2026-05-14_PA_NOTES_archive.md`.

---

## Bret's preferences

- Uses IntelliJ IDEA (not Android Studio) for this project
- All git pushes are done manually by Bret — never push without being asked
- Prefers multiple-choice questions over open-ended clarification requests
- Likes a clean end-of-session summary
- `say "..."` on macOS is useful for async audio alerts on long agent tasks
- Secrets: only Bret knows production secrets — never ask for them, never log them

## Project facts

- Package: `digital.heirlooms` (not com.heirloom)
- Domain: `heirlooms.digital`
- GitHub: `github.com/bacalv/Heirlooms`
- GCP project: `heirlooms-495416`, region: `us-central1`
- Current version: v0.53.1 (14 May 2026)
- Android versionCode: 59

## Live environments

| | Production | Staging |
|--|--|--|
| API | https://api.heirlooms.digital | https://test.api.heirlooms.digital |
| Web | https://heirlooms.digital | https://test.heirlooms.digital |
| DB | `heirlooms` on `heirlooms-db` | `heirlooms-test` on `heirlooms-db` |
| API key | (secret — ask Bret) | `heirlooms-test-api-key` in Secret Manager |

## Architecture summary

- **Server**: Kotlin/http4k, PostgreSQL (Flyway), GCS. Package: `config/ crypto/ domain/ filters/ repository/ routes/ service/ representation/ storage/`. Database.kt is 37 lines — migration façade only.
- **Android**: Jetpack Compose. Flavors: `prod` (production) and `staging` (burnt-orange icon, `.staging` app ID)
- **Web**: React/Vite, nginx on Cloud Run
- **iOS**: Swift/SwiftUI, CryptoKit. Scaffold exists; QRScannerView is a stub (IOS-001 in queue)
- **E2EE**: `p256-ecdh-hkdf-aes256gcm-v1` envelope — see `docs/envelope_format.md`

## Key decisions

- Flow → Trellis rename approved — see `tasks/brainstorming/IDEA-001_trellis-naming.md`, task REF-001 queued
- Coverage gate: 90% overall wired; target 100% for auth/crypto paths — task SEC-002 queued
- Playwright E2E: actor-based, against staging — task TST-004 queued
- Production deployments always require Bret's explicit approval before OpsManager proceeds

## Task system

See `tasks/progress.md` for the full queue.
Persona files: `personalities/` — PA, Developer, DevManager, TestManager, OpsManager, SecurityManager, TechnicalArchitect.
Start any session: `@personalities/PA.md`
