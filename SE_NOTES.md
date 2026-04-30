# SE Notes — Software Engineer Working Memory

Maintained by the Software Engineer (Claude Code in IntelliJ). Things worth
remembering between sessions that don't belong in PROMPT_LOG.md or PA_NOTES.md.

---

## Getting context at the start of a session

Referencing `@PROMPT_LOG.md` in the first message is enough for most code-level
tasks. For larger or architectural work, also reference `@TEAM.md` and
`@PA_NOTES.md`. I will read them before doing anything.

I also maintain a persistent memory store that survives between sessions:
`~/.claude/projects/-Users-bac-Downloads-Heirlooms/memory/`
This is loaded automatically — Bret does not need to paste it in.

---

## Commit conventions

- Short subject line, blank line, brief body only if the why isn't obvious
- Always include: `Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>`
- Update PROMPT_LOG.md **in the same commit** as the change it documents — not
  as a follow-up
- Create commits freely; **never push** — Bret always does the final push

---

## Project structure

Three Gradle subprojects under `/Users/bac/Downloads/Heirlooms/`:

| Subproject | Package | Purpose |
|---|---|---|
| `HeirloomsApp` | `digital.heirlooms.app` | Android share-target app |
| `HeirloomsServer` | `digital.heirlooms.server` | Kotlin/http4k backend |
| `HeirloomsTest` | `digital.heirlooms.test` | Testcontainers integration tests |

---

## Things to remember

- Package is `digital.heirlooms` — `com.heirloom` was the old name, fully replaced
- `HeirloomsTest` requires `~/.testcontainers.properties` with `docker.raw.sock`
  (see PA_NOTES.md for details) — one-time machine setup, not a code problem
- `local.properties` in `HeirloomsApp` must contain:
  `sdk.dir=/Users/bac/Library/Android/sdk`