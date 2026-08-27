# AGENTS.md

Guidance for coding agents working in this repository.

## Project

`jeap-spring-modulith-error-handling-starter` integrates failed Spring Modulith JDBC v2 event publications with the
jEAP Error Handling Service. Version 1 targets PostgreSQL and Spring Modulith 2.1.

## Repository layout

```text
docs/                                                   Documentation
jeap-spring-modulith-error-handling-starter/            Published Spring Boot starter
jeap-spring-modulith-error-handling-starter-it/         Integration tests
pom.xml                                                 Parent POM
```

## Build and test

```bash
./mvnw clean install
./mvnw test
./mvnw verify
./mvnw -pl jeap-spring-modulith-error-handling-starter-it test
```

The Java baseline and Spring Boot version are inherited from
`ch.admin.bit.jeap:jeap-internal-spring-boot-parent`.

## Conventions

- Base package: `ch.admin.bit.jeap.modulith.errorhandling`.
- Register auto-configurations in
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Keep the starter constrained to Spring Modulith JDBC v2 and PostgreSQL until another store is explicitly supported.
- Do not use package-private Spring Modulith JDBC implementation classes.
- Production database migrations are owned by the consuming service; this repository provides documented reference DDL.
- Prefer real PostgreSQL and Kafka integration tests over mocks for persistence and messaging guarantees.
- Keep README and `docs/` synchronized with configuration and behavior changes.

## Versioning

- Use Semantic Versioning and document changes in `CHANGELOG.md`.
- POM versions on feature branches use the `-SNAPSHOT` suffix.
- `CHANGELOG.md` and `publiccode.yml` use the release version without `-SNAPSHOT`.
- Use `setPomVersions.sh` to update all Maven module versions.
- Prefix commits with the Jira issue from the branch name when available.
