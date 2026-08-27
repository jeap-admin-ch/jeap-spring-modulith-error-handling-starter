package ch.admin.bit.jeap.modulith.errorhandling;

import java.util.Optional;
import java.util.UUID;

final class PublicationFailureCaptureContext {

    private final ThreadLocal<Capture> capture = new ThreadLocal<>();

    void begin() {
        capture.set(new Capture());
    }

    void publicationFailed(UUID publicationId) {
        Capture current = capture.get();
        if (current != null) {
            current.publicationId = publicationId;
        }
    }

    Optional<UUID> failedPublicationId() {
        Capture current = capture.get();
        return current == null ? Optional.empty() : Optional.ofNullable(current.publicationId);
    }

    void clear() {
        capture.remove();
    }

    private static final class Capture {
        private UUID publicationId;
    }
}
