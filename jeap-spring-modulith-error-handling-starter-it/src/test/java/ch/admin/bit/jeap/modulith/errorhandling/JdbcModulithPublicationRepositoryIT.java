package ch.admin.bit.jeap.modulith.errorhandling;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.core.TargetEventPublication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes the starter's SQL against a real PostgreSQL. These are the guarantees that cannot be shown with
 * a mocked {@code JdbcTemplate}: the atomic claim, the primary key conflict behind the idempotency of an
 * escalation, and the generation matching of a command.
 */
class JdbcModulithPublicationRepositoryIT extends ModulithErrorHandlingITBase {

    private static final String LISTENER = "example.Listener.on(java.lang.String)";

    @Autowired
    private JdbcModulithPublicationRepository repository;

    @Test
    void onlyOneOfTwoConcurrentClaimsOfTheSameGenerationWins() throws Exception {
        UUID publicationId = insertFailedPublication(LISTENER, 1);
        PublicationGeneration generation = new PublicationGeneration(publicationId, 1);

        List<Boolean> results = inParallel(
                () -> repository.markResubmitted(generation, Instant.now(), 3),
                () -> repository.markResubmitted(generation, Instant.now(), 3));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(completionAttemptsOf(publicationId)).isEqualTo(2);
        assertThat(statusOf(publicationId)).isEqualTo("RESUBMITTED");
    }

    @Test
    void claimingRespectsTheRetryBudgetAndTheExpectedGeneration() {
        UUID publicationId = insertFailedPublication(LISTENER, 3);

        boolean beyondBudget = repository.markResubmitted(
                new PublicationGeneration(publicationId, 3), Instant.now(), 3);
        boolean wrongGeneration = repository.markResubmitted(
                new PublicationGeneration(publicationId, 2), Instant.now(), null);
        boolean manualRetry = repository.markResubmitted(
                new PublicationGeneration(publicationId, 3), Instant.now(), null);

        assertThat(beyondBudget).isFalse();
        assertThat(wrongGeneration).isFalse();
        assertThat(manualRetry)
                .withFailMessage("a manual retry has to be able to exceed the automatic budget")
                .isTrue();
    }

    @Test
    void escalationIsInsertedOncePerGeneration() {
        UUID publicationId = insertFailedPublication(LISTENER, 3);
        PublicationFailure failure = new PublicationFailure(publicationId, LISTENER, "java.lang.String", 3, null);

        boolean first = repository.recordEscalation(failure, "event-1", Instant.now());
        boolean duplicate = repository.recordEscalation(failure, "event-2", Instant.now());
        boolean nextGeneration = repository.recordEscalation(
                new PublicationFailure(publicationId, LISTENER, "java.lang.String", 4, null),
                "event-3", Instant.now());

        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();
        assertThat(nextGeneration).isTrue();
        assertThat(escalationCount()).isEqualTo(2);
    }

    @Test
    void concurrentEscalationsOfTheSameGenerationInsertOnlyOneRow() throws Exception {
        UUID publicationId = insertFailedPublication(LISTENER, 3);
        PublicationFailure failure = new PublicationFailure(publicationId, LISTENER, "java.lang.String", 3, null);

        List<Boolean> results = inParallel(
                () -> repository.recordEscalation(failure, "event-a", Instant.now()),
                () -> repository.recordEscalation(failure, "event-b", Instant.now()));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(escalationCount()).isEqualTo(1);
    }

    @Test
    void unescalatedFailuresExcludeAlreadyEscalatedGenerations() {
        UUID escalated = insertFailedPublication(LISTENER, 3);
        UUID pending = insertFailedPublication(LISTENER, 3);
        repository.recordEscalation(
                new PublicationFailure(escalated, LISTENER, "java.lang.String", 3, null), "event", Instant.now());

        List<PublicationFailure> failures = repository.findUnescalatedFailures(3, Instant.now().plusSeconds(60), 10);

        assertThat(failures).extracting(PublicationFailure::publicationId).containsExactly(pending);
    }

