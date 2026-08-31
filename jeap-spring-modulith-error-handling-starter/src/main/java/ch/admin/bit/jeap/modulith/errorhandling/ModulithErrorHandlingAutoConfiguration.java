package ch.admin.bit.jeap.modulith.errorhandling;

import ch.admin.bit.jeap.messaging.transactionaloutbox.outbox.TransactionalOutbox;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.config.EventPublicationAutoConfiguration;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

import javax.sql.DataSource;

/**
 * Auto-configuration entry point for Spring Modulith publication error handling.
 */
@AutoConfiguration(
        afterName = "org.springframework.modulith.events.jdbc.JdbcEventPublicationAutoConfiguration",
        before = EventPublicationAutoConfiguration.class)
@ConditionalOnClass(EventPublicationRepository.class)
@ConditionalOnProperty(prefix = "jeap.modulith.error-handling", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(ModulithErrorHandlingProperties.class)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "30m")
public class ModulithErrorHandlingAutoConfiguration {

    /**
     * Creates the auto-configuration.
     */
    public ModulithErrorHandlingAutoConfiguration() {
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock modulithErrorHandlingClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(LockProvider.class)
    LockProvider modulithErrorHandlingLockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }

    @Bean
    PublicationFailureCaptureContext publicationFailureCaptureContext() {
        return new PublicationFailureCaptureContext();
    }

    @Bean
    JdbcModulithPublicationRepository jdbcModulithPublicationRepository(
            JdbcTemplate jdbcTemplate, EventSerializer eventSerializer, ModulithErrorHandlingProperties properties) {
        return new JdbcModulithPublicationRepository(jdbcTemplate, eventSerializer, properties);
    }

    @Bean
    PublicationSelectionContext publicationSelectionContext(
            JdbcModulithPublicationRepository repository, ModulithErrorHandlingProperties properties) {
        return new PublicationSelectionContext(repository, properties);
    }

    @Bean
    @Primary
    EventPublicationRepository modulithErrorHandlingEventPublicationRepository(
            @Qualifier("jdbcEventPublicationRepository") EventPublicationRepository delegate,
            PublicationFailureCaptureContext failureCaptureContext,
            PublicationSelectionContext selectionContext,
            JdbcModulithPublicationRepository jdbcRepository) {
        return new DecoratingEventPublicationRepository(delegate, failureCaptureContext, selectionContext,
                jdbcRepository);
    }

    @Bean
    ModulithPublicationEscalationService modulithPublicationEscalationService(
            JdbcModulithPublicationRepository repository,
            TransactionalOutbox outbox,
            ModulithErrorHandlingProperties properties,
            PlatformTransactionManager transactionManager,
            Environment environment,
            Clock modulithErrorHandlingClock) {
        return new ModulithPublicationEscalationService(repository, outbox, properties, transactionManager,
                environment, modulithErrorHandlingClock);
    }

    @Bean
    Advisor modulithPublicationFailureAdvisor(PublicationFailureCaptureContext captureContext,
            ObjectProvider<ModulithPublicationEscalationService> escalationService) {
        return new ModulithPublicationFailureAdvisor(captureContext, escalationService);
    }

    @Bean
    ModulithPublicationScheduler modulithPublicationScheduler(
            FailedEventPublications failedPublications,
            PublicationSelectionContext selectionContext,
            JdbcModulithPublicationRepository repository,
            ModulithPublicationEscalationService escalationService,
            ModulithErrorHandlingProperties properties,
            Clock modulithErrorHandlingClock) {
        return new ModulithPublicationScheduler(failedPublications, selectionContext, repository, escalationService,
                properties, modulithErrorHandlingClock);
    }

    @Bean
    ModulithPublicationCommandListener modulithPublicationCommandListener(
            FailedEventPublications failedPublications,
            PublicationSelectionContext selectionContext,
            JdbcModulithPublicationRepository repository,
            Clock modulithErrorHandlingClock) {
        return new ModulithPublicationCommandListener(failedPublications, selectionContext, repository,
                modulithErrorHandlingClock);
    }
}
