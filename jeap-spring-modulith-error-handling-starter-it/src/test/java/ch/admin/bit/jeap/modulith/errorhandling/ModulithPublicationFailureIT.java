package ch.admin.bit.jeap.modulith.errorhandling;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The base case: a failing {@code @ApplicationModuleListener} has to leave a failed publication behind,
 * and it must not be escalated while it still has retries left.
 */
class ModulithPublicationFailureIT extends ModulithErrorHandlingITBase {

    @Test
    void failingListenerLeavesAFailedPublicationAndCountsTheFirstAttempt() {
        Thread publishingThread = Thread.currentThread();
        failurePolicy.failAlways();

        UUID publicationId = completeOrderAndAwaitFailedPublication();

        assertThat(statusOf(publicationId)).isEqualTo("FAILED");
        assertThat(failurePolicy.invocationCount()).isEqualTo(1);
        assertThat(failurePolicy.invocationThreads()).singleElement().isNotSameAs(publishingThread);
        // Spring Modulith counts the initial invocation, so the budget of max-completion-attempts
        // covers the first attempt plus the retries, not the retries alone.
        assertThat(completionAttemptsOf(publicationId)).isEqualTo(1);
        assertThat(escalationCount()).isZero();
        assertThat(outboxMessages()).isEmpty();
    }

    @Test
    void reconciliationDoesNotEscalateAPublicationThatStillHasRetriesLeft() {
        failurePolicy.failAlways();
        UUID publicationId = completeOrderAndAwaitFailedPublication();

        scheduler.reconcileExhaustedPublications();

        assertThat(completionAttemptsOf(publicationId)).isEqualTo(1);
        assertThat(escalationCount()).isZero();
        assertThat(outboxMessages()).isEmpty();
    }

    @Test
    void successfulListenerCompletesThePublicationWithoutAnyErrorHandling() {
        failurePolicy.succeedAlways();

        orderService.completeOrder();

        awaitSingleCompletedPublication();
        assertThat(failurePolicy.invocationCount()).isEqualTo(1);
        assertThat(escalationCount()).isZero();
        assertThat(outboxMessages()).isEmpty();
    }
}
