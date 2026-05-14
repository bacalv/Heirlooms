# Task 10: JaCoCo Coverage Setup

## Summary

Added JaCoCo code coverage measurement for `HeirloomsServer`, driven by running the existing
integration tests in-process (no Docker server image required). The existing Docker Compose-based
`./gradlew :HeirloomsTest:test` path is fully preserved and unchanged.

## Changes

### `HeirloomsTest/settings.gradle.kts`
Added `includeBuild("../HeirloomsServer")` so that `HeirloomsTest` can resolve `HeirloomsServer`
as a composite build dependency (by GAV coordinates), enabling the test classpath to include the
server's compiled classes for both in-process startup and JaCoCo instrumentation.

### `HeirloomsTest/build.gradle.kts`
- Added `jacoco` plugin.
- Added `testImplementation("digital.heirlooms:HeirloomsServer:0.50.4")` (composite build substitution).
- Added `testImplementation` for `http4k-core` and `http4k-server-netty` (needed at compile time
  for in-process server assembly in `HeirloomTestEnvironment`).
- Added three new tasks:
  - **`coverageTest`**: Runs integration tests with `heirloom.test.mode=inprocess`. Finalises with `jacocoCoverageReport`.
  - **`jacocoCoverageReport`**: Generates HTML + XML JaCoCo reports from `coverageTest` execution data. Class directories point at `HeirloomsServer/build/classes/kotlin/main`, excluding `Main` and `AppConfig`.
  - **`jacocoCoverageVerify`**: Enforces 90% instruction coverage gate (currently informational — does not fail the build until the refactor milestone).

### `HeirloomsTest/src/test/kotlin/digital/heirlooms/test/HeirloomTestEnvironment.kt`
Added `inProcessMode` flag (read from system property `heirloom.test.mode`). When enabled:
1. Starts a `postgres:16-alpine` Testcontainer.
2. Starts a `minio/minio:latest` Testcontainer.
3. Creates the `heirloom-bucket` S3 bucket via the AWS SDK.
4. Builds an `AppConfig` pointing at the mapped ports.
5. Starts `HeirloomsServer` in-process via `corsFilter().then(sessionAuthFilter(...).then(buildApp(...)))`.asServer(Netty(18080))`.
6. Sets `baseUrl`, `minioBaseUrl`, `httpClient`, and `minioS3Client` companion vars.
7. Stops server and containers in `close()`.

The existing Docker Compose path is untouched and remains the default (when the system property is absent).

## How to Run

```bash
# Docker must be running (for Postgres + MinIO Testcontainers)
cd HeirloomsTest
./gradlew coverageTest --no-daemon
```

Reports are written to:
- `HeirloomsTest/build/reports/jacoco/html/index.html`
- `HeirloomsTest/build/reports/jacoco/coverage.xml`

The existing Docker Compose integration tests are unchanged:
```bash
./gradlew test --no-daemon
```
