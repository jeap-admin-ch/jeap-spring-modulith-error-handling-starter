package ch.admin.bit.jeap.modulith.errorhandling;

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;

class ModulithPublicationScheduler {

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
    @SchedulerLock(name = "modulith-publication-retry",
            lockAtLeastFor = "${jeap.modulith.error-handling.retry-lock-at-least:5s}",
            lockAtMostFor = "${jeap.modulith.error-handling.retry-lock-at-most:5m}")
    void retryFailedPublications() {
        LockAssert.assertLocked();
        selectionContext.retryable(() -> failedPublications.resubmit(ResubmissionOptions.defaults()
                .withBatchSize(properties.getBatchSize())
                .withMinAge(properties.getRetryMinAge())));
    }

    @Scheduled(fixedDelayString = "${jeap.modulith.error-handling.reconciliation-interval:5m}")
    @SchedulerLock(name = "modulith-publication-reconciliation",
            lockAtLeastFor = "${jeap.modulith.error-handling.reconciliation-lock-at-least:5s}",
            lockAtMostFor = "${jeap.modulith.error-handling.reconciliation-lock-at-most:30m}")
    void reconcileExhaustedPublications() {
        LockAssert.assertLocked();
        repository.findUnescalatedFailures(
                        properties.getMaxCompletionAttempts(),
                        clock.instant().minus(properties.getReconciliationMinAge()),
                        properties.getBatchSize())
                .forEach(failure -> escalationService.escalate(failure, null));
    }
}
