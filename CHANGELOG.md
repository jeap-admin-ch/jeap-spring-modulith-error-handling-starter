# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.13.0] - 2026-09-22

### Changed
- Update parent from 10.0.1 to 11.0.0
- update jeap-messaging from 19.6.0 to 19.7.0
- update jeap-spring-boot-roles-anywhere-starter from 3.48.0 to 3.49.0
- update jeap-crypto from 11.8.0 to 11.9.0
- update jeap-spring-boot-vault-starter from 25.8.0 to 25.9.0
- update jeap-messaging-outbox from 18.7.0 to 18.8.0

## [1.12.0] - 2026-09-22

### Changed
- Update parent from 9.7.1 to 10.0.1
- update jeap-messaging from 19.5.0 to 19.6.0
- update jeap-spring-boot-roles-anywhere-starter from 3.46.0 to 3.48.0
- update jeap-crypto from 11.6.0 to 11.8.0
- Update parent from 10.0.0 to 10.0.1
- update jeap-spring-boot-vault-starter from 25.7.0 to 25.8.0
- update jeap-messaging-outbox from 18.6.0 to 18.7.0

## [1.11.0] - 2026-09-17

### Changed
- Update parent from 9.6.2 to 9.7.1
- Update parent from 9.6.2 to 9.7.0
- update jeap-messaging from 18.10.1 to 19.5.0
- update jeap-spring-boot-roles-anywhere-starter from 3.44.0 to 3.46.0
- update jeap-crypto from 11.4.0 to 11.6.0
- Update parent from 9.7.0 to 9.7.1
- update jeap-spring-boot-vault-starter from 25.5.0 to 25.6.0
- Configure the AWS JDBC Wrapper's HikariCP exception override so recoverable failover connections are not evicted.
- update jeap-messaging-outbox from 17.19.0 to 18.6.0
- update jeap-messaging from 19.4.0 to 19.5.0

## [1.10.1] - 2026-09-16

### Fixed

- Align Modulith failure stack-trace hashes and limits with standard jEAP Messaging error handling.

## [1.10.0] - 2026-09-15

### Changed

- Update parent from 9.6.1 to 9.6.2

## [1.9.0] - 2026-09-15

### Changed

- Update parent from 9.6.0 to 9.6.1

## [1.8.0] - 2026-09-12

### Changed

- Update parent from 9.5.0 to 9.6.0

## [1.7.0] - 2026-09-11

### Changed

- Update parent from 9.4.2 to 9.5.0

## [1.6.0] - 2026-09-10

### Changed

- Update parent from 9.4.1 to 9.4.2

## [1.5.0] - 2026-09-09

### Changed

- Update parent from 9.4.0 to 9.4.1

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
