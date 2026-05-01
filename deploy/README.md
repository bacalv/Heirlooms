# Heirlooms — Self-Hosted Deployment

This folder contains everything needed to run the full Heirlooms stack on a
home server or a cheap VPS (e.g. Hetzner CX22, DigitalOcean Droplet).

No Kubernetes, no cloud accounts, no app store required.

---

## What gets deployed

| Service | Purpose | Port |
|---|---|---|
| `postgres` | Metadata database | internal only |
| `minio` | File storage (S3-compatible) | 9001 (admin console) |
| `minio-init` | Creates the storage bucket on first run | — |
| `heirloom-server` | The Heirlooms API | **8080** |

---

## Prerequisites

- Docker and Docker Compose v2 installed on the server
- Git (to clone the repo)
- The server's port 8080 open in its firewall (and 9001 if you want the MinIO console)

---

## First-time setup

**1. Clone the repo**

```bash
git clone https://github.com/bacalv/Heirlooms.git
cd Heirlooms
```

**2. Create your `.env` file**

```bash
cp deploy/.env.example deploy/.env
```

Open `deploy/.env` and change the three `change_me_*` values to strong passwords
of your choosing. Keep this file private — it is already excluded from git.

**3. Build and start**

```bash
cd deploy
docker compose up -d --build
```

The first run downloads base images and builds the server JAR. This takes a few
minutes. Subsequent starts (when the image is cached) are much faster.

**4. Verify it's running**

```bash
curl http://localhost:8080/health
# → ok
```

---

## Pointing the Android app at your server

Open the Heirlooms app, tap the settings icon from the share sheet, and set the
endpoint to:

```
http://<your-server-ip>:8080/api/content/upload
```

---

## Stopping and starting

```bash
# Stop (data is preserved in Docker volumes)
docker compose down

# Start again
docker compose up -d

# Stop and wipe all data (destructive — deletes photos/videos and database)
docker compose down -v
```

---

## Updating to a new version

```bash
git pull
docker compose up -d --build
```

Docker rebuilds the server image and restarts only the changed service.
Postgres and MinIO data volumes are untouched.

---

## MinIO admin console

Available at `http://<your-server-ip>:9001`. Log in with the `MINIO_ROOT_USER`
and `MINIO_ROOT_PASSWORD` values from your `.env` file. Close port 9001 in your
firewall if you do not need the console.

---

## Relationship to HeirloomsTest

The integration tests use their own separate compose file at
`HeirloomsTest/src/test/resources/docker-compose.yml`. That file uses hardcoded
test credentials and Testcontainers-style port mappings. It is not used here
and should not be modified.