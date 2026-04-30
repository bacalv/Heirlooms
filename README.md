# Heirlooms

A personal digital vault — share photos and videos from your phone to your own server.

## Contents

| Directory | Description |
|---|---|
| `HeirloomsServer/` | Kotlin/http4k backend server — receives uploads, stores metadata in PostgreSQL, files in MinIO/S3 |
| `HeirloomsApp/` | Android app — adds "My Heirlooms" to the system share sheet |
| `HeirloomsTest/` | End-to-end test suite — spins up the full stack in Docker and runs tests |

## Docs

| File | Description |
|---|---|
| [ROADMAP.md](docs/ROADMAP.md) | Product vision, milestone plan, and key concepts explored during the founding session |
| [PROMPT_LOG.md](docs/PROMPT_LOG.md) | Running record of key decisions, prompts, and technical choices made during development |
| [TEAM.md](docs/TEAM.md) | Team structure: roles, responsibilities, and working agreements |
| [PA_NOTES.md](docs/PA_NOTES.md) | PA (claude.ai) working memory — preferences, project facts, pending decisions, and known gotchas |
| [SE_NOTES.md](docs/SE_NOTES.md) | Software Engineer (Claude Code) working memory — session context, commit conventions, and project notes |
| [chats/2026-04-30-initial-chat.md](docs/chats/2026-04-30-initial-chat.md) | Original claude.ai founding session chat, formatted as markdown (356 turns, 24–30 April 2026) |

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
