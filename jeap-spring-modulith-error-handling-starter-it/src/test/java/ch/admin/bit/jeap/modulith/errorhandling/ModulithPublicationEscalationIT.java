package ch.admin.bit.jeap.modulith.errorhandling;

import ch.admin.bit.jeap.messaging.transactionaloutbox.outbox.TransactionalOutboxException;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Escalation is the point where the starter hands a failure to the Error Handling Service. It has to happen
 * exactly once per generation, and the escalation record and the outbox message have to be written together.
 */
class ModulithPublicationEscalationIT extends ModulithErrorHandlingITBase {

    @Test
    void exhaustedPublicationIsEscalatedOnceWithTheFailureEventTheOperatorNeeds() {
        failurePolicy.failAlways();
        UUID publicationId = completeOrderAndAwaitFailedPublication();

        exhaustRetryBudget(publicationId);
        await().atMost(TIMEOUT).until(() -> escalationCount() == 1);
        awaitSentOutboxMessages(1);
        awaitReceivedFailureEvents(1);

        Map<String, Object> escalation = escalations().getFirst();
        assertThat(escalation.get("publication_id")).isEqualTo(publicationId);
        assertThat(escalation.get("completion_attempts")).isEqualTo(properties.getMaxCompletionAttempts());

        Map<String, Object> outboxMessage = outboxMessages().getFirst();
        assertThat(outboxMessage.get("topic")).isEqualTo(properties.getFailureEventTopic());
        assertThat(outboxMessage.get("message_id")).isEqualTo(escalation.get("error_event_id"));
        assertThat(outboxMessage.get("message_idempotence_id"))
                .isEqualTo(publicationId + ":" + properties.getMaxCompletionAttempts());
        assertThat(outboxMessage.get("message_type_name")).isEqualTo("ModulithPublicationProcessingFailedEvent");
        assertThat(outboxMessage.get("send_immediately")).isEqualTo(true);
        assertThat(outboxMessage.get("sent_immediately")).isNotNull();

        ModulithPublicationProcessingFailedEvent event = receivedFailureEvents.single();
        assertThat(event.getIdentity().getEventId()).isEqualTo(escalation.get("error_event_id"));
        assertThat(event.getIdentity().getIdempotenceId())
                .isEqualTo(publicationId + ":" + properties.getMaxCompletionAttempts());
        assertThat(event.getPayload().getListener()).contains("ShippingListener");
        assertThat(event.getPayload().getEventType()).contains("OrderCompleted");
        assertThat(event.getPayload().getErrorMessage()).contains("Shipping of order");
        assertThat(event.getPayload().getRetryCommandTopicName()).isEqualTo(properties.getRetryCommandTopic());
        assertThat(event.getPayload().getDiscardCommandTopicName()).isEqualTo(properties.getDiscardCommandTopic());
        assertThat(event.getPayload().getStackTrace()).contains("ShipmentFailedException");
    }

    @Test
    void repeatedReconciliationDoesNotEscalateTheSameGenerationTwice() {
        failurePolicy.failAlways();
        UUID publicationId = completeOrderAndAwaitFailedPublication();
        exhaustRetryBudget(publicationId);
        await().atMost(TIMEOUT).until(() -> escalationCount() == 1);
        awaitSentOutboxMessages(1);
        awaitReceivedFailureEvents(1);

        scheduler.reconcileExhaustedPublications();
        scheduler.reconcileExhaustedPublications();

        assertThat(escalationCount()).isEqualTo(1);
        assertThat(outboxMessages())
                .withFailMessage("a duplicate escalation must not reach the outbox")
                .hasSize(1);
        assertThat(receivedFailureEvents.all()).hasSize(1);
    }

    @Test
    void failedOutboxSendRollsBackTheEscalationRecord() {
        UUID publicationId = insertFailedPublication(
                "some.other.Listener.on(java.lang.Object)", properties.getMaxCompletionAttempts());
        String configuredFailureEventTopic = properties.getFailureEventTopic();
        properties.setFailureEventTopic("test-uncontracted-modulith-publication-processing-failed");

        try {
            assertThatThrownBy(() -> escalationService.escalate(publicationId, null))
                    .isInstanceOf(TransactionalOutboxException.class)
                    .hasMessageContaining("Contract validation");
        } finally {
            properties.setFailureEventTopic(configuredFailureEventTopic);
        }

        assertThat(escalationCount()).isZero();
        assertThat(outboxMessages()).isEmpty();
        assertThat(receivedFailureEvents.all()).isEmpty();
    }

    @Test
    void reconciliationEscalatesAPublicationThatWasNeverObservedInProcess() {
        // A row left behind by an instance that died: no advisor ever saw it fail.
        UUID publicationId = insertFailedPublication(
                "some.other.Listener.on(java.lang.Object)", properties.getMaxCompletionAttempts());

        scheduler.reconcileExhaustedPublications();
        awaitSentOutboxMessages(1);
        awaitReceivedFailureEvents(1);

        assertThat(escalationCount()).isEqualTo(1);
        assertThat(escalations().getFirst().get("publication_id")).isEqualTo(publicationId);
        assertThat(outboxMessages().getFirst().get("topic")).isEqualTo(properties.getFailureEventTopic());
    }

    @Test
    void escalatingAgainAfterAManualRetryProducesANewErrorForTheNewGeneration() {
        failurePolicy.failAlways();
        UUID publicationId = completeOrderAndAwaitFailedPublication();
        exhaustRetryBudget(publicationId);
        await().atMost(TIMEOUT).until(() -> escalationCount() == 1);
        awaitSentOutboxMessages(1);
        awaitReceivedFailureEvents(1);
        int exhaustedAt = completionAttemptsOf(publicationId);

        // A manual retry lifts the publication into the next generation, which fails again.
        retryCommandFor(publicationId, currentErrorEventId(publicationId));
        await().atMost(TIMEOUT).until(() -> completionAttemptsOf(publicationId) > exhaustedAt
                && "FAILED".equals(statusOf(publicationId)));
        scheduler.reconcileExhaustedPublications();

        await().atMost(TIMEOUT).until(() -> escalationCount() == 2);
        awaitSentOutboxMessages(2);
        awaitReceivedFailureEvents(2);
        assertThat(escalations()).hasSize(2);
        assertThat(receivedFailureEvents.all()).hasSize(2);
    }
}
