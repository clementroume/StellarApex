---
name: run-tests
description: Run StellarApex tests - targeted tests while working, the full suite of every touched service before proposing a commit. Use whenever code was changed and must be verified. Returns only failures and timings.
context: fork
allowed-tools: Bash(./mvnw *) Bash(npm test*) Bash(npm run build*) Bash(npx ng test *) Bash(git status*) Bash(git diff *)
---

# Run the tests of StellarApex

Argument: what to run (a service, a test class or method, or nothing = every touched service).
Backend ITs need Docker (Testcontainers): check `docker info` first and say so if it is down.

## While working: targeted tests

- Antares / Aldebaran unit tests: `cd <service> && ./mvnw -B test -Dtest=ClassName` (or `ClassName#method`).
- Backend ITs only: `cd <service> && ./mvnw -B verify -Dtest=NoSuch -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ClassName`.
- Sirius: `cd sirius-app && npm test -- --include='src/app/path/to/file.spec.ts'`.

## Before proposing a commit: the full suite of each touched service

Find the touched services with `git status --short` (folders `antares-auth`, `aldebaran-training`,
`bellatrix-swagger`, `sirius-app`), then run:

- Backends: `cd <service> && ./mvnw -B verify` (about 30 s to 1 min each).
- Sirius: `cd sirius-app && npm test && npm run build`.

ITs share one JVM, one database and one Spring context: a test that passes alone and fails in the
suite (or the opposite) is order coupling. Re-run with `-Dfailsafe.runOrder=random` to confirm.

## Report (and nothing else)

- Per service: command, result (green/red), duration.
- For each failure: test name, one-line assertion or exception, file:line of the first relevant frame.
- Never paste full logs. Do not fix anything; the caller decides.
