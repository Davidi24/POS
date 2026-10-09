package pos.pos.unit.ops.health;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import pos.pos.ops.health.OperationalHealthController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalHealthControllerTest {

    @Test
    void livenessDoesNotDependOnDatabase() {
        OperationalHealthController controller = new OperationalHealthController(mock(JdbcTemplate.class));

        assertThat(controller.live()).containsEntry("status", "UP");
    }

    @Test
    void readinessReportsDatabaseAvailabilityWithoutLeakingFailureDetails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("secret-db.internal:5432 password=hunter2"));
        OperationalHealthController controller = new OperationalHealthController(jdbcTemplate);

        var response = controller.ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "DOWN");
        assertThat(response.getBody().toString()).doesNotContain("secret-db", "password", "hunter2");
    }
}
