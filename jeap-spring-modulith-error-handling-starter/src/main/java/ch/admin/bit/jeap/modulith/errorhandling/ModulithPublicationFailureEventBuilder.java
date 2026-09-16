package ch.admin.bit.jeap.modulith.errorhandling;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventBuilder;
import ch.admin.bit.jeap.messaging.kafka.errorhandling.StackTraceHasher;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedEvent;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedPayload;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedReferences;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationReference;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.Temporality;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Arrays;

final class ModulithPublicationFailureEventBuilder extends
        AvroDomainEventBuilder<ModulithPublicationFailureEventBuilder, ModulithPublicationProcessingFailedEvent> {

    private final String systemName;
    private final String serviceName;
    private final PublicationFailure failure;
    private final Throwable exception;
    private final ModulithErrorHandlingProperties properties;
    private final KafkaProperties kafkaProperties;
    private final StackTraceHasher stackTraceHasher;

    ModulithPublicationFailureEventBuilder(String systemName, String serviceName, PublicationFailure failure,
            Throwable exception, ModulithErrorHandlingProperties properties, KafkaProperties kafkaProperties,
            StackTraceHasher stackTraceHasher) {
        super(ModulithPublicationProcessingFailedEvent::new);
        this.systemName = systemName;
        this.serviceName = serviceName;
        this.failure = failure;
        this.exception = exception;
        this.properties = properties;
        this.kafkaProperties = kafkaProperties;
        this.stackTraceHasher = stackTraceHasher;
        idempotenceId(failure.publicationId() + ":" + failure.completionAttempts());
    }

    @Override
    protected String getServiceName() {
        return serviceName;
    }

    @Override
    protected String getSystemName() {
        return systemName;
    }

    @Override
    protected ModulithPublicationFailureEventBuilder self() {
        return this;
    }

    @Override
    public ModulithPublicationProcessingFailedEvent build() {
        String stackTrace = truncate(stackTrace(exception), kafkaProperties.getErrorEventStackTraceMaxLength());
        byte[] serializedEvent = truncate(failure.serializedEvent(), properties.getMaxPayloadBytes());

        var payload = ModulithPublicationProcessingFailedPayload.newBuilder()
                .setListener(failure.listener())
                .setEventType(failure.eventType())
                .setErrorMessage(errorMessage())
                .setErrorDescription("Spring Modulith listener processing failed after %d completion attempts."
                        .formatted(failure.completionAttempts()))
                .setTemporality(Temporality.PERMANENT)
                .setRetryCommandTopicName(properties.getRetryCommandTopic())
                .setDiscardCommandTopicName(properties.getDiscardCommandTopic());

        if (stackTrace != null) {
            payload.setStackTrace(stackTrace);
            if (kafkaProperties.isErrorStackTraceHashEnabled()) {
                payload.setStackTraceHash(stackTraceHasher.hash(exception));
            }
        }
        if (serializedEvent != null) {
            payload.setSerializedEvent(ByteBuffer.wrap(serializedEvent))
                    .setSerializedEventContentType("application/json");
        }

        setReferences(ModulithPublicationProcessingFailedReferences.newBuilder()
                .setPublication(ModulithPublicationReference.newBuilder()
                        .setType("modulithPublication")
                        .setPublicationId(failure.publicationId().toString())
                        .build())
                .build());
        setPayload(payload.build());
        return super.build();
    }

    private String errorMessage() {
        if (exception == null) {
            return "Event publication processing failed and exhausted its retry budget.";
        }
        return exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage();
    }

    private static String stackTrace(Throwable exception) {
        if (exception == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static byte[] truncate(byte[] payload, int maxPayloadBytes) {
        if (payload == null) {
            return null;
        }
        return payload.length <= maxPayloadBytes ? payload : Arrays.copyOf(payload, maxPayloadBytes);
    }

    private static String truncate(String stackTrace, int maxLength) {
        if (stackTrace == null || stackTrace.length() <= maxLength) {
            return stackTrace;
        }
        return stackTrace.substring(0, maxLength) + "...";
    }
}
