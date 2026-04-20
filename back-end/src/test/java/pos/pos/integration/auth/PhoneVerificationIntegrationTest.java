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
import pos.pos.auth.entity.AuthSmsOtpCode;
import pos.pos.auth.enums.SmsOtpPurpose;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.auth.repository.AuthSmsOtpCodeRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.auth.service.SmsMessageService;
import pos.pos.security.service.PasswordService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("Phone verification integration test")
class PhoneVerificationIntegrationTest {

    private static final String SCHEMA = "phone_verify_" + UUID.randomUUID().toString().replace("-", "");
    private static final String DEFAULT_PASSWORD = "StrongPass123!";
    private static final String TOO_MANY_REQUESTS_MESSAGE = "Too many phone verification requests. Try again later.";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> "jdbc:postgresql://localhost:5432/pos?currentSchema=" + SCHEMA);
        registry.add("DB_USERNAME", () -> "pos_user");
        registry.add("DB_PASSWORD", () -> "pos_pass");
        registry.add("JWT_SECRET", () -> "phone-verification-test-secret-key-for-hs256-123456");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "phone-verification-refresh-token-pepper-0123456789");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "phone-verification-password-reset-pepper-value");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "phone-verification-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "phone-verification-sms-code-pepper-value");
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
    private UserSessionRepository userSessionRepository;

    @Autowired
    private AuthLoginAttemptRepository authLoginAttemptRepository;

    @Autowired
    private AuthSmsOtpCodeRepository authSmsOtpCodeRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private JavaMailSender javaMailSender;

    @SpyBean
    private SmsMessageService smsMessageService;

    private final AtomicInteger userSequence = new AtomicInteger(1);
    private final AtomicInteger ipSequence = new AtomicInteger(180);
    private final AtomicReference<String> latestPhoneVerificationCode = new AtomicReference<>();

    @BeforeEach
    void resetState() {
        latestPhoneVerificationCode.set(null);

        doNothing().when(javaMailSender).send(any(org.springframework.mail.SimpleMailMessage.class));

        Mockito.reset(smsMessageService);

        doAnswer(invocation -> {
            latestPhoneVerificationCode.set(invocation.getArgument(2, String.class));
            return invocation.callRealMethod();
        }).when(smsMessageService).sendPhoneVerificationCode(anyString(), anyString(), anyString(), any(Duration.class));

        authLoginAttemptRepository.deleteAllInBatch();
        userSessionRepository.deleteAllInBatch();
        authSmsOtpCodeRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("AUTH-046 POST /auth/request-phone-verification issues phone verification code for current user")
    void auth046RequestPhoneVerificationIssuesCodeForCurrentUser() throws Exception {
        User user = createUser("auth046", false);
        AuthTokens tokens = webLogin(user.getUsername(), DEFAULT_PASSWORD, nextIp(), "AUTH-046-login", status().isOk());

        mockMvc.perform(post("/auth/request-phone-verification")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isNoContent());

        assertThat(latestPhoneVerificationCode.get()).isNotBlank();

        AuthSmsOtpCode code = latestPhoneCodeFor(user.getId());
        assertThat(code.getUsedAt()).isNull();
        assertThat(code.getFailedAttempts()).isZero();
        assertThat(code.getPurpose()).isEqualTo(SmsOtpPurpose.PHONE_VERIFICATION);
        assertThat(code.getPhoneNumberSnapshot()).isEqualTo(user.getNormalizedPhone());
    }

    @Test
    @DisplayName("AUTH-047 POST /auth/request-phone-verification respects cooldown and daily limit rules")
    void auth047RequestPhoneVerificationRespectsCooldownAndDailyLimitRules() throws Exception {
        User user = createUser("auth047", false);
        AuthTokens tokens = webLogin(user.getUsername(), DEFAULT_PASSWORD, nextIp(), "AUTH-047-login", status().isOk());

        mockMvc.perform(post("/auth/request-phone-verification")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isNoContent());

        MvcResult cooldownResult = mockMvc.perform(post("/auth/request-phone-verification")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        assertThat(bodyOf(cooldownResult).get("message").asText()).isEqualTo(TOO_MANY_REQUESTS_MESSAGE);

        AuthSmsOtpCode firstCode = latestPhoneCodeFor(user.getId());
        backdateSmsCode(firstCode.getId(), Duration.ofMinutes(2));

        mockMvc.perform(post("/auth/request-phone-verification")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isNoContent());

        AuthSmsOtpCode secondCode = latestPhoneCodeFor(user.getId());
        assertThat(secondCode.getId()).isNotEqualTo(firstCode.getId());
        backdateSmsCode(secondCode.getId(), Duration.ofMinutes(2));

        latestPhoneVerificationCode.set(null);

        MvcResult dailyLimitResult = mockMvc.perform(post("/auth/request-phone-verification")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        assertThat(bodyOf(dailyLimitResult).get("message").asText()).isEqualTo(TOO_MANY_REQUESTS_MESSAGE);
        assertThat(latestPhoneVerificationCode.get()).isNull();
    }

    @Test
    @DisplayName("AUTH-048 POST /auth/verify-phone valid code verifies current phone and marks code used")
    void auth048VerifyPhoneWithValidCodeMarksPhoneVerifiedAndCodeUsed() throws Exception {
        User user = createUser("auth048", false);
        AuthTokens tokens = webLogin(user.getUsername(), DEFAULT_PASSWORD, nextIp(), "AUTH-048-login", status().isOk());

        mockMvc.perform(post("/auth/request-phone-verification")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isNoContent());

        String verificationCode = latestPhoneVerificationCode.get();

        mockMvc.perform(post("/auth/verify-phone")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", verificationCode))))
                .andExpect(status().isNoContent());

        User verifiedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(verifiedUser.isPhoneVerified()).isTrue();
        assertThat(verifiedUser.getPhoneVerifiedAt()).isNotNull();
        assertThat(latestPhoneCodeFor(user.getId()).getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("AUTH-049 POST /auth/verify-phone wrong code increments failed attempts and expires after max attempts")
    void auth049WrongPhoneVerificationCodeIncrementsFailedAttemptsAndExpiresAfterMaxAttempts() throws Exception {
        User user = createUser("auth049", false);
        AuthTokens tokens = webLogin(user.getUsername(), DEFAULT_PASSWORD, nextIp(), "AUTH-049-login", status().isOk());

        mockMvc.perform(post("/auth/request-phone-verification")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isNoContent());

        String issuedCode = latestPhoneVerificationCode.get();
        String wrongCode = differentCodeFrom(issuedCode);
        AuthSmsOtpCode verificationCode = latestPhoneCodeFor(user.getId());

        for (int attempt = 1; attempt < 5; attempt++) {
            mockMvc.perform(post("/auth/verify-phone")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("code", wrongCode))))
                    .andExpect(status().isUnauthorized());

            AuthSmsOtpCode persistedCode = authSmsOtpCodeRepository.findById(verificationCode.getId()).orElseThrow();
            assertThat(persistedCode.getFailedAttempts()).isEqualTo(attempt);
            assertThat(persistedCode.getUsedAt()).isNull();
        }

        mockMvc.perform(post("/auth/verify-phone")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", wrongCode))))
                .andExpect(status().isUnauthorized());

        AuthSmsOtpCode expiredCode = authSmsOtpCodeRepository.findById(verificationCode.getId()).orElseThrow();
        assertThat(expiredCode.getFailedAttempts()).isEqualTo(5);
        assertThat(expiredCode.getUsedAt()).isNotNull();

        mockMvc.perform(post("/auth/verify-phone")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", issuedCode))))
                .andExpect(status().isUnauthorized());
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

        JsonNode body = bodyOf(result);
        return new AuthTokens(body.get("accessToken").asText());
    }

    private User createUser(String label, boolean phoneVerified) {
        int number = userSequence.getAndIncrement();
        String suffix = label + "-" + number;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return userRepository.save(User.builder()
                .email("user." + suffix + "@pos.example")
                .username("user." + suffix)
                .passwordHash(passwordService.hash(DEFAULT_PASSWORD))
                .firstName("Phone")
                .lastName("User")
                .phone("+15550107" + String.format("%02d", number))
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .emailVerifiedAt(now)
                .phoneVerified(phoneVerified)
                .phoneVerifiedAt(phoneVerified ? now : null)
                .build());
    }

    private AuthSmsOtpCode latestPhoneCodeFor(UUID userId) {
        return authSmsOtpCodeRepository.findAll().stream()
                .filter(code -> code.getUserId().equals(userId) && code.getPurpose() == SmsOtpPurpose.PHONE_VERIFICATION)
                .max(Comparator.comparing(AuthSmsOtpCode::getCreatedAt))
                .orElseThrow();
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

    private String differentCodeFrom(String issuedCode) {
        return "000000".equals(issuedCode) ? "111111" : "000000";
    }

    private record AuthTokens(String accessToken) {
    }
}
