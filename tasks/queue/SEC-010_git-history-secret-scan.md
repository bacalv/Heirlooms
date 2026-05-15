---
id: SEC-010
title: Git history secret scan — rotate exposed staging API key, audit for others
category: Security
priority: High
status: queued
depends_on: []
touches:
  - tasks/queue/TST-005_playwright-infrastructure.md
  - tasks/in-progress/TST-003_manual-staging-checklist.md
  - docs/ (any files referencing the key)
assigned_to: SecurityManager
estimated: half day
---

## Background

During TST-003 (2026-05-15), the Test Manager found the staging API key hardcoded in
multiple committed files across the git history. Bret flagged this for a security review.

## Confirmed exposure

**Staging API key** (`k71CFcf59rdvmFqfV_nZhBd4W7DUao4jAvRvmTE4neA`) appears in the git
history in at minimum:

- `tasks/queue/TST-005_playwright-infrastructure.md`
- `tasks/in-progress/TST-003_manual-staging-checklist.md`
- `docs/PA_NOTES.md` (historical versions)
- Various session/prompt log docs

This is a **staging-only** key. No production secrets were found hardcoded — production
Cloud Run commands in history only reference Secret Manager secret names, not values.

## Tasks

### 1. Full scan (start here)

Run a thorough scan across all commits and all branches for any hardcoded secret values:

```bash
# Known staging key
git log --all -p | grep -E "k71CFcf59"

# Broader patterns: anything resembling a real secret value
git log --all -p | grep -E "^\+" | grep -iE \
  "(password|api.key|api_key|secret|token|private.key|BEGIN RSA|BEGIN EC)\s*[=:]\s*['\"]?[A-Za-z0-9+/\_\-]{20,}"
```

Also check for:
- GCS service account JSON or credentials (even partial)
- Database passwords
- Any `-----BEGIN` key material
- JWT secrets or signing keys

### 2. Rotate the staging API key

The staging test API key must be rotated regardless of exposure risk:

1. Generate a new key in Secret Manager:
   ```bash
   gcloud secrets versions add heirlooms-test-api-key \
     --data-file=<(openssl rand -base64 32) \
     --project heirlooms-495416
   ```
2. Update the staging Cloud Run service to use the new secret version
3. Verify: `curl -H "X-API-Key: <new-key>" https://test.api.heirlooms.digital/api/auth/invites`
4. Disable the old version in Secret Manager

### 3. Redact from working tree

After rotating:
- Remove the hardcoded key from all current working-tree files
- Replace with `(rotated — fetch from Secret Manager)` or fetch dynamically:
  ```bash
  gcloud secrets versions access latest --secret=heirlooms-test-api-key --project heirlooms-495416
  ```
- Future task files and docs must never embed the actual key value

### 4. Assess git history rewrite

Evaluate whether to rewrite git history (BFG Repo Cleaner or `git filter-repo`)
to remove the key from all commits:

**Factors for rewriting:**
- Repo is public or will become public
- Key gives access to sensitive staging data

**Factors against:**
- Repo is private — exposure risk is lower
- History rewrite disrupts all existing clones and branches
- Key will be rotated anyway (old value becomes useless)

**Recommendation:** If the repo is and will remain private, rotation + redaction is
sufficient. Document the decision either way.

### 5. Prevent recurrence

- Add a pre-commit hook or CI check (e.g. `gitleaks`, `trufflehog`) to block
  future secret commits
- Add a note to `CLAUDE.md` and agent personas: never hardcode secret values in
  task files — reference the Secret Manager secret name only

## Acceptance criteria

- Staging API key rotated; old version disabled in Secret Manager
- All current working-tree files scrubbed of the old key value
- Full scan completed with findings documented
- Decision on history rewrite recorded with rationale
- Prevention measure in place (hook or CI check)

## Completion notes

<!-- SecurityManager appends here and moves file to tasks/done/ -->
