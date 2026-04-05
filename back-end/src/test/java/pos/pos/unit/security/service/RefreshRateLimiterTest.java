package pos.pos.unit.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pos.pos.exception.auth.TooManyRequestsException;
import pos.pos.security.service.RefreshRateLimiter;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RefreshRateLimiter")
class RefreshRateLimiterTest {

    private static final String TOO_MANY_REQUESTS_MESSAGE = "Too many refresh attempts. Try again later.";

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
                refreshRateLimiter.check("127.0.0.1");
                refreshRateLimiter.check("127.0.0.1");
            }).doesNotThrowAnyException();

            assertThat(ipWindow().get("127.0.0.1")).hasSize(2);
        }

        @Test
        @DisplayName("Should block attempts after the configured IP limit")
        void shouldBlockAttemptsAfterConfiguredIpLimit() {
            refreshRateLimiter.check("127.0.0.1");
            refreshRateLimiter.check("127.0.0.1");

            assertThatThrownBy(() -> refreshRateLimiter.check("127.0.0.1"))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessage(TOO_MANY_REQUESTS_MESSAGE);
        }

        @Test
        @DisplayName("Should prune expired IP attempts before enforcing the limit")
        void shouldPruneExpiredIpAttemptsBeforeEnforcingTheLimit() {
            ReflectionTestUtils.setField(refreshRateLimiter, "maxAttemptsPerIp", 1);
            ArrayDeque<Instant> timestamps = new ArrayDeque<>();
            timestamps.add(Instant.now().minusSeconds(120));
            ipWindow().put("127.0.0.1", timestamps);

            assertThatCode(() -> refreshRateLimiter.check("127.0.0.1")).doesNotThrowAnyException();

            assertThat(ipWindow().get("127.0.0.1")).hasSize(1);
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
        @DisplayName("Should block attempts after the configured token limit")
        void shouldBlockAttemptsAfterConfiguredTokenLimit() {
            UUID tokenId = UUID.randomUUID();

            refreshRateLimiter.checkByTokenId(tokenId);
            refreshRateLimiter.checkByTokenId(tokenId);

            assertThatThrownBy(() -> refreshRateLimiter.checkByTokenId(tokenId))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessage(TOO_MANY_REQUESTS_MESSAGE);
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
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, ArrayDeque<Instant>> ipWindow() {
        return (ConcurrentHashMap<String, ArrayDeque<Instant>>) ReflectionTestUtils.getField(refreshRateLimiter, "ipWindow");
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, ArrayDeque<Instant>> tokenWindow() {
        return (ConcurrentHashMap<String, ArrayDeque<Instant>>) ReflectionTestUtils.getField(refreshRateLimiter, "tokenWindow");
    }
}
