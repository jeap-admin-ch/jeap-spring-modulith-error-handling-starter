package ch.admin.bit.jeap.modulith.errorhandling;

import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

final class JdbcTargetEventPublication implements TargetEventPublication {

    private final UUID identifier;
    private final Instant publicationDate;
    private final PublicationTargetIdentifier targetIdentifier;
    private final Supplier<Object> eventSupplier;
    private final Instant lastResubmissionDate;
    private final int completionAttempts;
    private Object event;
    private Instant completionDate;
    private Status status = Status.FAILED;

    JdbcTargetEventPublication(UUID identifier, Instant publicationDate, String listenerId,
            Supplier<Object> eventSupplier, Instant lastResubmissionDate, int completionAttempts) {
        this.identifier = identifier;
        this.publicationDate = publicationDate;
        this.targetIdentifier = PublicationTargetIdentifier.of(listenerId);
        this.eventSupplier = eventSupplier;
        this.lastResubmissionDate = lastResubmissionDate;
        this.completionAttempts = completionAttempts;
    }

    @Override
    public UUID getIdentifier() {
        return identifier;
    }

    @Override
    public Object getEvent() {
        if (event == null) {
            event = eventSupplier.get();
        }
        return event;
    }

    @Override
    public Instant getPublicationDate() {
        return publicationDate;
    }

    @Override
    public Optional<Instant> getCompletionDate() {
        return Optional.ofNullable(completionDate);
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public Instant getLastResubmissionDate() {
        return lastResubmissionDate;
    }

    @Override
    public int getCompletionAttempts() {
        return completionAttempts;
    }

    @Override
    public PublicationTargetIdentifier getTargetIdentifier() {
        return targetIdentifier;
    }

    @Override
    public void markCompleted(Instant instant) {
        completionDate = instant;
        status = Status.COMPLETED;
    }
}
