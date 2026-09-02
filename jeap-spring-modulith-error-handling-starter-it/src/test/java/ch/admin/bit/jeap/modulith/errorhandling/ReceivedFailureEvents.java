package ch.admin.bit.jeap.modulith.errorhandling;

import ch.admin.bit.jeap.messaging.kafka.test.TestKafkaListener;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedEvent;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class ReceivedFailureEvents {

    private final List<ModulithPublicationProcessingFailedEvent> events = new CopyOnWriteArrayList<>();

    @TestKafkaListener(id = "modulith-publication-processing-failed-test",
            topics = "${jeap.modulith.error-handling.failure-event-topic}")
    void receive(ModulithPublicationProcessingFailedEvent event, Acknowledgment acknowledgment) {
        events.add(event);
        acknowledgment.acknowledge();
    }

    void clear() {
        events.clear();
    }

    int size() {
        return events.size();
    }

    List<ModulithPublicationProcessingFailedEvent> all() {
        return List.copyOf(events);
    }

    ModulithPublicationProcessingFailedEvent single() {
        if (events.size() != 1) {
            throw new AssertionError("Expected exactly one failure event but received " + events.size());
        }
        return events.getFirst();
    }
}
