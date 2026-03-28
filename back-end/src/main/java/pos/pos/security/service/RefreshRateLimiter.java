package pos.pos.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pos.pos.exception.auth.TooManyRequestsException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RefreshRateLimiter {

    private static final String TOO_MANY_REQUESTS_MESSAGE = "Too many refresh attempts. Try again later.";

    @Value("${app.security.refresh-token.rate-limit.max-attempts-per-ip:20}")
    private int maxAttemptsPerIp;

    @Value("${app.security.refresh-token.rate-limit.window-minutes:1}")
    private long windowMinutes;

    private final ConcurrentHashMap<String, ArrayDeque<Instant>> window = new ConcurrentHashMap<>();

    public void check(String ip) {
        if (ip == null) {
            return;
        }

        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowMinutes * 60);

        ArrayDeque<Instant> timestamps = window.computeIfAbsent(ip, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }
            timestamps.addLast(now);
            if (timestamps.size() > maxAttemptsPerIp) {
                throw new TooManyRequestsException(TOO_MANY_REQUESTS_MESSAGE);
            }
        }
    }
}