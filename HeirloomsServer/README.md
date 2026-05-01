# HeirloomsServer

A minimal http4k server (Kotlin + Gradle) that receives file uploads from the Heirloom
Android app and stores them via a configurable storage backend — either a local directory
or an Amazon S3 bucket.

---

## API documentation

An interactive Swagger UI is served at:

```
http://localhost:8080/docs
```

The raw OpenAPI 3.0 spec (JSON) is at:

```
http://localhost:8080/api/content/openapi.json
```

> The Swagger UI loads assets from the unpkg CDN — the browser making the request
> needs internet access. The server itself does not.

---

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/content/upload` | Upload a file |
| `GET` | `/api/content/uploads` | List all uploaded files |
| `GET` | `/health` | Health check |
| `GET` | `/docs` | Swagger UI |
| `GET` | `/api/content/openapi.json` | OpenAPI 3.0 spec |

### POST /api/content/upload

- **Request body**: raw file bytes
- **`Content-Type` header**: MIME type of the file (e.g. `image/jpeg`, `video/mp4`)
- **201 Created**: file stored — response body contains the storage key (e.g. `a3f7…c1.mp4`)
- **400 Bad Request**: empty request body
- **500 Internal Server Error**: file could not be stored

Uploaded files are stored as `<UUID>.<extension>` where the extension is derived from the
Content-Type header.

---

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Port the server listens on
server.port=8080

# Storage backend: LOCAL or S3
storage.backend=LOCAL

# --- Local storage (used when storage.backend=LOCAL) ---
storage.dir=uploads

# --- S3 storage (used when storage.backend=S3) ---
s3.bucket=your-bucket-name
s3.region=eu-west-1
s3.access-key=YOUR_ACCESS_KEY
s3.secret-key=YOUR_SECRET_KEY
```

---

## Setting up Amazon S3 storage

### 1. Create an S3 bucket

Log in to the [AWS Console](https://console.aws.amazon.com/s3) and create a new bucket.
Note the bucket name and the AWS region it is in (e.g. `eu-west-1`, `us-east-1`).

### 2. Create an IAM user with S3 access

1. Go to **IAM → Users → Create user**
2. Give the user a name (e.g. `heirloom-server`)
3. Select **Attach policies directly** and attach `AmazonS3FullAccess` (or create a
   custom policy scoped to just the specific bucket — see below)
4. Complete the user creation, then go to **Security credentials → Access keys →
   Create access key**
5. Choose **Application running outside AWS**, then copy the **Access key ID** and
   **Secret access key** — you will not be able to see the secret again

**Minimal IAM policy (recommended over full S3 access):**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::your-bucket-name/*"
    }
  ]
}
```

### 3. Configure application.properties

```properties
storage.backend=S3
s3.bucket=your-bucket-name
s3.region=eu-west-1
s3.access-key=AKIAIOSFODNN7EXAMPLE
s3.secret-key=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
```

> **Security note:** Do not commit `application.properties` containing real credentials
> to version control. Consider using environment variable substitution or AWS Secrets
> Manager for production deployments.

---

## Running

### Via Gradle
```bash
./gradlew run
```

### Fat JAR
```bash
./gradlew jar
java -jar build/libs/HeirloomsServer-0.2.0.jar
```

The server prints the active backend and port on startup, then blocks until interrupted.

---

## Running the tests

```bash
./gradlew test
# Report: build/reports/tests/test/index.html
```

### Test coverage

| File | What is tested |
|---|---|
| `ContentTypeExtensionsTest` | MIME → extension mapping for images, videos, unknown types, case insensitivity, charset stripping |
| `LocalFileStoreTest` | UUID naming, correct extension, exact bytes on disk, multiple saves get different UUIDs, nested directory creation |
| `S3FileStoreTest` | Storage key format, bucket/key/Content-Type/Content-Length in PutObject request, exactly one call per save, S3 failure wrapped in RuntimeException |
| `UploadHandlerTest` | 201 on success, key in response, correct bytes and MIME type passed to storage, charset stripping, missing Content-Type fallback, 400 on empty body, 500 on storage exception, 404 on wrong method or unknown path |

---

## Project structure

```
HeirloomsServer/
├── src/
│   ├── main/
│   │   ├── kotlin/com/heirloom/server/
│   │   │   ├── Main.kt                  ← entry point, selects storage backend
│   │   │   ├── AppConfig.kt             ← loads application.properties
│   │   │   ├── FileStore.kt             ← storage abstraction interface
│   │   │   ├── FileStorage.kt           ← LocalFileStore implementation
│   │   │   ├── S3FileStore.kt           ← S3FileStore implementation
│   │   │   ├── ContentTypeExtensions.kt ← MIME type → file extension
│   │   │   └── UploadHandler.kt         ← HTTP route + handler
│   │   └── resources/
│   │       └── application.properties   ← configuration
│   └── test/
│       └── kotlin/com/heirloom/server/
│           ├── ContentTypeExtensionsTest.kt
│           ├── LocalFileStoreTest.kt
│           ├── S3FileStoreTest.kt
│           └── UploadHandlerTest.kt
├── build.gradle.kts
└── settings.gradle.kts
```
