---
id: SEC-010
title: Git history secret scan report
date: 2026-05-15
author: SecurityManager
status: complete
---

# SEC-010 — Git History Secret Scan Report

## 1. Scope

Full scan of all commits across all local branches (401 total commits at time of
scan) for hardcoded secrets, credentials, API keys, private key material, and
database passwords.

The scan targeted:
- The known exposed staging API key (secret name: `heirlooms-test-api-key`)
- Generic base64url token patterns ≥ 40 chars adjacent to API-key context
- PEM private key headers (`-----BEGIN ...`)
- Password-style env var assignments
- GCS service account JSON fragments
- JWT secrets and signing keys

Scanning method: `git log --all -S <pattern>` (pickaxe) for targeted searches;
`git log --all -p | grep` for broad pattern sweeps. No external tools required.

---

## 2. Findings

### 2.1 Exposed credential: staging API key

**Secret Manager secret name:** `heirlooms-test-api-key`  
**Exposure type:** staging-only API key — no production access  
**Scope:** docs, task files, and session logs committed to the main branch

The key value appeared in the following commits and files. Commit SHAs are listed
in full; the key value itself is not reproduced in this report.

| Commit (short) | Full SHA prefix | Date | File(s) affected |
|---|---|---|---|
| `e478f0c9` | `e478f0c9828e1d46ef9eb776db437e9a861b94c7` | 2026-05-14 | `tasks/queue/TST-003_manual-staging-checklist.md`, `tasks/queue/TST-004_playwright-e2e-suite.md` |
| `82659cee` | `82659ceeb712bcd6be158d7569fa9340a0aba275` | 2026-05-14 | `tasks/queue/TST-003_manual-staging-checklist.md` (via path rename from queue to done) |
| `d4b0f084` | `d4b0f0843cf58a840264e0891ddd95f303dc6581` | 2026-05-14 | `tasks/queue/TST-004_playwright-e2e-suite.md`, `tasks/queue/TST-005_playwright-infrastructure.md` |
| `00f0b810` | `00f0b810f6421e4dea897bc1b4d50c267c867dbf` | 2026-05-15 | `docs/testing/TST-003_walkthrough.md`, `tasks/in-progress/TST-003_manual-staging-checklist.md` |
| `db64315e` | `db64315e0cbe23f4700b1c19fef0f1434683447d` | 2026-05-15 | `tasks/done/TST-003_manual-staging-checklist.md` |

**Files where the key appeared at some point in history:**
- `tasks/queue/TST-003_manual-staging-checklist.md`
- `tasks/in-progress/TST-003_manual-staging-checklist.md`
- `tasks/done/TST-003_manual-staging-checklist.md`
- `tasks/queue/TST-004_playwright-e2e-suite.md`
- `tasks/queue/TST-005_playwright-infrastructure.md`
- `docs/testing/TST-003_walkthrough.md`
- `tasks/done/DONE-004_staging-environment.md` (current tree only — in history via initial add)

### 2.2 Other credential patterns

No other credential values were found.

| Pattern checked | Result |
|---|---|
| `-----BEGIN (RSA/EC/OPENSSH/PRIVATE)` | Not found in any commit |
| `DB_PASS` / `DATABASE_PASSWORD` / `POSTGRES_PASSWORD` | Not found with plaintext values |
| `JWT_SECRET` / `SECRET_KEY` = plaintext | Not found |
| GCS service account JSON (private_key + PEM header pattern) | Not found |
| GCS credentials JSON blob | Not found — only Secret Manager secret names referenced |
| Production API key | Not found — Cloud Run deployments reference Secret Manager names only |
| `heirlooms-gcs-credentials` value | Not found — name only, no JSON blob |

**Conclusion:** No production secrets or key material are present in git history.
Only the staging API key (a low-privilege test credential) was exposed.

---

## 3. Working-tree redactions

The following files in the current working tree contained the exposed key value
and have been redacted. The credential value was replaced with
`<REDACTED_ROTATE_PENDING>` with a note to fetch from Secret Manager.

| File | Occurrences redacted |
|---|---|
| `tasks/done/DONE-004_staging-environment.md` | 1 |
| `tasks/done/TST-003_manual-staging-checklist.md` | 2 |
| `tasks/queue/TST-004_playwright-e2e-suite.md` | 1 |
| `tasks/queue/TST-005_playwright-infrastructure.md` | 1 (note updated to require env var, no fallback) |
| `docs/testing/TST-003_walkthrough.md` | 3 |
| `tasks/queue/SEC-010_git-history-secret-scan.md` | 1 (task file itself) |

Verification: `grep -r "k71CFcf59rdvmFqfV" .` returns no results in the current
working tree after redaction.

---

## 4. Git history rewrite decision

**Decision: No history rewrite at this time.**

Rationale:
- The repository is **private** with a small, known team. Public exposure risk is
  negligible.
- The exposed key is **staging-only** — it provides access only to the test
  environment, not production data.
