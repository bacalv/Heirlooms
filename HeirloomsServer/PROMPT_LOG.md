# Prompt Log — HeirloomsServer Development Session

Timestamps are not available and are marked as `[unknown]`.

---

## Entry 1
**Time:** [unknown]
**Prompt:** Write a simple server that the Android application can post content to. The server should be written using Gradle, Kotlin and http4k. It should have a single endpoint: POST /api/content/upload. When receiving a file from the Android application, it should store it in a local directory using a random UUID as the filename and the appropriate file extension for the content type. The local storage location should be configurable in application.properties.

**Response:** Generated a complete http4k server project with five source files and 30 tests across three test classes.

**Files generated:**
- `HeirloomsServer/build.gradle.kts`
- `HeirloomsServer/settings.gradle.kts`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/Main.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/AppConfig.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/FileStorage.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/ContentTypeExtensions.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/UploadHandler.kt`
- `HeirloomsServer/src/main/resources/application.properties`
- `HeirloomsServer/src/test/kotlin/com/heirloom/server/ContentTypeExtensionsTest.kt`
- `HeirloomsServer/src/test/kotlin/com/heirloom/server/FileStorageTest.kt`
- `HeirloomsServer/src/test/kotlin/com/heirloom/server/UploadHandlerTest.kt`
- `HeirloomsServer/README.md`

---

## Entry 2
**Time:** [unknown]
**Prompt:** Could you rewrite the HeirloomsServer to use Java version 21.

**Response:** Changed `jvmToolchain(17)` to `jvmToolchain(21)` in `build.gradle.kts`. No other changes required.

**Files changed:**
- `HeirloomsServer/build.gradle.kts`

---

## Entry 3
**Time:** [unknown]
**Prompt:** This does not compile for me - it complains in Main.kt that the import 'asServer' on line 4 cannot be found.

**Response:** Identified the root cause as http4k version `5.14.1.0` having undergone a major package restructuring in v5/v6. Downgraded to stable `4.46.0.0` where all import paths are correct and which runs fine on Java 21.

**Files changed:**
- `HeirloomsServer/build.gradle.kts`

---

## Entry 4
**Time:** [unknown]
**Prompt:** Please tag both of the artefacts (the android app and the server) as version 0.0.1.

**Response:** Updated version to `0.0.1` in `build.gradle.kts`.

**Files changed:**
- `HeirloomsServer/build.gradle.kts`

---

## Entry 5
**Time:** [unknown]
**Prompt:** It worked. Please update the version of both apps to 0.1.0.

**Response:** Updated version to `0.1.0` in `build.gradle.kts`.

**Files changed:**
- `HeirloomsServer/build.gradle.kts`

---

## Entry 6
**Time:** [unknown]
**Prompt:** Can you create a file called "VERSION_HISTORY.md" and summarise the changes between each version?

**Response:** Created `VERSION_HISTORY.md` covering both projects across versions `0.0.1` and `0.1.0`.

**Files generated:**
- `HeirloomsServer/VERSION_HISTORY.md`

---

## Entry 7
**Time:** [unknown]
**Prompt:** In the Vaultserver, create an abstraction that stores the files. Currently, the code stores the file to a local directory. This will be the first implementation of the abstraction. The second implementation should store files in an Amazon S3 bucket using the asynchronous API. Ensure the prompt log is updated and documentation regarding how to set the amazon access and secret keys is documented in the README.

**Response:** Introduced `FileStore` interface and `StorageKey` value class. Renamed `FileStorage` to `LocalFileStore`. Added `S3FileStore` using AWS SDK v2 async client. Extended `AppConfig` with S3 properties. Updated `Main` to select backend from config. Updated `UploadHandler` to depend on the interface. Added `S3FileStoreTest` (8 tests). Updated README with full S3/IAM setup guide. Bumped version to `0.2.0`.

**Files changed/added:**
- `HeirloomsServer/build.gradle.kts`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/FileStore.kt` (new)
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/FileStorage.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/S3FileStore.kt` (new)
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/AppConfig.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/UploadHandler.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/Main.kt`
- `HeirloomsServer/src/main/resources/application.properties`
- `HeirloomsServer/src/test/kotlin/com/heirloom/server/LocalFileStoreTest.kt` (was FileStorageTest.kt)
- `HeirloomsServer/src/test/kotlin/com/heirloom/server/S3FileStoreTest.kt` (new)
- `HeirloomsServer/src/test/kotlin/com/heirloom/server/UploadHandlerTest.kt`
- `HeirloomsServer/README.md`
- `HeirloomsServer/VERSION_HISTORY.md`

---

## Entry 8
**Time:** [unknown]
**Prompt:** Yes (confirming to add PROMPT_LOG.md to the server project).

**Response:** Created this file.

**Files generated:**
- `HeirloomsServer/PROMPT_LOG.md` (this file)

---

## Entry 9
**Time:** [unknown]
**Prompt:** You asked "where to host the server". We should add a Dockerfile to each backend service and change the gradle build to generate a docker image. Also introduced PostgreSQL, Flyway, MinIO support, and a new HeirloomsTest project.

**Response:** Added PostgreSQL + HikariCP + Flyway. Added `Database.kt`, `UploadRecord`. Updated `AppConfig` with DB and S3 endpoint override properties and environment variable support. Updated `UploadHandler` with database integration, `GET /api/content/uploads`, and `GET /health`. Updated `S3FileStore` with endpoint override and `forcePathStyle`. Updated `Main.kt` to wire database. Added Flyway migration `V1__create_uploads_table.sql`. Added `Dockerfile`. Added Jib plugin to `build.gradle.kts`. Bumped version to `0.3.0`.

**Files changed/added:**
- `HeirloomsServer/build.gradle.kts`
- `HeirloomsServer/Dockerfile` (new)
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/Database.kt` (new)
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/AppConfig.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/UploadHandler.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/S3FileStore.kt`
- `HeirloomsServer/src/main/kotlin/com/heirloom/server/Main.kt`
- `HeirloomsServer/src/main/resources/application.properties`
- `HeirloomsServer/src/main/resources/db/migration/V1__create_uploads_table.sql` (new)
- `HeirloomsServer/VERSION_HISTORY.md`
