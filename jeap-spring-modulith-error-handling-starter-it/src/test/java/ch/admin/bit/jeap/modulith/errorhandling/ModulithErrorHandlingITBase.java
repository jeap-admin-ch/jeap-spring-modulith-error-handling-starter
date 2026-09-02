package ch.admin.bit.jeap.modulith.errorhandling;

import ch.admin.bit.jeap.command.avro.AvroCommand;
import ch.admin.bit.jeap.command.avro.AvroCommandBuilder;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.test.KafkaIntegrationTestBase;
import ch.admin.bit.jeap.messaging.model.MessagePayload;
import ch.admin.bit.jeap.messaging.model.MessageReferences;
import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommand;
import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommandPayload;
import ch.admin.bit.jeap.modulith.command.discardpublication.DiscardModulithPublicationCommandReferences;
import ch.admin.bit.jeap.modulith.command.retrypublication.RetryModulithPublicationCommand;
import ch.admin.bit.jeap.modulith.command.retrypublication.RetryModulithPublicationCommandPayload;
import ch.admin.bit.jeap.modulith.command.retrypublication.RetryModulithPublicationCommandReferences;
import ch.admin.bit.jeap.modulith.errorhandling.testapp.ModulithTestApplication;
import ch.admin.bit.jeap.modulith.errorhandling.testapp.order.OrderService;
import ch.admin.bit.jeap.modulith.errorhandling.testapp.order.SynchronousOrderService;
import ch.admin.bit.jeap.modulith.errorhandling.testapp.shipping.ShipmentFailurePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

/**
 * Base class for the starter's integration tests. They run a real Spring Boot application, Spring Modulith event
 * publication registry with asynchronous and synchronous persistent listeners, PostgreSQL created from the reference
 * DDL this repository ships to consumers, transactional outbox, and Kafka messaging.
 * <p>
 * Both scheduled jobs are configured with an interval of an hour so that they never fire on their own during
 * a test. The tests invoke them through the injected bean instead, which still goes through the ShedLock
 * proxy and therefore still takes a real database lock.
 */
@SpringBootTest(classes = ModulithTestApplication.class)
@Import(ModulithErrorHandlingTestConfiguration.class)
@ActiveProfiles("test")
abstract class ModulithErrorHandlingITBase extends KafkaIntegrationTestBase {

    protected static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    protected OrderService orderService;

    @Autowired
    protected SynchronousOrderService synchronousOrderService;

    @Autowired
    protected ShipmentFailurePolicy failurePolicy;

    @Autowired
    protected ReceivedFailureEvents receivedFailureEvents;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ModulithPublicationScheduler scheduler;

    @Autowired
    protected ModulithErrorHandlingProperties properties;

    @Autowired
    protected ModulithPublicationCommandListener commandListener;

    @Autowired
    protected ModulithPublicationEscalationService escalationService;

