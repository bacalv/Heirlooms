# TST-014 — M11 Local Stack: Design Brief for Iteration Sign-Off

*Test Manager. Branch: M11. Status: design only — implementation is a separate task.*
*Prerequisite reading: `docs/briefs/ARCH-003`, `ARCH-006`, `ARCH-010`.*

---

## 1. Background and purpose

Heirlooms is moving to a server-first development strategy for M11 and M12. The test
environment (Cloud Run + GCS + Cloud SQL) will not be redeployed between M11
iterations. Each iteration must be signed off by running an automated suite against a
**fully local stack** — no cloud credentials, no Docker Desktop restart triggered by
the PA, no manual deploy.

The foundation already exists: `SharingFlowIntegrationTest.kt` starts the server
in-process via `buildApp(LocalFileStore, Database, authSecret)`, uses Testcontainers
for a real PostgreSQL instance, and exercises the full sharing pipeline. M11 needs
this pattern elevated into a first-class, repeatable iteration gate.

This brief answers the feasibility questions raised in TST-014, recommends a stack
architecture, and specifies the Gradle command that constitutes the gate.

---

## 2. Feasibility findings

### 2.1 Can the full server start in-process for all M11 endpoints?

**Yes, with one caveat for tlock.**

The server entry point (`buildApp`) accepts `FileStore` and `Database` as constructor
arguments. All repositories are constructed from the `DataSource` inside `Database`.
There are no hard-coded GCS or Cloud SQL imports in `buildApp` itself — the storage
backend is chosen at startup by `Main.kt` based on environment variables, and
`buildApp` never touches those variables.

The tlock path (`TimeLockProvider`) is specified in ARCH-006 as an environment-
controlled abstraction with three settings: `TLOCK_PROVIDER=disabled` (the default),
`TLOCK_PROVIDER=stub`, and `TLOCK_PROVIDER=sidecar`. At the time of this feasibility
review, the M11 server endpoints for tlock (Wave 5/6 in ARCH-010) have not yet been
implemented — the `TimeLockProvider` interface and `StubTimeLockProvider` are specified
but not yet in the codebase. When they are implemented, tests that exercise the tlock
path must set `TLOCK_PROVIDER=stub` and provide a deterministic `TLOCK_STUB_SECRET`.
Tests that do not exercise tlock (Wave 1–4 connection/nomination/share endpoints) set
`TLOCK_PROVIDER=disabled`.

**Summary:** No hard GCS or Cloud SQL dependencies exist in `buildApp`. The tlock
path requires `StubTimeLockProvider` to be wired into the server at test construction
time — this is a natural constructor injection, not an external service.

### 2.2 Is `LocalFileStore` suitable for all M11 tests?

**Yes, fully.**

`LocalFileStore` implements the complete `FileStore` interface: `save`, `saveWithKey`,
`get`, `getFirst`, and `delete`. It writes to a temp directory and is already used
successfully in `SharingFlowIntegrationTest.kt` for upload and retrieval round-trips.

The M11 capsule sealing path does not interact with `FileStore` directly — capsule
crypto columns (`wrapped_capsule_key`, `tlock_wrapped_key`, etc.) are stored in the
PostgreSQL `capsules` table as `BYTEA`, not as blob-store objects. The `FileStore` is
only consulted for upload content (`/api/content/upload`, `/api/content/uploads/:id/file`),
which `LocalFileStore` handles correctly.

The `/tlock-key` endpoint reads from `capsules.tlock_dek_tlock` (a database column),
not from the file store. No special stub is needed for the file store on the tlock path.

**One known limitation:** `LocalFileStore` does not implement `DirectUploadSupport`
(the interface for GCS/S3 pre-signed URLs). The `readUrlContractRoute` returns a 501
when `directUpload` is null (which it is for `LocalFileStore`). This is acceptable:
M11 integration tests should not test the pre-signed URL path, and existing tests
already tolerate this behaviour.

### 2.3 Should TOOL-001 be the test driver (HTTP over localhost) or should tests stay in-process?

**Recommendation: in-process for the iteration gate; TOOL-001 as an optional layer.**

Reasons to keep the iteration gate in-process:

1. **Speed.** In-process tests using http4k's `HttpHandler` invoke the full routing
   and middleware stack without network overhead. The `SharingFlowIntegrationTest`
   completes the six-test sharing pipeline in seconds. M11 will add more tests but
   the bottleneck is Testcontainers PostgreSQL startup, not the test logic itself.
