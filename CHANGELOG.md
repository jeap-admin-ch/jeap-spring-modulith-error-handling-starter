# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.0] - 2026-09-09

### Changed

- Update parent from 9.3.0 to 9.4.0

## [1.3.1] - 2026-09-07

### Changed

- Require explicit retry/discard consumer contracts and validate their configured topics at application startup.
- Use the Messaging producer exemption for framework-owned failure events, verified through the real transactional
  outbox without a failure-event producer contract.

## [1.3.0] - 2026-09-05

### Changed

- Update parent from 9.2.2 to 9.3.0

## [1.2.1] - 2026-09-03

### Changed

- Replace the mocked tests of the starter with integration tests that run a real Spring Modulith application,
  with asynchronous and synchronous persistent listeners, PostgreSQL created from the reference DDL, the transactional
  outbox, and Kafka.
- Support asynchronous `@ApplicationModuleListener` and synchronous `@TransactionalEventListener(AFTER_COMMIT)`
  publications, and document that `max-completion-attempts` includes the first invocation of the listener.
- Make the initial delay of retry and reconciliation jobs configurable.
- Announce a new release to the jEAP parent dependency update job, so the managed version of this starter is
  updated automatically.

## [1.2.0] - 2026-09-03

### Changed

- Update parent from 9.2.1 to 9.2.2

## [1.1.0] - 2026-09-02

### Changed

- Update parent from 9.0.1 to 9.2.1

## [1.0.0] - 2026-09-01

### Added

- Add the initial starter and integration-test repository structure.
- Add Spring Modulith 2.1 compatibility tests for failure interception and targeted resubmission behavior.
- Add persistent retry and reconciliation for Spring Modulith JDBC v2 publications on PostgreSQL.
- Coordinate retry and reconciliation jobs across application instances with ShedLock.
- Add idempotent failure escalation through the jEAP transactional outbox.
- Add UUID-exact retry and discard command handling.
- Add PostgreSQL reference DDL for application-owned migrations.
- Make retry claims generation-aware and enforce the automatic retry limit atomically.
- Bind commands to the current escalation event so stale, duplicate and tokenless commands are safe no-ops.
- Lock and reload the exact failed generation before transactional escalation.
- Load only selected failed publications and reuse an application-provided `Clock`.
- Scope command consumer groups to the configured application service.
