package pos.pos.ops.health;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Minimal orchestrator probes. Responses intentionally reveal no database or configuration details. */
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class OperationalHealthController {

    private final JdbcTemplate jdbcTemplate;

    /** Process liveness: does not depend on external systems, so a database outage does not restart the app. */
    @GetMapping("/live")
    public Map<String, String> live() {
        return Map.of("status", "UP");
    }

    /** Readiness: only accept traffic when the database can answer a trivial query. */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (Integer.valueOf(1).equals(result)) {
                return ResponseEntity.ok(Map.of("status", "UP"));
            }
        } catch (DataAccessException ignored) {
            // Report only the status; SQL, hostnames, and credentials must never appear in a public probe response.
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
    }
}
