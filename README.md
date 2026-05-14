# Heirlooms

A personal digital vault — share photos and videos from your phone to your own server.

## Contents

| Directory | Description |
|---|---|
| `HeirloomsServer/` | Kotlin/http4k backend server — stores metadata in PostgreSQL, media in GCS |
| `HeirloomsApp/` | Android app (Jetpack Compose) — E2EE upload, garden, shared plots |
| `HeirloomsWeb/` | React/Vite web app — garden, uploads, shared plots |
| `HeirloomsiOS/` | Swift/SwiftUI iOS app — E2EE upload, home grid, QR activation |
| `HeirloomsTest/` | Integration test suite — spins up the full stack via Testcontainers |

## Docs

| File | Description |
|---|---|
| [tasks/progress.md](tasks/progress.md) | Task queue — what's queued, in-progress, and done |
| [ROADMAP.md](docs/ROADMAP.md) | Product vision and milestone plan |
| [TEAM.md](docs/TEAM.md) | Team structure and personas |
| [envelope_format.md](docs/envelope_format.md) | E2EE envelope format specification |
| [docs/sessions/](docs/sessions/) | Archived session logs and change notes |

## AI Team

Heirlooms uses a persona-based AI team to coordinate development. Start a new session with:

```
@personalities/PA.md
```

This loads the PA persona — your personal assistant who reads the task queue, dispatches specialist agents (Developer, SecurityManager, TestManager, OpsManager, TechnicalArchitect), and reports back. See `personalities/PERSONAS.md` for the full team structure.

---

## One-time machine setup

Before running the tests for the first time, create this file in your home directory.
Find your correct Docker socket path first:

```bash
ls ~/Library/Containers/com.docker.docker/Data/docker.raw.sock
```

Then create the file (replace YOUR_USERNAME):

```
~/.testcontainers.properties

docker.host=unix:///Users/YOUR_USERNAME/Library/Containers/com.docker.docker/Data/docker.raw.sock
testcontainers.reuse.enable=false
ryuk.disabled=true
```

This tells Testcontainers which Docker socket to use on macOS with Docker Desktop.
It only needs to be done once per machine.

---

## Running the tests

Requires Docker Desktop, Java 21+, and an internet connection on first run.

```bash
chmod +x run-tests.sh
./run-tests.sh
```

This single command will:
1. Download the Gradle wrapper if needed (first run only)
2. Build the HeirloomsServer Docker image
3. Start PostgreSQL, MinIO, and HeirloomsServer in Docker
4. Run all end-to-end tests against the live stack
5. Shut down all containers
6. Open the HTML test report (macOS only)

Report location:
```
HeirloomsTest/build/reports/heirloom-test/index.html
```

---

## Building the Android app

Open `HeirloomsApp/` in Android Studio or IntelliJ IDEA. Enter your server URL in the
app settings screen, then:

```bash
cd HeirloomsApp
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Running the server locally

Edit `HeirloomsServer/src/main/resources/application.properties`, then:

```bash
cd HeirloomsServer
./gradlew run
```
