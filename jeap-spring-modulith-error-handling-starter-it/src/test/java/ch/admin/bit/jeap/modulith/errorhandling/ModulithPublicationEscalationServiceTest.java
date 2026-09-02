package ch.admin.bit.jeap.modulith.errorhandling;

import ch.admin.bit.jeap.messaging.transactionaloutbox.outbox.TransactionalOutbox;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModulithPublicationEscalationServiceTest {

    @Test
    void staleReconciliationGenerationDoesNotInsertOrPublish() {
        JdbcModulithPublicationRepository repository = mock(JdbcModulithPublicationRepository.class);
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        Environment environment = mock(Environment.class);
        when(environment.getRequiredProperty("jeap.messaging.kafka.systemName")).thenReturn("TEST");
        when(environment.getProperty("jeap.messaging.kafka.serviceName", "test-service"))
                .thenReturn("test-service");
        ModulithErrorHandlingProperties properties = properties();
        PublicationFailure stale = new PublicationFailure(UUID.randomUUID(), "listener", "event", 3, null);
        when(repository.lockFailed(stale.publicationId(), stale.completionAttempts()))
                .thenReturn(Optional.empty());
        ModulithPublicationEscalationService service = new ModulithPublicationEscalationService(
                repository, outbox, properties, transactionManager, environment, Clock.systemUTC());

        service.escalate(stale, null);

        ArgumentCaptor<TransactionDefinition> transactionDefinition =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(transactionDefinition.capture());
        assertThat(transactionDefinition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(repository, never()).recordEscalation(any(), any(), any());
        verify(outbox, never()).sendMessage(any(), any());
    }

    private static ModulithErrorHandlingProperties properties() {
        ModulithErrorHandlingProperties properties = new ModulithErrorHandlingProperties();
        properties.setFailureEventTopic("failures");
        properties.setRetryCommandTopic("retry");
        properties.setDiscardCommandTopic("discard");
        return properties;
    }
}
