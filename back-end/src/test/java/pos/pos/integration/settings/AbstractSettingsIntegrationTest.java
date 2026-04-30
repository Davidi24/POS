package pos.pos.integration.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.BranchStatus;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.service.PasswordService;
import pos.pos.settings.repository.SettingsRepository;
import pos.pos.support.TestPostgresContainerSupport;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AbstractSettingsIntegrationTest {

    protected static final String SCHEMA = "settings_core_" + UUID.randomUUID().toString().replace("-", "");
    protected static final String SUPER_ADMIN_EMAIL = "settings.super.admin@pos.example";
    protected static final String SUPER_ADMIN_USERNAME = "settingssuperadmin";
    protected static final String SUPER_ADMIN_PASSWORD = "StrongPass123!";
    protected static final String DEFAULT_PASSWORD = "StrongPass123!";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        TestPostgresContainerSupport.registerProdDatabaseProperties(registry, SCHEMA);
        registry.add("JWT_SECRET", () -> "settings-core-test-secret-key-for-hs256-123456");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "settings-core-refresh-token-pepper-0123456789");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "settings-core-password-reset-pepper");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "settings-core-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "settings-core-sms-code-pepper-value");
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
        registry.add("BOOTSTRAP_SUPER_ADMIN_FIRST_NAME", () -> "Settings");
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
    protected RestaurantRepository restaurantRepository;

    @Autowired
    protected BranchRepository branchRepository;

    @Autowired
    protected SettingsRepository settingsRepository;

    @Autowired
    protected UserSessionRepository userSessionRepository;

    @Autowired
    protected AuthLoginAttemptRepository authLoginAttemptRepository;

    @Autowired
    protected PasswordService passwordService;

    @MockBean
    protected JavaMailSender javaMailSender;

    private final AtomicInteger restaurantSequence = new AtomicInteger(1);
    private final AtomicInteger branchSequence = new AtomicInteger(1);
    private final AtomicInteger userSequence = new AtomicInteger(1);
    private final AtomicInteger ipSequence = new AtomicInteger(200);

    @BeforeEach
    void resetAuthState() {
        doNothing().when(javaMailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
        authLoginAttemptRepository.deleteAllInBatch();
        userSessionRepository.deleteAllInBatch();
    }

    protected User superAdminUser() {
        return userRepository.findByEmailAndDeletedAtIsNull(SUPER_ADMIN_EMAIL).orElseThrow();
    }

    protected Role role(String code) {
        return roleRepository.findByCode(code).orElseThrow();
    }

    protected Restaurant createRestaurant(String label) {
        int sequence = restaurantSequence.getAndIncrement();
        UUID actorId = superAdminUser().getId();

        Restaurant restaurant = new Restaurant();
        restaurant.setName("Restaurant " + label + " " + sequence);
        restaurant.setLegalName("Restaurant Legal " + label + " " + sequence);
        restaurant.setCode(("REST_" + label + "_" + sequence).toUpperCase());
        restaurant.setSlug(("rest-" + label + "-" + sequence).toLowerCase());
        restaurant.setCurrency("USD");
        restaurant.setTimezone("Europe/Berlin");
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        restaurant.setOwnerId(actorId);
        restaurant.setActive(true);
        restaurant.setCreatedBy(actorId);
        restaurant.setUpdatedBy(actorId);
        return restaurantRepository.save(restaurant);
    }

    protected Branch createBranch(Restaurant restaurant, String label) {
        int sequence = branchSequence.getAndIncrement();
        UUID actorId = superAdminUser().getId();

        Branch branch = new Branch();
        branch.setRestaurant(restaurant);
        branch.setName("Branch " + label + " " + sequence);
        branch.setCode(("BR_" + label + "_" + sequence).toUpperCase());
        branch.setStatus(BranchStatus.ACTIVE);
        branch.setActive(true);
        branch.setCreatedBy(actorId);
        branch.setUpdatedBy(actorId);
        return branchRepository.save(branch);
    }

    protected User createRestaurantAdmin(Restaurant restaurant, String label) {
        int sequence = userSequence.getAndIncrement();
        UUID actorId = superAdminUser().getId();

        User user = userRepository.save(User.builder()
                .email("settings.admin." + label + "." + sequence + "@pos.example")
                .username("settings.admin." + label + "." + sequence)
                .passwordHash(passwordService.hash(DEFAULT_PASSWORD))
                .restaurantId(restaurant.getId())
                .firstName("Settings")
                .lastName("Admin")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .emailVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .createdBy(actorId)
                .updatedBy(actorId)
                .build());

        userRoleRepository.save(UserRole.builder()
                .userId(user.getId())
                .roleId(role("ADMIN").getId())
                .assignedBy(actorId)
                .build());

        return user;
    }

    protected String accessTokenFor(User user, String userAgent) throws Exception {
        return webLogin(user.getUsername(), DEFAULT_PASSWORD, nextIp(), userAgent, status().isOk());
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
