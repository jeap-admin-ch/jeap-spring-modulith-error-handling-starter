package ch.admin.bit.jeap.modulith.errorhandling;

import ch.admin.bit.jeap.modulith.errorhandling.testapp.shipping.ShipmentFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A synchronous {@code @TransactionalEventListener(AFTER_COMMIT)} uses the same persistent publication
 * lifecycle as an asynchronous {@code @ApplicationModuleListener}.
 */
class SynchronousModulithPublicationIT extends ModulithErrorHandlingITBase {

    private int configuredMaxCompletionAttempts;

    @BeforeEach
    void rememberConfiguredMaxCompletionAttempts() {
        configuredMaxCompletionAttempts = properties.getMaxCompletionAttempts();
    }

    @AfterEach
    void restoreConfiguredMaxCompletionAttempts() {
        properties.setMaxCompletionAttempts(configuredMaxCompletionAttempts);
    }

    @Test
    void synchronousAfterCommitListenerCompletesBeforeThePublisherReturns() {
        Thread publishingThread = Thread.currentThread();
        failurePolicy.succeedAlways();

        UUID orderId = synchronousOrderService.completeOrder();

        assertThat(failurePolicy.invocations()).containsExactly(orderId);
        assertThat(failurePolicy.invocationThreads()).containsExactly(publishingThread);
        assertThat(publications()).singleElement().satisfies(publication -> {
            assertThat(publication.get("status")).isEqualTo("COMPLETED");
            assertThat(publication.get("completion_attempts")).isEqualTo(1);
        });
    }

    @Test
    void synchronousAfterCommitFailureLeavesAPersistentRetryablePublication() {
        Thread publishingThread = Thread.currentThread();
        failurePolicy.failAlways();

        UUID orderId = synchronousOrderService.completeOrder();

        assertThat(failurePolicy.invocations()).containsExactly(orderId);
        assertThat(failurePolicy.invocationThreads()).containsExactly(publishingThread);
        assertThat(failedPublications()).singleElement().satisfies(publication ->
                assertThat(publication.get("completion_attempts")).isEqualTo(1));
        assertThat(escalationCount()).isZero();
    }

    @Test
    void retrySweepResubmitsTheSynchronousListener() {
        failurePolicy.failTimes(1);
        synchronousOrderService.completeOrder();
        UUID publicationId = onlyPublicationId();

        scheduler.retryFailedPublications();

        assertThat(statusOf(publicationId)).isEqualTo("COMPLETED");
        assertThat(completionAttemptsOf(publicationId)).isEqualTo(2);
        assertThat(failurePolicy.invocationCount()).isEqualTo(2);
    }

    @Test
    void exhaustedInitialSynchronousFailureIsEscalatedImmediately() {
        UUID publicationId = escalatedSynchronousPublication();

        assertThat(statusOf(publicationId)).isEqualTo("FAILED");
        assertThat(escalations()).singleElement().satisfies(escalation -> {
            assertThat(escalation.get("publication_id")).isEqualTo(publicationId);
            assertThat(escalation.get("completion_attempts")).isEqualTo(1);
        });
        assertThat(outboxMessages()).singleElement().satisfies(message -> {
            assertThat(message.get("send_immediately")).isEqualTo(true);
            assertThat(message.get("sent_immediately")).isNotNull();
        });
    }

    @Test
    void operatorCanRetryAnEscalatedSynchronousPublication() {
        UUID publicationId = escalatedSynchronousPublication();
        failurePolicy.succeedAlways();

        retryCommandFor(publicationId, currentErrorEventId(publicationId));

        assertThat(statusOf(publicationId)).isEqualTo("COMPLETED");
        assertThat(completionAttemptsOf(publicationId)).isEqualTo(2);
    }

    @Test
    void operatorCanDiscardAnEscalatedSynchronousPublicationWithoutInvokingIt() {
        UUID publicationId = escalatedSynchronousPublication();
        int invocationsBeforeDiscard = failurePolicy.invocationCount();

        discardCommandFor(publicationId, currentErrorEventId(publicationId));

        assertThat(statusOf(publicationId)).isEqualTo("COMPLETED");
        assertThat(failurePolicy.invocationCount()).isEqualTo(invocationsBeforeDiscard);
    }

    @Test
    void plainEventListenerFailurePropagatesAndCreatesNoPublication() {
        failurePolicy.failAlways();

        assertThatThrownBy(synchronousOrderService::completeOrderWithPlainListener)
                .isInstanceOf(ShipmentFailedException.class)
                .hasMessageContaining("Shipping of order");

        assertThat(publications()).isEmpty();
        assertThat(escalationCount()).isZero();
        assertThat(outboxMessages()).isEmpty();
    }

    private UUID escalatedSynchronousPublication() {
        properties.setMaxCompletionAttempts(1);
        failurePolicy.failAlways();
        synchronousOrderService.completeOrder();
        assertThat(escalationCount()).isEqualTo(1);
        awaitSentOutboxMessages(1);
        awaitReceivedFailureEvents(1);
        return onlyPublicationId();
    }

    private UUID onlyPublicationId() {
        Map<String, Object> publication = publications().getFirst();
        return (UUID) publication.get("id");
    }
}
