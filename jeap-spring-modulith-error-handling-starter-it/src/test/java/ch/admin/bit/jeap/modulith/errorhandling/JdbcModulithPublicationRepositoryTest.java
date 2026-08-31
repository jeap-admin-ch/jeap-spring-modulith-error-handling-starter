package ch.admin.bit.jeap.modulith.errorhandling;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.modulith.events.core.EventSerializer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcModulithPublicationRepositoryTest {

    @Test
    void usesTypedCutoffPredicateWhenMinimumAgeIsSet() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcModulithPublicationRepository repository = repository(jdbcTemplate);
        Instant cutoff = Instant.parse("2026-08-27T10:00:00Z");

        repository.findRetryable(3, cutoff, 100);

        assertThat(jdbcTemplate.sql)
                .contains("COALESCE(last_resubmission_date, publication_date) < ?")
                .doesNotContain("? IS NULL");
        assertThat(jdbcTemplate.arguments).containsExactly(3, Timestamp.from(cutoff), 100);
    }

    @Test
    void omitsCutoffPredicateWhenMinimumAgeIsNotSet() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcModulithPublicationRepository repository = repository(jdbcTemplate);

        repository.findRetryable(3, null, 100);

        assertThat(jdbcTemplate.sql).doesNotContain("COALESCE(last_resubmission_date, publication_date) < ?");
        assertThat(jdbcTemplate.arguments).containsExactly(3, 100);
    }

    @Test
    void automaticClaimRequiresSelectedGenerationAndRetryLimit() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcModulithPublicationRepository repository = repository(jdbcTemplate);
        UUID publicationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-27T10:00:00Z");

        repository.markResubmitted(new PublicationGeneration(publicationId, 2), now, 3);

        assertThat(jdbcTemplate.sql)
                .contains("status = 'FAILED'")
                .contains("completion_attempts = ?")
                .contains("completion_attempts < ?");
        assertThat(jdbcTemplate.arguments).containsExactly(Timestamp.from(now), publicationId, 2, 3);
    }

    @Test
    void manualClaimRequiresGenerationButMayExceedAutomaticLimit() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcModulithPublicationRepository repository = repository(jdbcTemplate);
        UUID publicationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-27T10:00:00Z");

        repository.markResubmitted(new PublicationGeneration(publicationId, 7), now, null);

        assertThat(jdbcTemplate.sql)
                .contains("status = 'FAILED'")
                .contains("completion_attempts = ?")
                .doesNotContain("completion_attempts < ?");
        assertThat(jdbcTemplate.arguments).containsExactly(Timestamp.from(now), publicationId, 7);
    }

    @Test
    void discardIsBoundToFailureEventAndCurrentGeneration() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcModulithPublicationRepository repository = repository(jdbcTemplate);
        UUID publicationId = UUID.randomUUID();

        repository.completeFailed(publicationId, "failure-event", Instant.parse("2026-08-27T10:00:00Z"));

        assertThat(jdbcTemplate.sql)
                .contains("failure.completion_attempts = ep.completion_attempts")
                .contains("failure.error_event_id = ?")
                .contains("ep.status = 'FAILED'");
    }

    @Test
    void retryTokenResolvesOnlyCurrentFailedGeneration() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcModulithPublicationRepository repository = repository(jdbcTemplate);
        UUID publicationId = UUID.randomUUID();

        repository.findCommandTarget(publicationId, "failure-event");

        assertThat(jdbcTemplate.sql)
                .contains("failure.completion_attempts = ep.completion_attempts")
                .contains("failure.error_event_id = ?")
                .contains("ep.status = 'FAILED'");
        assertThat(jdbcTemplate.arguments).containsExactly(publicationId, "failure-event");
    }

    @Test
    void escalationReloadLocksExactFailedGeneration() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcModulithPublicationRepository repository = repository(jdbcTemplate);
        UUID publicationId = UUID.randomUUID();

        repository.lockFailed(publicationId, 4);

        assertThat(jdbcTemplate.sql)
                .contains("status = 'FAILED'")
                .contains("completion_attempts = ?")
                .contains("FOR UPDATE");
        assertThat(jdbcTemplate.arguments).containsExactly(publicationId, 4);
    }

    @Test
    void targetedLoadingUsesOnlySelectedIdsAndGenerations() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcModulithPublicationRepository repository = repository(jdbcTemplate);
        PublicationGeneration first = new PublicationGeneration(UUID.randomUUID(), 2);
        PublicationGeneration second = new PublicationGeneration(UUID.randomUUID(), 5);

        repository.findFailedPublications(List.of(first, second));

        assertThat(jdbcTemplate.sql)
                .contains("JOIN (VALUES (?, ?, 0), (?, ?, 1))")
                .contains("requested.completion_attempts = ep.completion_attempts")
                .contains("ORDER BY requested.position");
        assertThat(jdbcTemplate.arguments).containsExactly(
                first.publicationId(), 2, second.publicationId(), 5);
    }

    @Test
    void emptyTargetedSelectionDoesNotQueryDatabase() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();

        assertThat(repository(jdbcTemplate).findFailedPublications(List.of())).isEmpty();

        assertThat(jdbcTemplate.sql).isNull();
    }

    private static JdbcModulithPublicationRepository repository(JdbcTemplate jdbcTemplate) {
        return new JdbcModulithPublicationRepository(jdbcTemplate,
                org.mockito.Mockito.mock(EventSerializer.class), new ModulithErrorHandlingProperties());
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {
        private String sql;
        private Object[] arguments;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.sql = sql;
            this.arguments = args;
            return List.of();
        }

        @Override
        public int update(String sql, Object... args) {
            this.sql = sql;
            this.arguments = args;
            return 0;
        }
    }
}
