# Coverage Baseline — 2026-05-14

Run: `./gradlew :HeirloomsTest:coverageTest --no-daemon`
Mode: in-process (Netty + Testcontainers Postgres + MinIO)

## Status

The `coverageTest` infrastructure was set up and the test code compiles successfully against the
in-process server. The actual coverage run requires Docker Desktop to be running for the Postgres
and MinIO Testcontainers.

To generate the baseline, run:

```
cd HeirloomsTest
./gradlew coverageTest --no-daemon
```

The HTML report will appear at:
  `HeirloomsTest/build/reports/jacoco/html/index.html`

The XML report will appear at:
  `HeirloomsTest/build/reports/jacoco/coverage.xml`

## Overall

Instruction coverage: (run `./gradlew :HeirloomsTest:coverageTest` to populate)

## By package / class (instruction coverage)

| Class | Covered | Missed | % |
|---|---|---|---|
| (run coverageTest to populate) | — | — | — |
