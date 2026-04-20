package pos.pos.integration.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.security.service.PasswordService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("Web auth integration test")
class WebAuthIntegrationTest {

    private static final String SCHEMA = "web_auth_" + UUID.randomUUID().toString().replace("-", "");
    private static final String COOKIE_NAME = "refreshToken";
    private static final String DEFAULT_PASSWORD = "StrongPass123!";
    private static final String WRONG_PASSWORD = "WrongPass123!";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid username/email or password";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";
    private static final String TOO_MANY_LOGIN_ATTEMPTS_MESSAGE = "Too many login attempts. Try again later.";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> "jdbc:postgresql://localhost:5432/pos?currentSchema=" + SCHEMA);
        registry.add("DB_USERNAME", () -> "pos_user");
        registry.add("DB_PASSWORD", () -> "pos_pass");
        registry.add("JWT_SECRET", () -> "web-auth-test-secret-key-for-hs256-123456");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "web-auth-refresh-token-pepper-0123456789");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "web-auth-password-reset-pepper-value");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "web-auth-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "web-auth-sms-code-pepper-value");
        registry.add("MAIL_HOST", () -> "localhost");
        registry.add("MAIL_PORT", () -> "2525");
        registry.add("MAIL_USERNAME", () -> "integration");
        registry.add("MAIL_PASSWORD", () -> "integration");
        registry.add("MAIL_FROM", () -> "no-reply@pos.example");
        registry.add("FRONTEND_BASE_URL", () -> "https://app.pos.example");
        registry.add("FRONTEND_DEFAULT_LINK_TARGET", () -> "UNIVERSAL");
        registry.add("TRUSTED_PROXIES", () -> "127.0.0.1,::1");
        registry.add("COOKIE_DOMAIN", () -> "pos.example");
        registry.add("BOOTSTRAP_SUPER_ADMIN_ENABLED", () -> "false");
        registry.add("SMS_DELIVERY_MODE", () -> "LOG_ONLY");
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas[0]", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private AuthLoginAttemptRepository authLoginAttemptRepository;

    @Autowired
    private PasswordService passwordService;

    @MockBean
    private JavaMailSender javaMailSender;

    private final AtomicInteger userSequence = new AtomicInteger(1);
    private final AtomicInteger ipSequence = new AtomicInteger(10);

    @BeforeEach
    void resetState() {
        doNothing().when(javaMailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
        authLoginAttemptRepository.deleteAllInBatch();
        userSessionRepository.deleteAllInBatch();
        userRoleRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("AUTH-001 POST /auth/web/login logs in with valid email + password")
    void auth001LoginWithValidEmailAndPassword() throws Exception {
        User user = createVerifiedUser("auth001");
        String ip = nextIp();

        MvcResult result = webLogin(user.getEmail(), DEFAULT_PASSWORD, ip, "AUTH-001", status().isOk());

        JsonNode body = bodyOf(result);
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("user").get("email").asText()).isEqualTo(user.getEmail());
        assertRefreshCookieIssued(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(activeSessionsFor(user)).hasSize(1);
    }

    @Test
    @DisplayName("AUTH-002 POST /auth/web/login logs in with valid username + password")
    void auth002LoginWithValidUsernameAndPassword() throws Exception {
        User user = createVerifiedUser("auth002");
        String ip = nextIp();

        MvcResult result = webLogin(user.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-002", status().isOk());

        JsonNode body = bodyOf(result);
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("user").get("username").asText()).isEqualTo(user.getUsername());
        assertRefreshCookieIssued(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(activeSessionsFor(user)).hasSize(1);
    }

    @Test
    @DisplayName("AUTH-003 POST /auth/web/login rejects wrong password")
    void auth003RejectsWrongPassword() throws Exception {
        User user = createVerifiedUser("auth003");
        String ip = nextIp();

        MvcResult result = webLogin(user.getUsername(), WRONG_PASSWORD, ip, "AUTH-003", status().isUnauthorized());

        assertThat(bodyOf(result).get("message").asText()).isEqualTo(INVALID_CREDENTIALS_MESSAGE);
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(activeSessionsFor(user)).isEmpty();
    }

    @Test
    @DisplayName("AUTH-004 POST /auth/web/login rejects inactive or deleted user")
    void auth004RejectsInactiveOrDeletedUser() throws Exception {
        User inactiveUser = createUser("auth004-inactive", false, true, null, "INACTIVE");
        User deletedUser = createUser("auth004-deleted", true, true, OffsetDateTime.now(ZoneOffset.UTC), "ACTIVE");
        String ip = nextIp();

        MvcResult inactiveResult = webLogin(
                inactiveUser.getUsername(),
                DEFAULT_PASSWORD,
                ip,
                "AUTH-004-inactive",
                status().isUnauthorized()
        );
        MvcResult deletedResult = webLogin(
                deletedUser.getEmail(),
                DEFAULT_PASSWORD,
                ip,
                "AUTH-004-deleted",
                status().isUnauthorized()
        );

        assertThat(bodyOf(inactiveResult).get("message").asText()).isEqualTo(INVALID_CREDENTIALS_MESSAGE);
        assertThat(bodyOf(deletedResult).get("message").asText()).isEqualTo(INVALID_CREDENTIALS_MESSAGE);
        assertThat(activeSessionsFor(inactiveUser)).isEmpty();
        assertThat(activeSessionsFor(deletedUser)).isEmpty();
    }

    @Test
    @DisplayName("AUTH-005 POST /auth/web/login rejects user with unverified email")
    void auth005RejectsUserWithUnverifiedEmail() throws Exception {
        User user = createUser("auth005", true, false, null, "ACTIVE");
        String ip = nextIp();

        MvcResult result = webLogin(user.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-005", status().isUnauthorized());

        assertThat(bodyOf(result).get("message").asText()).isEqualTo(INVALID_CREDENTIALS_MESSAGE);
        assertThat(activeSessionsFor(user)).isEmpty();
    }

    @Test
    @DisplayName("AUTH-006 POST /auth/web/login locks account after max failed attempts")
    void auth006LocksAccountAfterMaxFailedAttempts() throws Exception {
        User user = createVerifiedUser("auth006");
        String ip = nextIp();

        for (int attempt = 0; attempt < 5; attempt++) {
            webLogin(user.getUsername(), WRONG_PASSWORD, ip, "AUTH-006", status().isUnauthorized());
        }

        User lockedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(lockedUser.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(lockedUser.getLockedUntil()).isNotNull();
        assertThat(lockedUser.getLockedUntil()).isAfter(OffsetDateTime.now(ZoneOffset.UTC));

        authLoginAttemptRepository.deleteAllInBatch();

        MvcResult lockedResult = webLogin(
                user.getUsername(),
                DEFAULT_PASSWORD,
                ip,
                "AUTH-006-locked",
                status().isUnauthorized()
        );

        assertThat(bodyOf(lockedResult).get("message").asText()).isEqualTo(INVALID_CREDENTIALS_MESSAGE);
        assertThat(activeSessionsFor(user)).isEmpty();
    }

    @Test
    @DisplayName("AUTH-007 POST /auth/web/login rate-limits by identifier")
    void auth007RateLimitsByIdentifier() throws Exception {
        User user = createVerifiedUser("auth007");
        String ip = nextIp();

        for (int attempt = 0; attempt < 5; attempt++) {
            webLogin(user.getEmail(), WRONG_PASSWORD, ip, "AUTH-007", status().isUnauthorized());
        }

        MvcResult rateLimitedResult = webLogin(
                user.getEmail(),
                DEFAULT_PASSWORD,
                ip,
                "AUTH-007-rate-limit",
                status().isTooManyRequests()
        );

        assertThat(bodyOf(rateLimitedResult).get("message").asText()).isEqualTo(TOO_MANY_LOGIN_ATTEMPTS_MESSAGE);
        assertThat(activeSessionsFor(user)).isEmpty();
    }

    @Test
    @DisplayName("AUTH-008 POST /auth/web/login rate-limits by IP")
    void auth008RateLimitsByIp() throws Exception {
        String ip = nextIp();

        for (int attempt = 0; attempt < 20; attempt++) {
            webLogin(
                    "missing-" + attempt + "@pos.example",
                    WRONG_PASSWORD,
                    ip,
                    "AUTH-008",
                    status().isUnauthorized()
            );
        }

        MvcResult rateLimitedResult = webLogin(
                "missing-final@pos.example",
                WRONG_PASSWORD,
                ip,
                "AUTH-008-rate-limit",
                status().isTooManyRequests()
        );

        assertThat(bodyOf(rateLimitedResult).get("message").asText()).isEqualTo(TOO_MANY_LOGIN_ATTEMPTS_MESSAGE);
    }

    @Test
    @DisplayName("AUTH-009 POST /auth/web/login revokes oldest session when session limit is reached")
    void auth009RevokesOldestSessionWhenSessionLimitIsReached() throws Exception {
        User user = createVerifiedUser("auth009");
        String ip = nextIp();

        String firstRefreshToken = loginAndExtractRefreshToken(user.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-009-1");
        Thread.sleep(20);
        loginAndExtractRefreshToken(user.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-009-2");
        Thread.sleep(20);
        loginAndExtractRefreshToken(user.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-009-3");
        Thread.sleep(20);
        String fourthRefreshToken = loginAndExtractRefreshToken(user.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-009-4");

        List<UserSession> allSessions = sessionsFor(user);
        assertThat(allSessions).hasSize(4);
        assertThat(activeSessionsFor(user)).hasSize(3);
        assertThat(allSessions)
                .filteredOn(UserSession::isRevoked)
                .singleElement()
                .satisfies(session -> assertThat(session.getRevokedReason()).isEqualTo("SESSION_LIMIT"));

        MvcResult revokedRefreshResult = webRefresh(
                firstRefreshToken,
                ip,
                "AUTH-009-refresh-oldest",
                status().isUnauthorized()
        );
        assertRefreshCookieCleared(revokedRefreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        MvcResult latestRefreshResult = webRefresh(
                fourthRefreshToken,
                ip,
                "AUTH-009-refresh-current",
                status().isOk()
        );
        assertThat(bodyOf(latestRefreshResult).get("accessToken").asText()).isNotBlank();
        assertRefreshCookieIssued(latestRefreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("AUTH-010 POST /auth/web/refresh succeeds with valid refresh cookie and rotates tokens")
    void auth010RefreshSucceedsAndRotatesTokens() throws Exception {
        User user = createVerifiedUser("auth010");
        String ip = nextIp();

        MvcResult loginResult = webLogin(user.getEmail(), DEFAULT_PASSWORD, ip, "AUTH-010-login", status().isOk());
        String firstAccessToken = bodyOf(loginResult).get("accessToken").asText();
        String firstRefreshToken = extractCookieValue(loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        MvcResult refreshResult = webRefresh(firstRefreshToken, ip, "AUTH-010-refresh", status().isOk());

        JsonNode body = bodyOf(refreshResult);
        String rotatedRefreshToken = extractCookieValue(refreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(body.get("accessToken").asText()).isNotEqualTo(firstAccessToken);
        assertThat(rotatedRefreshToken).isNotEqualTo(firstRefreshToken);
        assertThat(activeSessionsFor(user)).hasSize(1);
    }

    @Test
    @DisplayName("AUTH-011 POST /auth/web/refresh rejects missing refresh cookie")
    void auth011RefreshRejectsMissingCookie() throws Exception {
        String ip = nextIp();

        MvcResult result = webRefresh(null, ip, "AUTH-011", status().isUnauthorized());

        assertThat(bodyOf(result).get("message").asText()).isEqualTo(INVALID_REFRESH_TOKEN_MESSAGE);
        assertRefreshCookieCleared(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("AUTH-012 POST /auth/web/refresh rejects invalid refresh token and clears cookie")
    void auth012RefreshRejectsInvalidTokenAndClearsCookie() throws Exception {
        String ip = nextIp();

        MvcResult result = webRefresh("not-a-refresh-token", ip, "AUTH-012-invalid", status().isUnauthorized());

        assertThat(bodyOf(result).get("message").asText()).isEqualTo(INVALID_REFRESH_TOKEN_MESSAGE);
        assertRefreshCookieCleared(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("AUTH-012 POST /auth/web/refresh rejects reused refresh token and clears cookie")
    void auth012RefreshRejectsReusedTokenAndClearsCookie() throws Exception {
        User user = createVerifiedUser("auth012-reused");
        String ip = nextIp();

        MvcResult loginResult = webLogin(user.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-012-reused-login", status().isOk());
        String firstRefreshToken = extractCookieValue(loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        MvcResult rotatedResult = webRefresh(
                firstRefreshToken,
                ip,
                "AUTH-012-reused-refresh",
                status().isOk()
        );
        String rotatedRefreshToken = extractCookieValue(rotatedResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        MvcResult reusedResult = webRefresh(
                firstRefreshToken,
                ip,
                "AUTH-012-reused-old-token",
                status().isUnauthorized()
        );

        assertThat(bodyOf(reusedResult).get("message").asText()).isEqualTo(INVALID_REFRESH_TOKEN_MESSAGE);
        assertRefreshCookieCleared(reusedResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(rotatedRefreshToken).isNotEqualTo(firstRefreshToken);
    }

    @Test
    @DisplayName("AUTH-012 POST /auth/web/refresh rejects expired refresh token and clears cookie")
    void auth012RefreshRejectsExpiredTokenAndClearsCookie() throws Exception {
        User user = createVerifiedUser("auth012-expired");
        String ip = nextIp();

        MvcResult loginResult = webLogin(user.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-012-expired-login", status().isOk());
        String refreshToken = extractCookieValue(loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        UserSession session = sessionsFor(user).getFirst();
        session.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        userSessionRepository.save(session);

        MvcResult expiredResult = webRefresh(refreshToken, ip, "AUTH-012-expired-refresh", status().isUnauthorized());

        assertThat(bodyOf(expiredResult).get("message").asText()).isEqualTo(INVALID_REFRESH_TOKEN_MESSAGE);
        assertRefreshCookieCleared(expiredResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        UserSession expiredSession = userSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(expiredSession.isRevoked()).isTrue();
        assertThat(expiredSession.getRevokedReason()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("AUTH-013 POST /auth/web/logout revokes current session and clears cookie")
    void auth013LogoutRevokesCurrentSessionAndClearsCookie() throws Exception {
        User user = createVerifiedUser("auth013");
        String ip = nextIp();

        MvcResult loginResult = webLogin(user.getEmail(), DEFAULT_PASSWORD, ip, "AUTH-013-login", status().isOk());
        String refreshToken = extractCookieValue(loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        UserSession session = sessionsFor(user).getFirst();

        MvcResult logoutResult = webLogout(refreshToken, ip, "AUTH-013-logout", status().isNoContent());

        assertRefreshCookieCleared(logoutResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        UserSession revokedSession = userSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(revokedSession.isRevoked()).isTrue();
        assertThat(revokedSession.getRevokedReason()).isEqualTo("LOGOUT");

        MvcResult refreshAfterLogout = webRefresh(
                refreshToken,
                ip,
                "AUTH-013-refresh-after-logout",
                status().isUnauthorized()
        );
        assertRefreshCookieCleared(refreshAfterLogout.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("AUTH-014 POST /auth/web/logout safely no-ops with invalid or missing refresh cookie")
    void auth014LogoutNoOpsWithInvalidOrMissingCookie() throws Exception {
        String ip = nextIp();

        MvcResult missingCookieResult = webLogout(null, ip, "AUTH-014-missing", status().isNoContent());
        MvcResult invalidCookieResult = webLogout("not-a-refresh-token", ip, "AUTH-014-invalid", status().isNoContent());

        assertRefreshCookieCleared(missingCookieResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertRefreshCookieCleared(invalidCookieResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("AUTH-015 POST /auth/web/logout-all revokes all sessions for current user and clears cookie")
    void auth015LogoutAllRevokesAllSessionsAndClearsCookie() throws Exception {
        User user = createVerifiedUser("auth015");
        String ip = nextIp();

        String firstRefreshToken = loginAndExtractRefreshToken(user.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-015-1");
        String secondRefreshToken = loginAndExtractRefreshToken(user.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-015-2");
        String thirdRefreshToken = loginAndExtractRefreshToken(user.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-015-3");

        MvcResult logoutAllResult = webLogoutAll(secondRefreshToken, ip, "AUTH-015-logout-all", status().isNoContent());

        assertRefreshCookieCleared(logoutAllResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(activeSessionsFor(user)).isEmpty();
        assertThat(sessionsFor(user))
                .allSatisfy(session -> {
                    assertThat(session.isRevoked()).isTrue();
                    assertThat(session.getRevokedReason()).isEqualTo("LOGOUT_ALL");
                });

        MvcResult firstRefreshResult = webRefresh(
                firstRefreshToken,
                ip,
                "AUTH-015-refresh-first",
                status().isUnauthorized()
        );
        MvcResult secondRefreshResult = webRefresh(
                secondRefreshToken,
                ip,
                "AUTH-015-refresh-second",
                status().isUnauthorized()
        );
        MvcResult thirdRefreshResult = webRefresh(
                thirdRefreshToken,
                ip,
                "AUTH-015-refresh-third",
                status().isUnauthorized()
        );

        assertRefreshCookieCleared(firstRefreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertRefreshCookieCleared(secondRefreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertRefreshCookieCleared(thirdRefreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    private User createVerifiedUser(String label) {
        return createUser(label, true, true, null, "ACTIVE");
    }

    private User createUser(
            String label,
            boolean active,
            boolean emailVerified,
            OffsetDateTime deletedAt,
            String status
    ) {
        String suffix = label + "-" + userSequence.getAndIncrement();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return userRepository.save(User.builder()
                .email("user." + suffix + "@pos.example")
                .username("user." + suffix)
                .passwordHash(passwordService.hash(DEFAULT_PASSWORD))
                .firstName("Auth")
                .lastName("User")
                .status(status)
                .isActive(active)
                .emailVerified(emailVerified)
                .emailVerifiedAt(emailVerified ? now : null)
                .deletedAt(deletedAt)
                .build());
    }

    private String loginAndExtractRefreshToken(String identifier, String password, String ip, String userAgent)
            throws Exception {
        MvcResult result = webLogin(identifier, password, ip, userAgent, status().isOk());
        return extractCookieValue(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    private MvcResult webLogin(
            String identifier,
            String password,
            String ip,
            String userAgent,
            ResultMatcher expectedStatus
    ) throws Exception {
        return mockMvc.perform(post("/auth/web/login")
                        .with(client(ip, userAgent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", identifier,
                                "password", password
                        ))))
                .andExpect(expectedStatus)
                .andReturn();
    }

    private MvcResult webRefresh(
            String refreshToken,
            String ip,
            String userAgent,
            ResultMatcher expectedStatus
    ) throws Exception {
        var request = post("/auth/web/refresh")
                .with(client(ip, userAgent));

        if (refreshToken != null) {
            request.cookie(refreshCookie(refreshToken));
        }

        return mockMvc.perform(request)
                .andExpect(expectedStatus)
                .andReturn();
    }

    private MvcResult webLogout(
            String refreshToken,
            String ip,
            String userAgent,
            ResultMatcher expectedStatus
    ) throws Exception {
        var request = post("/auth/web/logout")
                .with(client(ip, userAgent));

        if (refreshToken != null) {
            request.cookie(refreshCookie(refreshToken));
        }

        return mockMvc.perform(request)
                .andExpect(expectedStatus)
                .andReturn();
    }

    private MvcResult webLogoutAll(
            String refreshToken,
            String ip,
            String userAgent,
            ResultMatcher expectedStatus
    ) throws Exception {
        var request = post("/auth/web/logout-all")
                .with(client(ip, userAgent));

        if (refreshToken != null) {
            request.cookie(refreshCookie(refreshToken));
        }

        return mockMvc.perform(request)
                .andExpect(expectedStatus)
                .andReturn();
    }

    private RequestPostProcessor client(String ip, String userAgent) {
        return request -> {
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", ip);
            request.addHeader(HttpHeaders.USER_AGENT, userAgent);
            return request;
        };
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MockCookie refreshCookie(String refreshToken) {
        return new MockCookie(COOKIE_NAME, refreshToken);
    }

    private String extractCookieValue(String setCookieHeader) {
        assertThat(setCookieHeader).isNotBlank();
        String prefix = COOKIE_NAME + "=";
        int start = setCookieHeader.indexOf(prefix);
        int end = setCookieHeader.indexOf(';', start);
        return setCookieHeader.substring(start + prefix.length(), end);
    }

    private void assertRefreshCookieIssued(String setCookieHeader) {
        assertThat(setCookieHeader).contains(COOKIE_NAME + "=");
        assertThat(setCookieHeader).contains("Path=/auth/web");
        assertThat(setCookieHeader).contains("HttpOnly");
        assertThat(setCookieHeader).contains("Secure");
        assertThat(setCookieHeader).contains("SameSite=Strict");
        assertThat(setCookieHeader).contains("Domain=pos.example");
    }

    private void assertRefreshCookieCleared(String setCookieHeader) {
        assertThat(setCookieHeader).contains(COOKIE_NAME + "=");
        assertThat(setCookieHeader).contains("Max-Age=0");
        assertThat(setCookieHeader).contains("Path=/auth/web");
        assertThat(setCookieHeader).contains("HttpOnly");
        assertThat(setCookieHeader).contains("Secure");
        assertThat(setCookieHeader).contains("SameSite=Strict");
        assertThat(setCookieHeader).contains("Domain=pos.example");
    }

    private List<UserSession> sessionsFor(User user) {
        return userSessionRepository.findAll().stream()
                .filter(session -> session.getUserId().equals(user.getId()))
                .toList();
    }

    private List<UserSession> activeSessionsFor(User user) {
        return userSessionRepository.findActiveSessionsByUserId(user.getId(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    private String nextIp() {
        return "198.51.100." + ipSequence.getAndIncrement();
    }
}
