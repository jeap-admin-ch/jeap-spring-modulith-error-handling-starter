package ch.admin.bit.jeap.modulith.errorhandling;

import org.springframework.modulith.events.core.EventPublicationRepository.FailedCriteria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class PublicationSelectionContext {

    private final ThreadLocal<Selection> selection = new ThreadLocal<>();
    private final JdbcModulithPublicationRepository repository;
    private final ModulithErrorHandlingProperties properties;

    PublicationSelectionContext(JdbcModulithPublicationRepository repository,
            ModulithErrorHandlingProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    void retryable(Runnable action) {
        runWith(new Selection(null), action);
    }

    void exact(UUID publicationId, Runnable action) {
        runWith(new Selection(publicationId), action);
    }

    Optional<List<UUID>> select(FailedCriteria criteria) {
        Selection current = selection.get();
        if (current == null) {
            return Optional.empty();
        }
        if (current.publicationId != null) {
            return Optional.of(repository.findFailed(current.publicationId)
                    .map(PublicationFailure::publicationId)
                    .stream().toList());
        }
        return Optional.of(repository.findRetryableIds(
                properties.getMaxCompletionAttempts(),
                criteria.getPublicationDateReference(),
                criteria.getMaxItemsToRead()));
    }

    private void runWith(Selection requested, Runnable action) {
        Selection previous = selection.get();
        selection.set(requested);
        try {
            action.run();
        } finally {
            if (previous == null) {
                selection.remove();
            } else {
                selection.set(previous);
            }
        }
    }

    private record Selection(UUID publicationId) {
    }
}
