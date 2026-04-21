package pos.pos.integration.role;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AbstractRoleIntegrationTest {

    protected static final String ADMIN_EMAIL = "role.admin@pos.example";
    protected static final String ADMIN_USERNAME = "roleadmin";
    protected static final String ADMIN_PASSWORD = "StrongPass123!";
    protected static final String DEFAULT_PASSWORD = "StrongPass123!";

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
    protected PermissionRepository permissionRepository;

    @Autowired
    protected RolePermissionRepository rolePermissionRepository;

    @Autowired
    protected UserSessionRepository userSessionRepository;

    @Autowired
    protected AuthLoginAttemptRepository authLoginAttemptRepository;

    @Autowired
    protected PasswordService passwordService;

    private final AtomicInteger roleSequence = new AtomicInteger(1);
    private final AtomicInteger userSequence = new AtomicInteger(1);
    private final AtomicInteger ipSequence = new AtomicInteger(200);

    static void registerProdProperties(DynamicPropertyRegistry registry, String schema) {
        TestPostgresContainerSupport.registerProdDatabaseProperties(registry, schema);
        registry.add("JWT_SECRET", () -> schema + "-jwt-secret-key-for-hs256-123456");
        registry.add("REFRESH_TOKEN_PEPPER", () -> schema + "-refresh-token-pepper-0123456789");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> schema + "-password-reset-pepper");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> schema + "-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> schema + "-sms-code-pepper");
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
        registry.add("BOOTSTRAP_SUPER_ADMIN_FIRST_NAME", () -> "Role");
        registry.add("BOOTSTRAP_SUPER_ADMIN_LAST_NAME", () -> "Admin");
        registry.add("SMS_DELIVERY_MODE", () -> "LOG_ONLY");
    }

    @BeforeEach
    void resetAuthState() {
        authLoginAttemptRepository.deleteAllInBatch();
        userSessionRepository.deleteAllInBatch();
    }

    protected User adminUser() {
        return userRepository.findByEmailAndDeletedAtIsNull(ADMIN_EMAIL).orElseThrow();
    }

    protected Role role(String code) {
        return roleRepository.findByCode(code).orElseThrow();
    }

    protected Permission permission(String code) {
        return permissionRepository.findByCode(code).orElseThrow();
    }

    protected Role createRole(
            String code,
            String name,
            String description,
            long rank,
            boolean system,
            boolean active,
            boolean assignable,
            boolean protectedRole
    ) {
        return roleRepository.save(Role.builder()
                .code(code)
                .name(name)
                .description(description)
                .rank(rank)
                .isSystem(system)
                .isActive(active)
                .assignable(assignable)
                .protectedRole(protectedRole)
                .build());
    }

    protected Role createRole(
            String label,
            long rank,
            boolean system,
            boolean active,
            boolean assignable,
            boolean protectedRole
    ) {
        int sequence = roleSequence.getAndIncrement();
        String normalizedLabel = label.replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT);
        return createRole(
                normalizedLabel + "_" + sequence,
                "Role " + label + " " + sequence,
                "Role integration test role",
                rank,
                system,
                active,
                assignable,
                protectedRole
        );
    }

    protected Role createDeletedRole(
            String code,
            String name,
            String description,
            long rank,
            boolean system,
            boolean assignable,
            boolean protectedRole
    ) {
        Role role = createRole(code, name, description, rank, system, true, assignable, protectedRole);
        role.setActive(false);
        role.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return roleRepository.save(role);
    }

    protected Role createRoleWithPermissions(
            String code,
            String name,
            String description,
            long rank,
            boolean system,
            boolean active,
            boolean assignable,
            boolean protectedRole,
            String... permissionCodes
    ) {
        Role role = createRole(code, name, description, rank, system, active, assignable, protectedRole);
        assignPermissions(role, permissionCodes);
        return role;
    }

    protected void assignPermissions(Role role, String... permissionCodes) {
        Set<UUID> permissionIds = Arrays.stream(permissionCodes)
                .map(this::permission)
                .map(Permission::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        permissionIds.forEach(permissionId -> rolePermissionRepository.save(RolePermission.builder()
                .roleId(role.getId())
                .permissionId(permissionId)
                .build()));
    }

    protected User createUser(String label, Role... roles) {
        int sequence = userSequence.getAndIncrement();
        String suffix = label + "-" + sequence;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID adminId = adminUser().getId();

        User user = userRepository.save(User.builder()
                .email("role." + suffix + "@pos.example")
                .username("role." + suffix)
                .passwordHash(passwordService.hash(DEFAULT_PASSWORD))
                .firstName("Role")
                .lastName("User")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .emailVerifiedAt(now)
                .createdBy(adminId)
                .updatedBy(adminId)
                .build());

        Arrays.stream(roles).forEach(role -> assignRole(user, role, adminId));
        return user;
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
        return accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "ROLE-ADMIN");
    }

    protected String accessTokenFor(User user, String userAgent) throws Exception {
        return accessTokenFor(user.getUsername(), DEFAULT_PASSWORD, userAgent);
    }

    protected String accessTokenFor(String identifier, String password, String userAgent) throws Exception {
        return webLogin(identifier, password, nextIp(), userAgent, status().isOk());
    }

    protected String webLogin(
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

        return bodyOf(result).get("accessToken").asText();
    }

    protected JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected String messageOf(MvcResult result) throws Exception {
        return bodyOf(result).get("message").asText();
    }

    protected List<String> codesOf(JsonNode arrayNode) {
        return java.util.stream.StreamSupport.stream(arrayNode.spliterator(), false)
                .map(node -> node.get("code").asText())
                .toList();
    }

    protected int indexOf(List<String> codes, String code) {
        assertThat(codes).contains(code);
        return codes.indexOf(code);
    }

    protected String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    protected String nextIp() {
        return "198.51.100." + ipSequence.getAndIncrement();
    }

    protected RequestPostProcessor client(String ip, String userAgent) {
        return request -> {
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", ip);
            request.addHeader(HttpHeaders.USER_AGENT, userAgent);
            return request;
        };
    }
}
