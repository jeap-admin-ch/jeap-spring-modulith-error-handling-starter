package ch.admin.bit.jeap.modulith.errorhandling;

import ch.admin.bit.jeap.messaging.transactionaloutbox.outbox.TransactionalOutbox;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ModulithErrorHandlingAutoConfigurationTest {

    private final LockProvider applicationLockProvider = mock(LockProvider.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ModulithErrorHandlingAutoConfiguration.class))
            .withBean("applicationLockProvider", LockProvider.class, () -> applicationLockProvider)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean("jdbcEventPublicationRepository", EventPublicationRepository.class,
                    () -> mock(EventPublicationRepository.class))
            .withBean(TransactionalOutbox.class, () -> mock(TransactionalOutbox.class))
            .withBean(FailedEventPublications.class, () -> mock(FailedEventPublications.class))
            .withBean(EventSerializer.class, () -> mock(EventSerializer.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(TaskScheduler.class, () -> mock(TaskScheduler.class))
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
                .hasSingleBean(LockProvider.class)
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

    @Test
    void keepsExistingLockProvider() {
        contextRunner.run(context -> assertThat(context).getBean(LockProvider.class).isSameAs(applicationLockProvider));
    }

    @Test
    void createsJdbcLockProviderWhenApplicationDoesNotProvideOne() {
        LockProvider lockProvider = new ModulithErrorHandlingAutoConfiguration()
                .modulithErrorHandlingLockProvider(mock(DataSource.class));

        assertThat(lockProvider).isInstanceOf(JdbcTemplateLockProvider.class);
    }

    @Test
    void keepsApplicationClock() {
        Clock applicationClock = mock(Clock.class);

        contextRunner.withBean(Clock.class, () -> applicationClock)
                .run(context -> assertThat(context).getBean(Clock.class).isSameAs(applicationClock));
    }
}
