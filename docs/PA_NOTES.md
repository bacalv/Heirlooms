# PA Notes — Working Memory

This file is maintained by the PA (claude.ai) as a working memory across sessions.
It captures things that are easy to forget but important to remember — preferences,
patterns, pending decisions, and context that doesn't fit neatly into PROMPT_LOG.md.

---

## Bret's preferences and working style

- Prefers IntelliJ IDEA Community Edition over Android Studio for this project
- Uses IntelliJ's built-in Git UI for commits; does all pushes manually as a
  deliberate human checkpoint before code enters the remote repo
- Dislikes the macOS Finder file picker — use `Cmd+Shift+G` to navigate by path
- Prefers the Claude Code plugin for hands-on fixes; uses claude.ai for architecture
  and product thinking
- Tends to work in focused sessions and likes a clean summary at the end of each one
- Appreciates being told when something is a one-time setup vs ongoing requirement

---

## Things to always remember

- **Package name:** `digital.heirlooms` (not `com.heirloom` — that was the old name)
- **Domain:** `heirlooms.digital` (registered 30 April 2026)
- **GitHub:** `github.com/bacalv/Heirlooms` (capital H)
- **Current version:** v0.4.0 (tagged on main)
- **One-time machine setup required:** `~/.testcontainers.properties` with
  `docker.raw.sock` path — see PROMPT_LOG.md for details

---

## Pending decisions / next actions

- **Milestone 3 — DONE (v0.4.0):** deploy/ folder added, tested locally in Docker,
  tagged and pushed. See PROMPT_LOG.md for details.

- **Milestone 3 deployment — next session:** Provision Hetzner CX22, point
  `heirlooms.digital` A record at its IP, run `docker compose up -d --build`
  from deploy/. Verify health endpoint. Update Android app endpoint.

- **HTTPS:** Deferred to Milestone 4. Will add Caddy as a reverse proxy in the
  deploy/ compose file to handle SSL via Let's Encrypt.
- **heirlooms.com:** Currently parked on venture.com. Worth monitoring — only worth
  acquiring if the project grows to consumer scale
- **License:** Deliberately deferred. Revisit when deciding whether Heirlooms will
  be open source, source-available, or strictly proprietary

---

## Things that tripped us up (don't repeat)

- The `~/.testcontainers.properties` file must use `docker.raw.sock`, not
  `docker.sock` or `docker-cli.sock` — the others return stub 400 responses on
  macOS Docker Desktop
- Zip files must be built fresh each time — `zip -r` with `--exclude` only works
  on a clean build; updating an existing zip preserves entries that were already in it
- `.idea/` must always be excluded from zips and commits — IntelliJ generates it
  fresh on first open and stale paths cause import failures
- `local.properties` in HeirloomsApp must point to the Android SDK:
  `sdk.dir=/Users/bac/Library/Android/sdk`

---

## Team reminders

- The Software Engineer (Claude Code) creates commits but **Bret always pushes**
- Ask the Software Engineer to update PROMPT_LOG.md after significant code changes
- At the start of a new claude.ai session, paste both PROMPT_LOG.md and TEAM.md
- PA_NOTES.md (this file) should also be pasted in if working memory is needed
