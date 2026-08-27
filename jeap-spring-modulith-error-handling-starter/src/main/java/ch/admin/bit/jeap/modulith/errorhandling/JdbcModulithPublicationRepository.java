package ch.admin.bit.jeap.modulith.errorhandling;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class JdbcModulithPublicationRepository {

    private static final String SQL_IDENTIFIER = "[A-Za-z0-9_.]+";

    private final JdbcTemplate jdbcTemplate;
    private final String publicationTable;
    private final String escalationTable;

    JdbcModulithPublicationRepository(JdbcTemplate jdbcTemplate, ModulithErrorHandlingProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.publicationTable = validatedIdentifier(properties.getPublicationTable());
        this.escalationTable = validatedIdentifier(properties.getEscalationTable());
    }

    List<UUID> findRetryableIds(int maxCompletionAttempts, Instant publishedBefore, long limit) {
        String agePredicate = publishedBefore == null
                ? ""
                : "AND COALESCE(last_resubmission_date, publication_date) < ?";
        String sql = """
                SELECT id
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
                    (resultSet, row) -> resultSet.getObject("id", UUID.class),
                    maxCompletionAttempts, effectiveLimit);
        }
        return jdbcTemplate.query(sql,
                (resultSet, row) -> resultSet.getObject("id", UUID.class),
                maxCompletionAttempts, Timestamp.from(publishedBefore), effectiveLimit);
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

    Optional<PublicationFailure> findFailed(UUID publicationId) {
        String sql = """
                SELECT id, listener_id, event_type, completion_attempts, serialized_event
                  FROM %s
                 WHERE id = ? AND status = 'FAILED'
                """.formatted(publicationTable);
        return jdbcTemplate.query(sql, this::mapFailure, publicationId).stream().findFirst();
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

    boolean completeFailed(UUID publicationId, Instant completedAt) {
        String sql = """
                UPDATE %s
                   SET status = 'COMPLETED', completion_date = ?
                 WHERE id = ? AND status = 'FAILED'
                """.formatted(publicationTable);
        return jdbcTemplate.update(sql, Timestamp.from(completedAt), publicationId) == 1;
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
