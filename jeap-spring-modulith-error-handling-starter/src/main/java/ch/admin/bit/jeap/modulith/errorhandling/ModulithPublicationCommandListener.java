package ch.admin.bit.jeap.modulith.errorhandling;

import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommand;
import ch.admin.bit.jeap.modulith.command.retrypublication.RetryModulithPublicationCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

import java.time.Clock;
import java.util.UUID;

final class ModulithPublicationCommandListener {

    private static final Logger LOG = LoggerFactory.getLogger(ModulithPublicationCommandListener.class);

    private final FailedEventPublications failedPublications;
    private final PublicationSelectionContext selectionContext;
    private final JdbcModulithPublicationRepository repository;
    private final Clock clock;

    ModulithPublicationCommandListener(FailedEventPublications failedPublications,
            PublicationSelectionContext selectionContext,
            JdbcModulithPublicationRepository repository,
            Clock clock) {
        this.failedPublications = failedPublications;
        this.selectionContext = selectionContext;
        this.repository = repository;
        this.clock = clock;
    }

    @KafkaListener(id = "jeap-modulith-publication-retry",
            topics = "${jeap.modulith.error-handling.retry-command-topic}")
    void retry(RetryModulithPublicationCommand command, Acknowledgment acknowledgment) {
        UUID publicationId = UUID.fromString(command.getReferences().getPublication().getPublicationId());
        selectionContext.exact(publicationId, () -> failedPublications.resubmit(ResubmissionOptions.defaults()
                .withBatchSize(1)
                .withFilter(publication -> publication.getIdentifier().equals(publicationId))));
        acknowledgment.acknowledge();
        LOG.info("Processed retry command for Modulith publication {}.", publicationId);
    }

    @KafkaListener(id = "jeap-modulith-publication-discard",
            topics = "${jeap.modulith.error-handling.discard-command-topic}")
    void discard(DiscardModulithPublicationCommand command, Acknowledgment acknowledgment) {
        UUID publicationId = UUID.fromString(command.getReferences().getPublication().getPublicationId());
        boolean completed = repository.completeFailed(publicationId, clock.instant());
        acknowledgment.acknowledge();
        LOG.info("Processed discard command for Modulith publication {} (state changed: {}, reason: {}).",
                publicationId, completed, command.getPayload().getReason());
    }
}
