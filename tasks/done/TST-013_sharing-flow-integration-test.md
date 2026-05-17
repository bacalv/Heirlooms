# TST-013 — Sharing flow integration test

**Category:** Testing
**Priority:** High
**Assigned to:** Developer
**Branch:** agent/developer-16/TST-013

## Objective

Write `SharingFlowIntegrationTest.kt` exercising the complete server-side sharing pipeline: two real user accounts, friend connection, upload, direct share, and retrieval — all in-process against a real Postgres database via Testcontainers.

## Tests written

### Integration tests (5)

1. `sharingFlowEndToEnd` — full happy path: founding session → Alice + Bob registered → friend connect → upload → share → file retrieval by bytes → received list
2. `non-recipient cannot access uploader original file` — Bob cannot GET Alice's original uploadId after being shared a recipient copy
3. `share to non-friend returns 403` — sharing to Charlie (no friendship with Alice) returns FORBIDDEN
4. `non-member cannot retrieve shared file` — Dave (unrelated user) cannot GET Bob's recipientUploadId, returns NOT_FOUND

### Unit tests (2)

5. `login succeeds when authKey sha256 matches stored authVerifier` — documents the sha256 login contract against mocked AuthService
6. `login fails when authKey sha256 does not match stored authVerifier` — inverse invariant

## Completion notes

- `SharingFlowIntegrationTest.kt` written to `HeirloomsServer/src/test/kotlin/digital/heirlooms/server/`
- Uses `LocalFileStore(Files.createTempDirectory(...))` so bytes are actually stored and retrievable for the retrieval assertion
- Also fixed a pre-existing compilation error in `SessionAuthFilterUnitTest.kt` (missing `import org.http4k.core.then` and `import org.http4k.core.Filter` which caused unresolved reference errors on all `.then()` calls)
- All 6 tests pass: `tests=6, skipped=0, failures=0, errors=0`
- Completed: 2026-05-17
