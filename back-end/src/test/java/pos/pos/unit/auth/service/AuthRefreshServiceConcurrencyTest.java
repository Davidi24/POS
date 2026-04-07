package pos.pos.unit.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.dto.AuthenticationResponse;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.auth.service.AuthRefreshService;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.RefreshRateLimiter;
import pos.pos.security.service.RefreshTokenSecurityService;
import pos.pos.security.util.ClientInfo;
import pos.pos.user.entity.User;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AuthRefreshService.class, JwtService.class, RefreshTokenSecurityService.class, UserMapper.class})
@DisplayName("AuthRefreshService concurrency")
class AuthRefreshServiceConcurrencyTest {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";

    @Autowired
    private AuthRefreshService authRefreshService;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenSecurityService refreshTokenSecurityService;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private RoleRepository roleRepository;

    @MockBean
    private JwtProperties jwtProperties;

    @MockBean
    private RefreshRateLimiter refreshRateLimiter;

    @BeforeEach
    void setUp() {
        given(jwtProperties.getAccessExpiration()).willReturn(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("Should allow only one concurrent refresh for the same refresh token")
    void shouldAllowOnlyOneConcurrentRefreshForTheSameRefreshToken() throws Exception {
        assertThat(AopUtils.isAopProxy(authRefreshService)).isTrue();

        UUID userId = UUID.randomUUID();
        UUID oldTokenId = UUID.randomUUID();
        List<String> roles = List.of("ADMIN");
        CountDownLatch firstRefreshInsideTransaction = new CountDownLatch(1);
        CountDownLatch allowFirstRefreshToFinish = new CountDownLatch(1);
        AtomicBoolean firstRoleLookup = new AtomicBoolean(true);

        User user = userRepository.saveAndFlush(activeUser(userId, "owner@pos.local"));
        String refreshToken = jwtService.generateRefreshToken(userId, oldTokenId);
        userSessionRepository.saveAndFlush(activeSession(userId, oldTokenId, refreshToken));

        given(roleRepository.findActiveRoleCodesByUserId(eq(userId))).willAnswer(invocation -> {
            if (firstRoleLookup.compareAndSet(true, false)) {
                firstRefreshInsideTransaction.countDown();
                if (!allowFirstRefreshToFinish.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to release the first refresh call");
                }
            }
            return roles;
        });

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<RefreshAttemptResult> firstAttempt = executorService.submit(
                    () -> attemptRefresh(refreshToken, new ClientInfo("127.0.0.1", "JUnit/first"))
            );

            assertThat(firstRefreshInsideTransaction.await(5, TimeUnit.SECONDS)).isTrue();

            Future<RefreshAttemptResult> secondAttempt = executorService.submit(
                    () -> attemptRefresh(refreshToken, new ClientInfo("127.0.0.1", "JUnit/second"))
            );

            Thread.sleep(150);
            allowFirstRefreshToFinish.countDown();

            RefreshAttemptResult firstResult = firstAttempt.get(10, TimeUnit.SECONDS);
            RefreshAttemptResult secondResult = secondAttempt.get(10, TimeUnit.SECONDS);

            assertThat(firstResult.error()).isNull();
            assertThat(firstResult.response()).isNotNull();
            assertThat(firstResult.response().getRefreshToken()).isNotBlank();
            assertThat(firstResult.response().getAccessToken()).isNotBlank();
            assertThat(firstResult.response().getTokenType()).isEqualTo("Bearer");
            assertThat(firstResult.response().getExpiresIn()).isEqualTo(900L);
            assertThat(firstResult.response().getUser()).isNotNull();
            assertThat(firstResult.response().getUser().getId()).isEqualTo(user.getId());

            assertThat(secondResult.response()).isNull();
            assertThat(secondResult.error()).isInstanceOf(InvalidCredentialsException.class);
            assertThat(secondResult.error()).hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            RefreshTokenSecurityService.ValidatedRefreshToken rotatedToken =
                    refreshTokenSecurityService.validate(firstResult.response().getRefreshToken());

            assertThat(userSessionRepository.count()).isEqualTo(1);
            assertThat(userSessionRepository.findByTokenIdAndRevokedFalse(oldTokenId)).isEmpty();
            assertThat(userSessionRepository.findByTokenIdAndRevokedFalse(rotatedToken.tokenId()))
                    .isPresent()
                    .get()
                    .satisfies(session -> {
                        assertThat(session.isRevoked()).isFalse();
                        assertThat(session.getUserId()).isEqualTo(userId);
                        assertThat(session.getRefreshTokenHash()).isEqualTo(rotatedToken.tokenHash());
                        assertThat(session.getUserAgent()).isEqualTo("JUnit/first");
                    });
        } finally {
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private RefreshAttemptResult attemptRefresh(String refreshToken, ClientInfo clientInfo) {
        try {
            return new RefreshAttemptResult(authRefreshService.refresh(refreshToken, clientInfo), null);
        } catch (Throwable error) {
            return new RefreshAttemptResult(null, error);
        }
    }

    private User activeUser(UUID userId, String email) {
        return User.builder()
                .id(userId)
                .email(email)
                .passwordHash("stored-password-hash")
                .firstName("Owner")
                .lastName("Manager")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .build();
    }

    private UserSession activeSession(UUID userId, UUID tokenId, String refreshToken) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return UserSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenId(tokenId)
                .sessionType("PASSWORD")
                .deviceName("Device")
                .refreshTokenHash(refreshTokenSecurityService.hash(refreshToken))
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .lastUsedAt(now.minusMinutes(5))
                .expiresAt(now.plusDays(7))
                .revoked(false)
                .createdAt(now.minusDays(1))
                .build();
    }

    private record RefreshAttemptResult(AuthenticationResponse response, Throwable error) {
    }
}
