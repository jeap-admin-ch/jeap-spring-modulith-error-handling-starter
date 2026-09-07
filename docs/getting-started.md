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
      retry-initial-delay: 0s
      retry-lock-at-least: 5s
      retry-lock-at-most: 5m
      retry-min-age: 30s
      reconciliation-interval: 5m
      reconciliation-initial-delay: 0s
      reconciliation-lock-at-least: 5s
      reconciliation-lock-at-most: 30m
      reconciliation-min-age: 1m
      batch-size: 100
      max-payload-bytes: 262144
      failure-event-topic: my-system-modulith-publication-processing-failed
      retry-command-topic: my-system-my-service-retry-modulith-publication
      discard-command-topic: my-system-my-service-discard-modulith-publication
```

The failure event and retry/discard commands are framework-owned infrastructure messages. The starter handles their
contract-validation exemptions, so the application only needs to configure the topics and does not declare contracts
for these three message types. Contract declarations are still required for application-owned business messages.
Command producers must copy the failure event identity to `references.publication.failureEventId`. Although the field is
nullable in Avro for schema evolution, the starter requires it for retry and discard actions. Commands without it, or
commands for a stale generation, are acknowledged as no-ops. Consumer group IDs include the configured system and
service name so applications sharing command topics each receive commands for target filtering.

`max-completion-attempts` counts the attempts Spring Modulith records on the publication, and the **first invocation of
the listener is already attempt one**. A value of `3` therefore means one initial attempt plus two automatic retries,
after which the publication is escalated. A retry requested by an operator is applied regardless of this limit.

The starter handles persistent `AFTER_COMMIT` listeners in either of these forms:

```java
@ApplicationModuleListener
void on(OrderCompleted event) {
    // Runs asynchronously in a transaction supplied by Spring Modulith.
}

@TransactionalEventListener
@Transactional(propagation = Propagation.REQUIRES_NEW)
void on(PaymentCompleted event) {
    // Runs synchronously after commit in an independent transaction.
}
```

The synchronous form blocks the publishing thread until it finishes. `REQUIRES_NEW` is required because the original
transaction has committed while its resources can still be bound during the callback. Plain `@EventListener` methods
and transaction phases other than `AFTER_COMMIT` do not create Spring Modulith publications and are not handled. Events
must be published in an active thread-bound transaction. If `spring.modulith.events.registry-trigger-annotation` is set,
it must include the annotation used by the listener. See [architecture.md](architecture.md#supported-listeners).

The retry and reconciliation jobs use separate ShedLock locks. `lock-at-least` prevents several application instances
from running the same sweep one after another at startup, while `lock-at-most` releases a lock after an instance has
failed. Set each maximum above the longest expected execution time. The starter reuses an application-provided
`LockProvider` or creates a PostgreSQL JDBC provider that uses database time. The initial delays default to zero, so
both jobs run immediately after startup unless configured otherwise.

## Database

The consuming service owns the production Flyway migrations for the starter's persistence and the jEAP transactional
outbox. Reference PostgreSQL DDL is provided in [postgresql-reference-ddl.sql](postgresql-reference-ddl.sql). Copy the
relevant statements into an application-owned migration. Do not create `shedlock` again if the application already has
it. The ShedLock table is required even if the transactional outbox's scheduled relay is disabled.
