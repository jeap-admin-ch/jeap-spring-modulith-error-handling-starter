# Getting started

## Add the starter

Add the starter to an application that uses Spring Modulith JDBC v2 with PostgreSQL:

```xml
<dependency>
    <groupId>ch.admin.bit.jeap</groupId>
    <artifactId>jeap-spring-modulith-error-handling-starter</artifactId>
</dependency>
```

The jEAP Spring Boot parent will manage the released starter version once the starter has been added to its dependency
management.

## Configuration

The starter is enabled by default when it is on the classpath. It can be disabled explicitly:

```yaml
jeap:
  modulith:
    error-handling:
      enabled: false
```

Configure the retry policy and the topics owned by the consuming application:

```yaml
jeap:
  modulith:
    error-handling:
      max-completion-attempts: 3
      retry-interval: 30s
      retry-min-age: 30s
      reconciliation-interval: 5m
      reconciliation-min-age: 1m
      batch-size: 100
      max-payload-bytes: 262144
      failure-event-topic: my-system-messageprocessing-failed
      retry-command-topic: my-system-my-service-retry-modulith-publication
      discard-command-topic: my-system-my-service-discard-modulith-publication
```

The application must declare a producer contract for `ModulithPublicationProcessingFailedEvent` and consumer
contracts for `RetryModulithPublicationCommand` and `DiscardModulithPublicationCommand`, using the configured topics.

## Database

The consuming service owns the production Flyway migrations for the starter's persistence and the jEAP transactional
outbox. Reference PostgreSQL DDL is provided in [postgresql-reference-ddl.sql](postgresql-reference-ddl.sql). Copy the
relevant statements into an application-owned migration. Do not create `shedlock` again if the application already has
it.
