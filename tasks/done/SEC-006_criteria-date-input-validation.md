---
id: SEC-006
title: Input validation for CriteriaEvaluator date fields
category: Security
priority: Medium
status: queued
depends_on: [SEC-001]
touches:
  - HeirloomsServer/src/main/kotlin/digital/heirlooms/server/service/plot/CriteriaEvaluator.kt
assigned_to: SecurityManager
estimated: 0.25 day (agent)
---

## Background

SEC-001 threat model finding F-09:

`CriteriaEvaluator` passes date strings from criteria JSON directly to PostgreSQL as `?::date`. Although this is parameterised (no SQL injection), a malformed date string causes a `PSQLException` which surfaces as a 500 Internal Server Error rather than a 400 Bad Request, leaking an unhandled exception path.

## Goal

1. Add a format validator for the `date` field in `taken_after`, `taken_before`, `uploaded_after`, and `uploaded_before` atoms. The format should be `YYYY-MM-DD` (ISO 8601 local date). Use `LocalDate.parse(date)` and throw `CriteriaValidationException("invalid date format: expected YYYY-MM-DD")` on failure.
2. Apply a maximum length guard (e.g. `date.length > 20`) before attempting parse, to prevent excessively long strings from reaching the DB.
3. Write unit tests: valid date passes, malformed date (`"not-a-date"`, `"2024-13-01"`, `""; DROP TABLE uploads; --"`) throws `CriteriaValidationException` rather than a DB exception.

## Acceptance criteria

- Malformed date fields throw `CriteriaValidationException` with a descriptive message.
- The server returns 400 (not 500) for criteria with invalid date values.
- All existing tests continue to pass.

## Completion notes

Completed 2026-05-15 by SecurityManager.

### What was done

- Added `import java.time.LocalDate` and a private `validateDate(date: String, atomType: String)` helper to `CriteriaEvaluator.kt`.
- The helper:
  1. Rejects strings longer than 20 characters (MAX_DATE_LENGTH guard).
  2. Calls `LocalDate.parse(date)` and throws `CriteriaValidationException("'<atom>' invalid date format: expected YYYY-MM-DD, got '<value>'")` on `DateTimeParseException`.
- Applied `validateDate()` in all four date atom cases: `taken_after`, `taken_before`, `uploaded_after`, `uploaded_before`.
- Added unit tests in `CriteriaEvaluatorTest.kt` covering:
  - Valid date `"2024-06-15"` passes (all four atom types).
  - `"not-a-date"` throws `CriteriaValidationException` (not a DB exception).
  - `"2024-13-01"` (invalid month) throws `CriteriaValidationException`.
  - `"''; DROP TABLE uploads; --"` (SQL injection attempt) throws `CriteriaValidationException`.
  - A string exceeding 20 chars throws `CriteriaValidationException` via the length guard.
