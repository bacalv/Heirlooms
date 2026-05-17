# ARCH-016 — Hub Architecture and Underwear Layer

**Category:** Architecture  
**Priority:** Medium  
**Assigned to:** TechnicalArchitect / PA  
**Depends on:** M11 complete  
**Brief:** `docs/briefs/ARCH-016_hub-architecture-and-underwear.md`

## Summary

Plan and initiate the hub architecture restructure of the server codebase. The design brief is complete. This task covers milestone planning — breaking the six implementation phases into developer tasks and scheduling them into a future milestone.

## What the PA needs to do

1. Read `docs/briefs/ARCH-016_hub-architecture-and-underwear.md`
2. Decide which milestone this work lands in (M12 or later)
3. Break Phase 1 into a DEV task (Gradle restructure + underwear scaffolding — no logic changes, low risk)
4. Schedule subsequent phases once Phase 1 validates the module structure

## Notes

- Do not start during M11 — wait for M11 branch to merge
- Phase 1 is the lowest-risk entry point; it is purely structural with no business logic changes
- VaultHub (Phase 2) is the cleanest seam and should be the first hub extracted
- The standalone `vault-server` app (Phase 6) is the long-horizon product goal that justifies this investment