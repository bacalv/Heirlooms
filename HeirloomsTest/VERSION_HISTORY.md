# Version History — HeirloomsTest

---

### 0.2.0
**Initial release**

- Docker Compose environment: HeirloomsServer + PostgreSQL 16 + MinIO
- Testcontainers manages full stack lifecycle (start on first test, stop after last)
- `@HeirloomsTest` meta-annotation for easy test class setup
- REST API tests: health check, upload (image/video/octet-stream), empty body rejection, listing
- Playwright (Chromium) journey tests: upload and verify via browser fetch, multi-type upload journey, health reachable from browser
- `run-tests.sh` — one-command build + test + report
- HTML test report at `build/reports/heirloom-test/index.html`

## 0.3.0 — 30 April 2026

- Project renamed from VaultShare → Heirloom → Heirlooms
- Package names updated from `com.vaultshare` to `com.heirloom` throughout
- Domain `heirlooms.digital` registered
- All "Vault" terminology removed to avoid confusion with secret/credential vaults
- ROADMAP.md and PROMPT_LOG.md added to project root documenting product vision,
  early brainstorm, and full decision history