- The key is being **rotated** by Bret (CTO) via Secret Manager. Once the old
  version is disabled, the exposed value is harmless.
- A history rewrite (BFG Repo Cleaner or `git filter-repo`) would disrupt all
  existing worktrees and agent branches, which currently number 10+. The
  operational cost outweighs the residual risk.
- The old value remains in history but will be a dead credential after rotation.

**If the repository is ever made public**, a history rewrite must be performed
before making it public. See: https://rtyley.github.io/bfg-repo-cleaner/

---

## 5. Prevention measures installed

### 5.1 Pre-commit hook

A pre-commit hook has been installed at `.githooks/pre-commit` (tracked in the
repository). It blocks commits containing:

1. The known staging API key prefix pattern
2. API key / bearer token values ≥ 40 chars adjacent to `api_key`/`token` keywords
3. PEM private key headers (`-----BEGIN RSA PRIVATE KEY`, etc.)
4. Plaintext password env-var assignments (`DB_PASS=`, `JWT_SECRET=`, etc.)
5. GCS service account JSON private key fields

**Installation** (required for each checkout/worktree):

```bash
git config core.hooksPath .githooks
```

This has been configured for the security worktree. It should be run in all other
worktrees and in the main worktree at `~/IdeaProjects/Heirlooms/`.

The hook is enforced via `core.hooksPath` — this is a per-worktree git config
setting and must be set in each worktree separately. Consider adding it to
`scripts/create-agent-workspace.sh` so new worktrees are automatically configured.

### 5.2 Recommended CLAUDE.md addition

The following rule should be added to `CLAUDE.md` and all agent persona files:

> **Never hardcode secret values in task files, docs, or code.** Reference the
> Secret Manager secret name only (e.g. `heirlooms-test-api-key`). To use a
> secret locally, fetch it at runtime:
> `gcloud secrets versions access latest --secret=<name> --project heirlooms-495416`

---

## 6. Recommendations

1. **Rotate the staging key** (Bret — manual action): add a new version to
   `heirlooms-test-api-key` in Secret Manager and disable the old version. This
   renders the exposed value permanently useless.

2. **Update `scripts/create-agent-workspace.sh`**: add `git config core.hooksPath .githooks`
   after workspace creation so all future agent worktrees get the hook automatically.

3. **Add `git config core.hooksPath .githooks` to onboarding docs** for any new
   developer cloning the repository.

4. **Consider adding `trufflehog` or `gitleaks` to CI** as a secondary scan on
   every pull request (in addition to the local pre-commit hook). This provides
   defence-in-depth if the hook is skipped.

5. **Update CLAUDE.md** with the no-hardcoding rule described in §5.2.

---

## 7. Key rotation status

| Action | Responsible | Status |
|---|---|---|
| Redact working-tree files | SecurityManager (SEC-010) | DONE |
| Install pre-commit hook | SecurityManager (SEC-010) | DONE |
| Rotate key in Secret Manager (new version 2 created) | Bret (CTO) | DONE — 2026-05-15 |
| Disable old key version (version 1) | Bret (CTO) | PENDING — see §8 |
| Verify staging API works with new key | Bret (CTO) | PENDING |
| Update `create-agent-workspace.sh` | OpsManager | RECOMMENDED |

---

## 8. Recommended follow-up actions (Bret — manual)

### 8.1 Disable Secret Manager version 1 (REQUIRED)

Version 2 of `heirlooms-test-api-key` was created by Bret on 2026-05-15. Version 1
(the exposed value) is **still enabled** in Secret Manager as of 2026-05-15.

Once Cloud Run staging (`heirlooms-server-test`) is confirmed to be using the new
version 2 key, disable version 1:

```bash
gcloud secrets versions disable 1 \
  --secret=heirlooms-test-api-key \
  --project heirlooms-495416
```

To confirm Cloud Run is using the new version, verify staging health and that an
invite can be generated with the new key:

```bash
# Fetch the new key value
NEW_KEY=$(gcloud secrets versions access 2 \
  --secret=heirlooms-test-api-key --project heirlooms-495416)

# Test it against staging
curl -s -H "X-API-Key: $NEW_KEY" \
  https://test.api.heirlooms.digital/api/auth/invites
```

Expected: HTTP 200 with an invite token JSON. Once confirmed, disable version 1.

### 8.2 Activate pre-commit hook in all worktrees

Run the following in the main worktree and each active agent worktree:

```bash
git config core.hooksPath .githooks
```

Or add it to `scripts/create-agent-workspace.sh` so future worktrees get it
automatically (see recommendation 2 in §6 above).

### 8.3 Add no-hardcoding rule to CLAUDE.md and persona files

Add to `CLAUDE.md` (under "Agent constraints") and all agent persona files:

> **Never hardcode secret values** in task files, docs, or code. Reference the
> Secret Manager secret name only (e.g. `heirlooms-test-api-key`). To use a
> secret locally, fetch it at runtime:
> `gcloud secrets versions access latest --secret=<name> --project heirlooms-495416`
