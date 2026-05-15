# Session Logs

One log file per session, per persona. Written at "Let's wrap up" and committed before Bret pushes.

## Directory structure

```
session-logs/
  PA/
  DevManager/
  Developer/
  TestManager/
  SecurityManager/
  OpsManager/
  TechnicalArchitect/
```

## File naming

```
<role>-<YYYY-MM-DD>T<HH-MM>.md
```

Examples:
- `PA/pa-2026-05-15T09-30.md`
- `TestManager/test-manager-2026-05-15T01-00.md`
- `SecurityManager/security-manager-2026-05-14T22-15.md`

Use the **session start time** (not end time), in 24h local time, with `:` replaced by `-`.

## Log contents

Each log should cover:
- **Date / persona / session type**
- **Tasks completed** — IDs, titles, outcome
- **Tasks created** — IDs, titles, priority
- **Key decisions or findings** — anything non-obvious worth preserving
- **Recommended next actions** — top 3 for next sprint