2. **No additional module dependency.** TOOL-001 depends on ARCH-015 (API stability
   contract), which is not yet done. Gating M11 iteration sign-off on TOOL-001
   would introduce an unresolved dependency.
3. **Isolation.** In-process tests do not require a listening port, simplifying
   CI setup. There is no race condition between server startup and test client
   connection.

TOOL-001's role changes to: a **supplementary black-box validation** run manually
against the test environment or locally against a `./gradlew run` server instance,
not part of the automated gate. This matches its stated Phase 1 scope (proving the
capsule lifecycle works against the real API) and its patent demonstration purpose.

**If black-box HTTP testing is desired for M11 without TOOL-001**, http4k provides
`ApacheClient` and `OkHttp` adapters that can target `http://localhost:<port>` after
starting the server via `asServer(Netty(0))`. This pattern is available but adds
complexity; defer to a later task if needed.

### 2.4 Testcontainers versus H2 for the PostgreSQL provider

**Recommendation: Testcontainers (real PostgreSQL 16), not H2.**

Reasons:

1. **M11 uses PostgreSQL-specific features.** The `connections`, `executor_shares`,
   and `capsule_recipient_keys` tables (from ARCH-003/ARCH-010 schema) use
   `UUID` primary keys generated by `gen_random_uuid()`, `BYTEA` columns with `CHECK`
   constraints (e.g. `capsule_key_format IN ('capsule-ecdh-aes256gcm-v1', 'tlock-bls12381-v1')`),
   `JSONB` in the existing `criteria` column, and `CREATE INDEX` patterns specific to
   PostgreSQL. H2's PostgreSQL compatibility mode does not support all of these.
   The existing `SchemaMigrationTest.kt` uses Testcontainers precisely because it
   tests Flyway migrations against real PostgreSQL constraints.
2. **The pattern is already proven.** `SharingFlowIntegrationTest.kt` and
   `SchemaMigrationTest.kt` both use `GenericContainer("postgres:16")` with Testcontainers.
   The Docker socket configuration and Ryuk disabling are already handled in
   `build.gradle.kts` and the test `@BeforeAll` setup blocks.
3. **Startup cost is acceptable.** PostgreSQL startup via Testcontainers adds ~10–15
   seconds per test class (`@BeforeAll` scope). With a shared container per test
   class (the current pattern), this is a one-time cost. The full M11 gate is expected
   to have 2–4 test classes, meaning 20–60 seconds of container startup across the
   entire suite — acceptable for an iteration gate.
4. **H2 adds a schema maintenance burden.** Any H2-specific dialect workaround for
   `BYTEA`, `UUID`, or constraint syntax would need to be kept in sync with every
   Flyway migration going forward.

The only prerequisite for Testcontainers in CI is Docker-in-Docker or a Docker socket.
GitHub Actions standard runners (`ubuntu-latest`) support Docker out of the box;
Testcontainers detects this automatically. No cloud credentials are needed.

**Known friction (local development only):** Docker Desktop on macOS requires a manual
restart before Testcontainers works if Docker has crashed (documented in project
memory). This is not a CI concern.

### 2.5 What does a clean run look like?

**One Gradle command:**

```bash
./gradlew :HeirloomsServer:test
```

This runs the full test suite including unit tests (mockk-based), schema migration
tests (Testcontainers), and integration tests (Testcontainers + LocalFileStore). For
M11, all new tests live in the same `:HeirloomsServer:test` source set.

To run only the integration gate (excluding unrelated unit tests) a dedicated Gradle
task can be introduced — see §5 for the recommended task definition.

---

## 3. Recommended stack architecture

```
+---------------------------+
|   JUnit 5 test process    |
|  (in-process, no port)    |
|                           |
|  +-----------------------+|
|  |  sessionAuthFilter    ||  ← wraps the app handler (same as production)
|  |    + buildApp(...)    ||
|  |                       ||
|  |  All M11 routes:      ||
|  |   /api/connections    ||
|  |   /api/executor-noms  ||
|  |   /api/capsules/:id   ||
|  |     /seal             ||
|  |     /tlock-key        ||
|  |     /executor-shares  ||
|  |   /api/capsule-...    ||
|  +---------+-------------+|
|            |               |
|   +--------+--------+      |
|   | LocalFileStore  |      |  ← temp dir per test class
|   | (no cloud deps) |      |
|   +-----------------+      |
|            |               |
|   +--------+--------+      |
|   |  Database       |      |  ← Hikari pool → Testcontainers PG 16
|   +-----------------+      |
+---------------------------+
           |
  +--------+--------+
  |  PostgreSQL 16  |  ← Testcontainers (GenericContainer)
  |  (real schema,  |    Flyway-migrated (V1..V33+)
  |   all M11 cols) |    Ryuk disabled (no Docker restart)
  +-----------------+
```

