package ch.admin.bit.jeap.modulith.errorhandling;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The retry sweep has to drive Spring Modulith's own resubmission, so that the listener really runs again,
 * and it has to stop at the configured budget.
 */
class ModulithPublicationRetryIT extends ModulithErrorHandlingITBase {

    @Test
    void retrySweepResubmitsThePublicationAndTheListenerRunsAgain() {
        failurePolicy.failTimes(1);
        UUID publicationId = completeOrderAndAwaitFailedPublication();

        scheduler.retryFailedPublications();

        await().atMost(TIMEOUT).until(() -> failurePolicy.invocationCount() == 2);
        awaitPublicationStatus(publicationId, "COMPLETED");
        assertThat(escalationCount()).isZero();
        assertThat(outboxMessages()).isEmpty();
    }

    @Test
    void retrySweepStopsAtTheConfiguredBudget() {
        failurePolicy.failAlways();
        UUID publicationId = completeOrderAndAwaitFailedPublication();

        exhaustRetryBudget(publicationId);
        int attemptsAtExhaustion = completionAttemptsOf(publicationId);

        // Another sweep must not pick the publication up again.
        scheduler.retryFailedPublications();
        awaitSentOutboxMessages(1);
        awaitReceivedFailureEvents(1);

        assertThat(attemptsAtExhaustion).isEqualTo(properties.getMaxCompletionAttempts());
        assertThat(completionAttemptsOf(publicationId)).isEqualTo(attemptsAtExhaustion);
        assertThat(failurePolicy.invocationCount()).isEqualTo(properties.getMaxCompletionAttempts());
        assertThat(statusOf(publicationId)).isEqualTo("FAILED");
    }

    @Test
    void retrySweepReturnsAPublicationWithAnUnknownListenerToFailed() {
        failurePolicy.failAlways();
        UUID publicationId = completeOrderAndAwaitFailedPublication();
        int attemptsBefore = completionAttemptsOf(publicationId);

        // Spring Modulith temporarily claims the publication, then returns it to FAILED when no listener matches.
        UUID unknown = insertFailedPublication("some.other.Listener.on(java.lang.Object)", 0);
        scheduler.retryFailedPublications();

        await().atMost(TIMEOUT).until(() -> completionAttemptsOf(publicationId) > attemptsBefore
                && "FAILED".equals(statusOf(publicationId)));
        await().atMost(TIMEOUT).until(() -> completionAttemptsOf(unknown) == 1
                && "FAILED".equals(statusOf(unknown)));
    }
}
