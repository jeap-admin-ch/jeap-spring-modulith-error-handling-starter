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

## Kafka topics and service responsibilities

Provision the topics and grant access before deploying the application. Reuse the system's existing Error Handling
Service (EHS), running a version with Modulith support. No separate EHS instance is required for each Modulith service.
The names below are examples: use your system's naming convention and keep configuration and contracts consistent.
A shared failure topic per system and separate retry/discard topics per source service make ownership explicit.

| Example topic | Message type | Producer | Consumer |
|---|---|---|---|
| `my-system-modulith-publication-processing-failed` | `ModulithPublicationProcessingFailedEvent` | Modulith application, through the starter's outbox | EHS |
| `my-system-my-service-retry-modulith-publication` | `RetryModulithPublicationCommand` | EHS | Modulith application, through the starter |
| `my-system-my-service-discard-modulith-publication` | `DiscardModulithPublicationCommand` | EHS | Modulith application, through the starter |

```text
Modulith application -- failure topic --> EHS
Modulith application <-- retry topic ---- EHS
Modulith application <-- discard topic -- EHS
```

### Configure the Modulith application

Set these three properties under `jeap.modulith.error-handling`:

| Property | Example value |
|---|---|
| `failure-event-topic` | `my-system-modulith-publication-processing-failed` |
| `retry-command-topic` | `my-system-my-service-retry-modulith-publication` |
| `discard-command-topic` | `my-system-my-service-discard-modulith-publication` |

Grant the application's Kafka identity write access to the failure topic and read access to both command topics.
Grant consumer-group access for the starter's command listeners, and the required Schema Registry access.
Declare the two command consumer contracts shown in [Configuration](#configuration); the enabled starter checks them
at startup. No application producer contract is required for the framework-owned failure event.

### Configure the EHS

Set the same failure topic on the EHS:

```yaml
jeap:
  errorhandling:
    modulithPublicationProcessingFailedTopic: my-system-modulith-publication-processing-failed
```

Grant the EHS's Kafka identity read and consumer-group access for the failure topic, write access to the retry/discard
topics of its source services, and the required Schema Registry access. Application permissions do not grant EHS
permissions: configure both identities. On RHOS these are the respective Kafka service bindings; on Nivel they are
the respective MSK IAM permissions.

The EHS needs no separate retry/discard topic properties: each failure event carries those destinations, which the
EHS persists together with the source Kafka cluster. The EHS needs no additional contract annotations because it uses
its existing `ErrorServiceContractValidator`. Roll out the configuration and restart the EHS to activate the listener
before sending the first test failure. See the
[EHS setup guide](https://github.com/jeap-admin-ch/jeap-error-handling/blob/main/docs/getting-started.md).

### Existing topics and message signatures

Keep the normal Kafka failure and dead-letter topics distinct from the Modulith failure topic. Existing permissions
for normal error handling remain necessary, including EHS write access to business topics for Kafka-message resends.
Internal application events stay inside Spring Modulith and do not require their own Kafka topics. The JME example's
`jme-order-created-modulith` topic is only its business-input demonstration, not a requirement for using this starter.

Contracts, Kafka permissions and message signatures are independent. If a receiver requires signatures, configure
the sender's signing key/certificate and the receiver's certificate trust and publisher permissions. Check both
directions: the application sends failures, and EHS sends commands. A producer-contract exemption does not exempt a
message from signature validation. Restart an application when enabling previously unconfigured signing; do not rely
on configuration refresh to create the signing service. See
[message signing](https://github.com/jeap-admin-ch/jeap-messaging/blob/main/docs/signing-messages.md).

### Verify the integration

In a test environment, fail one persistent asynchronous listener, wait for retry exhaustion, and inspect the error
and payload in the EHS UI. Retry the publication, then discard its latest failure if it fails again. Confirm that only
that publication is affected and discard causes no further listener invocation. For a shared EHS, use a unique test
identifier and leave other applications' errors untouched.

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

The application must declare consumer contracts for both commands, with topics matching the configuration above:

```java
@JeapMessageConsumerContract(value = RetryModulithPublicationCommand.TypeRef.class,
        topic = "my-system-my-service-retry-modulith-publication")
@JeapMessageConsumerContract(value = DiscardModulithPublicationCommand.TypeRef.class,
        topic = "my-system-my-service-discard-modulith-publication")
```

The enabled starter checks both consumer contracts against the configured topics using the application's existing
`ContractsValidator` before creating the command listener. Missing contracts or mismatched topics fail application
startup; normal consumer contract validation also remains active when commands arrive. Disabling the starter skips
these startup checks. Business messages still require their own contracts.

The framework-owned `ModulithPublicationProcessingFailedEvent` is exempt from producer contract validation in jEAP
Messaging, so the application does not declare a producer contract for it. The EHS instance needs no contracts for
these transport messages because it deliberately uses its own `ErrorServiceContractValidator`.

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
