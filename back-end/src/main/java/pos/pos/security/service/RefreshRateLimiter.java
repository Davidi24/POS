package pos.pos.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pos.pos.exception.auth.TooManyRequestsException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

s

@Component
public class RefreshRateLimiter {

    private static final String TOO_MANY_REQUESTS_MESSAGE = "Too many refresh attempts. Try again later.";

    @Value("${app.security.refresh-token.rate-limit.max-attempts-per-ip:20}")
    private int maxAttemptsPerIp;

    @Value("${app.security.refresh-token.rate-limit.max-attempts-per-token:5}")
    private int maxAttemptsPerToken;

    @Value("${app.security.refresh-token.rate-limit.window-minutes:1}")
    private long windowMinutes;

    private final ConcurrentHashMap<String, ArrayDeque<Instant>> ipWindow = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ArrayDeque<Instant>> tokenWindow = new ConcurrentHashMap<>();

    public void check(String ip) {
        if (ip == null) {
            return;
        }
        checkBucket(ip, ipWindow, maxAttemptsPerIp);
    }

    public void checkByTokenId(UUID tokenId) {
        checkBucket(tokenId.toString(), tokenWindow, maxAttemptsPerToken);
    }

    private void checkBucket(String key, ConcurrentHashMap<String, ArrayDeque<Instant>> window, int maxAttempts) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowMinutes * 60);

        ArrayDeque<Instant> timestamps = window.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }
            timestamps.addLast(now);
            if (timestamps.size() > maxAttempts) {
                throw new TooManyRequestsException(TOO_MANY_REQUESTS_MESSAGE);
            }
        }
    }
}