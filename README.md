# jEAP Spring Modulith Error Handling Starter

This repository provides the Spring Boot starter that connects failed Spring Modulith event publications to the jEAP
Error Handling Service. It applies an application-owned retry policy, reports exhausted publications and consumes
commands that retry or discard an individual publication.

The first release targets Spring Modulith JDBC v2 with PostgreSQL.

## Modules

| Module | Purpose |
|---|---|
| `jeap-spring-modulith-error-handling-starter` | Auto-configuration and the application-side error-handling integration. |
| `jeap-spring-modulith-error-handling-starter-it` | Integration tests for the starter's Spring Boot, PostgreSQL and messaging integration. |

## Status

Retry, generation-based escalation through the transactional outbox, scheduled reconciliation and UUID-exact retry and
discard command handling are implemented. Correctness is based on persistent PostgreSQL state; proactive failure
observation only reduces escalation latency.

## Documentation

| Topic | File |
|---|---|
| Getting started | [docs/getting-started.md](docs/getting-started.md) |
| Architecture | [docs/architecture.md](docs/architecture.md) |

## Changes

This library uses [Semantic Versioning](https://semver.org/). Changes are documented in
[CHANGELOG.md](CHANGELOG.md) following [Keep a Changelog](https://keepachangelog.com/).

## License

This repository is Open Source Software licensed under the [Apache License 2.0](LICENSE).
