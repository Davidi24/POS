package pos.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.service.PasswordService;
import pos.pos.support.TestPostgresContainerSupport;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("User admin API integration test")
class UserAdminApiIntegrationTest {

    private static final String SCHEMA = "user_admin_" + UUID.randomUUID().toString().replace("-", "");
    private static final String ADMIN_EMAIL = "prod.admin@pos.example";
    private static final String ADMIN_USERNAME = "prodadmin";
    private static final String ADMIN_PASSWORD = "StrongPass123!";
    private static final String TARGET_EMAIL = "waiter.one@pos.example";
    private static final String TARGET_USERNAME = "waiter.one";
    private static final String TARGET_PASSWORD = "WaiterPass123!";
    private static final String TARGET_PHONE = "+1555010011";
    private static final String UPDATED_PHONE = "+1555010099";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        TestPostgresContainerSupport.registerProdDatabaseProperties(registry, SCHEMA);
        registry.add("JWT_SECRET", () -> "user-admin-test-secret-key-for-hs256");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "user-admin-test-refresh-token-pepper");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "user-admin-test-password-reset-pepper");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "user-admin-test-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "user-admin-test-sms-pepper");
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
        registry.add("BOOTSTRAP_SUPER_ADMIN_FIRST_NAME", () -> "Prod");
        registry.add("BOOTSTRAP_SUPER_ADMIN_LAST_NAME", () -> "Admin");
        registry.add("SMS_DELIVERY_MODE", () -> "LOG_ONLY");
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
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private PasswordService passwordService;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void muteMailSender() {
        doNothing().when(javaMailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Should manage users end to end through the new /users APIs")
    void shouldManageUsersEndToEnd() throws Exception {
        assertThat(List.of(environment.getActiveProfiles())).containsExactly("prod");
        Integer migrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1'",
                Integer.class
        );
        assertThat(migrationCount).isEqualTo(1);

        User admin = userRepository.findByEmailAndDeletedAtIsNull(ADMIN_EMAIL).orElseThrow();
        Role waiterRole = roleRepository.findByCode("WAITER").orElseThrow();
        Role managerRole = roleRepository.findByCode("MANAGER").orElseThrow();
        User target = createUser(admin.getId(), TARGET_EMAIL, TARGET_USERNAME, TARGET_PASSWORD, TARGET_PHONE);
        assignRole(target, waiterRole, admin.getId());

        String adminAccessToken = bodyOf(webLogin(ADMIN_USERNAME, ADMIN_PASSWORD)).get("accessToken").asText();

        JsonNode usersPage = bodyOf(mockMvc.perform(get("/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .param("search", TARGET_USERNAME)
                        .param("roleCode", "WAITER"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(usersPage.get("items")).hasSize(1);
        assertThat(usersPage.get("items").get(0).get("id").asText()).isEqualTo(target.getId().toString());
        assertThat(usersPage.get("items").get(0).get("roles").get(0).asText()).isEqualTo("WAITER");

        JsonNode userDetails = bodyOf(mockMvc.perform(get("/users/{userId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(userDetails.get("username").asText()).isEqualTo(TARGET_USERNAME);
        assertThat(userDetails.get("roles").get(0).asText()).isEqualTo("WAITER");

        JsonNode userRoles = bodyOf(mockMvc.perform(get("/users/{userId}/roles", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(userRoles).hasSize(1);
        assertThat(userRoles.get(0).get("code").asText()).isEqualTo("WAITER");

        JsonNode replaceRolesResponse = bodyOf(mockMvc.perform(put("/users/{userId}/roles", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roleIds", List.of(managerRole.getId().toString())
                        ))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(replaceRolesResponse.get("roles")).hasSize(1);
        assertThat(replaceRolesResponse.get("roles").get(0).asText()).isEqualTo("MANAGER");
        assertThat(roleRepository.findActiveRoleCodesByUserId(target.getId())).containsExactly("MANAGER");

        webLogin(TARGET_USERNAME, TARGET_PASSWORD);
        UserSession targetSession = userSessionRepository.findActiveSessionsByUserId(target.getId(), OffsetDateTime.now(ZoneOffset.UTC))
                .stream()
                .findFirst()
                .orElseThrow();

        mockMvc.perform(delete("/users/{userId}/sessions/{sessionId}", target.getId(), targetSession.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isNoContent());

        UserSession revokedSession = userSessionRepository.findById(targetSession.getId()).orElseThrow();
        assertThat(revokedSession.isRevoked()).isTrue();

        JsonNode updatedUser = bodyOf(mockMvc.perform(put("/users/{userId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Updated",
                                "lastName", "User",
                                "phone", UPDATED_PHONE,
                                "isActive", false
                        ))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(updatedUser.get("firstName").asText()).isEqualTo("Updated");
        assertThat(updatedUser.get("phone").asText()).isEqualTo(UPDATED_PHONE);
        assertThat(updatedUser.get("isActive").asBoolean()).isFalse();

        User afterUpdate = userRepository.findById(target.getId()).orElseThrow();
        assertThat(afterUpdate.getPhone()).isEqualTo(UPDATED_PHONE);
        assertThat(afterUpdate.isActive()).isFalse();
        assertThat(afterUpdate.isPhoneVerified()).isFalse();
        assertThat(afterUpdate.getPhoneVerifiedAt()).isNull();

        mockMvc.perform(delete("/users/{userId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isNoContent());

        User deletedUser = userRepository.findById(target.getId()).orElseThrow();
        assertThat(deletedUser.getDeletedAt()).isNotNull();
        assertThat(deletedUser.getStatus()).isEqualTo("DELETED");
        assertThat(deletedUser.isActive()).isFalse();

        mockMvc.perform(post("/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", TARGET_EMAIL,
                                "username", TARGET_USERNAME,
                                "temporaryPassword", "WaiterPass456!",
                                "firstName", "Replacement",
                                "lastName", "User",
                                "phone", TARGET_PHONE,
                                "roleId", waiterRole.getId().toString(),
                                "clientTarget", "UNIVERSAL"
                        ))))
                .andExpect(status().isCreated());

        User recreatedUser = userRepository.findByEmailAndDeletedAtIsNull(TARGET_EMAIL).orElseThrow();
        assertThat(recreatedUser.getId()).isNotEqualTo(target.getId());
        Integer duplicateEmailCount = jdbcTemplate.queryForObject(
                "select count(*) from users where email = ?",
                Integer.class,
                TARGET_EMAIL
        );
        assertThat(duplicateEmailCount).isEqualTo(2);
    }

    private MvcResult webLogin(String identifier, String password) throws Exception {
        return mockMvc.perform(post("/auth/web/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", identifier,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private User createUser(UUID createdBy, String email, String username, String password, String phone) {
        User user = User.builder()
                .email(email)
                .username(username)
                .passwordHash(passwordService.hash(password))
                .firstName("Waiter")
                .lastName("User")
                .phone(phone)
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .phoneVerified(true)
                .phoneVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();

        return userRepository.save(user);
    }

    private void assignRole(User user, Role role, UUID actorId) {
        userRoleRepository.save(UserRole.builder()
                .userId(user.getId())
                .roleId(role.getId())
                .assignedBy(actorId)
                .build());
    }
}
