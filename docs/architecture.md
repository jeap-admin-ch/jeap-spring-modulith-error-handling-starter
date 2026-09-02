# Architecture

The starter runs inside the application that owns the Spring Modulith `event_publication` table. It is separate from the
jEAP Error Handling Service, which stores operational errors in its own database.

## Supported listeners

Version 1 supports persistent Spring Modulith `AFTER_COMMIT` publications for both execution models:

- asynchronous methods annotated with `@ApplicationModuleListener`, Spring Modulith's shortcut for
  `@Async @Transactional(REQUIRES_NEW) @TransactionalEventListener`;
- synchronous methods annotated with `@TransactionalEventListener` and
  `@Transactional(propagation = REQUIRES_NEW)`.

A synchronous listener blocks the publishing thread until its after-commit execution finishes. The publisher's business
transaction is already committed, and Spring's transaction synchronization logs rather than propagates a listener
failure. The listener needs `REQUIRES_NEW` because the committed publisher transaction's resources can still be bound
during the callback; joining those resources would not provide a transaction in which listener changes can safely
commit. Immediate escalation uses `REQUIRES_NEW` for the same reason.

Plain `@EventListener` methods and `@TransactionalEventListener` methods in phases other than `AFTER_COMMIT` are not
stored by Spring Modulith's event publication registry and are therefore outside this starter's scope. A plain listener
runs in the publishing call and its failure retains normal Spring semantics, including rollback of the publishing
transaction. Events must be published in an active thread-bound transaction, and
`spring.modulith.events.registry-trigger-annotation` must not exclude supported synchronous listeners.

The implemented flow is:

```text
Spring Modulith listener fails
  -> event_publication becomes FAILED
  -> starter applies the configured retry policy
  -> exhausted publication is persisted and sent through the transactional outbox
  -> jEAP Error Handling Service creates an operator-visible error
  -> retry or discard command returns to the starter
  -> starter changes exactly the referenced publication
```

Correctness is based on persistent PostgreSQL state, generation-aware atomic status transitions and idempotent command
consumption. A
scheduled reconciliation sweep is the source of truth; proactive listener-failure observation only reduces latency.
ShedLock coordinates both scheduled retry and reconciliation across application instances. The database claims and
generation key remain the final correctness guards if a lock expires during a long-running sweep.

## Spring Modulith integration constraints

The implementation uses only public Spring Modulith APIs, but cannot implement its policies with
`EventPublicationRegistry.processFailedPublications(...)` alone:

- Spring Modulith selects and limits a database batch before applying `ResubmissionOptions.getFilter()`. A publication
  selected by UUID can therefore be starved indefinitely behind older publications that the filter rejects.
- Every accepted publication is atomically changed from `FAILED` to `RESUBMITTED` before the callback runs. The method
  is consequently neither a read-only inspection API nor suitable for an escalation sweep that must leave rows failed.

The starter therefore uses a PostgreSQL/JDBC v2 adapter for policy-aware selection and atomic state changes. Retry
commands claim exactly one `FAILED` publication by UUID, completion-attempt generation and escalation event ID. Missing,
stale and duplicate command tokens are acknowledged without changing the publication. Manual retries may exceed the
automatic retry limit. The scheduled sweep selects retryable and exhausted rows in SQL so that its batch limit applies
only after the retry policy has been evaluated, then claims the selected generation with the retry limit in the update.

For the low-latency failure path, an outer listener advisor retains the thrown exception while Spring Modulith's
`CompletionRegisteringAdvisor` performs the state transition. A primary `EventPublicationRepository` decorator
observes `markFailed(UUID)` after delegating it to the JDBC repository. This preserves the updated attempt count and
the exact publication identifier without depending on package-private JDBC implementation classes.

Escalation is recorded by publication identifier and completion-attempt generation. A manually retried publication
that fails again has a higher generation and therefore produces a new operational error, while repeated observation
of the same generation remains idempotent. The exact failed generation is locked and reloaded in the escalation
transaction before the escalation record and outbox message are created.
