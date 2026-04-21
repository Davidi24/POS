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
import pos.pos.auth.repository.AuthEmailVerificationTokenRepository;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.support.TestPostgresContainerSupport;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("Auth session integration test")
class AuthSessionIntegrationTest {

    private static final String SCHEMA = "auth_session_" + UUID.randomUUID().toString().replace("-", "");
    private static final String ADMIN_EMAIL = "auth.session.admin@pos.example";
    private static final String ADMIN_USERNAME = "authsessionadmin";
    private static final String ADMIN_PASSWORD = "StrongPass123!";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        TestPostgresContainerSupport.registerProdDatabaseProperties(registry, SCHEMA);
        registry.add("JWT_SECRET", () -> "auth-session-test-secret-key-for-hs256-123456");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "auth-session-refresh-token-pepper-0123456789");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "auth-session-password-reset-pepper");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "auth-session-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "auth-session-sms-code-pepper-value");
        registry.add("MAIL_HOST", () -> "localhost");
        registry.add("MAIL_PORT", () -> "2525");
        registry.add("MAIL_USERNAME", () -> "integration");
        registry.add("MAIL_PASSWORD", () -> "integration");
        registry.add("MAIL_FROM", () -> "no-reply@pos.example");
        registry.add("FRONTEND_BASE_URL", () -> "https://app.pos.example");
        registry.add("FRONTEND_DEFAULT_LINK_TARGET", () -> "UNIVERSAL");
        registry.add("TRUSTED_PROXIES", () -> "127.0.0.1,::1");
        registry.add("COOKIE_DOMAIN", () -> "pos.example");
        registry.add("BOOTSTRAP_SUPER_ADMIN_ENABLED", () -> "true");
        registry.add("BOOTSTRAP_SUPER_ADMIN_EMAIL", () -> ADMIN_EMAIL);
        registry.add("BOOTSTRAP_SUPER_ADMIN_USERNAME", () -> ADMIN_USERNAME);
        registry.add("BOOTSTRAP_SUPER_ADMIN_PASSWORD", () -> ADMIN_PASSWORD);
        registry.add("BOOTSTRAP_SUPER_ADMIN_FIRST_NAME", () -> "Auth");
        registry.add("BOOTSTRAP_SUPER_ADMIN_LAST_NAME", () -> "Session");
        registry.add("SMS_DELIVERY_MODE", () -> "LOG_ONLY");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private AuthLoginAttemptRepository authLoginAttemptRepository;

    @Autowired
    private AuthEmailVerificationTokenRepository authEmailVerificationTokenRepository;

    @MockBean
    private JavaMailSender javaMailSender;

    private final AtomicInteger ipSequence = new AtomicInteger(90);

    @BeforeEach
    void resetState() {
        doNothing().when(javaMailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
        authLoginAttemptRepository.deleteAllInBatch();
        userSessionRepository.deleteAllInBatch();
        authEmailVerificationTokenRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("AUTH-023 GET /auth/me returns current user profile with roles and permissions")
    void auth023ReturnsCurrentUserProfileWithRolesAndPermissions() throws Exception {
        AuthTokens tokens = webLogin(ADMIN_USERNAME, ADMIN_PASSWORD, nextIp(), "AUTH-023-login");

        MvcResult result = mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("email").asText()).isEqualTo(ADMIN_EMAIL);
        assertThat(body.get("username").asText()).isEqualTo(ADMIN_USERNAME);
        assertThat(asTextList(body.get("roles"))).contains("SUPER_ADMIN");
        assertThat(asTextList(body.get("permissions")))
                .contains("USERS_CREATE", "USERS_READ", "SESSIONS_MANAGE", "ROLES_ASSIGN_PERMISSIONS");
    }

    @Test
    @DisplayName("AUTH-024 GET /auth/sessions returns current user active sessions")
    void auth024ReturnsCurrentUserActiveSessions() throws Exception {
        AuthTokens first = webLogin(ADMIN_USERNAME, ADMIN_PASSWORD, nextIp(), "AUTH-024-1");
        pause();
        AuthTokens second = webLogin(ADMIN_USERNAME, ADMIN_PASSWORD, nextIp(), "AUTH-024-2");
        pause();
        AuthTokens third = webLogin(ADMIN_USERNAME, ADMIN_PASSWORD, nextIp(), "AUTH-024-3");

        MvcResult result = mockMvc.perform(get("/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(third.accessToken())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body).hasSize(3);
        assertThat(body.findValuesAsText("sessionType")).containsOnly("PASSWORD");
        assertThat(body.findValuesAsText("ipAddress")).containsExactlyInAnyOrder(first.ip(), second.ip(), third.ip());
        assertThat(body.findValuesAsText("deviceName")).containsOnly("Device");
        assertThat(body.findValuesAsText("userAgent")).contains("AUTH-024-3");
        assertThat(body.findValuesAsText("current")).contains("true");
    }

    @Test
    @DisplayName("AUTH-025 GET /auth/sessions/current returns current active session from bearer token")
    void auth025ReturnsCurrentActiveSessionFromBearerToken() throws Exception {
        AuthTokens tokens = webLogin(ADMIN_USERNAME, ADMIN_PASSWORD, nextIp(), "AUTH-025-login");

        MvcResult result = mockMvc.perform(get("/auth/sessions/current")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("current").asBoolean()).isTrue();
        assertThat(body.get("ipAddress").asText()).isEqualTo(tokens.ip());
        assertThat(body.get("userAgent").asText()).isEqualTo("AUTH-025-login");
        assertThat(body.get("sessionType").asText()).isEqualTo("PASSWORD");
    }

    @Test
    @DisplayName("AUTH-026 DELETE /auth/sessions/{sessionId} revokes one of my sessions")
    void auth026RevokesOneOfMySessions() throws Exception {
        webLogin(ADMIN_USERNAME, ADMIN_PASSWORD, nextIp(), "AUTH-026-1");
        pause();
        AuthTokens current = webLogin(ADMIN_USERNAME, ADMIN_PASSWORD, nextIp(), "AUTH-026-2");

        List<UserSession> sessions = activeSessionsForAdmin();
        UserSession targetSession = sessions.stream()
                .filter(session -> !session.getIpAddress().equals(current.ip()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(delete("/auth/sessions/{sessionId}", targetSession.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(current.accessToken())))
                .andExpect(status().isNoContent());

        UserSession revokedSession = userSessionRepository.findById(targetSession.getId()).orElseThrow();
        assertThat(revokedSession.isRevoked()).isTrue();
        assertThat(revokedSession.getRevokedReason()).isEqualTo("SESSION_REVOKED");
        assertThat(activeSessionsForAdmin()).hasSize(1);
    }

    @Test
    @DisplayName("AUTH-027 DELETE /auth/sessions/others revokes all other sessions but keeps current session")
    void auth027RevokesAllOtherSessionsButKeepsCurrentSession() throws Exception {
        webLogin(ADMIN_USERNAME, ADMIN_PASSWORD, nextIp(), "AUTH-027-1");
        pause();
        webLogin(ADMIN_USERNAME, ADMIN_PASSWORD, nextIp(), "AUTH-027-2");
        pause();
        AuthTokens current = webLogin(ADMIN_USERNAME, ADMIN_PASSWORD, nextIp(), "AUTH-027-3");

        mockMvc.perform(delete("/auth/sessions/others")
                        .header(HttpHeaders.AUTHORIZATION, bearer(current.accessToken())))
                .andExpect(status().isNoContent());

        List<UserSession> activeSessions = activeSessionsForAdmin();
        assertThat(activeSessions).hasSize(1);
        assertThat(activeSessions.getFirst().getIpAddress()).isEqualTo(current.ip());
        assertThat(activeSessions.getFirst().isRevoked()).isFalse();

        List<UserSession> allSessions = sessionsForAdmin();
        assertThat(allSessions).hasSize(3);
        assertThat(allSessions.stream().filter(UserSession::isRevoked)).hasSize(2);
        assertThat(allSessions.stream()
                .filter(UserSession::isRevoked)
                .map(UserSession::getRevokedReason)
                .distinct()
                .toList()).containsExactly("SESSION_REVOKED");
    }

    private AuthTokens webLogin(String identifier, String password, String ip, String userAgent) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/web/login")
                        .with(client(ip, userAgent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", identifier,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        return new AuthTokens(
                body.get("accessToken").asText(),
                extractCookieValue(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)),
                ip
        );
    }

    private String extractCookieValue(String setCookieHeader) {
        String prefix = "refreshToken=";
        int start = setCookieHeader.indexOf(prefix);
        int end = setCookieHeader.indexOf(';', start);
        return setCookieHeader.substring(start + prefix.length(), end);
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private RequestPostProcessor client(String ip, String userAgent) {
        return request -> {
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", ip);
            request.addHeader(HttpHeaders.USER_AGENT, userAgent);
            return request;
        };
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private User adminUser() {
        return userRepository.findByEmailAndDeletedAtIsNull(ADMIN_EMAIL).orElseThrow();
    }

    private List<UserSession> activeSessionsForAdmin() {
        return userSessionRepository.findActiveSessionsByUserId(adminUser().getId(), java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
    }

    private List<UserSession> sessionsForAdmin() {
        return userSessionRepository.findAll().stream()
                .filter(session -> session.getUserId().equals(adminUser().getId()))
                .sorted(Comparator.comparing(UserSession::getCreatedAt))
                .toList();
    }

    private List<String> asTextList(JsonNode arrayNode) {
        return arrayNode == null || !arrayNode.isArray()
                ? List.of()
                : java.util.stream.StreamSupport.stream(arrayNode.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private String nextIp() {
        return "198.51.100." + ipSequence.getAndIncrement();
    }

    private void pause() throws InterruptedException {
        Thread.sleep(20);
    }

    private record AuthTokens(String accessToken, String refreshToken, String ip) {
    }
}
