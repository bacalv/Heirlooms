---
id: TST-006
title: Investigate remote-controlled Android testing for E2E automation
category: Testing
priority: Medium
status: queued
depends_on: []
touches: []
assigned_to: TestManager
estimated: half day (research)
---

## Goal

Investigate options for remotely controlling the Android app so that staging journeys
(currently manual — TST-003) can be automated end-to-end, covering flows that the
Playwright web suite (TST-004/TST-005) cannot reach.

## Context

TST-003 manual testing uncovered two bugs (BUG-003, BUG-004) that required a human with
a physical device. An automated Android E2E suite would catch these regressions in CI
before they reach manual testing.

## Candidates to evaluate

| Tool | Notes |
|------|-------|
| **Maestro** | YAML-based, runs over ADB, no app instrumentation needed, good for black-box E2E |
| **UI Automator / Espresso** | Built into Android SDK; Espresso requires test APK, UI Automator is black-box; both run via ADB |
| **Appium** | Cross-platform (Android + iOS), WebDriver protocol, higher setup cost |
| **Firebase Test Lab** | Cloud device farm; runs Espresso/UI Automator remotely; requires Google Cloud |
| **ADB shell input** | Raw ADB commands — low fidelity, fragile, last resort |

## Questions to answer

1. Which tool gives the best signal-to-noise for the journeys in TST-003 without requiring
   significant app instrumentation?
2. Can the staging API key and invite flow be driven from the test harness (e.g. curl +
   ADB), matching the actor-based pattern used in the Playwright suite?
3. Can vault unlock (biometric / master key) be handled in a test environment, or does it
   block automation?
4. What does CI integration look like — is a physical device required, or can an emulator
   cover the staging upload/E2EE flows?
5. Is there a feasible path to sharing actor abstractions between the Playwright web suite
   and an Android suite (e.g. shared API helpers)?

## Deliverables

- A short recommendation doc (`docs/testing/android-e2e-recommendation.md`) covering:
  - Chosen tool and rationale
  - What it can and cannot automate (vault unlock, camera, notifications)
  - Estimated effort to automate Journey 1 (upload + garden) as a proof of concept
  - CI requirements (emulator vs real device, ADB over network, etc.)
- A follow-up task stub for the actual implementation if the recommendation is approved

## Completion notes

<!-- Test Manager appends here and moves file to tasks/done/ -->
