package pos.pos.integration.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pos.pos.auth.entity.AuthPasswordResetToken;
import pos.pos.auth.entity.AuthSmsOtpCode;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.enums.SmsOtpPurpose;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.auth.repository.AuthPasswordResetTokenRepository;
import pos.pos.auth.repository.AuthSmsOtpCodeRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.auth.service.AuthMailService;
import pos.pos.auth.service.SmsMessageService;
import pos.pos.security.service.PasswordService;
import pos.pos.support.TestPostgresContainerSupport;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("Password integration test")
class PasswordIntegrationTest {

    private static final String SCHEMA = "password_auth_" + UUID.randomUUID().toString().replace("-", "");
    private static final String DEFAULT_PASSWORD = "StrongPass123!";
    private static final String NEW_PASSWORD = "ResetPass123!";
    private static final String INVALID_TOKEN_MESSAGE = "Invalid token";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        TestPostgresContainerSupport.registerProdDatabaseProperties(registry, SCHEMA);
        registry.add("JWT_SECRET", () -> "password-auth-test-secret-key-for-hs256-123456");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "password-auth-refresh-token-pepper-0123456789");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "password-auth-password-reset-pepper-value");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "password-auth-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "password-auth-sms-code-pepper-value");
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
        registry.add("app.auth.sms.daily-request-limit", () -> "2");
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
    private AuthPasswordResetTokenRepository authPasswordResetTokenRepository;

    @Autowired
    private AuthSmsOtpCodeRepository authSmsOtpCodeRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private JavaMailSender javaMailSender;

    @SpyBean
    private AuthMailService authMailService;

    @SpyBean
    private SmsMessageService smsMessageService;

    private final AtomicInteger userSequence = new AtomicInteger(1);
    private final AtomicInteger ipSequence = new AtomicInteger(140);
    private final AtomicReference<String> latestPasswordResetUrl = new AtomicReference<>();
    private final AtomicReference<String> latestPasswordResetCode = new AtomicReference<>();

    @BeforeEach
    void resetState() {
        latestPasswordResetUrl.set(null);
        latestPasswordResetCode.set(null);

        doNothing().when(javaMailSender).send(any(org.springframework.mail.SimpleMailMessage.class));

        Mockito.reset(authMailService, smsMessageService);

        doAnswer(invocation -> {
            latestPasswordResetUrl.set(invocation.getArgument(3, String.class));
            return invocation.callRealMethod();
        }).when(authMailService).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyString(), any(Duration.class));

        doAnswer(invocation -> {
            latestPasswordResetCode.set(invocation.getArgument(2, String.class));
            return invocation.callRealMethod();
        }).when(smsMessageService).sendPasswordResetCode(anyString(), anyString(), anyString(), any(Duration.class));

        authLoginAttemptRepository.deleteAllInBatch();
        userSessionRepository.deleteAllInBatch();
        authPasswordResetTokenRepository.deleteAllInBatch();
        authSmsOtpCodeRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("AUTH-033 POST /auth/forgot-password email flow issues reset token for active verified user")
    void auth033ForgotPasswordEmailIssuesResetTokenForActiveVerifiedUser() throws Exception {
        User user = createUser("auth033", true, null, false);

        requestEmailReset(user.getEmail());

        assertThat(latestPasswordResetUrl.get()).isNotBlank();
        assertThat(tokenFromUrl(latestPasswordResetUrl.get())).isNotBlank();
        assertThat(passwordResetTokensFor(user)).hasSize(1);
        assertThat(passwordResetTokensFor(user).getFirst().getUsedAt()).isNull();
    }

    @Test
    @DisplayName("AUTH-034 POST /auth/forgot-password SMS flow issues reset code for eligible verified phone user")
    void auth034ForgotPasswordSmsIssuesResetCodeForEligibleVerifiedPhoneUser() throws Exception {
        User user = createUser("auth034", true, "+1555010601", true);

        requestSmsReset(user.getPhone());

        assertThat(latestPasswordResetCode.get()).isNotBlank();

        AuthSmsOtpCode code = latestSmsCodeFor(user.getId(), SmsOtpPurpose.PASSWORD_RESET);
        assertThat(code.getUsedAt()).isNull();
        assertThat(code.getFailedAttempts()).isZero();
        assertThat(code.getPhoneNumberSnapshot()).isEqualTo(user.getNormalizedPhone());
    }

    @Test
    @DisplayName("AUTH-035 POST /auth/forgot-password respects cooldown and daily limit rules")
    void auth035ForgotPasswordRespectsCooldownAndDailyLimitRules() throws Exception {
        User emailUser = createUser("auth035-email", true, null, false);

        requestEmailReset(emailUser.getEmail());
        AuthPasswordResetToken firstEmailToken = passwordResetTokensFor(emailUser).getFirst();
        latestPasswordResetUrl.set(null);

        requestEmailReset(emailUser.getEmail());

        assertThat(latestPasswordResetUrl.get()).isNull();
        assertThat(passwordResetTokensFor(emailUser)).hasSize(1);
        assertThat(passwordResetTokensFor(emailUser).getFirst().getId()).isEqualTo(firstEmailToken.getId());

        User smsUser = createUser("auth035-sms", true, "+1555010602", true);

        requestSmsReset(smsUser.getPhone());
        AuthSmsOtpCode firstCode = latestSmsCodeFor(smsUser.getId(), SmsOtpPurpose.PASSWORD_RESET);
        backdateSmsCode(firstCode.getId(), Duration.ofMinutes(2));

        requestSmsReset(smsUser.getPhone());
        AuthSmsOtpCode secondCode = latestSmsCodeFor(smsUser.getId(), SmsOtpPurpose.PASSWORD_RESET);
        assertThat(secondCode.getId()).isNotEqualTo(firstCode.getId());
        assertThat(latestPasswordResetCode.get()).isNotBlank();
        backdateSmsCode(secondCode.getId(), Duration.ofMinutes(2));

        latestPasswordResetCode.set(null);
        requestSmsReset(smsUser.getPhone());

        assertThat(latestPasswordResetCode.get()).isNull();
        assertThat(latestSmsCodeFor(smsUser.getId(), SmsOtpPurpose.PASSWORD_RESET).getId()).isEqualTo(secondCode.getId());
    }

    @Test
    @DisplayName("AUTH-036 POST /auth/reset-password valid token resets password and revokes active sessions")
    void auth036ResetPasswordWithValidTokenResetsPasswordAndRevokesActiveSessions() throws Exception {
        User user = createUser("auth036", true, null, false);
        AuthTokens firstSession = webLogin(user.getUsername(), DEFAULT_PASSWORD, nextIp(), "AUTH-036-1", status().isOk());
        webLogin(user.getEmail(), DEFAULT_PASSWORD, nextIp(), "AUTH-036-2", status().isOk());

        requestEmailReset(user.getEmail());
        String resetToken = tokenFromUrl(latestPasswordResetUrl.get());

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", resetToken,
                                "newPassword", NEW_PASSWORD
                        ))))
                .andExpect(status().isNoContent());

        assertThat(sessionsFor(user))
                .hasSize(2)
                .allSatisfy(session -> {
                    assertThat(session.isRevoked()).isTrue();
                    assertThat(session.getRevokedReason()).isEqualTo("PASSWORD_RESET");
                });

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstSession.accessToken())))
                .andExpect(status().isUnauthorized());

        webLogin(user.getUsername(), DEFAULT_PASSWORD, nextIp(), "AUTH-036-old-password", status().isUnauthorized());
        webLogin(user.getUsername(), NEW_PASSWORD, nextIp(), "AUTH-036-new-password", status().isOk());

        assertThat(passwordResetTokensFor(user).getFirst().getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("AUTH-037 POST /auth/reset-password rejects invalid or used token")
    void auth037ResetPasswordRejectsInvalidOrUsedToken() throws Exception {
        User user = createUser("auth037", true, null, false);

        MvcResult invalidResult = mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", "not-a-valid-reset-token",
                                "newPassword", NEW_PASSWORD
                        ))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(bodyOf(invalidResult).get("message").asText()).isEqualTo(INVALID_TOKEN_MESSAGE);

        requestEmailReset(user.getEmail());
        String resetToken = tokenFromUrl(latestPasswordResetUrl.get());

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", resetToken,
                                "newPassword", NEW_PASSWORD
                        ))))
                .andExpect(status().isNoContent());

        MvcResult reusedResult = mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", resetToken,
                                "newPassword", "AnotherPass123!"
                        ))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(bodyOf(reusedResult).get("message").asText()).isEqualTo(INVALID_TOKEN_MESSAGE);
    }

    @Test
    @DisplayName("AUTH-038 POST /auth/reset-password/code valid code resets password and revokes active sessions")
    void auth038ResetPasswordWithValidCodeResetsPasswordAndRevokesActiveSessions() throws Exception {
        User user = createUser("auth038", true, "+1555010603", true);
        AuthTokens firstSession = webLogin(user.getUsername(), DEFAULT_PASSWORD, nextIp(), "AUTH-038-1", status().isOk());
        webLogin(user.getEmail(), DEFAULT_PASSWORD, nextIp(), "AUTH-038-2", status().isOk());

        requestSmsReset(user.getPhone());
        String resetCode = latestPasswordResetCode.get();

        mockMvc.perform(post("/auth/reset-password/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", user.getPhone(),
                                "code", resetCode,
                                "newPassword", NEW_PASSWORD
                        ))))
                .andExpect(status().isNoContent());

        assertThat(sessionsFor(user))
                .hasSize(2)
                .allSatisfy(session -> {
                    assertThat(session.isRevoked()).isTrue();
                    assertThat(session.getRevokedReason()).isEqualTo("PASSWORD_RESET");
                });

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstSession.accessToken())))
                .andExpect(status().isUnauthorized());

        webLogin(user.getUsername(), DEFAULT_PASSWORD, nextIp(), "AUTH-038-old-password", status().isUnauthorized());
        webLogin(user.getUsername(), NEW_PASSWORD, nextIp(), "AUTH-038-new-password", status().isOk());

        assertThat(latestSmsCodeFor(user.getId(), SmsOtpPurpose.PASSWORD_RESET).getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("AUTH-039 POST /auth/reset-password/code wrong code increments failed attempts and expires after max attempts")
    void auth039WrongResetCodeIncrementsFailedAttemptsAndExpiresAfterMaxAttempts() throws Exception {
        User user = createUser("auth039", true, "+1555010604", true);

        requestSmsReset(user.getPhone());
        String issuedCode = latestPasswordResetCode.get();
        String wrongCode = differentCodeFrom(issuedCode);
        AuthSmsOtpCode resetCode = latestSmsCodeFor(user.getId(), SmsOtpPurpose.PASSWORD_RESET);

        for (int attempt = 1; attempt < 5; attempt++) {
            mockMvc.perform(post("/auth/reset-password/code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "phone", user.getPhone(),
                                    "code", wrongCode,
                                    "newPassword", NEW_PASSWORD
                            ))))
                    .andExpect(status().isUnauthorized());

            AuthSmsOtpCode persistedCode = authSmsOtpCodeRepository.findById(resetCode.getId()).orElseThrow();
            assertThat(persistedCode.getFailedAttempts()).isEqualTo(attempt);
            assertThat(persistedCode.getUsedAt()).isNull();
        }

        mockMvc.perform(post("/auth/reset-password/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", user.getPhone(),
                                "code", wrongCode,
                                "newPassword", NEW_PASSWORD
                        ))))
                .andExpect(status().isUnauthorized());

        AuthSmsOtpCode expiredCode = authSmsOtpCodeRepository.findById(resetCode.getId()).orElseThrow();
        assertThat(expiredCode.getFailedAttempts()).isEqualTo(5);
        assertThat(expiredCode.getUsedAt()).isNotNull();

        mockMvc.perform(post("/auth/reset-password/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", user.getPhone(),
                                "code", issuedCode,
                                "newPassword", NEW_PASSWORD
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AUTH-040 PUT /auth/change-password valid current password changes password and revokes other sessions")
    void auth040ChangePasswordRevokesOtherSessions() throws Exception {
        User user = createUser("auth040", true, null, false);
        AuthTokens firstSession = webLogin(user.getUsername(), DEFAULT_PASSWORD, nextIp(), "AUTH-040-1", status().isOk());
        AuthTokens currentSession = webLogin(user.getUsername(), DEFAULT_PASSWORD, nextIp(), "AUTH-040-2", status().isOk());

        mockMvc.perform(put("/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(currentSession.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", DEFAULT_PASSWORD,
                                "newPassword", NEW_PASSWORD
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(currentSession.accessToken())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstSession.accessToken())))
                .andExpect(status().isUnauthorized());

        assertThat(activeSessionsFor(user)).hasSize(1);
        assertThat(sessionsFor(user).stream().filter(UserSession::isRevoked)).hasSize(1);
        assertThat(sessionsFor(user).stream()
                .filter(UserSession::isRevoked)
                .findFirst()
                .orElseThrow()
                .getRevokedReason()).isEqualTo("PASSWORD_CHANGED");

        webLogin(user.getUsername(), DEFAULT_PASSWORD, nextIp(), "AUTH-040-old-password", status().isUnauthorized());
        webLogin(user.getUsername(), NEW_PASSWORD, nextIp(), "AUTH-040-new-password", status().isOk());
    }

    @Test
    @DisplayName("AUTH-041 PUT /auth/change-password rejects wrong current password")
    void auth041ChangePasswordRejectsWrongCurrentPassword() throws Exception {
        User user = createUser("auth041", true, null, false);
        AuthTokens currentSession = webLogin(user.getUsername(), DEFAULT_PASSWORD, nextIp(), "AUTH-041-1", status().isOk());

        MvcResult result = mockMvc.perform(put("/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(currentSession.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "WrongPass123!",
                                "newPassword", NEW_PASSWORD
                        ))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(bodyOf(result).get("message").asText()).isEqualTo("Current password is incorrect");
        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(currentSession.accessToken())))
                .andExpect(status().isOk());
        assertThat(activeSessionsFor(user)).hasSize(1);

        webLogin(user.getUsername(), DEFAULT_PASSWORD, nextIp(), "AUTH-041-old-password", status().isOk());
        webLogin(user.getUsername(), NEW_PASSWORD, nextIp(), "AUTH-041-new-password", status().isUnauthorized());
    }

    private void requestEmailReset(String email) throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", "EMAIL",
                                "email", email,
                                "clientTarget", "UNIVERSAL"
                        ))))
                .andExpect(status().isNoContent());
    }

    private void requestSmsReset(String phone) throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", "SMS",
                                "phone", phone
                        ))))
                .andExpect(status().isNoContent());
    }

    private AuthTokens webLogin(String identifier, String password, String ip, String userAgent, ResultMatcher expectedStatus)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/web/login")
                        .with(client(ip, userAgent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", identifier,
                                "password", password
                        ))))
                .andExpect(expectedStatus)
                .andReturn();

        JsonNode body = result.getResponse().getContentAsString().isBlank()
                ? null
                : bodyOf(result);

        return new AuthTokens(body == null || body.get("accessToken") == null ? null : body.get("accessToken").asText());
    }

    private User createUser(String label, boolean emailVerified, String phone, boolean phoneVerified) {
        String suffix = label + "-" + userSequence.getAndIncrement();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return userRepository.save(User.builder()
                .email("user." + suffix + "@pos.example")
                .username("user." + suffix)
                .passwordHash(passwordService.hash(DEFAULT_PASSWORD))
                .firstName("Auth")
                .lastName("User")
                .phone(phone)
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(emailVerified)
                .emailVerifiedAt(emailVerified ? now : null)
                .phoneVerified(phoneVerified)
                .phoneVerifiedAt(phoneVerified ? now : null)
                .build());
    }

    private List<AuthPasswordResetToken> passwordResetTokensFor(User user) {
        return authPasswordResetTokenRepository.findAll().stream()
                .filter(token -> token.getUserId().equals(user.getId()))
                .sorted(Comparator.comparing(AuthPasswordResetToken::getCreatedAt).reversed())
                .toList();
    }

    private AuthSmsOtpCode latestSmsCodeFor(UUID userId, SmsOtpPurpose purpose) {
        return authSmsOtpCodeRepository.findAll().stream()
                .filter(code -> code.getUserId().equals(userId) && code.getPurpose() == purpose)
                .max(Comparator.comparing(AuthSmsOtpCode::getCreatedAt))
                .orElseThrow();
    }

    private List<UserSession> sessionsFor(User user) {
        return userSessionRepository.findAll().stream()
                .filter(session -> session.getUserId().equals(user.getId()))
                .sorted(Comparator.comparing(UserSession::getCreatedAt))
                .toList();
    }

    private List<UserSession> activeSessionsFor(User user) {
        return userSessionRepository.findActiveSessionsByUserId(user.getId(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void backdateSmsCode(UUID codeId, Duration duration) {
        jdbcTemplate.update(
                "update auth_sms_otp_codes set created_at = ? where id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minus(duration),
                codeId
        );
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

    private String nextIp() {
        return "198.51.100." + ipSequence.getAndIncrement();
    }

    private String tokenFromUrl(String url) {
        String query = URI.create(url).getQuery();
        return query.substring(query.indexOf("token=") + 6);
    }

    private String differentCodeFrom(String issuedCode) {
        return "000000".equals(issuedCode) ? "111111" : "000000";
    }

    private record AuthTokens(String accessToken) {
    }
}
