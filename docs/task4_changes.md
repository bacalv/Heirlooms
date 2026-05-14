# Task 4 — Server Refactor Proposal

**Date:** 2026-05-14

## What was produced

`docs/briefs/task4_server_refactor_proposal.md` — a full design proposal for restructuring the HeirloomsServer Kotlin codebase from a single flat package into the sub-package layout: `config/`, `domain/`, `repository/`, `service/`, `representation/`, `routes/`, `storage/`, `crypto/`, `filters/`.

## Scope of the analysis

Every `.kt` file in `HeirloomsServer/src/main/kotlin/digital/heirlooms/server/` was read in full (28 files, ~9100 lines total). No source files were modified.

## Key decisions captured

- `Database.kt` (3849 lines) splits into 11 repository classes + `DatabaseInfra.kt`. Full function-to-file mapping provided.
- All domain data classes move from `Database.kt` to `domain/` sub-packages.
- Handler files split into thin `routes/` files (route wiring only) + `service/` classes (business logic).
- JSON serialisation (`toJson()` extensions) moves to a dedicated `representation/` layer.
- Repository interfaces are recommended for testability; service interfaces are not recommended at this scale.
- An 8-phase migration sequence is specified, starting with the safest moves (data classes, storage) and ending with the highest-coupling changes (UploadRepository, service extraction).
- Risks documented: cross-repository transactions, `withTransaction` pattern, IntelliJ refactoring pitfalls, merge conflict window.
