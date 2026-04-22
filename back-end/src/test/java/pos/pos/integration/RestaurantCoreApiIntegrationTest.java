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
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.security.service.PasswordService;
import pos.pos.support.TestPostgresContainerSupport;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
@DisplayName("Restaurant core API integration test")
class RestaurantCoreApiIntegrationTest {

    private static final String SCHEMA = "restaurant_core_" + UUID.randomUUID().toString().replace("-", "");
    private static final String ADMIN_EMAIL = "prod.admin@pos.example";
    private static final String ADMIN_USERNAME = "prodadmin";
    private static final String ADMIN_PASSWORD = "StrongPass123!";
    private static final String OWNER_EMAIL = "owner.one@pos.example";
    private static final String OWNER_USERNAME = "owner.one";
    private static final String OWNER_PASSWORD = "OwnerPass123!";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        TestPostgresContainerSupport.registerProdDatabaseProperties(registry, SCHEMA);
        registry.add("JWT_SECRET", () -> "restaurant-core-test-secret-key-for-hs256");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "restaurant-core-test-refresh-token-pepper");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "restaurant-core-test-password-reset-pepper");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "restaurant-core-test-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "restaurant-core-test-sms-pepper");
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
    private RestaurantRepository restaurantRepository;

    @Autowired
    private PasswordService passwordService;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void muteMailSender() {
        doNothing().when(javaMailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Should manage restaurant core endpoints end to end")
    void shouldManageRestaurantCoreEndpointsEndToEnd() throws Exception {
        assertThat(List.of(environment.getActiveProfiles())).containsExactly("prod");
        Integer migrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '7'",
                Integer.class
        );
        assertThat(migrationCount).isEqualTo(1);

        User admin = userRepository.findByEmailAndDeletedAtIsNull(ADMIN_EMAIL).orElseThrow();
        Role ownerRole = roleRepository.findByCode("OWNER").orElseThrow();
        User owner = createUser(admin.getId(), OWNER_EMAIL, OWNER_USERNAME, OWNER_PASSWORD);
        assignRole(owner, ownerRole, admin.getId());

        String adminAccessToken = bodyOf(webLogin(ADMIN_USERNAME, ADMIN_PASSWORD)).get("accessToken").asText();

        JsonNode createdRestaurant = bodyOf(mockMvc.perform(post("/restaurants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Main Restaurant",
                                "legalName", "Main Restaurant LLC",
                                "currency", "USD",
                                "timezone", "Europe/Berlin",
                                "ownerUserId", owner.getId().toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn());

        UUID restaurantId = UUID.fromString(createdRestaurant.get("id").asText());
        assertThat(createdRestaurant.get("code").asText()).isEqualTo("MAIN_RESTAURANT");
        assertThat(createdRestaurant.get("slug").asText()).isEqualTo("main-restaurant");
        assertThat(createdRestaurant.get("ownerUserId").asText()).isEqualTo(owner.getId().toString());

        JsonNode restaurantsPage = bodyOf(mockMvc.perform(get("/restaurants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .param("search", "main-rest"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(restaurantsPage.get("items")).hasSize(1);
        assertThat(restaurantsPage.get("items").get(0).get("id").asText()).isEqualTo(restaurantId.toString());

        String ownerAccessToken = bodyOf(webLogin(OWNER_USERNAME, OWNER_PASSWORD)).get("accessToken").asText();

        JsonNode ownerVisibleRestaurants = bodyOf(mockMvc.perform(get("/restaurants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAccessToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(ownerVisibleRestaurants.get("items")).hasSize(1);
        assertThat(ownerVisibleRestaurants.get("items").get(0).get("id").asText()).isEqualTo(restaurantId.toString());

        JsonNode ownerRestaurant = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAccessToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(ownerRestaurant.get("name").asText()).isEqualTo("Main Restaurant");

        JsonNode updatedRestaurant = bodyOf(mockMvc.perform(put("/restaurants/{restaurantId}", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Main Restaurant Updated",
                                "legalName", "Main Restaurant Updated LLC",
                                "code", "MAIN_RESTAURANT_UPDATED",
                                "slug", "main-restaurant-updated",
                                "currency", "USD",
                                "timezone", "Europe/Berlin",
                                "ownerUserId", owner.getId().toString(),
                                "isActive", true,
                                "status", "ACTIVE"
                        ))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(updatedRestaurant.get("name").asText()).isEqualTo("Main Restaurant Updated");

        mockMvc.perform(put("/restaurants/{restaurantId}", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Main Restaurant Updated",
                                "legalName", "Main Restaurant Updated LLC",
                                "code", "MAIN_RESTAURANT_UPDATED",
                                "slug", "main-restaurant-updated",
                                "currency", "USD",
                                "timezone", "Europe/Berlin",
                                "isActive", true,
                                "status", "ACTIVE"
                        ))))
                .andExpect(status().isForbidden());

        JsonNode suspendedRestaurant = bodyOf(mockMvc.perform(patch("/restaurants/{restaurantId}/status", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "isActive", false,
                                "status", "SUSPENDED"
                        ))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(suspendedRestaurant.get("isActive").asBoolean()).isFalse();
        assertThat(suspendedRestaurant.get("status").asText()).isEqualTo("SUSPENDED");

        mockMvc.perform(delete("/restaurants/{restaurantId}", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isNoContent());

        Restaurant deletedRestaurant = restaurantRepository.findById(restaurantId).orElseThrow();
        assertThat(deletedRestaurant.getDeletedAt()).isNotNull();
        assertThat(deletedRestaurant.getStatus()).isEqualTo(pos.pos.restaurant.enums.RestaurantStatus.ARCHIVED);
        assertThat(deletedRestaurant.isActive()).isFalse();
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

    private User createUser(UUID createdBy, String email, String username, String password) {
        User user = User.builder()
                .email(email)
                .username(username)
                .passwordHash(passwordService.hash(password))
                .firstName("Owner")
                .lastName("User")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .phoneVerified(false)
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
