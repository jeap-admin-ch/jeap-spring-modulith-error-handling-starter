package ch.admin.bit.jeap.modulith.errorhandling;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcModulithPublicationRepositoryTest {

    @Test
    void usesTypedCutoffPredicateWhenMinimumAgeIsSet() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcModulithPublicationRepository repository = repository(jdbcTemplate);
        Instant cutoff = Instant.parse("2026-08-27T10:00:00Z");

        repository.findRetryableIds(3, cutoff, 100);

        assertThat(jdbcTemplate.sql)
                .contains("COALESCE(last_resubmission_date, publication_date) < ?")
                .doesNotContain("? IS NULL");
        assertThat(jdbcTemplate.arguments).containsExactly(3, Timestamp.from(cutoff), 100);
    }

    @Test
    void omitsCutoffPredicateWhenMinimumAgeIsNotSet() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcModulithPublicationRepository repository = repository(jdbcTemplate);

        repository.findRetryableIds(3, null, 100);

        assertThat(jdbcTemplate.sql).doesNotContain("COALESCE(last_resubmission_date, publication_date) < ?");
        assertThat(jdbcTemplate.arguments).containsExactly(3, 100);
    }

    private static JdbcModulithPublicationRepository repository(JdbcTemplate jdbcTemplate) {
        return new JdbcModulithPublicationRepository(jdbcTemplate, new ModulithErrorHandlingProperties());
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
    }
}
