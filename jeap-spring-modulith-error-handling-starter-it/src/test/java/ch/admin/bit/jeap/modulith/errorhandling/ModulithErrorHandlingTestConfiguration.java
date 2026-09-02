package ch.admin.bit.jeap.modulith.errorhandling;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Infrastructure for the starter's integration tests.
 * <p>
 * PostgreSQL is real, because the starter's SQL is PostgreSQL specific and its idempotency guarantee
 * depends on a real primary key conflict. The transactional outbox and Kafka ingress and egress use their
 * production implementations against embedded Kafka.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ModulithErrorHandlingTestConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresqlContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"));
    }

    @Bean
    ReceivedFailureEvents receivedFailureEvents() {
        return new ReceivedFailureEvents();
    }
}