**Component table:**

| Component | Implementation | Notes |
|---|---|---|
| HTTP routing/middleware | http4k in-process (`buildApp`) | No Netty server started |
| Auth filter | `sessionAuthFilter(authRepo)` | Same as production |
| File store | `LocalFileStore(Files.createTempDirectory(...))` | One temp dir per test class |
| Database | `HikariDataSource` → Testcontainers PostgreSQL 16 | One container per test class (via `@BeforeAll`) |
| Schema | Flyway (all migrations, V1..V33+) | Run in `@BeforeAll` |
| tlock (non-tlock tests) | `TLOCK_PROVIDER=disabled` (no stub needed) | Env var set in Gradle `tasks.test` block |
| tlock (tlock tests) | `StubTimeLockProvider` injected via `buildApp` | 32-byte deterministic secret per test class |
| Shamir | No stub needed | Server stores opaque BYTEA; tests use fake wrapped shares |
| Cloud credentials | None required | `LocalFileStore` and Testcontainers have no cloud deps |

---

## 4. Coverage scope for an M11 iteration pass

The following API surfaces must be covered for an iteration to be signed off. Coverage
is organised by the Wave structure from ARCH-010.

### Wave 1 — Connections (mandatory)

| Test | Description |
|---|---|
| `POST /api/connections` — create bound connection | Creates connection with `sharing_pubkey`; returns 201 with connection ID |
| `POST /api/connections` — create deferred-pubkey | Creates connection with no pubkey; `sharing_pubkey` is null |
| `GET /api/connections` | Lists connections for the authenticated user |
| `GET /api/connections/:id` | Fetches a specific connection |
| `PATCH /api/connections/:id` | Updates display name or roles |
| `DELETE /api/connections/:id` — happy path | Deletes a connection with no active nominations |
| `DELETE /api/connections/:id` — blocked by pending nomination | Returns 409 |

### Wave 2 — Executor nominations (mandatory)

| Test | Description |
|---|---|
| `POST /api/executor-nominations` | Creates pending nomination for a connection |
| `GET /api/executor-nominations` (owner list) | Lists all nominations issued by the owner |
| `GET /api/executor-nominations/received` (nominee list) | Lists nominations extended to the caller |
| `POST /api/executor-nominations/:id/accept` | Nominee accepts; status → accepted |
| `POST /api/executor-nominations/:id/decline` | Nominee declines; status → declined |
| `POST /api/executor-nominations/:id/revoke` | Owner revokes; status → revoked |

### Wave 3 — Capsule recipient linking (mandatory)

| Test | Description |
|---|---|
| `PATCH /api/capsules/:id/recipients/:recipientId/link` | Links a capsule recipient to a connection |

### Wave 4 — Executor share distribution (mandatory)

| Test | Description |
|---|---|
| `POST /api/capsules/:id/executor-shares` | Submits all Shamir shares; validates nomination count and envelope format |
| `GET /api/capsules/:id/executor-shares/mine` | Accepted executor retrieves their share |
| `GET /api/capsules/:id/executor-shares/collect` | Owner retrieves all shares |

### Wave 5 — Sealing (mandatory, three sub-paths)

| Test | Description |
|---|---|
| `PUT /api/capsules/:id/seal` — ECDH-only (non-tlock) | Happy path; validates `wrapped_capsule_key` envelope; writes crypto columns |
| `PUT /api/capsules/:id/seal` — tlock path | With `StubTimeLockProvider`; validates tlock blob, dek_tlock digest, writes tlock columns |
| `PUT /api/capsules/:id/seal` — Shamir path | With accepted nominations; validates share count and envelope format |
| `PUT /api/capsules/:id/seal` — deferred-pubkey rejected | Returns 422 when no tlock/executor fallback configured |
| `PUT /api/capsules/:id/seal` — invalid `wrapped_capsule_key` | Returns 422 on malformed envelope |
| `PUT /api/capsules/:id/seal` — tlock disabled | Returns 422 when `TLOCK_PROVIDER=disabled` and tlock fields present |
| `PUT /api/capsules/:id/seal` — digest mismatch | Returns 422 when `SHA-256(dek_tlock) != tlock_key_digest` |

