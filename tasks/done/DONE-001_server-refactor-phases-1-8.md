---
id: DONE-001
title: Server refactor phases 1–8
category: Refactoring
priority: High
status: done
completed: 2026-05-14
---

## What was done

Full restructure of HeirloomsServer from a flat package into:
`config/ crypto/ domain/ filters/ repository/ routes/ service/ representation/ storage/`

- Phase 1: Domain data classes extracted from Database.kt
- Phase 2–4: Storage, config, filters, crypto moved to sub-packages
- Phase 5: Database.kt split into 11 repository classes
- Phase 6: Services extracted from handler files
- Phase 7: Routes extracted from handler files
- Phase 8: Repository interfaces + PostgresXxx concrete classes
- Cleanup: All remaining root-level files moved to sub-packages; empty handler stubs deleted
- Final: Database delegation layer removed — services call repository interfaces directly

Database.kt now contains only: `create()`, `runMigrations()`, `dataSource`.

## Test result

326 unit tests pass. Integration test coverage: 53.3%.
