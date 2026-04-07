package pos.pos.unit.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pos.pos.exception.auth.TooManyRequestsException;
import pos.pos.security.service.RefreshRateLimiter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RefreshRateLimiter")
class RefreshRateLimiterTest {

    private static final String TOO_MANY_REQUESTS_MESSAGE = "Too many refresh attempts. Try again later.";
    private static final String PRIMARY_IP = "127.0.0.1";
    private static final String SECONDARY_IP = "192.168.0.1";

    private RefreshRateLimiter refreshRateLimiter;

    @BeforeEach
    void setUp() {
        refreshRateLimiter = new RefreshRateLimiter();
        ReflectionTestUtils.setField(refreshRateLimiter, "maxAttemptsPerIp", 2);
        ReflectionTestUtils.setField(refreshRateLimiter, "maxAttemptsPerToken", 2);
        ReflectionTestUtils.setField(refreshRateLimiter, "windowMinutes", 1L);
    }

    @Nested
    @DisplayName("check")
    class CheckTests {

        @Test
        @DisplayName("Should ignore null IP addresses")
        void shouldIgnoreNullIpAddresses() {
            assertThatCode(() -> refreshRateLimiter.check(null)).doesNotThrowAnyException();
            assertThat(ipWindow()).isEmpty();
        }

        @Test
        @DisplayName("Should allow attempts up to the configured IP limit")
        void shouldAllowAttemptsUpToConfiguredIpLimit() {
            assertThatCode(() -> {
                refreshRateLimiter.check(PRIMARY_IP);
                refreshRateLimiter.check(PRIMARY_IP);
            }).doesNotThrowAnyException();

            assertThat(ipWindow().get(PRIMARY_IP)).hasSize(2);
        }

        @Test
        @DisplayName("Should block attempts after the configured IP limit without growing the bucket")
        void shouldBlockAttemptsAfterConfiguredIpLimitWithoutGrowingTheBucket() {
            refreshRateLimiter.check(PRIMARY_IP);
            refreshRateLimiter.check(PRIMARY_IP);

            assertThatThrownBy(() -> refreshRateLimiter.check(PRIMARY_IP))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessage(TOO_MANY_REQUESTS_MESSAGE);
            assertThat(ipWindow().get(PRIMARY_IP)).hasSize(2);

            assertThatThrownBy(() -> refreshRateLimiter.check(PRIMARY_IP))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessage(TOO_MANY_REQUESTS_MESSAGE);
            assertThat(ipWindow().get(PRIMARY_IP)).hasSize(2);
        }

        @Test
        @DisplayName("Should prune expired IP attempts before enforcing the limit")
        void shouldPruneExpiredIpAttemptsBeforeEnforcingTheLimit() {
            ReflectionTestUtils.setField(refreshRateLimiter, "maxAttemptsPerIp", 1);
            ArrayDeque<Instant> timestamps = new ArrayDeque<>();
            timestamps.add(Instant.now().minusSeconds(120));
            ipWindow().put(PRIMARY_IP, timestamps);

            assertThatCode(() -> refreshRateLimiter.check(PRIMARY_IP)).doesNotThrowAnyException();

            assertThat(ipWindow().get(PRIMARY_IP)).hasSize(1);
        }

        @Test
        @DisplayName("Should evict expired IP buckets during cleanup")
        void shouldEvictExpiredIpBucketsDuringCleanup() {
            ArrayDeque<Instant> timestamps = new ArrayDeque<>();
            timestamps.add(Instant.now().minusSeconds(120));
            ipWindow().put(PRIMARY_IP, timestamps);

            refreshRateLimiter.check(SECONDARY_IP);

            assertThat(ipWindow()).doesNotContainKey(PRIMARY_IP);
            assertThat(ipWindow().get(SECONDARY_IP)).hasSize(1);
        }

        @Test
        @DisplayName("Should enforce the IP limit under concurrent requests for the same IP")
        void shouldEnforceTheIpLimitUnderConcurrentRequestsForTheSameIp() throws Exception {
            ReflectionTestUtils.setField(refreshRateLimiter, "maxAttemptsPerIp", 3);

            ConcurrentResult result = runConcurrently(8, () -> refreshRateLimiter.check(PRIMARY_IP));

            assertThat(result.successCount()).isEqualTo(3);
            assertThat(result.failures())
                    .hasSize(5)
                    .allSatisfy(throwable -> assertThat(throwable).isInstanceOf(TooManyRequestsException.class));
            assertThat(ipWindow().get(PRIMARY_IP)).hasSize(3);
        }
    }

    @Nested
    @DisplayName("checkByTokenId")
    class CheckByTokenIdTests {

