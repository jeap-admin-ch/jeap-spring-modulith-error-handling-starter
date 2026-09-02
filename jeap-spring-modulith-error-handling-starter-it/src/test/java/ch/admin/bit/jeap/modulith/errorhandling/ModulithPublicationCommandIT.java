package ch.admin.bit.jeap.modulith.errorhandling;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Retry and discard commands come back from the Error Handling Service and must act on exactly the
 * generation they were issued for. Anything stale, unknown or incomplete has to be a silent no-op.
 */
class ModulithPublicationCommandIT extends ModulithErrorHandlingITBase {

    @Test
    void retryCommandResubmitsTheEscalatedPublicationEvenBeyondTheAutomaticBudget() {
        failurePolicy.failAlways();
        UUID publicationId = escalatedPublication();
        int attemptsAtEscalation = completionAttemptsOf(publicationId);
        int invocationsBefore = failurePolicy.invocationCount();

        sendRetryCommandFor(publicationId, currentErrorEventId(publicationId));

        // A manual retry deliberately ignores max-completion-attempts: the operator overrides the policy.
        await().atMost(TIMEOUT).until(() -> failurePolicy.invocationCount() > invocationsBefore
                && completionAttemptsOf(publicationId) > attemptsAtEscalation
                && "FAILED".equals(statusOf(publicationId)));
        awaitSentOutboxMessages(2);
        awaitReceivedFailureEvents(2);
        assertThat(completionAttemptsOf(publicationId))
                .isGreaterThan(properties.getMaxCompletionAttempts());
    }

    @Test
    void retryCommandOfASupersededGenerationIsANoOp() {
        failurePolicy.failAlways();
        UUID publicationId = escalatedPublication();
        String staleEventId = currentErrorEventId(publicationId);

        // Move the publication into the next generation, which makes the command above stale.
        retryCommandFor(publicationId, staleEventId);
        await().atMost(TIMEOUT).until(() -> "FAILED".equals(statusOf(publicationId))
                && completionAttemptsOf(publicationId) > properties.getMaxCompletionAttempts());
        awaitSentOutboxMessages(2);
        awaitReceivedFailureEvents(2);
        int attemptsAfterFirstRetry = completionAttemptsOf(publicationId);
        int invocationsAfterFirstRetry = failurePolicy.invocationCount();

        retryCommandFor(publicationId, staleEventId);

        assertThat(completionAttemptsOf(publicationId)).isEqualTo(attemptsAfterFirstRetry);
        assertThat(failurePolicy.invocationCount()).isEqualTo(invocationsAfterFirstRetry);
    }

    @Test
    void retryCommandWithoutFailureEventIdIsANoOp() {
        failurePolicy.failAlways();
        UUID publicationId = escalatedPublication();
        int attemptsBefore = completionAttemptsOf(publicationId);

        retryCommandFor(publicationId, null);

        assertThat(completionAttemptsOf(publicationId)).isEqualTo(attemptsBefore);
    }

    @Test
    void retryCommandForAnUnknownPublicationIsANoOp() {
        retryCommandFor(UUID.randomUUID(), "does-not-exist");

        assertThat(publications()).isEmpty();
    }

    @Test
    void discardCommandCompletesThePublicationWithoutInvokingTheListener() {
        failurePolicy.failAlways();
        UUID publicationId = escalatedPublication();
        int invocationsBefore = failurePolicy.invocationCount();

        sendDiscardCommandFor(publicationId, currentErrorEventId(publicationId));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(statusOf(publicationId)).isEqualTo("COMPLETED"));
        assertThat(failurePolicy.invocationCount())
                .withFailMessage("discarding must not run the listener")
                .isEqualTo(invocationsBefore);
    }

    @Test
    void discardCommandOfASupersededGenerationIsANoOp() {
        failurePolicy.failAlways();
        UUID publicationId = escalatedPublication();

        discardCommandFor(publicationId, "some-older-event-id");

        assertThat(statusOf(publicationId)).isEqualTo("FAILED");
    }

    /**
     * Drives a publication all the way to an escalated, operator-visible failure.
     */
    private UUID escalatedPublication() {
        UUID publicationId = completeOrderAndAwaitFailedPublication();
        exhaustRetryBudget(publicationId);
        await().atMost(TIMEOUT).until(() -> escalationCount() == 1);
        awaitSentOutboxMessages(1);
        awaitReceivedFailureEvents(1);
        return publicationId;
    }
}
