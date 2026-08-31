package ch.admin.bit.jeap.modulith.errorhandling;

import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class DecoratingEventPublicationRepository implements EventPublicationRepository {

    private final EventPublicationRepository delegate;
    private final PublicationFailureCaptureContext failureCapture;
    private final PublicationSelectionContext selection;
    private final JdbcModulithPublicationRepository jdbcRepository;

    DecoratingEventPublicationRepository(EventPublicationRepository delegate,
            PublicationFailureCaptureContext failureCapture,
            PublicationSelectionContext selection,
            JdbcModulithPublicationRepository jdbcRepository) {
        this.delegate = delegate;
        this.failureCapture = failureCapture;
        this.selection = selection;
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public TargetEventPublication create(TargetEventPublication publication) {
        return delegate.create(publication);
    }

    @Override
    public void markProcessing(UUID identifier) {
        delegate.markProcessing(identifier);
    }

    @Override
    public void markCompleted(TargetEventPublication publication, Instant completionDate) {
        delegate.markCompleted(publication, completionDate);
    }

    @Override
    public void markCompleted(Object event, PublicationTargetIdentifier identifier, Instant completionDate) {
        delegate.markCompleted(event, identifier, completionDate);
    }

    @Override
    public void markCompleted(UUID identifier, Instant completionDate) {
        delegate.markCompleted(identifier, completionDate);
    }

    @Override
    public void markFailed(UUID identifier) {
        delegate.markFailed(identifier);
        failureCapture.publicationFailed(identifier);
    }

    @Override
    public boolean markResubmitted(UUID identifier, Instant resubmissionDate) {
        return selection.claim(identifier)
                .map(claim -> jdbcRepository.markResubmitted(
                        claim.generation(), resubmissionDate, claim.maxCompletionAttempts()))
                .orElseGet(() -> delegate.markResubmitted(identifier, resubmissionDate));
    }

    @Override
    public List<TargetEventPublication> findIncompletePublications() {
        return delegate.findIncompletePublications();
    }

    @Override
    public List<TargetEventPublication> findIncompletePublicationsPublishedBefore(Instant instant) {
        return delegate.findIncompletePublicationsPublishedBefore(instant);
    }

    @Override
    public Optional<TargetEventPublication> findIncompletePublicationsByEventAndTargetIdentifier(
            Object event, PublicationTargetIdentifier targetIdentifier) {
        return delegate.findIncompletePublicationsByEventAndTargetIdentifier(event, targetIdentifier);
    }

    @Override
    public List<TargetEventPublication> findCompletedPublications() {
        return delegate.findCompletedPublications();
    }

    @Override
    public void deletePublications(List<UUID> identifiers) {
        delegate.deletePublications(identifiers);
    }

    @Override
    public void deleteCompletedPublications() {
        delegate.deleteCompletedPublications();
    }

    @Override
    public void deleteCompletedPublicationsBefore(Instant instant) {
        delegate.deleteCompletedPublicationsBefore(instant);
    }

    @Override
    public List<TargetEventPublication> findFailedPublications(FailedCriteria criteria) {
        return selection.select(criteria)
                .map(jdbcRepository::findFailedPublications)
                .orElseGet(() -> delegate.findFailedPublications(criteria));
    }

    @Override
    public List<TargetEventPublication> findByStatus(Status status) {
        return delegate.findByStatus(status);
    }

    @Override
    public int countByStatus(Status status) {
        return delegate.countByStatus(status);
    }
}
