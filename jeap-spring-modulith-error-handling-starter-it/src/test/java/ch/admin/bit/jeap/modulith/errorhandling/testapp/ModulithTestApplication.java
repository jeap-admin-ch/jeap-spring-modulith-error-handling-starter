package ch.admin.bit.jeap.modulith.errorhandling.testapp;

import ch.admin.bit.jeap.messaging.annotations.JeapMessageConsumerContract;
import ch.admin.bit.jeap.messaging.annotations.JeapMessageProducerContract;
import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommand;
import ch.admin.bit.jeap.modulith.command.retrypublication.RetryModulithPublicationCommand;
import ch.admin.bit.jeap.modulith.event.publicationprocessingfailed.ModulithPublicationProcessingFailedEvent;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A real Spring Boot application with a real Spring Modulith setup, used by the integration tests of the
 * starter.
 * <p>
 * The application deliberately lives in its own package so that component scanning picks up only the test
 * modules below it, while the tests themselves stay in the starter's package and can therefore reach its
 * package private types.
 */
@SpringBootApplication
@JeapMessageConsumerContract(appName = "modulith-error-handling-it",
        value = RetryModulithPublicationCommand.TypeRef.class,
        topic = "test-retry-modulith-publication")
@JeapMessageConsumerContract(appName = "modulith-error-handling-it",
        value = DiscardModulithPublicationCommand.TypeRef.class,
        topic = "test-discard-modulith-publication")
@JeapMessageProducerContract(appName = "modulith-error-handling-it",
        value = ModulithPublicationProcessingFailedEvent.TypeRef.class,
        topic = "test-modulith-publication-processing-failed")
public class ModulithTestApplication {
}