        @Test
        @DisplayName("Should allow attempts up to the configured token limit")
        void shouldAllowAttemptsUpToConfiguredTokenLimit() {
            UUID tokenId = UUID.randomUUID();

            assertThatCode(() -> {
                refreshRateLimiter.checkByTokenId(tokenId);
                refreshRateLimiter.checkByTokenId(tokenId);
            }).doesNotThrowAnyException();

            assertThat(tokenWindow().get(tokenId.toString())).hasSize(2);
        }

        @Test
        @DisplayName("Should block attempts after the configured token limit without growing the bucket")
        void shouldBlockAttemptsAfterConfiguredTokenLimitWithoutGrowingTheBucket() {
            UUID tokenId = UUID.randomUUID();

            refreshRateLimiter.checkByTokenId(tokenId);
            refreshRateLimiter.checkByTokenId(tokenId);

            assertThatThrownBy(() -> refreshRateLimiter.checkByTokenId(tokenId))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessage(TOO_MANY_REQUESTS_MESSAGE);
            assertThat(tokenWindow().get(tokenId.toString())).hasSize(2);

            assertThatThrownBy(() -> refreshRateLimiter.checkByTokenId(tokenId))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessage(TOO_MANY_REQUESTS_MESSAGE);
            assertThat(tokenWindow().get(tokenId.toString())).hasSize(2);
        }

        @Test
        @DisplayName("Should prune expired token attempts before enforcing the limit")
        void shouldPruneExpiredTokenAttemptsBeforeEnforcingTheLimit() {
            UUID tokenId = UUID.randomUUID();
            ReflectionTestUtils.setField(refreshRateLimiter, "maxAttemptsPerToken", 1);
            ArrayDeque<Instant> timestamps = new ArrayDeque<>();
            timestamps.add(Instant.now().minusSeconds(120));
            tokenWindow().put(tokenId.toString(), timestamps);

            assertThatCode(() -> refreshRateLimiter.checkByTokenId(tokenId)).doesNotThrowAnyException();

            assertThat(tokenWindow().get(tokenId.toString())).hasSize(1);
        }

        @Test
        @DisplayName("Should evict expired token buckets during cleanup")
        void shouldEvictExpiredTokenBucketsDuringCleanup() {
            UUID expiredTokenId = UUID.randomUUID();
            UUID activeTokenId = UUID.randomUUID();
            ArrayDeque<Instant> timestamps = new ArrayDeque<>();
            timestamps.add(Instant.now().minusSeconds(120));
            tokenWindow().put(expiredTokenId.toString(), timestamps);

            refreshRateLimiter.checkByTokenId(activeTokenId);

            assertThat(tokenWindow()).doesNotContainKey(expiredTokenId.toString());
            assertThat(tokenWindow().get(activeTokenId.toString())).hasSize(1);
        }

        @Test
        @DisplayName("Should enforce the token limit under concurrent requests for the same token")
        void shouldEnforceTheTokenLimitUnderConcurrentRequestsForTheSameToken() throws Exception {
            UUID tokenId = UUID.randomUUID();
            ReflectionTestUtils.setField(refreshRateLimiter, "maxAttemptsPerToken", 2);

            ConcurrentResult result = runConcurrently(6, () -> refreshRateLimiter.checkByTokenId(tokenId));

            assertThat(result.successCount()).isEqualTo(2);
            assertThat(result.failures())
                    .hasSize(4)
                    .allSatisfy(throwable -> assertThat(throwable).isInstanceOf(TooManyRequestsException.class));
            assertThat(tokenWindow().get(tokenId.toString())).hasSize(2);
        }
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, ArrayDeque<Instant>> ipWindow() {
        return (ConcurrentHashMap<String, ArrayDeque<Instant>>) ReflectionTestUtils.getField(refreshRateLimiter, "ipWindow");
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, ArrayDeque<Instant>> tokenWindow() {
        return (ConcurrentHashMap<String, ArrayDeque<Instant>>) ReflectionTestUtils.getField(refreshRateLimiter, "tokenWindow");
    }

    private ConcurrentResult runConcurrently(int threadCount, Runnable action) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to start concurrent rate limiter test");
                    }
                    try {
                        action.run();
                        return null;
                    } catch (Throwable throwable) {
                        return throwable;
                    }
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                Throwable throwable = future.get(5, TimeUnit.SECONDS);
                if (throwable != null) {
                    failures.add(throwable);
                }
            }

            return new ConcurrentResult(threadCount - failures.size(), failures);
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private record ConcurrentResult(int successCount, List<Throwable> failures) {
    }
}