    @Test
    void commandTargetMatchesOnlyTheCurrentGenerationOfTheFailureEvent() {
        UUID publicationId = insertFailedPublication(LISTENER, 3);
        repository.recordEscalation(
                new PublicationFailure(publicationId, LISTENER, "java.lang.String", 3, null),
                "current-event", Instant.now());

        Optional<PublicationGeneration> matching = repository.findCommandTarget(publicationId, "current-event");
        Optional<PublicationGeneration> stale = repository.findCommandTarget(publicationId, "older-event");
        Optional<PublicationGeneration> unknown = repository.findCommandTarget(UUID.randomUUID(), "current-event");

        assertThat(matching).contains(new PublicationGeneration(publicationId, 3));
        assertThat(stale).isEmpty();
        assertThat(unknown).isEmpty();
    }

    @Test
    void discardCompletesOnlyWhenTheFailureEventStillMatches() {
        UUID publicationId = insertFailedPublication(LISTENER, 3);
        repository.recordEscalation(
                new PublicationFailure(publicationId, LISTENER, "java.lang.String", 3, null),
                "current-event", Instant.now());

        boolean stale = repository.completeFailed(publicationId, "older-event", Instant.now());
        boolean current = repository.completeFailed(publicationId, "current-event", Instant.now());
        boolean repeated = repository.completeFailed(publicationId, "current-event", Instant.now());

        assertThat(stale).isFalse();
        assertThat(current).isTrue();
        assertThat(repeated)
                .withFailMessage("a publication that is no longer FAILED must not be completed again")
                .isFalse();
        assertThat(statusOf(publicationId)).isEqualTo("COMPLETED");
    }

    @Test
    void retryableSelectionAppliesTheBudgetBeforeTheLimit() {
        UUID withinBudget = insertFailedPublication(LISTENER, 1);
        insertFailedPublication(LISTENER, 3);
        insertFailedPublication(LISTENER, 4);

        List<PublicationGeneration> retryable = repository.findRetryable(3, null, 10);

        assertThat(retryable).extracting(PublicationGeneration::publicationId).containsExactly(withinBudget);
    }

    @Test
    void retryableSelectionHonoursTheAgeCutoffOnlyWhenOneIsGiven() {
        UUID publicationId = insertFailedPublication(LISTENER, 1);

        List<PublicationGeneration> tooYoung =
                repository.findRetryable(3, Instant.now().minusSeconds(60), 10);
        List<PublicationGeneration> oldEnough =
                repository.findRetryable(3, Instant.now().plusSeconds(60), 10);
        List<PublicationGeneration> withoutCutoff = repository.findRetryable(3, null, 10);

        assertThat(tooYoung).isEmpty();
        assertThat(oldEnough).extracting(PublicationGeneration::publicationId).containsExactly(publicationId);
        assertThat(withoutCutoff).extracting(PublicationGeneration::publicationId).containsExactly(publicationId);
    }

    @Test
    void targetedLoadingKeepsTheRequestedOrderAndSkipsSupersededGenerations() {
        UUID first = insertFailedPublication(LISTENER, 1);
        UUID second = insertFailedPublication(LISTENER, 2);
        UUID superseded = insertFailedPublication(LISTENER, 2);

        List<TargetEventPublication> loaded = repository.findFailedPublications(List.of(
                new PublicationGeneration(second, 2),
                new PublicationGeneration(first, 1),
                // asking for a generation the row is no longer in must not return it
                new PublicationGeneration(superseded, 99)));

        assertThat(loaded).extracting(TargetEventPublication::getIdentifier).containsExactly(second, first);
    }

    @Test
    void targetedLoadingOfNothingReturnsNothing() {
        insertFailedPublication(LISTENER, 1);

        assertThat(repository.findFailedPublications(List.of())).isEmpty();
    }

    @Test
    void lockingLoadsOnlyTheRequestedFailedGeneration() {
        UUID publicationId = insertFailedPublication(LISTENER, 3);

        assertThat(repository.lockFailed(publicationId, 3)).isPresent();
        assertThat(repository.lockFailed(publicationId, 2)).isEmpty();
        assertThat(repository.lockFailed(UUID.randomUUID(), 3)).isEmpty();
    }

    private static List<Boolean> inParallel(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> futures = executor.invokeAll(List.of(first, second));
            return List.of(futures.get(0).get(), futures.get(1).get());
        }
    }
}
