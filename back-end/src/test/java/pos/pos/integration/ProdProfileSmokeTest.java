package pos.pos.integration;

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
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pos.pos.auth.service.AuthMailService;
import pos.pos.auth.service.SmsMessageService;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("Prod profile smoke test")
class ProdProfileSmokeTest {

    private static final String SCHEMA = "prod_verify_" + UUID.randomUUID().toString().replace("-", "");
    private static final String ADMIN_EMAIL = "prod.admin@pos.example";
    private static final String ADMIN_USERNAME = "prodadmin";
    private static final String ADMIN_PASSWORD = "StrongPass123!";
    private static final String NEW_USER_EMAIL = "cashier.prod@pos.example";
    private static final String NEW_USER_USERNAME = "cashier.prod";
    private static final String NEW_USER_PASSWORD = "RegisterPass123!";
    private static final String NEW_USER_NEW_PASSWORD = "RegisterPass456!";
    private static final String NEW_USER_PHONE = "+1555010002";
    private static final String COOKIE_NAME = "refreshToken";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> "jdbc:postgresql://localhost:5432/pos?currentSchema=" + SCHEMA);
        registry.add("DB_USERNAME", () -> "pos_user");
        registry.add("DB_PASSWORD", () -> "pos_pass");
        registry.add("JWT_SECRET", () -> "prod-smoke-secret-key-for-hs256-minimum-32b");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "prod-smoke-refresh-token-pepper-0123456789");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "prod-smoke-password-reset-pepper");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "prod-smoke-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "prod-smoke-sms-code-pepper");
        registry.add("MAIL_HOST", () -> "localhost");
        registry.add("MAIL_PORT", () -> "2525");
        registry.add("MAIL_USERNAME", () -> "smoke");
        registry.add("MAIL_PASSWORD", () -> "smoke");
        registry.add("MAIL_FROM", () -> "no-reply@pos.example");
        registry.add("FRONTEND_BASE_URL", () -> "https://app.pos.example");
        registry.add("FRONTEND_DEFAULT_LINK_TARGET", () -> "UNIVERSAL");
        registry.add("TRUSTED_PROXIES", () -> "127.0.0.1,::1");
        registry.add("COOKIE_DOMAIN", () -> "pos.example");
        registry.add("BOOTSTRAP_SUPER_ADMIN_ENABLED", () -> "true");
        registry.add("BOOTSTRAP_SUPER_ADMIN_EMAIL", () -> ADMIN_EMAIL);
        registry.add("BOOTSTRAP_SUPER_ADMIN_USERNAME", () -> ADMIN_USERNAME);
        registry.add("BOOTSTRAP_SUPER_ADMIN_PASSWORD", () -> ADMIN_PASSWORD);
        registry.add("BOOTSTRAP_SUPER_ADMIN_FIRST_NAME", () -> "Prod");
        registry.add("BOOTSTRAP_SUPER_ADMIN_LAST_NAME", () -> "Admin");
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
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @MockBean
    private JavaMailSender javaMailSender;

    @SpyBean
    private AuthMailService authMailService;

    @SpyBean
    private SmsMessageService smsMessageService;

    private final AtomicReference<String> latestVerificationUrl = new AtomicReference<>();
    private final AtomicReference<String> latestPhoneVerificationCode = new AtomicReference<>();
    private final AtomicReference<String> latestPasswordResetCode = new AtomicReference<>();

    @BeforeEach
    void setUpSpies() {
        latestVerificationUrl.set(null);
        latestPhoneVerificationCode.set(null);
        latestPasswordResetCode.set(null);

        doNothing().when(javaMailSender).send(any(org.springframework.mail.SimpleMailMessage.class));

        Mockito.reset(authMailService, smsMessageService);

        doAnswer(invocation -> {
            latestVerificationUrl.set(invocation.getArgument(3, String.class));
            return invocation.callRealMethod();
        }).when(authMailService).sendEmailVerificationEmail(anyString(), anyString(), anyString(), anyString(), any(Duration.class));

        doAnswer(invocation -> {
            latestPhoneVerificationCode.set(invocation.getArgument(2, String.class));
            return invocation.callRealMethod();
        }).when(smsMessageService).sendPhoneVerificationCode(anyString(), anyString(), anyString(), any(Duration.class));

        doAnswer(invocation -> {
            latestPasswordResetCode.set(invocation.getArgument(2, String.class));
            return invocation.callRealMethod();
        }).when(smsMessageService).sendPasswordResetCode(anyString(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Should boot with prod profile and exercise core auth flows end to end")
    void shouldVerifyProdProfileAuthFlows() throws Exception {
        assertThat(List.of(environment.getActiveProfiles())).containsExactly("prod");
        assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(environment.getProperty("app.security.cookie.domain")).isEqualTo("pos.example");

        Integer migrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1'",
                Integer.class
        );
        assertThat(migrationCount).isEqualTo(1);

        User admin = userRepository.findByEmailAndDeletedAtIsNull(ADMIN_EMAIL).orElseThrow();
        assertThat(admin.getUsername()).isEqualTo(ADMIN_USERNAME);
        assertThat(admin.isEmailVerified()).isTrue();

        MvcResult firstLogin = webLogin(ADMIN_USERNAME, ADMIN_PASSWORD, status().isOk());
        JsonNode firstLoginBody = bodyOf(firstLogin);
        String firstAccessToken = firstLoginBody.get("accessToken").asText();
        String firstRefreshToken = extractCookieValue(firstLogin.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        assertThat(firstLogin.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("Domain=pos.example")
                .contains("Path=/auth/web")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict");

        assertThat(bodyOf(mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstAccessToken)))
                .andExpect(status().isOk())
                .andReturn()).get("username").asText()).isEqualTo(ADMIN_USERNAME);

        JsonNode assignableRoles = bodyOf(mockMvc.perform(get("/roles/assignable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstAccessToken)))
                .andExpect(status().isOk())
                .andReturn());
        List<String> assignableRoleCodes = StreamSupport.stream(assignableRoles.spliterator(), false)
                .map(node -> node.get("code").asText())
                .toList();
        assertThat(assignableRoleCodes).contains("WAITER");

        MvcResult refreshed = mockMvc.perform(post("/auth/web/refresh")
                        .cookie(refreshCookie(firstRefreshToken)))
                .andExpect(status().isOk())
                .andReturn();
        String refreshedAccessToken = bodyOf(refreshed).get("accessToken").asText();
        String refreshedRefreshToken = extractCookieValue(refreshed.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(refreshedAccessToken).isNotEqualTo(firstAccessToken);
        assertThat(refreshedRefreshToken).isNotEqualTo(firstRefreshToken);

        mockMvc.perform(post("/auth/web/logout")
                        .cookie(refreshCookie(refreshedRefreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/web/refresh")
                        .cookie(refreshCookie(refreshedRefreshToken)))
                .andExpect(status().isUnauthorized());

        MvcResult secondLogin = webLogin(ADMIN_USERNAME, ADMIN_PASSWORD, status().isOk());
        String secondRefreshToken = extractCookieValue(secondLogin.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        mockMvc.perform(post("/auth/web/logout-all")
                        .cookie(refreshCookie(secondRefreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/web/refresh")
                        .cookie(refreshCookie(secondRefreshToken)))
                .andExpect(status().isUnauthorized());

        MvcResult thirdLogin = webLogin(ADMIN_USERNAME, ADMIN_PASSWORD, status().isOk());
        String adminAccessToken = bodyOf(thirdLogin).get("accessToken").asText();

        Role waiterRole = roleRepository.findByCode("WAITER").orElseThrow();
        mockMvc.perform(post("/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", NEW_USER_EMAIL,
                                "username", NEW_USER_USERNAME,
                                "temporaryPassword", NEW_USER_PASSWORD,
                                "firstName", "Cashier",
                                "lastName", "Prod",
                                "phone", NEW_USER_PHONE,
                                "roleId", waiterRole.getId().toString(),
                                "clientTarget", "UNIVERSAL"
                        ))))
                .andExpect(status().isCreated());

        assertThat(latestVerificationUrl.get()).isNotBlank();
        String firstVerificationToken = tokenFromUrl(latestVerificationUrl.get());

        mockMvc.perform(post("/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", NEW_USER_EMAIL,
                                "clientTarget", "UNIVERSAL"
                        ))))
                .andExpect(status().isNoContent());

        assertThat(latestVerificationUrl.get()).isNotBlank();
        String verificationTokenAfterResendAttempt = tokenFromUrl(latestVerificationUrl.get());
        assertThat(verificationTokenAfterResendAttempt).isEqualTo(firstVerificationToken);

        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", firstVerificationToken))))
                .andExpect(status().isNoContent());

        User registeredUser = userRepository.findByEmailAndDeletedAtIsNull(NEW_USER_EMAIL).orElseThrow();
        assertThat(registeredUser.isEmailVerified()).isTrue();

        MvcResult newUserLogin = webLogin(NEW_USER_USERNAME, NEW_USER_PASSWORD, status().isOk());
        String newUserAccessToken = bodyOf(newUserLogin).get("accessToken").asText();

        latestPhoneVerificationCode.set(null);
        mockMvc.perform(post("/auth/request-phone-verification")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newUserAccessToken)))
                .andExpect(status().isNoContent());

        assertThat(latestPhoneVerificationCode.get()).isNotBlank();

        mockMvc.perform(post("/auth/verify-phone")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newUserAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", latestPhoneVerificationCode.get()))))
                .andExpect(status().isNoContent());

        User phoneVerifiedUser = userRepository.findByEmailAndDeletedAtIsNull(NEW_USER_EMAIL).orElseThrow();
        assertThat(phoneVerifiedUser.isPhoneVerified()).isTrue();

        latestPasswordResetCode.set(null);
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", "SMS",
                                "phone", NEW_USER_PHONE
                        ))))
                .andExpect(status().isNoContent());

        assertThat(latestPasswordResetCode.get()).isNotBlank();

        mockMvc.perform(post("/auth/reset-password/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", NEW_USER_PHONE,
                                "code", latestPasswordResetCode.get(),
                                "newPassword", NEW_USER_NEW_PASSWORD
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newUserAccessToken)))
                .andExpect(status().isUnauthorized());

        webLogin(NEW_USER_USERNAME, NEW_USER_PASSWORD, status().isUnauthorized());
        webLogin(NEW_USER_USERNAME, NEW_USER_NEW_PASSWORD, status().isOk());
    }

    private MvcResult webLogin(String identifier, String password, org.springframework.test.web.servlet.ResultMatcher expectedStatus)
            throws Exception {
        return mockMvc.perform(post("/auth/web/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", identifier,
                                "password", password
                        ))))
                .andExpect(expectedStatus)
                .andReturn();
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        return objectMapper.readTree(content);
    }

    private String extractCookieValue(String setCookieHeader) {
        assertThat(setCookieHeader).isNotBlank();
        String prefix = COOKIE_NAME + "=";
        int start = setCookieHeader.indexOf(prefix);
        int end = setCookieHeader.indexOf(';', start);
        return setCookieHeader.substring(start + prefix.length(), end);
    }

    private MockCookie refreshCookie(String refreshToken) {
        return new MockCookie(COOKIE_NAME, refreshToken);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String tokenFromUrl(String url) {
        String query = URI.create(url).getQuery();
        assertThat(query).startsWith("token=");
        return query.substring("token=".length());
    }
}
