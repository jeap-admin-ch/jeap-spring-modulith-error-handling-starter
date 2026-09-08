package ch.admin.bit.jeap.modulith.errorhandling;

import ch.admin.bit.jeap.messaging.contract.v2.Contract;
import ch.admin.bit.jeap.messaging.kafka.contract.ContractsValidator;
import ch.admin.bit.jeap.messaging.kafka.contract.DefaultContractsValidator;
import ch.admin.bit.jeap.messaging.kafka.contract.NoContractException;
import ch.admin.bit.jeap.messaging.transactionaloutbox.outbox.TransactionalOutbox;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ModulithErrorHandlingAutoConfigurationTest {

    private final LockProvider applicationLockProvider = mock(LockProvider.class);
    private final List<Contract> commandContracts = new ArrayList<>(List.of(
            Contract.builder().appName("test-service").role("consumer")
                    .messageTypeName("RetryModulithPublicationCommand").topics(new String[]{"retry"}).build(),
            Contract.builder().appName("test-service").role("consumer")
                    .messageTypeName("DiscardModulithPublicationCommand").topics(new String[]{"discard"}).build()));

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ModulithErrorHandlingAutoConfiguration.class))
            .withBean(ContractsValidator.class, () -> new DefaultContractsValidator("test-service", () -> commandContracts))
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
        commandContracts.clear();
        contextRunner
                .withPropertyValues("jeap.modulith.error-handling.enabled=false")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(ModulithErrorHandlingProperties.class)
                        .doesNotHaveBean(ModulithPublicationCommandListener.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RetryModulithPublicationCommand", "DiscardModulithPublicationCommand"})
    void failsStartupWhenCommandConsumerContractIsMissing(String missingCommand) {
        commandContracts.removeIf(contract -> contract.getMessageTypeName().equals(missingCommand));
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(NoContractException.class)
                    .hasStackTraceContaining(missingCommand);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"retry", "discard"})
    void failsStartupWhenConfiguredCommandTopicDoesNotMatchContract(String command) {
        contextRunner.withPropertyValues("jeap.modulith.error-handling." + command + "-command-topic=wrong-topic")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(NoContractException.class)
                            .hasStackTraceContaining("wrong-topic");
                });
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
