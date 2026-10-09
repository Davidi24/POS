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
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared PostgreSQL-backed refresh limiter. Keys are peppered hashes so client IPs and token IDs are not stored.
 * Each key uses a fixed window anchored at its first request; PostgreSQL atomically serializes competing requests.
 */
@Component
public class RefreshRateLimiter {

    private static final String TOO_MANY_REQUESTS_MESSAGE = "Too many refresh attempts. Try again later.";
    private static final String UPSERT_SQL = """
            INSERT INTO auth_refresh_rate_limit (scope, key_hash, window_started_at, attempt_count, expires_at)
            VALUES (?, ?, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP + (? * INTERVAL '1 second'))
            ON CONFLICT (scope, key_hash) DO UPDATE SET
                window_started_at = CASE
                    WHEN auth_refresh_rate_limit.window_started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                        THEN CURRENT_TIMESTAMP
                    ELSE auth_refresh_rate_limit.window_started_at
                END,
                attempt_count = CASE
                    WHEN auth_refresh_rate_limit.window_started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                        THEN 1
                    ELSE auth_refresh_rate_limit.attempt_count + 1
                END,
                expires_at = CASE
                    WHEN auth_refresh_rate_limit.window_started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                        THEN EXCLUDED.expires_at
                    ELSE auth_refresh_rate_limit.expires_at
                END
            WHERE auth_refresh_rate_limit.window_started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
               OR auth_refresh_rate_limit.attempt_count < ?
            RETURNING attempt_count
            """;

    private final JdbcTemplate jdbcTemplate;
    private final int maxAttemptsPerIp;
    private final int maxAttemptsPerToken;
    private final long windowSeconds;
    private final String pepper;
    private final AtomicLong lastCleanupEpochSecond = new AtomicLong();

    public RefreshRateLimiter(
            JdbcTemplate jdbcTemplate,
            @Value("${app.security.refresh-token.rate-limit.max-attempts-per-ip:20}") int maxAttemptsPerIp,
            @Value("${app.security.refresh-token.rate-limit.max-attempts-per-token:5}") int maxAttemptsPerToken,
            @Value("${app.security.refresh-token.rate-limit.window-minutes:1}") long windowMinutes,
            @Value("${app.security.refresh-token.pepper}") String pepper
    ) {
        if (maxAttemptsPerIp < 1 || maxAttemptsPerToken < 1) {
            throw new IllegalStateException("Refresh rate-limit attempt limits must be positive");
        }
        if (windowMinutes < 1 || windowMinutes > Long.MAX_VALUE / 60) {
            throw new IllegalStateException("Refresh rate-limit window must be positive and representable");
        }
        if (pepper == null || pepper.length() < 32) {
            throw new IllegalStateException("app.security.refresh-token.pepper must be at least 32 characters");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.maxAttemptsPerIp = maxAttemptsPerIp;
        this.maxAttemptsPerToken = maxAttemptsPerToken;
        this.windowSeconds = windowMinutes * 60;
        this.pepper = pepper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void check(String ip) {
        if (ip != null && !ip.isBlank()) {
            checkKey("IP", ip, maxAttemptsPerIp);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkByTokenId(UUID tokenId) {
        if (tokenId != null) {
            checkKey("TOKEN", tokenId.toString(), maxAttemptsPerToken);
        }
    }

    private void checkKey(String scope, String rawKey, int limit) {
        Instant now = Instant.now();
        cleanupExpiredRowsPeriodically(now);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                UPSERT_SQL,
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
            throw new TooManyRequestsException(TOO_MANY_REQUESTS_MESSAGE);
        }
    }

    private void cleanupExpiredRowsPeriodically(Instant now) {
        long epochSecond = now.getEpochSecond();
        long lastCleanup = lastCleanupEpochSecond.get();
        if (epochSecond - lastCleanup < windowSeconds || !lastCleanupEpochSecond.compareAndSet(lastCleanup, epochSecond)) {
            return;
        }
        jdbcTemplate.update("DELETE FROM auth_refresh_rate_limit WHERE expires_at <= CURRENT_TIMESTAMP");
    }

    private String hashKey(String scope, String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((pepper + "\u0000" + scope + "\u0000" + rawKey).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to hash refresh rate-limit key", ex);
        }
    }
}