    @Autowired
    protected KafkaProperties kafkaProperties;

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM deferred_message");
        jdbcTemplate.update("DELETE FROM modulith_publication_failure");
        jdbcTemplate.update("DELETE FROM event_publication");
        // The shedlock rows are deliberately left alone: ShedLock caches which lock records it has
        // created, so deleting them behind its back turns every later acquisition into an UPDATE that
        // matches nothing, and the sweeps would be skipped silently.
        failurePolicy.reset();
        receivedFailureEvents.clear();
    }

    /**
     * Completes an order and waits until the asynchronous listener has left a failed publication behind.
     *
     * @return the identifier of the failed publication
     */
    protected UUID completeOrderAndAwaitFailedPublication() {
        orderService.completeOrder();
        await().atMost(TIMEOUT).until(() -> failedPublications().size() == 1);
        return (UUID) failedPublications().getFirst().get("id");
    }

    protected List<Map<String, Object>> failedPublications() {
        return jdbcTemplate.queryForList(
                "SELECT id, status, completion_attempts FROM event_publication WHERE status = 'FAILED'");
    }

    protected void awaitPublicationStatus(UUID publicationId, String expectedStatus) {
        await().atMost(TIMEOUT).until(() -> expectedStatus.equals(statusOf(publicationId)));
    }

    protected void awaitSingleCompletedPublication() {
        await().atMost(TIMEOUT).until(() -> publications().size() == 1
                && "COMPLETED".equals(publications().getFirst().get("status")));
    }

    protected List<Map<String, Object>> publications() {
        return jdbcTemplate.queryForList(
                "SELECT id, status, completion_attempts FROM event_publication ORDER BY publication_date");
    }

    protected String statusOf(UUID publicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM event_publication WHERE id = ?", String.class, publicationId);
    }

    protected int completionAttemptsOf(UUID publicationId) {
        Integer attempts = jdbcTemplate.queryForObject(
                "SELECT completion_attempts FROM event_publication WHERE id = ?", Integer.class, publicationId);
        return attempts == null ? -1 : attempts;
    }

    protected int escalationCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM modulith_publication_failure", Integer.class);
        return count == null ? 0 : count;
    }

    protected List<Map<String, Object>> escalations() {
        return jdbcTemplate.queryForList(
                "SELECT publication_id, completion_attempts, error_event_id FROM modulith_publication_failure");
    }

    protected List<Map<String, Object>> outboxMessages() {
        return jdbcTemplate.queryForList("""
                SELECT topic, message_id, message_idempotence_id, message_type_name,
                       send_immediately, sent_immediately
                  FROM deferred_message
                  ORDER BY id
                """);
    }

    protected void awaitSentOutboxMessages(int expectedCount) {
        await().atMost(TIMEOUT).until(() -> {
            List<Map<String, Object>> messages = outboxMessages();
            return messages.size() == expectedCount
                    && messages.stream().allMatch(message -> message.get("sent_immediately") != null);
        });
    }

    protected void awaitReceivedFailureEvents(int expectedCount) {
        await().atMost(TIMEOUT).until(() -> receivedFailureEvents.size() == expectedCount);
    }

    /**
     * The identifier of the failure event the operator would be acting on, i.e. the one of the newest
     * escalated generation of the given publication.
     */
    protected String currentErrorEventId(UUID publicationId) {
        return jdbcTemplate.queryForObject("""
                SELECT error_event_id FROM modulith_publication_failure
                 WHERE publication_id = ?
                 ORDER BY completion_attempts DESC
                 LIMIT 1
                """, String.class, publicationId);
    }

    /**
     * Delivers a retry command the way the Error Handling Service would.
     */
    protected void retryCommandFor(UUID publicationId, String failureEventId) {
        commandListener.retry(retryCommand(publicationId, failureEventId), mock(Acknowledgment.class));
    }

    protected void sendRetryCommandFor(UUID publicationId, String failureEventId) {
        sendSync(properties.getRetryCommandTopic(), retryCommand(publicationId, failureEventId));
    }

    private RetryModulithPublicationCommand retryCommand(UUID publicationId, String failureEventId) {
        return new TestCommandBuilder<>(RetryModulithPublicationCommand::new, kafkaProperties,
                new RetryModulithPublicationCommandReferences(
                        new ch.admin.bit.jeap.modulith.command.retrypublication.ModulithPublicationReference(
                                "modulithPublication", publicationId.toString(), failureEventId)),
                new RetryModulithPublicationCommandPayload(), "retry:" + UUID.randomUUID()).build();
    }

    /**
     * Delivers a discard command the way the Error Handling Service would.
     */
    protected void discardCommandFor(UUID publicationId, String failureEventId) {
        commandListener.discard(discardCommand(publicationId, failureEventId), mock(Acknowledgment.class));
    }

    protected void sendDiscardCommandFor(UUID publicationId, String failureEventId) {
        sendSync(properties.getDiscardCommandTopic(), discardCommand(publicationId, failureEventId));
    }

    private DiscardModulithPublicationCommand discardCommand(UUID publicationId, String failureEventId) {
        return new TestCommandBuilder<>(DiscardModulithPublicationCommand::new, kafkaProperties,
                new DiscardModulithPublicationCommandReferences(
                        new ch.admin.bit.jeap.modulith.command.discardpublication.ModulithPublicationReference(
                                "modulithPublication", publicationId.toString(), failureEventId)),
                new DiscardModulithPublicationCommandPayload("operator request"),
                "discard:" + UUID.randomUUID()).build();
    }

    /**
     * Inserts a failed publication directly, for cases that need a row this application's listeners do not
     * own, or a specific generation.
     */
    protected UUID insertFailedPublication(String listenerId, int completionAttempts) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO event_publication
                    (id, listener_id, event_type, serialized_event, publication_date, status, completion_attempts)
                VALUES (?, ?, 'java.lang.String', '"payload"', ?, 'FAILED', ?)
                """, id, listenerId, Timestamp.from(Instant.now()), completionAttempts);
        return id;
    }

    /**
     * Runs the retry sweep until the publication has used up its retry budget.
     */
    protected void exhaustRetryBudget(UUID publicationId) {
        for (int attempt = completionAttemptsOf(publicationId);
             attempt < properties.getMaxCompletionAttempts();
             attempt = completionAttemptsOf(publicationId)) {
            int before = attempt;
            scheduler.retryFailedPublications();
            await().atMost(TIMEOUT).until(() -> completionAttemptsOf(publicationId) > before
                    && "FAILED".equals(statusOf(publicationId)));
        }
    }

    private static final class TestCommandBuilder<C extends AvroCommand>
            extends AvroCommandBuilder<TestCommandBuilder<C>, C> {

        private final KafkaProperties properties;
        private final MessageReferences references;
        private final MessagePayload payload;

        private TestCommandBuilder(Supplier<C> constructor, KafkaProperties properties,
                MessageReferences references, MessagePayload payload, String idempotenceId) {
            super(constructor);
            this.properties = properties;
            this.references = references;
            this.payload = payload;
            idempotenceId(idempotenceId);
        }

        @Override
        protected String getServiceName() {
            return properties.getServiceName();
        }

        @Override
        protected String getSystemName() {
            return properties.getSystemName();
        }

        @Override
        protected TestCommandBuilder<C> self() {
            return this;
        }

        @Override
        public C build() {
            setReferences(references);
            setPayload(payload);
            return super.build();
        }
    }
}