### Wave 6 — tlock key delivery (mandatory)

| Test | Description |
|---|---|
| `GET /api/capsules/:id/tlock-key` — gate open | Gate passes (`now() >= unlock_at`, stub `decrypt` returns non-null); returns 200 with `dek_tlock` |
| `GET /api/capsules/:id/tlock-key` — unlock_at in future | Returns 202 with `retry_after_seconds` |
| `GET /api/capsules/:id/tlock-key` — not a recipient | Returns 403 |
| `GET /api/capsules/:id/tlock-key` — tlock disabled | Returns 503 |

### Wave 7 — Read path amendments (mandatory)

| Test | Description |
|---|---|
| `GET /api/capsules/:id` (amended) | M11 columns (`wrapped_capsule_key`, `tlock_round`, `shamir_threshold`, etc.) appear in response; null fields are null/absent |
| `GET /api/capsule-recipient-keys/:capsuleId` | Returns per-recipient rows with `wrapped_capsule_key` and `wrapped_blinding_mask` |

### Pre-existing coverage (already passing, not regressions)

The existing `SharingFlowIntegrationTest.kt` (4 integration + 2 unit tests), `SchemaMigrationTest.kt`,
and `CapsuleHandlerTest.kt` continue to be part of the gate. A regression in any of
these is an automatic iteration fail.

---

## 5. Gradle task definition

### Option A — Run full `test` task (simplest, recommended for now)

```bash
./gradlew :HeirloomsServer:test
```

All tests in the `:HeirloomsServer` module run, including unit, schema migration, and
integration tests. The gate passes when all tests are green.

**Required environment for Testcontainers (already in `build.gradle.kts`):**

```kotlin
tasks.test {
    useJUnitPlatform()
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    val dockerHost = System.getenv("DOCKER_HOST")
        ?: "unix:///Users/bac/Library/Containers/com.docker.docker/Data/docker.raw.sock"
    environment("DOCKER_HOST", dockerHost)
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", dockerHost.removePrefix("unix://"))
}
```

The `TLOCK_STUB_SECRET` for tests that use `StubTimeLockProvider` should be provided
as a test-scoped system property, not as a real environment variable. The recommended
pattern:

```kotlin
tasks.test {
    // Existing config above, plus:
    systemProperty("heirlooms.tlock.stub.secret", "dGVzdC10bG9jay1zZWNyZXQtMzItYnl0ZXM=")
    // ^ base64url of 32 known bytes; safe to hardcode in test config (test key only)
}
```

Test classes that exercise tlock read `System.getProperty("heirlooms.tlock.stub.secret")`
in their `@BeforeAll` setup to construct the `StubTimeLockProvider`.

### Option B — Dedicated `integrationTest` task (recommended once M11 tests are substantial)

Add to `build.gradle.kts`:

```kotlin
tasks.register<Test>("integrationTest") {
    description = "M11 iteration gate — Testcontainers integration tests only"
    group = "verification"
    useJUnitPlatform {
        includeTags("integration")
    }
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    val dockerHost = System.getenv("DOCKER_HOST")
        ?: "unix:///Users/bac/Library/Containers/com.docker.docker/Data/docker.raw.sock"
    environment("DOCKER_HOST", dockerHost)
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", dockerHost.removePrefix("unix://"))
    systemProperty("heirlooms.tlock.stub.secret", "dGVzdC10bG9jay1zZWNyZXQtMzItYnl0ZXM=")
}
```

Tag integration test classes with `@Tag("integration")` in JUnit 5. Unit tests (mockk-only)
run with the standard `./gradlew test`; the integration gate runs with:

```bash
./gradlew :HeirloomsServer:integrationTest
```

**Recommendation:** Start with Option A. Introduce Option B when the number of
integration test classes grows enough that selectively running them has significant
time value (expected threshold: 5+ test classes or >2 minutes for the integration
subset alone).

---

## 6. Iteration gate definition

### What a passing run looks like

```
./gradlew :HeirloomsServer:test

BUILD SUCCESSFUL in Xs
N tests completed, 0 failed
```

All tests in all categories (unit, schema migration, integration) are green. No skips
on mandatory M11 paths.

### Automatic fail conditions

