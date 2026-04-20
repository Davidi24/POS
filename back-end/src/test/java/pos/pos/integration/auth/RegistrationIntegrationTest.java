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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pos.pos.auth.repository.AuthEmailVerificationTokenRepository;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
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
@DisplayName("Registration integration test")
class RegistrationIntegrationTest {

    private static final String SCHEMA = "registration_" + UUID.randomUUID().toString().replace("-", "");
    private static final String ADMIN_EMAIL = "registration.admin@pos.example";
    private static final String ADMIN_USERNAME = "registrationadmin";
    private static final String ADMIN_PASSWORD = "StrongPass123!";
    private static final String DEFAULT_USER_PASSWORD = "RegisterPass123!";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", () -> "jdbc:postgresql://localhost:5432/pos?currentSchema=" + SCHEMA);
        registry.add("DB_USERNAME", () -> "pos_user");
        registry.add("DB_PASSWORD", () -> "pos_pass");
        registry.add("JWT_SECRET", () -> "registration-test-secret-key-for-hs256-123456");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "registration-refresh-token-pepper-0123456789");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "registration-password-reset-pepper");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "registration-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "registration-sms-code-pepper-value");
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
        registry.add("BOOTSTRAP_SUPER_ADMIN_FIRST_NAME", () -> "Register");
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
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AuthEmailVerificationTokenRepository authEmailVerificationTokenRepository;

    @Autowired
    private PasswordService passwordService;

    @MockBean
    private JavaMailSender javaMailSender;

    private final AtomicInteger userSequence = new AtomicInteger(1);
    private final AtomicInteger ipSequence = new AtomicInteger(120);

    @BeforeEach
    void resetMailMock() {
        doNothing().when(javaMailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    @DisplayName("AUTH-028 POST /auth/register creates a user with an allowed role")
    void auth028CreatesUserWithAllowedRole() throws Exception {
        String accessToken = adminAccessToken();
        Role waiterRole = roleRepository.findByCode("WAITER").orElseThrow();
        RegistrationCandidate candidate = nextCandidate("auth028", "+1555010201");

        MvcResult result = mockMvc.perform(post("/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", candidate.email(),
                                "username", candidate.username(),
                                "temporaryPassword", DEFAULT_USER_PASSWORD,
                                "firstName", "Register",
                                "lastName", "User",
                                "phone", candidate.phone(),
                                "roleId", waiterRole.getId().toString(),
                                "clientTarget", "UNIVERSAL"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("email").asText()).isEqualTo(candidate.email());
        assertThat(body.get("username").asText()).isEqualTo(candidate.username());
        assertThat(body.get("roles").get(0).asText()).isEqualTo("WAITER");
        assertThat(body.get("emailVerified").asBoolean()).isFalse();

        User createdUser = userRepository.findByEmailAndDeletedAtIsNull(candidate.email()).orElseThrow();
        assertThat(createdUser.isEmailVerified()).isFalse();
        assertThat(createdUser.isPhoneVerified()).isFalse();
        assertThat(roleRepository.findActiveRoleCodesByUserId(createdUser.getId())).containsExactly("WAITER");
        assertThat(authEmailVerificationTokenRepository.findAll().stream()
                .filter(token -> token.getUserId().equals(createdUser.getId())))
                .hasSize(1);
    }

    @Test
    @DisplayName("AUTH-029 POST /auth/register rejects duplicate email")
    void auth029RejectsDuplicateEmail() throws Exception {
        String accessToken = adminAccessToken();
        Role waiterRole = roleRepository.findByCode("WAITER").orElseThrow();
        RegistrationCandidate existing = nextCandidate("auth029-existing", "+1555010202");
        createExistingUser(existing, null);
        RegistrationCandidate duplicate = nextCandidate("auth029-duplicate", "+1555010203");

        MvcResult result = mockMvc.perform(post("/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", existing.email(),
                                "username", duplicate.username(),
                                "temporaryPassword", DEFAULT_USER_PASSWORD,
                                "firstName", "Duplicate",
                                "lastName", "Email",
                                "phone", duplicate.phone(),
                                "roleId", waiterRole.getId().toString(),
                                "clientTarget", "UNIVERSAL"
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(bodyOf(result).get("message").asText()).isEqualTo("Email already in use");
    }

    @Test
    @DisplayName("AUTH-030 POST /auth/register rejects duplicate username")
    void auth030RejectsDuplicateUsername() throws Exception {
        String accessToken = adminAccessToken();
        Role waiterRole = roleRepository.findByCode("WAITER").orElseThrow();
        RegistrationCandidate existing = nextCandidate("auth030-existing", "+1555010204");
        createExistingUser(existing, null);
        RegistrationCandidate duplicate = nextCandidate("auth030-duplicate", "+1555010205");

        MvcResult result = mockMvc.perform(post("/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", duplicate.email(),
                                "username", existing.username(),
                                "temporaryPassword", DEFAULT_USER_PASSWORD,
                                "firstName", "Duplicate",
                                "lastName", "Username",
                                "phone", duplicate.phone(),
                                "roleId", waiterRole.getId().toString(),
                                "clientTarget", "UNIVERSAL"
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(bodyOf(result).get("message").asText()).isEqualTo("Username already in use");
    }

    @Test
    @DisplayName("AUTH-031 POST /auth/register rejects duplicate phone")
    void auth031RejectsDuplicatePhone() throws Exception {
        String accessToken = adminAccessToken();
        Role waiterRole = roleRepository.findByCode("WAITER").orElseThrow();
        RegistrationCandidate existing = nextCandidate("auth031-existing", "+1555010206");
        createExistingUser(existing, existing.phone());
        RegistrationCandidate duplicate = nextCandidate("auth031-duplicate", "+1555010207");

        MvcResult result = mockMvc.perform(post("/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", duplicate.email(),
                                "username", duplicate.username(),
                                "temporaryPassword", DEFAULT_USER_PASSWORD,
                                "firstName", "Duplicate",
                                "lastName", "Phone",
                                "phone", existing.phone(),
                                "roleId", waiterRole.getId().toString(),
                                "clientTarget", "UNIVERSAL"
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(bodyOf(result).get("message").asText()).isEqualTo("Phone already in use");
    }

    @Test
    @DisplayName("AUTH-032 POST /auth/register rejects assigning a role above actor rank or a protected role")
    void auth032RejectsAssigningRoleAboveActorRankOrProtectedRole() throws Exception {
        Role adminRole = roleRepository.findByCode("ADMIN").orElseThrow();
        Role ownerRole = roleRepository.findByCode("OWNER").orElseThrow();
        RegistrationCandidate managerCandidate = nextCandidate("auth032-manager", "+1555010208");
        createExistingUser(managerCandidate, managerCandidate.phone(), "MANAGER");
        String managerAccessToken = accessTokenFor(
                managerCandidate.username(),
                DEFAULT_USER_PASSWORD,
                "AUTH-032-manager-login"
        );

        RegistrationCandidate aboveRankCandidate = nextCandidate("auth032-admin", "+1555010209");
        MvcResult aboveRankResult = mockMvc.perform(post("/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", aboveRankCandidate.email(),
                                "username", aboveRankCandidate.username(),
                                "temporaryPassword", DEFAULT_USER_PASSWORD,
                                "firstName", "Blocked",
                                "lastName", "Admin",
                                "phone", aboveRankCandidate.phone(),
                                "roleId", adminRole.getId().toString(),
                                "clientTarget", "UNIVERSAL"
                        ))))
                .andExpect(status().isForbidden())
                .andReturn();

        RegistrationCandidate protectedRoleCandidate = nextCandidate("auth032-owner", "+1555010210");
        MvcResult protectedRoleResult = mockMvc.perform(post("/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", protectedRoleCandidate.email(),
                                "username", protectedRoleCandidate.username(),
                                "temporaryPassword", DEFAULT_USER_PASSWORD,
                                "firstName", "Blocked",
                                "lastName", "Owner",
                                "phone", protectedRoleCandidate.phone(),
                                "roleId", ownerRole.getId().toString(),
                                "clientTarget", "UNIVERSAL"
                        ))))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(bodyOf(aboveRankResult).get("message").asText()).isEqualTo("You are not allowed to assign this role");
        assertThat(bodyOf(protectedRoleResult).get("message").asText()).isEqualTo("You are not allowed to assign this role");
        assertThat(userRepository.findByEmailAndDeletedAtIsNull(aboveRankCandidate.email())).isEmpty();
        assertThat(userRepository.findByEmailAndDeletedAtIsNull(protectedRoleCandidate.email())).isEmpty();
    }

    private String adminAccessToken() throws Exception {
        return accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "REGISTRATION-ADMIN");
    }

    private String accessTokenFor(String identifier, String password, String userAgent) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/web/login")
                        .with(client(nextIp(), userAgent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", identifier,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        return bodyOf(result).get("accessToken").asText();
    }

    private User createExistingUser(RegistrationCandidate candidate, String phone) {
        return createExistingUser(candidate, phone, null);
    }

    private User createExistingUser(RegistrationCandidate candidate, String phone, String roleCode) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        User user = userRepository.save(User.builder()
                .email(candidate.email())
                .username(candidate.username())
                .passwordHash(passwordService.hash(DEFAULT_USER_PASSWORD))
                .firstName("Existing")
                .lastName("User")
                .phone(phone)
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .emailVerifiedAt(now)
                .build());

        if (roleCode != null) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.save(pos.pos.user.entity.UserRole.builder()
                    .userId(user.getId())
                    .roleId(role.getId())
                    .build());
        }

        return user;
    }

    private RegistrationCandidate nextCandidate(String label, String phone) {
        String suffix = label + "-" + userSequence.getAndIncrement();
        return new RegistrationCandidate(
                "user." + suffix + "@pos.example",
                "user." + suffix,
                phone
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

    private record RegistrationCandidate(String email, String username, String phone) {
    }
}
