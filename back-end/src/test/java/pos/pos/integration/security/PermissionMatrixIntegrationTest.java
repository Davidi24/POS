package pos.pos.integration.security;

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
import pos.pos.role.entity.Permission;
import pos.pos.role.entity.Role;
import pos.pos.role.entity.RolePermission;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.role.repository.RolePermissionRepository;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.service.PasswordService;
import pos.pos.support.TestPostgresContainerSupport;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("Permission matrix integration test")
class PermissionMatrixIntegrationTest {

    private static final String SCHEMA = "permission_matrix_" + UUID.randomUUID().toString().replace("-", "");
    private static final String COOKIE_NAME = "refreshToken";
    private static final String SUPER_ADMIN_EMAIL = "permission.matrix.admin@pos.example";
    private static final String SUPER_ADMIN_USERNAME = "permissionmatrixadmin";
    private static final String SUPER_ADMIN_PASSWORD = "StrongPass123!";
    private static final String DEFAULT_PASSWORD = "StrongPass123!";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        TestPostgresContainerSupport.registerProdDatabaseProperties(registry, SCHEMA);
        registry.add("JWT_SECRET", () -> "permission-matrix-test-secret-key-hs256-123456");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "permission-matrix-refresh-token-pepper-0123456789");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "permission-matrix-password-reset-pepper");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "permission-matrix-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "permission-matrix-sms-code-pepper-value");
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
        registry.add("BOOTSTRAP_SUPER_ADMIN_EMAIL", () -> SUPER_ADMIN_EMAIL);
        registry.add("BOOTSTRAP_SUPER_ADMIN_USERNAME", () -> SUPER_ADMIN_USERNAME);
        registry.add("BOOTSTRAP_SUPER_ADMIN_PASSWORD", () -> SUPER_ADMIN_PASSWORD);
        registry.add("BOOTSTRAP_SUPER_ADMIN_FIRST_NAME", () -> "Permission");
        registry.add("BOOTSTRAP_SUPER_ADMIN_LAST_NAME", () -> "Matrix");
        registry.add("SMS_DELIVERY_MODE", () -> "LOG_ONLY");
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
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private AuthLoginAttemptRepository authLoginAttemptRepository;

    @Autowired
    private PasswordService passwordService;

    @MockBean
    private JavaMailSender javaMailSender;

    private final AtomicInteger userSequence = new AtomicInteger(1);
    private final AtomicInteger roleSequence = new AtomicInteger(1);
    private final AtomicInteger ipSequence = new AtomicInteger(200);

    @BeforeEach
    void resetAuthState() {
        doNothing().when(javaMailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
        authLoginAttemptRepository.deleteAllInBatch();
        userSessionRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("SEC-001 Protected auth endpoints return 401 when unauthenticated")
    void sec001ProtectedAuthEndpointsReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/auth/sessions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/auth/sessions/current"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/auth/sessions/{sessionId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/auth/sessions/others"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SEC-002 User admin endpoints return 403 without required USERS_* or SESSIONS_MANAGE authority")
    void sec002UserAdminEndpointsReturn403WithoutRequiredAuthorities() throws Exception {
        User waiter = createUser("sec002-waiter", role("WAITER"));
        User manager = createUser("sec002-manager", role("MANAGER"));
        User target = createUser("sec002-target", role("WAITER"));

        String waiterAccessToken = accessTokenFor(waiter, "SEC-002-waiter");
        String managerAccessToken = accessTokenFor(manager, "SEC-002-manager");

        webLogin(target.getUsername(), DEFAULT_PASSWORD, nextIp(), "SEC-002-target-session", status().isOk());
        UserSession targetSession = activeSessionsFor(target).getFirst();

        mockMvc.perform(get("/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(waiterAccessToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/users/{userId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(waiterAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateUserPayload("Sec", "Updated", null, true))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/users/{userId}/roles", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(waiterAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roleIds", List.of(role("MANAGER").getId().toString())
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/users/{userId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAccessToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/users/{userId}/sessions", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAccessToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/users/{userId}/sessions/{sessionId}", target.getId(), targetSession.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAccessToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-003 Role admin endpoints return 403 without required ROLES_* authority")
    void sec003RoleAdminEndpointsReturn403WithoutRequiredAuthorities() throws Exception {
        User manager = createUser("sec003-manager", role("MANAGER"));
        Role targetRole = createRole("sec003-target", 12_000L, true, true, false);
        String accessToken = accessTokenFor(manager, "SEC-003-manager");

        mockMvc.perform(post("/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Sec003 Created Role",
                                "description", "should be forbidden"
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/roles/{roleId}", targetRole.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Sec003 Updated Role",
                                "description", "should be forbidden"
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/roles/{roleId}/permissions", targetRole.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "permissionIds", Set.of(permission("USERS_READ").getId().toString())
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/roles/{roleId}/status", targetRole.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("isActive", false))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/roles/{roleId}", targetRole.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/roles/{roleId}/clone", targetRole.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Sec003 Clone Role",
                                "description", "should be forbidden"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-004 Normal admin cannot manage protected roles or higher-rank users")
    void sec004NormalAdminCannotManageProtectedRolesOrHigherRankUsers() throws Exception {
        User admin = createUser("sec004-admin", role("ADMIN"));
        Role protectedRole = createRole("sec004-protected", 5_000L, true, true, true);
        Role higherRankRole = createRole("sec004-higher-rank", 35_000L, true, true, false);
        User higherRankUser = createUser("sec004-target", higherRankRole);
        String accessToken = accessTokenFor(admin, "SEC-004-admin");

        MvcResult protectedRoleResult = mockMvc.perform(put("/roles/{roleId}", protectedRole.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Sec004 Protected Updated",
                                "description", "should be blocked"
                        ))))
                .andExpect(status().isForbidden())
                .andReturn();

        MvcResult higherRankUserResult = mockMvc.perform(put("/users/{userId}", higherRankUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateUserPayload("Sec", "Four", null, true))))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(messageOf(protectedRoleResult)).isEqualTo("You are not allowed to manage this role");
        assertThat(messageOf(higherRankUserResult)).isEqualTo("You are not allowed to manage this user");
    }

    @Test
    @DisplayName("SEC-005 Super admin can perform flows blocked for normal admins")
    void sec005SuperAdminCanPerformFlowsBlockedForNormalAdmins() throws Exception {
        Role protectedRole = createRole("sec005-protected", 5_000L, true, true, true);
        Role higherRankRole = createRole("sec005-higher-rank", 35_000L, true, true, false);
        User higherRankUser = createUser("sec005-target", higherRankRole);
        String superAdminAccessToken = superAdminAccessToken();

        MvcResult roleResult = mockMvc.perform(put("/roles/{roleId}", protectedRole.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(superAdminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Sec005 Protected Updated",
                                "description", "super admin allowed"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult userResult = mockMvc.perform(put("/users/{userId}", higherRankUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(superAdminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateUserPayload("Sec", "Five", "+1555010605", true))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(roleResult).get("name").asText()).isEqualTo("Sec005 Protected Updated");
        assertThat(bodyOf(userResult).get("firstName").asText()).isEqualTo("Sec");
        assertThat(bodyOf(userResult).get("lastName").asText()).isEqualTo("Five");

        Role updatedRole = roleRepository.findById(protectedRole.getId()).orElseThrow();
        User updatedUser = userRepository.findById(higherRankUser.getId()).orElseThrow();
        assertThat(updatedRole.getDescription()).isEqualTo("super admin allowed");
        assertThat(updatedUser.getPhone()).isEqualTo("+1555010605");
    }

    @Test
    @DisplayName("SEC-006 Inactive or deleted roles no longer grant authorities after login or refresh")
    void sec006InactiveOrDeletedRolesNoLongerGrantAuthoritiesAfterLoginOrRefresh() throws Exception {
        Role inactiveRole = createRoleWithPermissions("sec006-inactive", 15_000L, true, true, false, "USERS_READ");
        User loginActor = createUser("sec006-login-actor", inactiveRole);
        inactiveRole.setActive(false);
        roleRepository.save(inactiveRole);

        LoginTokens loginTokens = webLogin(
                loginActor.getUsername(),
                DEFAULT_PASSWORD,
                nextIp(),
                "SEC-006-login",
                status().isOk()
        );

        JsonNode loginBody = bodyOf(loginTokens.result());
        assertThat(asTextList(loginBody.path("user").path("roles"))).isEmpty();
        assertThat(asTextList(loginBody.path("user").path("permissions"))).isEmpty();

        mockMvc.perform(get("/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginTokens.accessToken())))
                .andExpect(status().isForbidden());

        Role deletedRole = createRoleWithPermissions("sec006-deleted", 15_000L, true, true, false, "USERS_READ");
        User refreshActor = createUser("sec006-refresh-actor", deletedRole);

        LoginTokens initialRefreshTokens = webLogin(
                refreshActor.getUsername(),
                DEFAULT_PASSWORD,
                nextIp(),
                "SEC-006-refresh-initial",
                status().isOk()
        );

        mockMvc.perform(get("/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(initialRefreshTokens.accessToken())))
                .andExpect(status().isOk());

        softDeleteRole(deletedRole);

        MvcResult refreshResult = webRefresh(
                initialRefreshTokens.refreshToken(),
                nextIp(),
                "SEC-006-refresh-rotated",
                status().isOk()
        );

        JsonNode refreshBody = bodyOf(refreshResult);
        assertThat(asTextList(refreshBody.path("user").path("roles"))).isEmpty();
        assertThat(asTextList(refreshBody.path("user").path("permissions"))).isEmpty();

        mockMvc.perform(get("/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(refreshBody.get("accessToken").asText())))
                .andExpect(status().isForbidden());
    }

    private User superAdminUser() {
        return userRepository.findByEmailAndDeletedAtIsNull(SUPER_ADMIN_EMAIL).orElseThrow();
    }

    private String superAdminAccessToken() throws Exception {
        return webLogin(
                SUPER_ADMIN_USERNAME,
                SUPER_ADMIN_PASSWORD,
                nextIp(),
                "SEC-super-admin",
                status().isOk()
        ).accessToken();
    }

    private Role role(String code) {
        return roleRepository.findByCode(code).orElseThrow();
    }

    private Permission permission(String code) {
        return permissionRepository.findByCode(code).orElseThrow();
    }

    private Role createRole(String label, long rank, boolean active, boolean assignable, boolean protectedRole) {
        int sequence = roleSequence.getAndIncrement();
        String normalized = label.replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT);
        return roleRepository.save(Role.builder()
                .code(normalized + "_" + sequence)
                .name("Permission Matrix " + label + " " + sequence)
                .description("Permission matrix test role")
                .rank(rank)
                .isSystem(false)
                .isActive(active)
                .assignable(assignable)
                .protectedRole(protectedRole)
                .build());
    }

    private Role createRoleWithPermissions(
            String label,
            long rank,
            boolean active,
            boolean assignable,
            boolean protectedRole,
            String... permissionCodes
    ) {
        Role role = createRole(label, rank, active, assignable, protectedRole);
        for (String permissionCode : permissionCodes) {
            rolePermissionRepository.save(RolePermission.builder()
                    .roleId(role.getId())
                    .permissionId(permission(permissionCode).getId())
                    .build());
        }
        return role;
    }

    private void softDeleteRole(Role role) {
        role.setActive(false);
        role.setAssignable(false);
        role.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        roleRepository.save(role);
    }

    private User createUser(String label, Role... roles) {
        int sequence = userSequence.getAndIncrement();
        String suffix = label + "-" + sequence;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID actorId = superAdminUser().getId();

        User user = userRepository.save(User.builder()
                .email("security." + suffix + "@pos.example")
                .username("security." + suffix)
                .passwordHash(passwordService.hash(DEFAULT_PASSWORD))
                .firstName("Security")
                .lastName("User")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .emailVerifiedAt(now)
                .createdBy(actorId)
                .updatedBy(actorId)
                .build());

        for (Role role : roles) {
            userRoleRepository.save(UserRole.builder()
                    .userId(user.getId())
                    .roleId(role.getId())
                    .assignedBy(actorId)
                    .build());
        }

        return user;
    }

    private List<UserSession> activeSessionsFor(User user) {
        return userSessionRepository.findActiveSessionsByUserId(user.getId(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    private String accessTokenFor(User user, String userAgent) throws Exception {
        return webLogin(user.getUsername(), DEFAULT_PASSWORD, nextIp(), userAgent, status().isOk()).accessToken();
    }

    private LoginTokens webLogin(
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
                extractCookieValue(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)),
                ip,
                result
        );
    }

    private MvcResult webRefresh(
            String refreshToken,
            String ip,
            String userAgent,
            ResultMatcher expectedStatus
    ) throws Exception {
        return mockMvc.perform(post("/auth/web/refresh")
                        .with(client(ip, userAgent))
                        .cookie(refreshCookie(refreshToken)))
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

    private Map<String, Object> updateUserPayload(String firstName, String lastName, String phone, boolean active) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("firstName", firstName);
        payload.put("lastName", lastName);
        payload.put("phone", phone);
        payload.put("isActive", active);
        return payload;
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String messageOf(MvcResult result) throws Exception {
        return bodyOf(result).get("message").asText();
    }

    private List<String> asTextList(JsonNode arrayNode) {
        return arrayNode == null || !arrayNode.isArray()
                ? List.of()
                : java.util.stream.StreamSupport.stream(arrayNode.spliterator(), false)
                  .map(JsonNode::asText)
                  .toList();
    }

    private MockCookie refreshCookie(String refreshToken) {
        return new MockCookie(COOKIE_NAME, refreshToken);
    }

    private String extractCookieValue(String setCookieHeader) {
        if (setCookieHeader == null || setCookieHeader.isBlank()) {
            return null;
        }

        String prefix = COOKIE_NAME + "=";
        int start = setCookieHeader.indexOf(prefix);
        if (start < 0) {
            return null;
        }

        int end = setCookieHeader.indexOf(';', start);
        if (end < 0) {
            end = setCookieHeader.length();
        }

        return setCookieHeader.substring(start + prefix.length(), end);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String nextIp() {
        return "198.51.100." + ipSequence.getAndIncrement();
    }

    private record LoginTokens(String accessToken, String refreshToken, String ip, MvcResult result) {
    }
}