Any of the following is an unconditional iteration fail:

- Any test failure, regardless of category.
- Testcontainers fails to start PostgreSQL (Docker not running; treat as infrastructure
  failure, not a code failure — resolve before re-running the gate).
- `StubTimeLockProvider` cannot be constructed (missing or malformed stub secret).
- Any Flyway migration fails (`SchemaMigrationTest.kt` fails or `@BeforeAll` throws).
- A previously-passing test in `SharingFlowIntegrationTest.kt` or
  `SchemaMigrationTest.kt` regresses.

### Known skips (acceptable, not fails)

- The pre-signed URL tests (`readUrlContractRoute`) are expected to return 501 with
  `LocalFileStore`. These tests should be annotated `@Disabled("requires DirectUploadSupport")`.
- Any test explicitly annotated `@Disabled` with a documented reason is an acceptable
  skip provided it is tracked in a follow-up task. A test annotated `@Disabled` with
  no documented reason is a fail.
- `S3FileStoreTest.kt` is expected to skip or skip gracefully when no AWS credentials
  are present (it already does this). Not part of the M11 gate.

---

## 7. CI integration

### Can this run in GitHub Actions without cloud credentials?

**Yes.**

The existing `.github/workflows/` contains only Android farm workflows (`android-farm-pr.yml`,
`android-farm-nightly.yml`). Neither touches the server. Adding a server integration
test workflow requires:

1. A standard `ubuntu-latest` runner (available on GitHub Actions free tier — no
   self-hosted runner needed).
2. Docker service on the runner — `ubuntu-latest` runners have Docker pre-installed.
   Testcontainers detects the Docker socket automatically on Linux.
3. No GCP credentials. `LocalFileStore` and Testcontainers have zero cloud dependencies.

**Recommended workflow file (to be created as `.github/workflows/server-integration.yml`):**

```yaml
name: Server — integration tests

on:
  push:
    branches:
      - M11
      - 'agent/*/M11-*'
    paths:
      - 'HeirloomsServer/**'
  pull_request:
    branches:
      - M11
    paths:
      - 'HeirloomsServer/**'

jobs:
  integration:
    name: M11 local stack gate
    runs-on: ubuntu-latest
    timeout-minutes: 15

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run server tests (unit + integration)
        run: ./gradlew :HeirloomsServer:test
        env:
          # Testcontainers on GitHub Actions standard runners
          TESTCONTAINERS_RYUK_DISABLED: "true"
          DOCKER_HOST: "unix:///var/run/docker.sock"
          TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE: "/var/run/docker.sock"

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: server-test-results-${{ github.run_id }}
          path: HeirloomsServer/build/reports/tests/
```

**Note on Docker socket on GitHub Actions:** The standard `ubuntu-latest` runner
exposes Docker at `/var/run/docker.sock`. The `build.gradle.kts` Testcontainers
configuration currently hardcodes the macOS Docker Desktop socket path as a fallback.
The CI workflow overrides this with the Linux path via environment variables, which
`build.gradle.kts` reads via `System.getenv("DOCKER_HOST")`. No code change is needed.

**Ryuk disabling:** Already configured in `build.gradle.kts`. Ryuk is disabled
because its own container-lifecycle logic can interfere with CI environments.

---

## 8. Test class structure recommendation

The M11 integration gate should be organised into the following test classes (all in
`HeirloomsServer/src/test/kotlin/digital/heirlooms/server/`):

```
SharingFlowIntegrationTest.kt     ← existing (pre-M11 baseline)
SchemaMigrationTest.kt            ← existing (pre-M11 baseline)

M11ConnectionsIntegrationTest.kt  ← new (Wave 1 — connections CRUD)
M11NominationsIntegrationTest.kt  ← new (Wave 2 — executor nominations)
M11SealingIntegrationTest.kt      ← new (Waves 3–5 — linking, shares, sealing)
M11TlockKeyIntegrationTest.kt     ← new (Wave 6/7 — tlock-key delivery + read amendments)
```

All M11 integration test classes follow the same `@BeforeAll` pattern as
`SharingFlowIntegrationTest.kt`:

- `GenericContainer("postgres:16")` started in `@BeforeAll`
- Flyway-migrated via `classpath:db/migration`
- `buildApp(LocalFileStore(...), Database(dataSource), authSecret = ...)` called once
- Helper methods for registering users and obtaining session tokens

