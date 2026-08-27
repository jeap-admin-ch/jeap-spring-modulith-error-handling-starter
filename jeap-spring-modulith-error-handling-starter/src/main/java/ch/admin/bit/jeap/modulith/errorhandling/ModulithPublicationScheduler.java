package ch.admin.bit.jeap.modulith.errorhandling;

import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;

final class ModulithPublicationScheduler {

    private final FailedEventPublications failedPublications;
    private final PublicationSelectionContext selectionContext;
    private final JdbcModulithPublicationRepository repository;
    private final ModulithPublicationEscalationService escalationService;
    private final ModulithErrorHandlingProperties properties;
    private final Clock clock;

    ModulithPublicationScheduler(FailedEventPublications failedPublications,
            PublicationSelectionContext selectionContext,
            JdbcModulithPublicationRepository repository,
            ModulithPublicationEscalationService escalationService,
            ModulithErrorHandlingProperties properties,
            Clock clock) {
        this.failedPublications = failedPublications;
        this.selectionContext = selectionContext;
        this.repository = repository;
        this.escalationService = escalationService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${jeap.modulith.error-handling.retry-interval:30s}")
    void retryFailedPublications() {
        selectionContext.retryable(() -> failedPublications.resubmit(ResubmissionOptions.defaults()
                .withBatchSize(properties.getBatchSize())
                .withMinAge(properties.getRetryMinAge())));
    }

    @Scheduled(fixedDelayString = "${jeap.modulith.error-handling.reconciliation-interval:5m}")
    void reconcileExhaustedPublications() {
        repository.findUnescalatedFailures(
                        properties.getMaxCompletionAttempts(),
                        clock.instant().minus(properties.getReconciliationMinAge()),
                        properties.getBatchSize())
                .forEach(failure -> escalationService.escalate(failure, null));
    }
}
