package pos.pos.integration.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pos.pos.auth.entity.AuthEmailVerificationToken;
import pos.pos.auth.entity.AuthPasswordResetToken;
import pos.pos.auth.entity.AuthSmsOtpCode;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.enums.SmsOtpPurpose;
import pos.pos.auth.repository.AuthEmailVerificationTokenRepository;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.auth.repository.AuthPasswordResetTokenRepository;
import pos.pos.auth.repository.AuthSmsOtpCodeRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.auth.service.AuthMailService;
import pos.pos.auth.service.SmsMessageService;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.service.PasswordService;
import pos.pos.support.TestPostgresContainerSupport;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AbstractUserIntegrationTest {

    protected static final String SCHEMA = "user_grouped_" + UUID.randomUUID().toString().replace("-", "");
    protected static final String ADMIN_EMAIL = "user.grouped.admin@pos.example";
    protected static final String ADMIN_USERNAME = "usergroupedadmin";
    protected static final String ADMIN_PASSWORD = "StrongPass123!";
    protected static final String DEFAULT_PASSWORD = "StrongPass123!";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        TestPostgresContainerSupport.registerProdDatabaseProperties(registry, SCHEMA);
        registry.add("JWT_SECRET", () -> "user-grouped-test-secret-key-for-hs256-123456");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "user-grouped-refresh-token-pepper-0123456789");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "user-grouped-password-reset-pepper");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "user-grouped-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "user-grouped-sms-code-pepper-value");
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
        registry.add("BOOTSTRAP_SUPER_ADMIN_FIRST_NAME", () -> "User");
        registry.add("BOOTSTRAP_SUPER_ADMIN_LAST_NAME", () -> "Admin");
        registry.add("SMS_DELIVERY_MODE", () -> "LOG_ONLY");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected UserRoleRepository userRoleRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected UserSessionRepository userSessionRepository;

    @Autowired
    protected AuthLoginAttemptRepository authLoginAttemptRepository;

    @Autowired
    protected AuthEmailVerificationTokenRepository authEmailVerificationTokenRepository;

    @Autowired
    protected AuthPasswordResetTokenRepository authPasswordResetTokenRepository;

    @Autowired
    protected AuthSmsOtpCodeRepository authSmsOtpCodeRepository;

    @Autowired
    protected PasswordService passwordService;

    @MockBean
    protected JavaMailSender javaMailSender;

    @SpyBean
    protected AuthMailService authMailService;

    @SpyBean
    protected SmsMessageService smsMessageService;

    protected final AtomicInteger userSequence = new AtomicInteger(1);
    private final AtomicInteger roleSequence = new AtomicInteger(1);
    private final AtomicInteger ipSequence = new AtomicInteger(200);

    protected final AtomicReference<String> latestVerificationUrl = new AtomicReference<>();
    protected final AtomicReference<String> latestPasswordResetUrl = new AtomicReference<>();
    protected final AtomicReference<String> latestPasswordResetCode = new AtomicReference<>();
    protected final AtomicReference<String> latestPhoneVerificationCode = new AtomicReference<>();

    @BeforeEach
    void resetSharedState() {
        latestVerificationUrl.set(null);
        latestPasswordResetUrl.set(null);
        latestPasswordResetCode.set(null);
        latestPhoneVerificationCode.set(null);

        doNothing().when(javaMailSender).send(any(org.springframework.mail.SimpleMailMessage.class));

        Mockito.reset(authMailService, smsMessageService);

        doAnswer(invocation -> {
            latestVerificationUrl.set(invocation.getArgument(3, String.class));
            return invocation.callRealMethod();
        }).when(authMailService).sendEmailVerificationEmail(anyString(), anyString(), anyString(), anyString(), any(Duration.class));

        doAnswer(invocation -> {
            latestPasswordResetUrl.set(invocation.getArgument(3, String.class));
            return invocation.callRealMethod();
        }).when(authMailService).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyString(), any(Duration.class));

        doAnswer(invocation -> {
            latestPasswordResetCode.set(invocation.getArgument(2, String.class));
            return invocation.callRealMethod();
        }).when(smsMessageService).sendPasswordResetCode(anyString(), anyString(), anyString(), any(Duration.class));

        doAnswer(invocation -> {
            latestPhoneVerificationCode.set(invocation.getArgument(2, String.class));
            return invocation.callRealMethod();
        }).when(smsMessageService).sendPhoneVerificationCode(anyString(), anyString(), anyString(), any(Duration.class));

        authLoginAttemptRepository.deleteAllInBatch();
        userSessionRepository.deleteAllInBatch();
        authEmailVerificationTokenRepository.deleteAllInBatch();
        authPasswordResetTokenRepository.deleteAllInBatch();
        authSmsOtpCodeRepository.deleteAllInBatch();
    }

    protected User adminUser() {
        return userRepository.findByEmailAndDeletedAtIsNull(ADMIN_EMAIL).orElseThrow();
    }

    protected Role role(String code) {
        return roleRepository.findByCode(code).orElseThrow();
    }

    protected Role createRole(String label, long rank, boolean active, boolean assignable, boolean protectedRole) {
        int sequence = roleSequence.getAndIncrement();
        return roleRepository.save(Role.builder()
                .code(("TEST_" + label + "_" + sequence).toUpperCase(Locale.ROOT))
                .name("Test " + label + " " + sequence)
                .description("User integration test role")
                .rank(rank)
                .isSystem(false)
                .isActive(active)
                .assignable(assignable)
                .protectedRole(protectedRole)
                .build());
    }

    protected User createUser(
            String label,
            String roleCode,
            String firstName,
            String lastName,
            boolean active,
            boolean emailVerified,
            String phone,
            boolean phoneVerified
    ) {
        int sequence = userSequence.getAndIncrement();
        String suffix = label + "-" + sequence;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID adminId = adminUser().getId();

        User user = userRepository.save(User.builder()
                .email("user." + suffix + "@pos.example")
                .username("user." + suffix)
                .passwordHash(passwordService.hash(DEFAULT_PASSWORD))
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .status(active ? "ACTIVE" : "INACTIVE")
                .isActive(active)
                .emailVerified(emailVerified)
                .emailVerifiedAt(emailVerified ? now : null)
                .phoneVerified(phoneVerified)
                .phoneVerifiedAt(phoneVerified ? now : null)
                .createdBy(adminId)
                .updatedBy(adminId)
                .build());

        if (roleCode != null) {
            assignRole(user, role(roleCode), adminId);
        }

        return user;
    }

    protected User createUser(
            String label,
            String roleCode,
            boolean active,
            boolean emailVerified,
            String phone,
            boolean phoneVerified
    ) {
        return createUser(label, roleCode, "User", "Test", active, emailVerified, phone, phoneVerified);
    }

    protected void assignRole(User user, Role role) {
        assignRole(user, role, adminUser().getId());
    }

    protected void assignRole(User user, Role role, UUID assignedBy) {
        userRoleRepository.save(UserRole.builder()
                .userId(user.getId())
                .roleId(role.getId())
                .assignedBy(assignedBy)
                .build());
    }

    protected String adminAccessToken() throws Exception {
        return accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "USER-ADMIN");
    }

    protected String accessTokenFor(String identifier, String password, String userAgent) throws Exception {
        return webLogin(identifier, password, nextIp(), userAgent, status().isOk()).accessToken();
    }

    protected LoginTokens webLogin(
            String identifier,
            String password,
            String ip,
            String userAgent,
            ResultMatcher expectedStatus
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/web/login")
                        .with(client(ip, userAgent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", identifier,
                                "password", password
                        ))))
                .andExpect(expectedStatus)
                .andReturn();

        JsonNode body = result.getResponse().getContentAsString().isBlank() ? null : bodyOf(result);
        return new LoginTokens(
                body == null || body.get("accessToken") == null ? null : body.get("accessToken").asText(),
                ip
        );
    }

    protected JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected RequestPostProcessor client(String ip, String userAgent) {
        return request -> {
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", ip);
            request.addHeader(HttpHeaders.USER_AGENT, userAgent);
            return request;
        };
    }

    protected String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    protected String nextIp() {
        return "198.51.100." + ipSequence.getAndIncrement();
    }

    protected String tokenFromUrl(String url) {
        String query = URI.create(url).getQuery();
        return query.substring(query.indexOf("token=") + 6);
    }

    protected AuthPasswordResetToken latestPasswordResetTokenFor(User user) {
        return authPasswordResetTokenRepository.findAll().stream()
                .filter(token -> token.getUserId().equals(user.getId()))
                .max(Comparator.comparing(AuthPasswordResetToken::getCreatedAt))
                .orElseThrow();
    }

    protected AuthEmailVerificationToken latestEmailVerificationTokenFor(User user) {
        return authEmailVerificationTokenRepository.findAll().stream()
                .filter(token -> token.getUserId().equals(user.getId()))
                .max(Comparator.comparing(AuthEmailVerificationToken::getCreatedAt))
                .orElseThrow();
    }

    protected AuthSmsOtpCode latestSmsCodeFor(User user, SmsOtpPurpose purpose) {
        return authSmsOtpCodeRepository.findAll().stream()
                .filter(code -> code.getUserId().equals(user.getId()) && code.getPurpose() == purpose)
                .max(Comparator.comparing(AuthSmsOtpCode::getCreatedAt))
                .orElseThrow();
    }

    protected List<UserSession> sessionsFor(User user) {
        return userSessionRepository.findAll().stream()
                .filter(session -> session.getUserId().equals(user.getId()))
                .sorted(Comparator.comparing(UserSession::getCreatedAt))
                .toList();
    }

    protected List<UserSession> activeSessionsFor(User user) {
        return userSessionRepository.findActiveSessionsByUserId(user.getId(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    protected record LoginTokens(String accessToken, String ip) {
    }
}
