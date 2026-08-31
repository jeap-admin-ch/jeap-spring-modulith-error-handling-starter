package ch.admin.bit.jeap.modulith.errorhandling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class JdbcModulithPublicationRepository implements BeanClassLoaderAware {

    private static final String SQL_IDENTIFIER = "[A-Za-z0-9_.]+";
    private static final Logger LOG = LoggerFactory.getLogger(JdbcModulithPublicationRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final EventSerializer eventSerializer;
    private final String publicationTable;
    private final String escalationTable;
    private ClassLoader classLoader = ClassUtils.getDefaultClassLoader();

    JdbcModulithPublicationRepository(JdbcTemplate jdbcTemplate, EventSerializer eventSerializer,
            ModulithErrorHandlingProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventSerializer = eventSerializer;
        this.publicationTable = validatedIdentifier(properties.getPublicationTable());
        this.escalationTable = validatedIdentifier(properties.getEscalationTable());
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    List<PublicationGeneration> findRetryable(int maxCompletionAttempts, Instant publishedBefore, long limit) {
        String agePredicate = publishedBefore == null
                ? ""
                : "AND COALESCE(last_resubmission_date, publication_date) < ?";
        String sql = """
                SELECT id, completion_attempts
                  FROM %s
                 WHERE status = 'FAILED'
                   AND completion_attempts < ?
                   %s
                 ORDER BY COALESCE(last_resubmission_date, publication_date), publication_date
                 LIMIT ?
                """.formatted(publicationTable, agePredicate);
        int effectiveLimit = limit < 0 ? Integer.MAX_VALUE : Math.toIntExact(limit);
        if (publishedBefore == null) {
            return jdbcTemplate.query(sql,
                    (resultSet, row) -> mapGeneration(resultSet),
                    maxCompletionAttempts, effectiveLimit);
        }
        return jdbcTemplate.query(sql,
                (resultSet, row) -> mapGeneration(resultSet),
                maxCompletionAttempts, Timestamp.from(publishedBefore), effectiveLimit);
    }

    Optional<PublicationGeneration> findCommandTarget(UUID publicationId, String failureEventId) {
        String sql = """
                SELECT ep.id, ep.completion_attempts
                  FROM %s ep
                  JOIN %s failure
                    ON failure.publication_id = ep.id
                   AND failure.completion_attempts = ep.completion_attempts
                 WHERE ep.id = ?
                   AND ep.status = 'FAILED'
                   AND failure.error_event_id = ?
                """.formatted(publicationTable, escalationTable);
        return jdbcTemplate.query(sql, (resultSet, row) -> mapGeneration(resultSet),
                publicationId, failureEventId).stream().findFirst();
    }

    List<TargetEventPublication> findFailedPublications(List<PublicationGeneration> selected) {
        if (selected.isEmpty()) {
            return List.of();
        }
        String values = IntStream.range(0, selected.size())
                .mapToObj(index -> "(?, ?, %d)".formatted(index))
                .collect(Collectors.joining(", "));
        String sql = """
                SELECT ep.id, ep.listener_id, ep.event_type, ep.publication_date, ep.last_resubmission_date,
                       ep.completion_attempts, ep.serialized_event
                  FROM %s ep
                  JOIN (VALUES %s) requested(id, completion_attempts, position)
                    ON requested.id = ep.id
                   AND requested.completion_attempts = ep.completion_attempts
                 WHERE ep.status = 'FAILED'
                 ORDER BY requested.position
                """.formatted(publicationTable, values);
        List<Object> arguments = new ArrayList<>(selected.size() * 2);
        selected.forEach(publication -> {
            arguments.add(publication.publicationId());
            arguments.add(publication.completionAttempts());
        });
        return jdbcTemplate.query(sql, this::mapTargetPublication, arguments.toArray()).stream()
                .filter(Objects::nonNull)
                .toList();
    }

    boolean markResubmitted(PublicationGeneration generation, Instant resubmittedAt, Integer maxCompletionAttempts) {
        String maxPredicate = maxCompletionAttempts == null ? "" : "AND completion_attempts < ?";
        String sql = """
                UPDATE %s
                   SET status = 'RESUBMITTED',
                       completion_attempts = completion_attempts + 1,
                       last_resubmission_date = ?
                 WHERE id = ?
                   AND status = 'FAILED'
                   AND completion_attempts = ?
                   %s
                """.formatted(publicationTable, maxPredicate);
        if (maxCompletionAttempts == null) {
            return jdbcTemplate.update(sql, Timestamp.from(resubmittedAt), generation.publicationId(),
                    generation.completionAttempts()) == 1;
        }
        return jdbcTemplate.update(sql, Timestamp.from(resubmittedAt), generation.publicationId(),
                generation.completionAttempts(), maxCompletionAttempts) == 1;
    }

    List<PublicationFailure> findUnescalatedFailures(int maxCompletionAttempts, Instant failedBefore, int limit) {
        String sql = """
                SELECT ep.id, ep.listener_id, ep.event_type, ep.completion_attempts, ep.serialized_event
                  FROM %s ep
                 WHERE ep.status = 'FAILED'
                   AND ep.completion_attempts >= ?
                   AND COALESCE(ep.last_resubmission_date, ep.publication_date) < ?
                   AND NOT EXISTS (
                       SELECT 1
                         FROM %s failure
                        WHERE failure.publication_id = ep.id
                          AND failure.completion_attempts = ep.completion_attempts)
                 ORDER BY COALESCE(ep.last_resubmission_date, ep.publication_date), ep.publication_date
                 LIMIT ?
                """.formatted(publicationTable, escalationTable);
        return jdbcTemplate.query(sql, this::mapFailure,
                maxCompletionAttempts, Timestamp.from(failedBefore), limit);
    }

    Optional<PublicationFailure> lockFailed(UUID publicationId) {
        String sql = """
                SELECT id, listener_id, event_type, completion_attempts, serialized_event
                  FROM %s
                 WHERE id = ? AND status = 'FAILED'
                   FOR UPDATE
                """.formatted(publicationTable);
        return jdbcTemplate.query(sql, this::mapFailure, publicationId).stream().findFirst();
    }

    Optional<PublicationFailure> lockFailed(UUID publicationId, int completionAttempts) {
        String sql = """
                SELECT id, listener_id, event_type, completion_attempts, serialized_event
                  FROM %s
                 WHERE id = ? AND status = 'FAILED' AND completion_attempts = ?
                   FOR UPDATE
                """.formatted(publicationTable);
        return jdbcTemplate.query(sql, this::mapFailure, publicationId, completionAttempts).stream().findFirst();
    }

    boolean recordEscalation(PublicationFailure failure, String errorEventId, Instant escalatedAt) {
        String sql = """
                INSERT INTO %s (publication_id, completion_attempts, error_event_id, escalated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (publication_id, completion_attempts) DO NOTHING
                """.formatted(escalationTable);
        return jdbcTemplate.update(sql, failure.publicationId(), failure.completionAttempts(),
                errorEventId, Timestamp.from(escalatedAt)) == 1;
    }

    boolean completeFailed(UUID publicationId, String failureEventId, Instant completedAt) {
        String sql = """
                UPDATE %s ep
                   SET status = 'COMPLETED', completion_date = ?
                 WHERE ep.id = ? AND ep.status = 'FAILED'
                   AND EXISTS (
                       SELECT 1
                         FROM %s failure
                        WHERE failure.publication_id = ep.id
                          AND failure.completion_attempts = ep.completion_attempts
                          AND failure.error_event_id = ?)
                """.formatted(publicationTable, escalationTable);
        return jdbcTemplate.update(sql, Timestamp.from(completedAt), publicationId, failureEventId) == 1;
    }

    private PublicationGeneration mapGeneration(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new PublicationGeneration(resultSet.getObject("id", UUID.class),
                resultSet.getInt("completion_attempts"));
    }

    private TargetEventPublication mapTargetPublication(java.sql.ResultSet resultSet, int row)
            throws java.sql.SQLException {
        UUID publicationId = resultSet.getObject("id", UUID.class);
        String eventType = resultSet.getString("event_type");
        Class<?> eventClass;
        try {
            eventClass = ClassUtils.forName(eventType, classLoader);
        } catch (ClassNotFoundException exception) {
            LOG.warn("Event publication {} has unknown event type {}; skipping it.", publicationId, eventType);
            return null;
        }
        String serializedEvent = resultSet.getString("serialized_event");
        Timestamp lastResubmissionDate = resultSet.getTimestamp("last_resubmission_date");
        return new JdbcTargetEventPublication(
                publicationId,
                resultSet.getTimestamp("publication_date").toInstant(),
                resultSet.getString("listener_id"),
                () -> eventSerializer.deserialize(serializedEvent, eventClass),
                lastResubmissionDate == null ? null : lastResubmissionDate.toInstant(),
                resultSet.getInt("completion_attempts"));
    }

    private PublicationFailure mapFailure(java.sql.ResultSet resultSet, int row) throws java.sql.SQLException {
        String serializedEvent = resultSet.getString("serialized_event");
        return new PublicationFailure(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("listener_id"),
                resultSet.getString("event_type"),
                resultSet.getInt("completion_attempts"),
                serializedEvent == null ? null : serializedEvent.getBytes(StandardCharsets.UTF_8));
    }

    private static String validatedIdentifier(String identifier) {
        Assert.hasText(identifier, "Table name must not be empty");
        Assert.isTrue(identifier.matches(SQL_IDENTIFIER), "Table name contains unsupported characters");
        return identifier;
    }
}
