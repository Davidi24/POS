package pos.pos.unit.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pos.pos.exception.auth.TooManyRequestsException;
import pos.pos.security.service.RefreshRateLimiter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RefreshRateLimiter")
class RefreshRateLimiterTest {

    private JdbcTemplate jdbcTemplate;
    private RefreshRateLimiter limiter;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("attempt_count", 1)));
        limiter = new RefreshRateLimiter(jdbcTemplate, 2, 2, 1, "unit-test-refresh-rate-limit-pepper-32chars");
    }

    @Test
    @DisplayName("Skips the IP limit when the request has no IP")
    void skipsNullIp() {
        assertThatCode(() -> limiter.check(null)).doesNotThrowAnyException();
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Checks IP and token IDs through PostgreSQL")
    void checksBothDimensionsThroughDatabase() {
        UUID tokenId = UUID.randomUUID();
        limiter.check("203.0.113.20");
        limiter.checkByTokenId(tokenId);

        verify(jdbcTemplate, org.mockito.Mockito.times(2)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Rejects a database response indicating the key is already at its limit")
    void rejectsWhenDatabaseDoesNotReturnUpdatedBucket() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        assertThatThrownBy(() -> limiter.check("203.0.113.21"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Too many refresh attempts. Try again later.");
    }

    @Test
    @DisplayName("Rejects unsafe configuration")
    void rejectsUnsafeConfiguration() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RefreshRateLimiter(
                        jdbcTemplate, 0, 1, 1, "unit-test-refresh-rate-limit-pepper-32chars"))
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RefreshRateLimiter(
                        jdbcTemplate, 1, 1, 0, "unit-test-refresh-rate-limit-pepper-32chars"))
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RefreshRateLimiter(
                        jdbcTemplate, 1, 1, 1, "short"))
                .isInstanceOf(IllegalStateException.class);
    }
}
