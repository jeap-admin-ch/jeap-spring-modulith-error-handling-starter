package ch.admin.bit.jeap.modulith.errorhandling;

import ch.admin.bit.jeap.messaging.transactionaloutbox.outbox.TransactionalOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import java.time.Clock;
import java.util.UUID;

final class ModulithPublicationEscalationService {

    private static final Logger LOG = LoggerFactory.getLogger(ModulithPublicationEscalationService.class);

    private final JdbcModulithPublicationRepository repository;
    private final TransactionalOutbox outbox;
    private final ModulithErrorHandlingProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final String systemName;
    private final String serviceName;

    ModulithPublicationEscalationService(JdbcModulithPublicationRepository repository,
            TransactionalOutbox outbox,
            ModulithErrorHandlingProperties properties,
            PlatformTransactionManager transactionManager,
            Environment environment,
            Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.systemName = environment.getRequiredProperty("jeap.messaging.kafka.systemName");
        this.serviceName = environment.getProperty("jeap.messaging.kafka.serviceName",
                environment.getRequiredProperty("spring.application.name"));
        validateProperties(properties);
    }

    void escalate(UUID publicationId, Throwable exception) {
        repository.findFailed(publicationId)
                .filter(failure -> failure.completionAttempts() >= properties.getMaxCompletionAttempts())
                .ifPresent(failure -> escalate(failure, exception));
    }

    void escalate(PublicationFailure failure, Throwable exception) {
        transactionTemplate.executeWithoutResult(status -> {
            var event = new ModulithPublicationFailureEventBuilder(
                    systemName, serviceName, failure, exception, properties).build();
            String eventId = event.getIdentity().getEventId();
            if (!repository.recordEscalation(failure, eventId, clock.instant())) {
                return;
            }
            outbox.sendMessage(event, properties.getFailureEventTopic());
            LOG.info("Escalated failed Modulith publication {} generation {} as event {}.",
                    failure.publicationId(), failure.completionAttempts(), eventId);
        });
    }

    private static void validateProperties(ModulithErrorHandlingProperties properties) {
        Assert.isTrue(properties.getMaxCompletionAttempts() > 0,
                "max-completion-attempts must be greater than zero");
        Assert.isTrue(properties.getBatchSize() > 0, "batch-size must be greater than zero");
        Assert.isTrue(properties.getMaxPayloadBytes() > 0, "max-payload-bytes must be greater than zero");
        Assert.hasText(properties.getFailureEventTopic(), "failure-event-topic must be configured");
        Assert.hasText(properties.getRetryCommandTopic(), "retry-command-topic must be configured");
        Assert.hasText(properties.getDiscardCommandTopic(), "discard-command-topic must be configured");
    }
}
