package pos.pos.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pos.pos.exception.auth.TooManyRequestsException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory sliding-window limiter for refresh requests, keyed by client IP and refresh token ID.
 * Expired buckets are periodically pruned so state stays bounded even when a key is never used again.
 * works only for single node
 */
// checked
// tested
@Component
public class RefreshRateLimiter {

    private static final String TOO_MANY_REQUESTS_MESSAGE = "Too many refresh attempts. Try again later.";
    private static final long SECONDS_PER_MINUTE = 60L;

    @Value("${app.security.refresh-token.rate-limit.max-attempts-per-ip:20}")
    private int maxAttemptsPerIp;

    @Value("${app.security.refresh-token.rate-limit.max-attempts-per-token:5}")
    private int maxAttemptsPerToken;

    @Value("${app.security.refresh-token.rate-limit.window-minutes:1}")
    private long windowMinutes;

    private final ConcurrentHashMap<String, ArrayDeque<Instant>> ipWindow = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ArrayDeque<Instant>> tokenWindow = new ConcurrentHashMap<>();
    // AtomicLong is a thread-safe long value that supports atomic operations (no race conditions).
    // It behaves like a long, but: multiple threads can read/write safely, no need for synchronized
    // saved in RAM
    private final AtomicLong lastCleanupEpochSecond = new AtomicLong(0);

    public void check(String ip) {
        Instant now = Instant.now();
        cleanupExpiredBucketsIfNeeded(now);
        if (ip == null) {
            return;
        }
        checkBucket(ip, ipWindow, maxAttemptsPerIp, now);
    }

    public void checkByTokenId(UUID tokenId) {
        Instant now = Instant.now();
        cleanupExpiredBucketsIfNeeded(now);
        checkBucket(tokenId.toString(), tokenWindow, maxAttemptsPerToken, now);
    }

    // For a given key (IP or token), this updates its request history (bucket) atomically.
    // It gets or creates the deque of timestamps, removes expired ones (outside the window),
    // checks if the remaining requests exceed the limit → throws exception if so,
    // otherwise adds the current request time and stores the updated bucket back.
    private void checkBucket(
            String key,
            ConcurrentHashMap<String, ArrayDeque<Instant>> window,
            int maxAttempts,
            Instant now
    ) {
        Instant windowStart = now.minusSeconds(windowSizeSeconds());

        window.compute(key, (ignored, timestamps) -> {
            ArrayDeque<Instant> bucket = timestamps != null ? timestamps : new ArrayDeque<>();
            pruneExpired(bucket, windowStart);
            if (bucket.size() >= maxAttempts) {
                throw new TooManyRequestsException(TOO_MANY_REQUESTS_MESSAGE);
            }
            bucket.addLast(now);
            return bucket;
        });
    }

    private void cleanupExpiredBucketsIfNeeded(Instant now) {
        long cleanupIntervalSeconds = windowSizeSeconds();
        long nowEpochSecond = now.getEpochSecond();
        long lastCleanup = lastCleanupEpochSecond.get();
        // Has enough time passed since the last cleanup?
        if (nowEpochSecond - lastCleanup < cleanupIntervalSeconds) {
            return;
        }
        // This method is called on every request, so multiple threads can reach this point at the same time.
        // We use compareAndSet to ensure that only one thread performs the cleanup.
        // If another thread has already updated the value (meaning it already did the cleanup),
        // this thread skips and continues without doing it again.
        if (!lastCleanupEpochSecond.compareAndSet(lastCleanup, nowEpochSecond)) {
            return;
        }
        cleanupWindow(ipWindow, now);
        cleanupWindow(tokenWindow, now);
    }

    // Iterates over all keys (e.g., IPs) in the in-memory map.
    // For each key, it updates its deque of timestamps using computeIfPresent()
    // (so only existing entries are processed, thread-safe).
    // It calculates the window start and removes all timestamps older than that window.
    // If after cleanup the deque is empty, it returns null → which removes the key from the map.
    // Otherwise, it keeps the cleaned deque.
    // for all ip or tokens
    private void cleanupWindow(ConcurrentHashMap<String, ArrayDeque<Instant>> window, Instant now) {
        Instant windowStart = now.minusSeconds(windowSizeSeconds());
        for (String key : window.keySet()) {
            window.computeIfPresent(key, (ignored, timestamps) -> {
                pruneExpired(timestamps, windowStart);
                return timestamps.isEmpty() ? null : timestamps;
            });
        }
    }


    // Goes through the deque from the oldest elements (front) to the newest.
    // It checks each timestamp, and if it is before the window start,
    // it removes it from the deque.
    // We use pollFirst() instead of removeFirst() to avoid exceptions
    // in case the deque becomes empty.
    private void pruneExpired(ArrayDeque<Instant> timestamps, Instant windowStart) {
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
            timestamps.pollFirst();
        }
    }

    // Converts the configured window from minutes to seconds,
    // ensuring the result is at least 1 second to avoid zero or negative values
    private long windowSizeSeconds() {
        return Math.max(1L, windowMinutes * SECONDS_PER_MINUTE);
    }
}
