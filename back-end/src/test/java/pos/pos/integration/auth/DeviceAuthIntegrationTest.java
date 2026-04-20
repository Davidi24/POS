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
@DisplayName("Device auth integration test")
class DeviceAuthIntegrationTest {

    private static final String SCHEMA = "device_auth_" + UUID.randomUUID().toString().replace("-", "");
    private static final String DEFAULT_PASSWORD = "StrongPass123!";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> "jdbc:postgresql://localhost:5432/pos?currentSchema=" + SCHEMA);
        registry.add("DB_USERNAME", () -> "pos_user");
        registry.add("DB_PASSWORD", () -> "pos_pass");
        registry.add("JWT_SECRET", () -> "device-auth-test-secret-key-for-hs256-123456");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "device-auth-refresh-token-pepper-0123456789");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "device-auth-password-reset-pepper-value");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "device-auth-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "device-auth-sms-code-pepper-value");
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
    private final AtomicInteger ipSequence = new AtomicInteger(60);

    @BeforeEach
    void resetState() {
        doNothing().when(javaMailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
        authLoginAttemptRepository.deleteAllInBatch();
        userSessionRepository.deleteAllInBatch();
        userRoleRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("AUTH-016 POST /auth/device/login succeeds with valid credentials")
    void auth016DeviceLoginSucceedsWithValidCredentials() throws Exception {
        User user = createVerifiedUser("auth016");
        String ip = nextIp();

        MvcResult result = deviceLogin(user.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-016", status().isOk());

        JsonNode body = bodyOf(result);
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("user").get("username").asText()).isEqualTo(user.getUsername());
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(activeSessionsFor(user)).hasSize(1);
    }

    @Test
    @DisplayName("AUTH-017 POST /auth/device/refresh succeeds with valid refresh token in request body")
    void auth017DeviceRefreshSucceedsWithRefreshTokenInBody() throws Exception {
        User user = createVerifiedUser("auth017");
        String ip = nextIp();

        MvcResult loginResult = deviceLogin(user.getEmail(), DEFAULT_PASSWORD, ip, "AUTH-017-login", status().isOk());
        JsonNode loginBody = bodyOf(loginResult);
        String firstAccessToken = loginBody.get("accessToken").asText();
        String firstRefreshToken = loginBody.get("refreshToken").asText();

        MvcResult refreshResult = deviceRefresh(firstRefreshToken, ip, "AUTH-017-refresh", status().isOk());

        JsonNode refreshBody = bodyOf(refreshResult);
        assertThat(refreshBody.get("accessToken").asText()).isNotEqualTo(firstAccessToken);
        assertThat(refreshBody.get("refreshToken").asText()).isNotEqualTo(firstRefreshToken);
        assertThat(activeSessionsFor(user)).hasSize(1);
    }

    @Test
    @DisplayName("AUTH-018 POST /auth/device/refresh rejects missing refresh token")
    void auth018DeviceRefreshRejectsMissingRefreshToken() throws Exception {
        String ip = nextIp();

        MvcResult result = deviceRefreshWithoutToken(ip, "AUTH-018", status().isUnauthorized());

        assertThat(bodyOf(result).get("message").asText()).isEqualTo(INVALID_REFRESH_TOKEN_MESSAGE);
    }

    @Test
    @DisplayName("AUTH-019 POST /auth/device/refresh rejects invalid, reused, or expired refresh token")
    void auth019DeviceRefreshRejectsInvalidReusedOrExpiredToken() throws Exception {
        String ip = nextIp();

        MvcResult invalidResult = deviceRefresh("not-a-refresh-token", ip, "AUTH-019-invalid", status().isUnauthorized());
        assertThat(bodyOf(invalidResult).get("message").asText()).isEqualTo(INVALID_REFRESH_TOKEN_MESSAGE);

        User reusedUser = createVerifiedUser("auth019-reused");
        MvcResult reusedLogin = deviceLogin(reusedUser.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-019-reused-login", status().isOk());
        String initialRefreshToken = bodyOf(reusedLogin).get("refreshToken").asText();
        MvcResult rotatedResult = deviceRefresh(initialRefreshToken, ip, "AUTH-019-reused-refresh", status().isOk());
        String rotatedRefreshToken = bodyOf(rotatedResult).get("refreshToken").asText();
        MvcResult reusedResult = deviceRefresh(initialRefreshToken, ip, "AUTH-019-reused-old", status().isUnauthorized());

        assertThat(bodyOf(reusedResult).get("message").asText()).isEqualTo(INVALID_REFRESH_TOKEN_MESSAGE);
        assertThat(rotatedRefreshToken).isNotEqualTo(initialRefreshToken);

        User expiredUser = createVerifiedUser("auth019-expired");
        MvcResult expiredLogin = deviceLogin(expiredUser.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-019-expired-login", status().isOk());
        String expiredRefreshToken = bodyOf(expiredLogin).get("refreshToken").asText();
        UserSession expiredSession = sessionsFor(expiredUser).getFirst();
        expiredSession.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        userSessionRepository.save(expiredSession);

        MvcResult expiredResult = deviceRefresh(expiredRefreshToken, ip, "AUTH-019-expired-refresh", status().isUnauthorized());

        assertThat(bodyOf(expiredResult).get("message").asText()).isEqualTo(INVALID_REFRESH_TOKEN_MESSAGE);
        UserSession persistedExpiredSession = userSessionRepository.findById(expiredSession.getId()).orElseThrow();
        assertThat(persistedExpiredSession.isRevoked()).isTrue();
        assertThat(persistedExpiredSession.getRevokedReason()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("AUTH-020 POST /auth/device/logout revokes current device session")
    void auth020DeviceLogoutRevokesCurrentDeviceSession() throws Exception {
        User user = createVerifiedUser("auth020");
        String ip = nextIp();

        MvcResult loginResult = deviceLogin(user.getEmail(), DEFAULT_PASSWORD, ip, "AUTH-020-login", status().isOk());
        String refreshToken = bodyOf(loginResult).get("refreshToken").asText();
        UserSession session = sessionsFor(user).getFirst();

        MvcResult logoutResult = deviceLogout(refreshToken, ip, "AUTH-020-logout", status().isNoContent());

        assertThat(logoutResult.getResponse().getContentAsString()).isBlank();
        UserSession revokedSession = userSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(revokedSession.isRevoked()).isTrue();
        assertThat(revokedSession.getRevokedReason()).isEqualTo("LOGOUT");

        MvcResult refreshAfterLogout = deviceRefresh(refreshToken, ip, "AUTH-020-refresh-after-logout", status().isUnauthorized());
        assertThat(bodyOf(refreshAfterLogout).get("message").asText()).isEqualTo(INVALID_REFRESH_TOKEN_MESSAGE);
    }

    @Test
    @DisplayName("AUTH-021 POST /auth/device/logout safely no-ops when refresh token is omitted")
    void auth021DeviceLogoutSafelyNoOpsWhenRefreshTokenIsOmitted() throws Exception {
        String ip = nextIp();

        MvcResult result = deviceLogoutWithoutToken(ip, "AUTH-021", status().isNoContent());

        assertThat(result.getResponse().getContentAsString()).isBlank();
    }

    @Test
    @DisplayName("AUTH-022 POST /auth/device/logout-all revokes all device sessions for current user")
    void auth022DeviceLogoutAllRevokesAllDeviceSessionsForCurrentUser() throws Exception {
        User user = createVerifiedUser("auth022");
        String ip = nextIp();

        String firstRefreshToken = loginAndExtractRefreshToken(user.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-022-1");
        String secondRefreshToken = loginAndExtractRefreshToken(user.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-022-2");
        String thirdRefreshToken = loginAndExtractRefreshToken(user.getUsername(), DEFAULT_PASSWORD, ip, "AUTH-022-3");

        MvcResult logoutAllResult = deviceLogoutAll(secondRefreshToken, ip, "AUTH-022-logout-all", status().isNoContent());

        assertThat(logoutAllResult.getResponse().getContentAsString()).isBlank();
        assertThat(activeSessionsFor(user)).isEmpty();
        assertThat(sessionsFor(user))
                .allSatisfy(session -> {
                    assertThat(session.isRevoked()).isTrue();
                    assertThat(session.getRevokedReason()).isEqualTo("LOGOUT_ALL");
                });

        assertThat(bodyOf(deviceRefresh(firstRefreshToken, ip, "AUTH-022-refresh-first", status().isUnauthorized()))
                .get("message").asText()).isEqualTo(INVALID_REFRESH_TOKEN_MESSAGE);
        assertThat(bodyOf(deviceRefresh(secondRefreshToken, ip, "AUTH-022-refresh-second", status().isUnauthorized()))
                .get("message").asText()).isEqualTo(INVALID_REFRESH_TOKEN_MESSAGE);
        assertThat(bodyOf(deviceRefresh(thirdRefreshToken, ip, "AUTH-022-refresh-third", status().isUnauthorized()))
                .get("message").asText()).isEqualTo(INVALID_REFRESH_TOKEN_MESSAGE);
    }

    private User createVerifiedUser(String label) {
        String suffix = label + "-" + userSequence.getAndIncrement();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return userRepository.save(User.builder()
                .email("user." + suffix + "@pos.example")
                .username("user." + suffix)
                .passwordHash(passwordService.hash(DEFAULT_PASSWORD))
                .firstName("Device")
                .lastName("User")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .emailVerifiedAt(now)
                .build());
    }

    private String loginAndExtractRefreshToken(String identifier, String password, String ip, String userAgent)
            throws Exception {
        MvcResult result = deviceLogin(identifier, password, ip, userAgent, status().isOk());
        return bodyOf(result).get("refreshToken").asText();
    }

    private MvcResult deviceLogin(
            String identifier,
            String password,
            String ip,
            String userAgent,
            ResultMatcher expectedStatus
    ) throws Exception {
        return mockMvc.perform(post("/auth/device/login")
                        .with(client(ip, userAgent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", identifier,
                                "password", password
                        ))))
                .andExpect(expectedStatus)
                .andReturn();
    }

    private MvcResult deviceRefresh(
            String refreshToken,
            String ip,
            String userAgent,
            ResultMatcher expectedStatus
    ) throws Exception {
        return mockMvc.perform(post("/auth/device/refresh")
                        .with(client(ip, userAgent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(expectedStatus)
                .andReturn();
    }

    private MvcResult deviceRefreshWithoutToken(String ip, String userAgent, ResultMatcher expectedStatus) throws Exception {
        return mockMvc.perform(post("/auth/device/refresh")
                        .with(client(ip, userAgent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(expectedStatus)
                .andReturn();
    }

    private MvcResult deviceLogout(
            String refreshToken,
            String ip,
            String userAgent,
            ResultMatcher expectedStatus
    ) throws Exception {
        return mockMvc.perform(post("/auth/device/logout")
                        .with(client(ip, userAgent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(expectedStatus)
                .andReturn();
    }

    private MvcResult deviceLogoutWithoutToken(String ip, String userAgent, ResultMatcher expectedStatus) throws Exception {
        return mockMvc.perform(post("/auth/device/logout")
                        .with(client(ip, userAgent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(expectedStatus)
                .andReturn();
    }

    private MvcResult deviceLogoutAll(
            String refreshToken,
            String ip,
            String userAgent,
            ResultMatcher expectedStatus
    ) throws Exception {
        return mockMvc.perform(post("/auth/device/logout-all")
                        .with(client(ip, userAgent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
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
