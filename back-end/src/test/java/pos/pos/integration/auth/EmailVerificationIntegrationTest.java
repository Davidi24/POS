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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pos.pos.auth.entity.AuthEmailVerificationToken;
import pos.pos.auth.repository.AuthEmailVerificationTokenRepository;
import pos.pos.auth.service.AuthMailService;
import pos.pos.security.service.PasswordService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("Email verification integration test")
class EmailVerificationIntegrationTest {

    private static final String SCHEMA = "email_verify_" + UUID.randomUUID().toString().replace("-", "");
    private static final String DEFAULT_PASSWORD = "StrongPass123!";
    private static final String INVALID_TOKEN_MESSAGE = "Invalid token";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> "jdbc:postgresql://localhost:5432/pos?currentSchema=" + SCHEMA);
        registry.add("DB_USERNAME", () -> "pos_user");
        registry.add("DB_PASSWORD", () -> "pos_pass");
        registry.add("JWT_SECRET", () -> "email-verification-test-secret-key-for-hs256-123456");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "email-verification-refresh-token-pepper-0123456789");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "email-verification-password-reset-pepper");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "email-verification-token-pepper-value");
        registry.add("SMS_CODE_PEPPER", () -> "email-verification-sms-code-pepper-value");
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
    private AuthEmailVerificationTokenRepository authEmailVerificationTokenRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private JavaMailSender javaMailSender;

    @SpyBean
    private AuthMailService authMailService;

    private final AtomicInteger userSequence = new AtomicInteger(1);
    private final AtomicReference<String> latestVerificationUrl = new AtomicReference<>();

    @BeforeEach
    void resetState() {
        latestVerificationUrl.set(null);

        doNothing().when(javaMailSender).send(any(org.springframework.mail.SimpleMailMessage.class));

        Mockito.reset(authMailService);

        doAnswer(invocation -> {
            latestVerificationUrl.set(invocation.getArgument(3, String.class));
            return invocation.callRealMethod();
        }).when(authMailService).sendEmailVerificationEmail(anyString(), anyString(), anyString(), anyString(), any(Duration.class));

        authEmailVerificationTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("AUTH-042 POST /auth/verify-email valid token verifies email and marks token used")
    void auth042VerifyEmailWithValidTokenMarksUserVerifiedAndTokenUsed() throws Exception {
        User user = createUser("auth042", false);
        requestResendVerification(user.getEmail());
        String verificationToken = tokenFromUrl(latestVerificationUrl.get());

        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", verificationToken))))
                .andExpect(status().isNoContent());

        User verifiedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(verifiedUser.isEmailVerified()).isTrue();
        assertThat(verifiedUser.getEmailVerifiedAt()).isNotNull();
        assertThat(emailVerificationTokensFor(user).getFirst().getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("AUTH-043 POST /auth/verify-email rejects invalid or used token")
    void auth043VerifyEmailRejectsInvalidOrUsedToken() throws Exception {
        User user = createUser("auth043", false);

        MvcResult invalidResult = mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", "not-a-valid-token"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(bodyOf(invalidResult).get("message").asText()).isEqualTo(INVALID_TOKEN_MESSAGE);

        requestResendVerification(user.getEmail());
        String verificationToken = tokenFromUrl(latestVerificationUrl.get());

        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", verificationToken))))
                .andExpect(status().isNoContent());

        MvcResult usedResult = mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", verificationToken))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(bodyOf(usedResult).get("message").asText()).isEqualTo(INVALID_TOKEN_MESSAGE);
    }

    @Test
    @DisplayName("AUTH-044 POST /auth/resend-verification issues new verification token for active unverified user")
    void auth044ResendVerificationIssuesNewVerificationToken() throws Exception {
        User user = createUser("auth044", false);
        requestResendVerification(user.getEmail());
        String firstToken = tokenFromUrl(latestVerificationUrl.get());
        AuthEmailVerificationToken persistedFirstToken = emailVerificationTokensFor(user).getFirst();
        backdateEmailVerificationToken(persistedFirstToken.getId(), Duration.ofMinutes(6));

        latestVerificationUrl.set(null);
        requestResendVerification(user.getEmail());
        String secondToken = tokenFromUrl(latestVerificationUrl.get());

        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThat(emailVerificationTokensFor(user)).hasSize(1);
        assertThat(emailVerificationTokensFor(user).getFirst().getId()).isNotEqualTo(persistedFirstToken.getId());
    }

    @Test
    @DisplayName("AUTH-045 POST /auth/resend-verification respects resend cooldown")
    void auth045ResendVerificationRespectsCooldown() throws Exception {
        User user = createUser("auth045", false);
        requestResendVerification(user.getEmail());
        AuthEmailVerificationToken firstToken = emailVerificationTokensFor(user).getFirst();

        latestVerificationUrl.set(null);
        requestResendVerification(user.getEmail());

        assertThat(latestVerificationUrl.get()).isNull();
        assertThat(emailVerificationTokensFor(user)).hasSize(1);
        assertThat(emailVerificationTokensFor(user).getFirst().getId()).isEqualTo(firstToken.getId());
    }

    private void requestResendVerification(String email) throws Exception {
        mockMvc.perform(post("/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "clientTarget", "UNIVERSAL"
                        ))))
                .andExpect(status().isNoContent());
    }

    private User createUser(String label, boolean emailVerified) {
        String suffix = label + "-" + userSequence.getAndIncrement();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return userRepository.save(User.builder()
                .email("user." + suffix + "@pos.example")
                .username("user." + suffix)
                .passwordHash(passwordService.hash(DEFAULT_PASSWORD))
                .firstName("Email")
                .lastName("User")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(emailVerified)
                .emailVerifiedAt(emailVerified ? now : null)
                .build());
    }

    private List<AuthEmailVerificationToken> emailVerificationTokensFor(User user) {
        return authEmailVerificationTokenRepository.findAll().stream()
                .filter(token -> token.getUserId().equals(user.getId()))
                .sorted(Comparator.comparing(AuthEmailVerificationToken::getCreatedAt).reversed())
                .toList();
    }

    private void backdateEmailVerificationToken(UUID tokenId, Duration duration) {
        jdbcTemplate.update(
                "update auth_email_verification_tokens set created_at = ? where id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minus(duration),
                tokenId
        );
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String tokenFromUrl(String url) {
        String query = URI.create(url).getQuery();
        return query.substring(query.indexOf("token=") + 6);
    }
}
