package pos.pos.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.TooManyRequestsException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Limits for public requests that need no sign-in (restaurant applications and their status checks), shared by every
 * server through PostgreSQL. Each key gets a fixed window that starts at its first request; keys are stored only as
 * peppered hashes, so IP addresses and emails never are.
 */
@Component
public class PublicRequestRateLimiter {

    private static final String UPSERT_SQL = """
            INSERT INTO %s (scope, key_hash, window_started_at, attempt_count, expires_at)
            VALUES (?, ?, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP + (? * INTERVAL '1 second'))
            ON CONFLICT (scope, key_hash) DO UPDATE SET
                window_started_at = CASE
                    WHEN %2$s.window_started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second') THEN CURRENT_TIMESTAMP
                    ELSE %2$s.window_started_at
                END,
                attempt_count = CASE
                    WHEN %2$s.window_started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second') THEN 1
                    ELSE %2$s.attempt_count + 1
                END,
                expires_at = CASE
                    WHEN %2$s.window_started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second') THEN EXCLUDED.expires_at
                    ELSE %2$s.expires_at
                END
            WHERE %2$s.window_started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
               OR %2$s.attempt_count < ?
            RETURNING attempt_count
            """;

    private final JdbcTemplate jdbcTemplate;
    private final String pepper;
    private final AtomicLong lastCleanupEpochSecond = new AtomicLong();

    @Value("${spring.jpa.properties.hibernate.default_schema:public}")
    private String schemaName = "public";

    public PublicRequestRateLimiter(
            JdbcTemplate jdbcTemplate,
            @Value("${app.security.public-rate-limit.pepper:${app.security.refresh-token.pepper}}") String pepper
    ) {
        if (pepper == null || pepper.length() < 16) {
            throw new IllegalStateException("The public rate-limit pepper must be at least 16 characters");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.pepper = pepper;
    }

    /**
     * Counts one request for [rawKey] in [scope] and refuses it with 429 once [limit] requests were made within
     * [window]. A blank key (no IP or email known) is not limited.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void check(String scope, String rawKey, int limit, Duration window, String message) {
        if (rawKey == null || rawKey.isBlank()) {
            return;
        }
        if (limit < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Rate limits need a positive limit and window");
        }
        long windowSeconds = Math.max(1, window.toSeconds());
        cleanupExpiredRowsPeriodically(windowSeconds);
        String table = table();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                UPSERT_SQL.formatted(table, "public_rate_limits"),
                scope,
                hashKey(scope, rawKey),
                windowSeconds,
                windowSeconds,
                windowSeconds,
                windowSeconds,
                windowSeconds,
                limit
        );
        if (rows.isEmpty()) {
            throw new TooManyRequestsException(message);
        }
    }

    private String table() {
        if (!schemaName.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalStateException("Invalid schema");
        return "\"" + schemaName + "\".public_rate_limits";
    }

    private void cleanupExpiredRowsPeriodically(long windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long last = lastCleanupEpochSecond.get();
        if (now - last < Math.min(windowSeconds, 3600) || !lastCleanupEpochSecond.compareAndSet(last, now)) {
            return;
        }
        jdbcTemplate.update("DELETE FROM " + table() + " WHERE expires_at <= CURRENT_TIMESTAMP");
    }

    private String hashKey(String scope, String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((pepper + "\u0000" + scope + "\u0000" + rawKey.trim().toLowerCase(java.util.Locale.ROOT))
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to hash rate-limit key", ex);
        }
    }
}
