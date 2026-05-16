---
id: TST-009
title: Android device farm design — 3-device automated test infrastructure
category: Testing
priority: High
status: queued
assigned_to: TestManager
depends_on: []
touches:
  - scripts/
  - docs/testing/
estimated: 1 session (design only)
---

## Goal

Design the automated Android testing infrastructure for Heirlooms. The CTO has three
physical Android devices available for a local device farm. This task produces the
design and setup guide; implementation follows in a subsequent task.

## Context

Current state:
- All Android testing is manual
- TST-004 (Playwright) covers web automation — Android equivalent needed
- Heirlooms' most critical user journeys involve Android (vault unlock, upload,
  shared plot approval, capsule seal/open)
- Future epics (window capsule, Care Mode, chained capsule) require E2E Android
  coverage before shipping

## Design questions

### 1. Tool selection
- **Appium + UIAutomator2**: cross-platform, mature, widely used. Supports both prod
  and staging flavors. Requires Appium server + device setup.
- **Espresso**: Android-native, faster, tightly integrated with the build. Requires
  test code inside the Android project.
- **Maestro**: newer, simpler YAML-based flows. Less mature but very fast to write tests.
- Recommend one primary tool for Heirlooms with justification. Consider: crypto-heavy
  tests (vault unlock, DEK operations) benefit from white-box (Espresso); journey tests
  benefit from black-box (Appium/Maestro).

### 2. Device setup
Three physical Android devices. Design:
- Recommended Android versions to cover (minimum supported, current, one intermediate)
- Device naming convention for test addressing
- ADB connection setup (USB hub vs WiFi ADB)
- How to run tests against both prod and staging flavors simultaneously
- Reset/teardown procedure between test runs (clear app data, re-seed test accounts)

### 3. Test account management
- Test accounts on staging environment (test.api.heirlooms.digital)
- How to provision fresh accounts for each test run without manual intervention
- Secrets management for test credentials (not committed to repo)

### 4. Test scenario mapping
Map the existing TST-003/TST-007 manual checklist journeys to automatable scenarios:
- Journey 1: Upload and view a photo (thumbnail rotation, detail view)
- Journey 4: Shared plot + staging approval
- Journey 5: Member trellis creation
- Journey 9: Android session token security
- Journey 12: Android flavor smoke test

Identify which journeys are automatable now vs require future work.

### 5. CI integration
- Can this run on a schedule (nightly) or only on-demand?
- How does it integrate with the existing git workflow?
- What does a test failure notification look like?

### 6. Crypto-path testing
The highest-value tests for Heirlooms are the cryptographic paths:
- Vault unlock (master key derivation)
- Upload DEK generation and wrapping
- Shared plot plot-key unwrap and item decryption
- Future: window capsule seal/open, HMAC tag generation

How should these be tested — white-box unit tests (Espresso/JVM), or black-box E2E?

## Output

Produce a design document to `docs/testing/TST-009_android-device-farm-design.md`
covering all six areas above with recommendations and a setup guide.

Also produce a follow-on task file `TST-010_android-device-farm-setup.md` in
`tasks/queue/` covering the actual implementation steps.

## Completion notes

<!-- TestManager appends here and moves file to tasks/done/ -->
