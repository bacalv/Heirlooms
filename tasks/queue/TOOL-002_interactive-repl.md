---
id: TOOL-002
title: Interactive REPL — heirlooms> command shell for the capsule API
category: Tools
priority: Medium
status: queued
assigned_to: Developer
depends_on: [TOOL-001]
touches:
  - tools/api-client/
estimated: 1 session
---

## Background

TOOL-001 delivered a one-shot CLI that runs a fixed 7-step capsule lifecycle and exits.
During the first interactive walkthrough of the M11 capsule seal (2026-05-17), the need
for an interactive REPL became clear: step-by-step exploration, patent demonstrations,
and debugging the tlock/Shamir paths all benefit from a shell where you issue named
commands and see output between steps.

The REPL also resolves a specific limitation: to demo the tlock unlock path you need to
set `unlock_at` to seconds from now, which the one-shot CLI can't do interactively.

## Goals

1. A persistent `heirlooms> ` prompt backed by JLine3 (history, tab-completion, Ctrl-C).
2. Named commands covering the full M11 capsule lifecycle.
3. A fat JAR (via `shadowJar`) so startup is instant — no Gradle daemon overhead.
4. Reads credentials from `config.properties` or env vars (same as TOOL-001).

## Commands

| Command | Description |
|---------|-------------|
| `help` | List all commands with one-line descriptions |
| `auth` | Authenticate (challenge + login); prints user_id and token prefix |
| `upload <path>` | Upload a local file; prints upload_id |
| `create` | Interactive prompts: unlock_at, message, recipient display name; prints capsule_id |
| `seal <capsule-id>` | Seal a capsule; prompts for connection_id and wrapped_capsule_key (or generates a demo envelope) |
| `list` | List all capsules with id, shape, unlock_at |
| `get <capsule-id>` | Show full capsule detail |
| `connections` | List connections for the authenticated user |
| `tlock-key <capsule-id>` | Attempt tlock key delivery (succeeds only after unlock_at) |
| `quit` | Exit |

## Implementation notes

- Build on `HeirloomsClient` from TOOL-001 — all HTTP logic already exists.
- Add JLine3 (`org.jline:jline:3.x`) to `tools/api-client/build.gradle.kts`.
- `Main.kt` becomes a dispatch loop: read line → parse command → call client method → print result.
- `seal` command should offer a `--demo` flag that generates a structurally valid fake
  `capsule-ecdh-aes256gcm-v1` envelope (as used in the walkthrough), so the command works
  without a real ECDH implementation. Label the output clearly as a demo envelope.
- For the tlock path demo: `create` should accept `--unlock-in-seconds <N>` so you can
  set `unlock_at = now + N` and then call `tlock-key` after N seconds to see delivery.
- Fat JAR target: `./gradlew :api-client:shadowJar` → `tools/api-client/build/libs/api-client-all.jar`
  runnable as `java -jar api-client-all.jar`.

## Acceptance criteria

- `help` lists all commands.
- `auth` + `upload` + `create` + `seal` + `list` + `get` complete a full capsule lifecycle
  in a single REPL session without exiting.
- `tlock-key` returns the DEK_tlock after unlock_at passes (stub tlock, `--unlock-in-seconds 5`).
- Fat JAR starts in under 3 seconds on Bret's machine.
- All existing TOOL-001 tests still pass.
