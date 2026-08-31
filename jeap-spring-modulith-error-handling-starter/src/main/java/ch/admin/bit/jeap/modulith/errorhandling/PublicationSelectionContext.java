package ch.admin.bit.jeap.modulith.errorhandling;

import org.springframework.modulith.events.core.EventPublicationRepository.FailedCriteria;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        runWith(new Selection(null, properties.getMaxCompletionAttempts()), action);
    }

    void exact(PublicationGeneration generation, Runnable action) {
        runWith(new Selection(generation, null), action);
    }

    Optional<List<PublicationGeneration>> select(FailedCriteria criteria) {
        Selection current = selection.get();
        if (current == null) {
            return Optional.empty();
        }
        List<PublicationGeneration> selected = current.exact == null
                ? repository.findRetryable(properties.getMaxCompletionAttempts(),
                        criteria.getPublicationDateReference(), criteria.getMaxItemsToRead())
                : List.of(current.exact);
        current.selected = selected.stream().collect(Collectors.toMap(
                PublicationGeneration::publicationId, Function.identity()));
        return Optional.of(selected);
    }

    Optional<Claim> claim(UUID publicationId) {
        Selection current = selection.get();
        if (current == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(current.selected.get(publicationId))
                .map(generation -> new Claim(generation, current.maxCompletionAttempts));
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

    record Claim(PublicationGeneration generation, Integer maxCompletionAttempts) {
    }

    private static final class Selection {
        private final PublicationGeneration exact;
        private final Integer maxCompletionAttempts;
        private Map<UUID, PublicationGeneration> selected = Map.of();

        private Selection(PublicationGeneration exact, Integer maxCompletionAttempts) {
            this.exact = exact;
            this.maxCompletionAttempts = maxCompletionAttempts;
        }
    }
}