For tlock test classes, `StubTimeLockProvider` is constructed from the system property
`heirlooms.tlock.stub.secret` and passed to a variant of `buildApp` that accepts it.
This requires a minor extension to the `buildApp` signature once `StubTimeLockProvider`
is implemented (not in scope for TST-014).

**Shared test infrastructure:** Common setup helpers (register user, generate invite,
upload file) should be extracted to an `IntegrationTestBase` companion object or a
`TestHelpers.kt` file to avoid duplication across the M11 test classes.

---

## 9. Open questions and blockers to flag to the CTO

### 9.1 `buildApp` signature — tlock provider injection (blocker for Wave 5/6 tests)

The current `buildApp` signature does not include a `TimeLockProvider` parameter.
When the M11 sealing endpoint is implemented, `buildApp` must accept a
`TimeLockProvider` so tests can inject `StubTimeLockProvider`. This is a **required
code change** before Wave 5/6 integration tests can be written. It is not in scope
for TST-014 but must be part of the first M11 developer task that implements `/seal`.

### 9.2 `TLOCK_STUB_SECRET` handling in tests

The `StubTimeLockProvider` spec (ARCH-006 §2.1) requires a 32-byte secret from a
`TLOCK_STUB_SECRET` environment variable. For tests, this secret must be deterministic
(so tests are reproducible) but must not be the all-zeros key (which would be a
security regression if accidentally used in production). The recommended approach is
to read it from a JVM system property (`heirlooms.tlock.stub.secret`) set in
`build.gradle.kts` rather than from an environment variable, so it is visible in the
build file and not confused with a real credential. The developer implementing
`StubTimeLockProvider` should confirm this approach or propose an alternative.

### 9.3 V33 migration — M11 schema is not yet in the codebase

At the time of this review, the M11 schema migrations (V31 connections schema and V32
capsule crypto schema, as described in ARCH-010 §3) have not been applied to the M11
branch. The current highest migration is `V32__add_require_biometric_to_users.sql`.

The ARCH-010 document refers to a `V31__connections.sql` and `V32__m11_capsule_crypto.sql`
that must be written by the M11 developer tasks. Given the existing V32 in the branch
(biometric), the M11 connection migration will be V33 and the capsule crypto migration
will be V34 (or higher, depending on what else lands first). The developer implementing
the M11 schema must check the current highest version before naming the files.

This is not a blocker for TST-014 design work but must be resolved before any M11
integration tests can run against the real schema.

### 9.4 `sealed_at` column

ARCH-010 §5.1 specifies a `sealed_at` field in the `/seal` success response
(`"sealed_at": "2026-05-16T10:00:00Z"`). The current `capsules` table schema
(migrations V1–V32) does not include a `sealed_at` column. The M11 schema migration
(V32 in ARCH-010 terms) must either add this column or the sealing endpoint must
derive the value from `updated_at`. The developer implementing `/seal` should clarify
this and ensure the integration tests can assert on the response shape.

### 9.5 `account_sharing_keys` table — referenced in ARCH-010 V31 backfill

The V31 connections backfill (ARCH-010 §4.1) references `account_sharing_keys`, a
table that must exist for the backfill to run. The test suite must either seed this
table in `@BeforeAll` or ensure the backfill SQL handles an empty `account_sharing_keys`
gracefully (which it does via the `LEFT JOIN`, per ARCH-010 §4.5). No code change
needed, but test authors must be aware that connection rows for test users will have
`sharing_pubkey = NULL` unless they explicitly seed `account_sharing_keys`.

---

## 10. Summary recommendation

| Decision | Recommendation |
|---|---|
| Stack pattern | Extend `SharingFlowIntegrationTest.kt` pattern to M11 test classes |
| Database | Testcontainers PostgreSQL 16 — do not use H2 |
| File store | `LocalFileStore` with `Files.createTempDirectory()` — fully suitable |
| tlock stub | `StubTimeLockProvider` injected into `buildApp` — set `TLOCK_PROVIDER=stub` equivalent via constructor |
| Test driver | In-process (http4k `HttpHandler`) for the iteration gate |
| TOOL-001 role | Supplementary black-box validation only — not part of the gate |
| Gradle command | `./gradlew :HeirloomsServer:test` (Option A); introduce `integrationTest` task later |
| CI | `ubuntu-latest` GitHub Actions runner — no cloud credentials required |
| Key blocker | `buildApp` must accept `TimeLockProvider` — first M11 developer task must include this |
