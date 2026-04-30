# Prompt Log — HeirloomsTest Development Session

Timestamps are not available and are marked as `[unknown]`.

---

## Entry 1
**Time:** [unknown]
**Prompt:** You asked "where to host the server". Obviously we must always be able to run on a local machine for testing purposes. In order to maintain compatibility with most cloud providers (e.g. Amazon EC2, Google Cloud etc) we should make sure that each backend artefact generated can run in a Docker container. We should add a Dockerfile to each backend service generated and change the gradle build to generate a docker image. Then we can decide which cloud provider to run it on in the future. In order to test our system, we should generate a new 'test' project that: 1. Locally deploys fresh builds of all docker images needed to run the system (including third party containers such as database servers etc) 2. Runs tests against the local deployment that simulate user journeys through the system (using tools like Playwright and by calling the REST API directly) 3. After test execution, shuts down all of the running docker images created in step 1 4. Outputs the test results in HTML or Markdown form.

**Response:** Created HeirloomsTest project. Added PostgreSQL and Flyway to HeirloomsServer. Added Dockerfile to HeirloomsServer. Added Jib plugin for Docker image generation. Created Docker Compose stack (HeirloomsServer + PostgreSQL + MinIO). Implemented Testcontainers-based test infrastructure. Wrote REST API tests and Playwright browser journey tests. Added run-tests.sh convenience script. Updated HeirloomsServer UploadHandler with database integration, list endpoint, and health check.

**Files generated:**
- `HeirloomsTest/build.gradle.kts`
- `HeirloomsTest/settings.gradle.kts`
- `HeirloomsTest/gradle.properties`
- `HeirloomsTest/run-tests.sh`
- `HeirloomsTest/README.md`
- `HeirloomsTest/VERSION_HISTORY.md`
- `HeirloomsTest/PROMPT_LOG.md` (this file)
- `HeirloomsTest/src/test/resources/docker-compose.yml`
- `HeirloomsTest/src/test/kotlin/com/heirloom/test/HeirloomsTestEnvironment.kt`
- `HeirloomsTest/src/test/kotlin/com/heirloom/test/HeirloomsTest.kt`
- `HeirloomsTest/src/test/kotlin/com/heirloom/test/api/UploadApiTest.kt`
- `HeirloomsTest/src/test/kotlin/com/heirloom/test/journey/UploadJourneyTest.kt`
