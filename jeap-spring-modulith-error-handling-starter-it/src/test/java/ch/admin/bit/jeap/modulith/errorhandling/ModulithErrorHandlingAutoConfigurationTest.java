package ch.admin.bit.jeap.modulith.errorhandling;

import ch.admin.bit.jeap.messaging.transactionaloutbox.outbox.TransactionalOutbox;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ModulithErrorHandlingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ModulithErrorHandlingAutoConfiguration.class))
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean("jdbcEventPublicationRepository", EventPublicationRepository.class,
                    () -> mock(EventPublicationRepository.class))
            .withBean(TransactionalOutbox.class, () -> mock(TransactionalOutbox.class))
            .withBean(FailedEventPublications.class, () -> mock(FailedEventPublications.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withPropertyValues(
                    "spring.application.name=test-service",
                    "jeap.messaging.kafka.systemName=TEST",
                    "jeap.modulith.error-handling.failure-event-topic=errors",
                    "jeap.modulith.error-handling.retry-command-topic=retry",
                    "jeap.modulith.error-handling.discard-command-topic=discard");

    @Test
    void enablesErrorHandlingByDefault() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(ModulithErrorHandlingProperties.class)
                .hasSingleBean(ModulithPublicationScheduler.class)
                .hasSingleBean(ModulithPublicationCommandListener.class));
    }

    @Test
    void canDisableErrorHandling() {
        contextRunner
                .withPropertyValues("jeap.modulith.error-handling.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ModulithErrorHandlingProperties.class));
    }
}
