package ch.admin.bit.jeap.modulith.errorhandling;

import java.util.UUID;

record PublicationFailure(
        UUID publicationId,
        String listener,
        String eventType,
        int completionAttempts,
        byte[] serializedEvent) {
}
