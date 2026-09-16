package ch.admin.bit.jeap.modulith.errorhandling;

import ch.admin.bit.jeap.messaging.avro.security.AvroClassSecurity;
import ch.admin.bit.jeap.messaging.kafka.errorhandling.StackTraceHasher;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ModulithPublicationFailureEventBuilderTest {

    @BeforeAll
    static void trustGeneratedAvroTypes() {
        AvroClassSecurity.installDefaultIfMissing();
    }

    @Test
    void usesMessagingStackTraceHashIgnoringDynamicExceptionMessages() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        StackTraceHasher stackTraceHasher = new StackTraceHasher(kafkaProperties);
        RuntimeException firstFailure = failure("Order 123 failed");
        RuntimeException secondFailure = failure("Order 456 failed");

        String firstHash = build(firstFailure, kafkaProperties, stackTraceHasher).getPayload().getStackTraceHash();
        String secondHash = build(secondFailure, kafkaProperties, stackTraceHasher).getPayload().getStackTraceHash();

        assertThat(firstHash)
                .isEqualTo(stackTraceHasher.hash(firstFailure))
                .isEqualTo(secondHash)
                .hasSize(8);
    }

    @Test
    void omitsHashWhenMessagingStackTraceHashingIsDisabled() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setErrorStackTraceHashEnabled(false);

        ModulithPublicationProcessingFailedEvent event =
                build(failure("Processing failed"), kafkaProperties, new StackTraceHasher(kafkaProperties));

        assertThat(event.getPayload().getStackTraceHash()).isNull();
    }

    @Test
    void truncatesDisplayedStackTraceButHashesTheFullThrowable() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setErrorEventStackTraceMaxLength(40);
        StackTraceHasher stackTraceHasher = new StackTraceHasher(kafkaProperties);
        RuntimeException failure = failure("A deliberately long exception message");

        ModulithPublicationProcessingFailedEvent event = build(failure, kafkaProperties, stackTraceHasher);

        assertThat(event.getPayload().getStackTrace())
                .hasSize(43)
                .endsWith("...");
        assertThat(event.getPayload().getStackTraceHash()).isEqualTo(stackTraceHasher.hash(failure));
    }

    private static ModulithPublicationProcessingFailedEvent build(Throwable exception,
            KafkaProperties kafkaProperties, StackTraceHasher stackTraceHasher) {
        ModulithErrorHandlingProperties properties = new ModulithErrorHandlingProperties();
        properties.setFailureEventTopic("failures");
        properties.setRetryCommandTopic("retry");
        properties.setDiscardCommandTopic("discard");
        PublicationFailure failure = new PublicationFailure(
                UUID.randomUUID(), "example.Listener.on(example.Event)", "example.Event", 3, null);
        return new ModulithPublicationFailureEventBuilder(
                "test-system", "test-service", failure, exception, properties, kafkaProperties, stackTraceHasher)
                .build();
    }

    private static RuntimeException failure(String message) {
        RuntimeException exception = new RuntimeException(message);
        exception.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("example.Listener", "on", "Listener.java", 42)
        });
        return exception;
    }
}
