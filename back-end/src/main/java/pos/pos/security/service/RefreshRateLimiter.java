package pos.pos.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pos.pos.exception.auth.TooManyRequestsException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class implements a rate limiter using an in-memory data structure.
 * <p>
 * - ConcurrentHashMap:
 *   This is a thread-safe HashMap that can be accessed by multiple threads at the same time.
 *   It acts like a small database in RAM (not stored in a real database, so everything is temporary).
 *   It stores: IP → timestamps of requests for all users.
 * <p>
 * - computeIfAbsent:
 *   This checks if the IP (key) already exists in the map.
 *   If it exists → it returns the existing value.
 *   If it does not exist → it creates a new ArrayDeque and stores it.
 * <p>
 * - ArrayDeque:
 *   A queue where we can add/remove elements from both the beginning and the end.
 *   Here it stores timestamps of requests for a specific IP (computeIfAbsent) so it takes the timestamps
 *   of your ip in all users ip.
 * <p>
 * - Instant:
 *   Represents a timestamp (the exact time a request was made).
 * <p>
 * Flow:
 * <p>
 * 1. For a given IP, we get all its timestamps from the map and store them in "timestamps".
 * <p>
 * 2. synchronized(timestamps):
 *   This ensures that if multiple requests come from the SAME IP at the same time,
 *   only one thread can modify the timestamps at once.
 * <p>
 *   Without this, a race condition can happen:
 *   Example:
 *   - User 1 (IP 123) modifies timestamps
 *   - User 2 (IP 123) modifies timestamps at the same time
 *   → This can lead to incorrect data or corruption because both changes overlap.
 * <p>
 * 3. Old timestamps (outside the time window) are removed.
 * <p>
 * 4. The current timestamp is added.
 * <p>
 * 5. If the number of timestamps is greater than the allowed limit (e.g. 20),
 *    an error is thrown.
 * <p>
 * Important:
 * - This works per IP (not per user)
 * - Multiple users with the same IP share the same limit
 * - All data is stored only in memory (RAM), not in a database
 *
 * <p>
 * The same logic applies for tokenID
 */
// checked
// tested
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