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

**Staging API key** (`<REDACTED_ROTATE_PENDING>`) appears in the git
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
git log --all -p | grep -E "k71CFcf59"  # (pattern-only; key value not logged)

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

**Completed:** 2026-05-15  
**Agent:** SecurityManager (SEC-010)

### Summary of work done

1. **Full git history scan** — scanned all 401 commits across all branches using
   `git log --all -S` (pickaxe) and broad grep patterns. Found the staging API key
   (`heirlooms-test-api-key` value) in 5 commits across 6 distinct file paths.
   No other credentials (passwords, private keys, production API keys, GCS JSON)
   were found anywhere in history.

2. **Working-tree redaction** — redacted the exposed key value from all 6 affected
   working-tree files. Replaced with `<REDACTED_ROTATE_PENDING>` plus a note to
   fetch from Secret Manager. Verified with `grep -r` that no occurrences remain.

3. **Pre-commit hook** — installed `.githooks/pre-commit` (tracked file, executable).
   Blocks 5 categories of credential patterns. Configured via `git config
   core.hooksPath .githooks`. Hook is active in the security worktree; must be
   run in each worktree.

4. **Scan report** — written to `docs/security/sec-010-scan-report.md`. Lists all
   commit SHAs, affected files, redactions performed, history rewrite decision
   (no rewrite — repo is private, key will be rotated), and recommendations.

### Update — 2026-05-15 (session 2)

Bret confirmed: Secret Manager version 2 of `heirlooms-test-api-key` was created
on 2026-05-15. Old version 1 is still enabled — to be disabled once Cloud Run
is confirmed using version 2. See scan report §8 for the disable command.

Additional file redacted in this session: `tasks/queue/TST-007_manual-staging-checklist-v054.md`
(file was created after the initial commit; key appeared in 2 places).

Scan report updated: key rotation status table updated, §8 "Recommended follow-up
actions" added with disable command, verification steps, and hook activation instructions.

### Outstanding actions (Bret — manual)

- **Disable Secret Manager version 1** of `heirlooms-test-api-key` after confirming staging works with version 2 (see scan report §8.1)
- Verify staging API works with new key (version 2)
- Add `git config core.hooksPath .githooks` to `scripts/create-agent-workspace.sh`
- Add no-hardcoding rule to CLAUDE.md and agent persona files (see scan report §8.3)

### Files changed

- `tasks/done/DONE-004_staging-environment.md` — 1 redaction
- `tasks/done/TST-003_manual-staging-checklist.md` — 2 redactions
- `tasks/queue/TST-004_playwright-e2e-suite.md` — 1 redaction
- `tasks/queue/TST-005_playwright-infrastructure.md` — 1 redaction (removed hardcoded fallback guidance)
- `tasks/queue/TST-007_manual-staging-checklist-v054.md` — 2 redactions (added in session 2)
- `docs/testing/TST-003_walkthrough.md` — 3 redactions
- `tasks/queue/SEC-010_git-history-secret-scan.md` — 1 redaction (this file)
- `.githooks/pre-commit` — new tracked hook file
- `docs/security/sec-010-scan-report.md` — new scan report
