# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-08-26

### Added

- Add the initial starter and integration-test repository structure.
- Add Spring Modulith 2.1 compatibility tests for failure interception and targeted resubmission behavior.
- Add persistent retry and reconciliation for Spring Modulith JDBC v2 publications on PostgreSQL.
- Coordinate retry and reconciliation jobs across application instances with ShedLock.
- Add idempotent failure escalation through the jEAP transactional outbox.
- Add UUID-exact retry and discard command handling.
- Add PostgreSQL reference DDL for application-owned migrations.
