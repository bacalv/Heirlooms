# HeirloomsTest

End-to-end test suite for the Heirloom system. Spins up a full local
environment using Docker Compose, runs REST API and browser-based tests
against it, then tears everything down.

---

## What runs in the test environment

| Container | Purpose |
|---|---|
| `heirloom-server:latest` | The HeirloomsServer under test |
| `postgres:16-alpine` | PostgreSQL metadata database |
| `minio/minio` | S3-compatible object storage |
| `minio/mc` | One-shot init container — creates the test bucket |

All containers are started and stopped automatically by Testcontainers.
No manual `docker-compose up/down` is needed.

---

## Running the tests

### Directory structure required
HeirloomsTest and HeirloomsServer must be extracted into the same parent folder:
```
parent/
  HeirloomsServer/
  HeirloomsTest/
```
If you extracted them elsewhere, set `VAULT_SERVER_DIR`:
```bash
VAULT_SERVER_DIR=/path/to/HeirloomsServer ./run-tests.sh
```

### Quick start (recommended)
```bash
# From the HeirloomsTest directory
chmod +x run-tests.sh
./run-tests.sh
```

This script:
1. Builds the HeirloomsServer fat JAR
2. Builds the `heirloom-server:latest` Docker image
3. Runs all tests (Testcontainers starts/stops the stack)
4. Opens the HTML report

### Run with a specific image tag
```bash
./run-tests.sh 0.2.0
```

### Run tests only (image already built)
```bash
./gradlew test -Pheirloom-serverImage=heirloom-server:latest
```

### HTML report
After a test run, the report is at:
```
build/reports/heirloom-test/index.html
```

---

## Test structure

```
src/test/kotlin/com/heirloom/test/
├── HeirloomsTestEnvironment.kt   ← JUnit 5 extension, Docker Compose lifecycle
├── HeirloomsTest.kt              ← @HeirloomsTest meta-annotation
├── api/
│   └── UploadApiTest.kt      ← REST API tests (OkHttp)
└── journey/
    └── UploadJourneyTest.kt  ← Browser-based tests (Playwright)
```

### API tests (`api/`)
Call the HeirloomsServer REST API directly using OkHttp. Fast, no browser needed.
Cover: health check, upload returns 201, correct file extension, empty body
returns 400, uploads appear in listing.

### Journey tests (`journey/`)
Use Playwright (Chromium) to simulate what a real user or the web viewer
would do. Currently validate the API via `fetch()` in a browser context.
When the web viewer UI is added (Phase 1 roadmap), these tests will drive
actual UI interactions.

---

## Requirements

- Docker (with Compose v2)
- Java 21+
- Gradle (or use the wrapper `./gradlew`)

Playwright downloads Chromium automatically on first run.

---

## Adding new tests

**API test** — extend `UploadApiTest` or add a new file under `api/`:
```kotlin
@HeirloomsTest
class MyNewApiTest {
    private val base get() = HeirloomsTestEnvironment.vaultBaseUrl
    private val client get() = HeirloomsTestEnvironment.httpClient

    @Test
    fun `my new scenario`() { ... }
}
```

**Journey test** — extend `UploadJourneyTest` or add under `journey/`.
Create a `Page` instance from the shared `Browser` and navigate to
`HeirloomsTestEnvironment.vaultBaseUrl`.

The Docker Compose stack starts once for the entire test run regardless
of how many test classes use `@HeirloomsTest`.
