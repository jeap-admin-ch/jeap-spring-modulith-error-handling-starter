# Architecture

The starter runs inside the application that owns the Spring Modulith `event_publication` table. It is separate from the
jEAP Error Handling Service, which stores operational errors in its own database.

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

Correctness is based on persistent PostgreSQL state, atomic status transitions and idempotent command consumption. A
scheduled reconciliation sweep is the source of truth; proactive listener-failure observation only reduces latency.

## Spring Modulith integration constraints

The implementation uses only public Spring Modulith APIs, but cannot implement its policies with
`EventPublicationRegistry.processFailedPublications(...)` alone:

- Spring Modulith selects and limits a database batch before applying `ResubmissionOptions.getFilter()`. A publication
  selected by UUID can therefore be starved indefinitely behind older publications that the filter rejects.
- Every accepted publication is atomically changed from `FAILED` to `RESUBMITTED` before the callback runs. The method
  is consequently neither a read-only inspection API nor suitable for an escalation sweep that must leave rows failed.

The starter therefore uses a PostgreSQL/JDBC v2 adapter for policy-aware selection and atomic state changes. Retry
commands claim exactly one `FAILED` publication by UUID. The scheduled sweep selects retryable and exhausted rows in
SQL so that its batch limit applies only after the retry policy has been evaluated.

For the low-latency failure path, an outer listener advisor retains the thrown exception while Spring Modulith's
`CompletionRegisteringAdvisor` performs the state transition. A primary `EventPublicationRepository` decorator
observes `markFailed(UUID)` after delegating it to the JDBC repository. This preserves the updated attempt count and
the exact publication identifier without depending on package-private JDBC implementation classes.

Escalation is recorded by publication identifier and completion-attempt generation. A manually retried publication
that fails again has a higher generation and therefore produces a new operational error, while repeated observation
of the same generation remains idempotent.
