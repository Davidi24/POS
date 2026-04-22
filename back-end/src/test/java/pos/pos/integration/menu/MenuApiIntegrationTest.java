package pos.pos.integration.menu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.support.TestPostgresContainerSupport;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Transactional
@DisplayName("Menu API integration test")
class MenuApiIntegrationTest {

    private static final String SCHEMA = "menu_api_" + UUID.randomUUID().toString().replace("-", "");
    private static final String ADMIN_EMAIL = "menu.admin@pos.example";
    private static final String ADMIN_USERNAME = "menuadmin";
    private static final String ADMIN_PASSWORD = "StrongPass123!";
    private static final String DEFAULT_PASSWORD = "StrongPass123!";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        TestPostgresContainerSupport.registerProdDatabaseProperties(registry, SCHEMA);
        registry.add("JWT_SECRET", () -> "menu-api-test-secret-key-for-hs256-123456");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "menu-api-refresh-token-pepper-0123456789");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "menu-api-password-reset-pepper");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "menu-api-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "menu-api-sms-code-pepper");
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
        registry.add("BOOTSTRAP_SUPER_ADMIN_FIRST_NAME", () -> "Menu");
        registry.add("BOOTSTRAP_SUPER_ADMIN_LAST_NAME", () -> "Admin");
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
    private RestaurantRepository restaurantRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private MenuSectionRepository menuSectionRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final AtomicInteger userSequence = new AtomicInteger(1);
    private final AtomicInteger restaurantSequence = new AtomicInteger(1);
    private final AtomicInteger ipSequence = new AtomicInteger(200);

    @Test
    @DisplayName("Menu OpenAPI group exposes the menu endpoints")
    void shouldExposeMenuEndpointsInOpenApiGroup() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs/Menus"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        JsonNode paths = body.get("paths");
        assertThat(paths.has("/menus")).isTrue();
        assertThat(paths.has("/menus/{menuId}")).isTrue();
        assertThat(paths.has("/menus/{menuId}/status")).isTrue();
    }

    @Test
    @DisplayName("MENU-001, MENU-003, and MENU-004 list menus and return expanded menu details")
    void shouldListMenusAndReturnExpandedDetail() throws Exception {
        User admin = adminUser();
        Restaurant alpha = createRestaurant("alpha", admin.getId());
        Restaurant beta = createRestaurant("beta", admin.getId());

        Menu breakfast = createMenu(alpha, "breakfast", "Breakfast Menu", true, 1, admin.getId());
        createMenu(alpha, "late_night", "Late Night", false, 2, admin.getId());
        createMenu(beta, "dinner", "Dinner Menu", true, 1, admin.getId());

        MenuSection section = createSection(breakfast, "Mains", "Main dishes", true, 1);
        createItem(section, "BRG-001", "House Burger", new BigDecimal("12.50"), true, 1);

        String accessToken = accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "MENU-LIST");

        MvcResult listResult = mockMvc.perform(get("/menus")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("restaurantId", alpha.getId().toString())
                        .param("active", "true")
                        .param("search", "breakfast")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode listBody = bodyOf(listResult);
        assertThat(listBody.get("page").asInt()).isZero();
        assertThat(listBody.get("size").asInt()).isEqualTo(10);
        assertThat(listBody.get("totalElements").asInt()).isEqualTo(1);
        assertThat(listBody.get("items")).hasSize(1);
        assertThat(listBody.get("items").get(0).get("id").asText()).isEqualTo(breakfast.getId().toString());
        assertThat(listBody.get("items").get(0).get("restaurant").get("id").asText()).isEqualTo(alpha.getId().toString());
        assertThat(listBody.get("items").get(0).get("code").asText()).isEqualTo("BREAKFAST");

        MvcResult detailResult = mockMvc.perform(get("/menus/{menuId}", breakfast.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("includeSections", "true")
                        .param("includeItems", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode detailBody = bodyOf(detailResult);
        assertThat(detailBody.get("id").asText()).isEqualTo(breakfast.getId().toString());
        assertThat(detailBody.get("sections")).hasSize(1);
        assertThat(detailBody.get("sections").get(0).get("name").asText()).isEqualTo("Mains");
        assertThat(detailBody.get("sections").get(0).get("items")).hasSize(1);
        assertThat(detailBody.get("sections").get(0).get("items").get(0).get("name").asText()).isEqualTo("House Burger");
    }

    @Test
    @DisplayName("MENU-002, MENU-008, and MENU-010 create menus with derived code and persisted audit fields")
    void shouldCreateMenuWithDerivedCodeAndAuditFields() throws Exception {
        User admin = adminUser();
        Restaurant restaurant = createRestaurant("create", admin.getId());
        String accessToken = accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "MENU-CREATE");

        MvcResult result = mockMvc.perform(post("/menus")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "restaurantId", restaurant.getId().toString(),
                                "name", "Lunch Specials",
                                "description", " Midday menu "
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = bodyOf(result);
        UUID menuId = UUID.fromString(body.get("id").asText());
        Menu stored = menuRepository.findById(menuId).orElseThrow();

        assertThat(body.get("code").asText()).isEqualTo("LUNCH_SPECIALS");
        assertThat(stored.getCode()).isEqualTo("LUNCH_SPECIALS");
        assertThat(stored.getDisplayOrder()).isZero();
        assertThat(stored.getCreatedBy()).isEqualTo(admin.getId());
        assertThat(stored.getUpdatedBy()).isEqualTo(admin.getId());
    }

    @Test
    @DisplayName("MENU-005, MENU-006, and MENU-009 update menus, patch status, and reject duplicate codes")
    void shouldUpdatePatchStatusAndRejectDuplicateCodes() throws Exception {
        User admin = adminUser();
        User manager = createUser("manager", role("MANAGER"));
        Restaurant restaurant = createRestaurant("update", admin.getId());
        Menu breakfast = createMenu(restaurant, "breakfast", "Breakfast", true, 1, admin.getId());
        Menu lunch = createMenu(restaurant, "lunch", "Lunch", true, 2, admin.getId());

        String managerAccessToken = accessTokenFor(manager.getUsername(), DEFAULT_PASSWORD, "MENU-MANAGER");

        MvcResult updateResult = mockMvc.perform(put("/menus/{menuId}", lunch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", " brunch ",
                                "name", " Brunch ",
                                "description", " Weekend menu ",
                                "active", true,
                                "displayOrder", 5
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode updateBody = bodyOf(updateResult);
        assertThat(updateBody.get("code").asText()).isEqualTo("BRUNCH");
        assertThat(updateBody.get("name").asText()).isEqualTo("Brunch");
        assertThat(updateBody.get("updatedBy").asText()).isEqualTo(manager.getId().toString());

        MvcResult statusResult = mockMvc.perform(patch("/menus/{menuId}/status", lunch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(statusResult).get("active").asBoolean()).isFalse();
        assertThat(menuRepository.findById(lunch.getId()).orElseThrow().isActive()).isFalse();

        MvcResult duplicateCreateResult = mockMvc.perform(post("/menus")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "restaurantId", restaurant.getId().toString(),
                                "code", " breakfast ",
                                "name", "Breakfast Clone",
                                "active", true,
                                "displayOrder", 3
                        ))))
                .andExpect(status().isConflict())
                .andReturn();

        MvcResult duplicateUpdateResult = mockMvc.perform(put("/menus/{menuId}", lunch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", " breakfast ",
                                "name", "Conflicting Lunch",
                                "description", "conflict",
                                "active", true,
                                "displayOrder", 6
                        ))))
                .andExpect(status().isConflict())
                .andReturn();

        assertThat(messageOf(duplicateCreateResult)).isEqualTo("Menu code already in use for this restaurant");
        assertThat(messageOf(duplicateUpdateResult)).isEqualTo("Menu code already in use for this restaurant");
        assertThat(menuRepository.findById(breakfast.getId()).orElseThrow().getCode()).isEqualTo("BREAKFAST");
    }

    @Test
    @DisplayName("MENU-007 deletes menus without dependents and rejects delete when sections exist")
    void shouldDeleteMenusWithoutDependentsAndRejectDeleteWhenSectionsExist() throws Exception {
        User admin = adminUser();
        Restaurant restaurant = createRestaurant("delete", admin.getId());
        Menu blocked = createMenu(restaurant, "blocked", "Blocked Menu", true, 1, admin.getId());
        Menu clear = createMenu(restaurant, "clear", "Clear Menu", true, 2, admin.getId());
        createSection(blocked, "Mains", "Main dishes", true, 1);

        String accessToken = accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "MENU-DELETE");

        MvcResult blockedDelete = mockMvc.perform(delete("/menus/{menuId}", blocked.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isConflict())
                .andReturn();

        mockMvc.perform(delete("/menus/{menuId}", clear.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNoContent());

        assertThat(messageOf(blockedDelete)).isEqualTo("Menu cannot be deleted while it still has sections");
        assertThat(menuRepository.findById(blocked.getId())).isPresent();
        assertThat(menuRepository.findById(clear.getId())).isEmpty();
    }

    private User adminUser() {
        return userRepository.findByEmailAndDeletedAtIsNull(ADMIN_EMAIL).orElseThrow();
    }

    private Role role(String code) {
        return roleRepository.findByCode(code).orElseThrow();
    }

    private User createUser(String label, Role... roles) {
        int sequence = userSequence.getAndIncrement();
        UUID adminId = adminUser().getId();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        User user = userRepository.save(User.builder()
                .email("menu." + label + "." + sequence + "@pos.example")
                .username("menu." + label + "." + sequence)
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .firstName("Menu")
                .lastName("Manager")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .emailVerifiedAt(now)
                .createdBy(adminId)
                .updatedBy(adminId)
                .build());

        for (Role role : roles) {
            userRoleRepository.save(UserRole.builder()
                    .userId(user.getId())
                    .roleId(role.getId())
                    .assignedBy(adminId)
                    .build());
        }

        return user;
    }

    private Restaurant createRestaurant(String label, UUID actorId) {
        int sequence = restaurantSequence.getAndIncrement();
        Restaurant restaurant = new Restaurant();
        restaurant.setName("Restaurant " + label + " " + sequence);
        restaurant.setLegalName("Restaurant " + label + " " + sequence + " LLC");
        restaurant.setCode("restaurant_" + label + "_" + sequence);
        restaurant.setSlug("restaurant-" + label + "-" + sequence);
        restaurant.setDescription("Integration test restaurant");
        restaurant.setCurrency("USD");
        restaurant.setTimezone("Europe/Berlin");
        restaurant.setCreatedBy(actorId);
        restaurant.setUpdatedBy(actorId);
        return restaurantRepository.save(restaurant);
    }

    private Menu createMenu(Restaurant restaurant, String code, String name, boolean active, int displayOrder, UUID actorId) {
        Menu menu = new Menu();
        menu.setRestaurant(restaurant);
        menu.setCode(code);
        menu.setName(name);
        menu.setDescription(name + " description");
        menu.setActive(active);
        menu.setDisplayOrder(displayOrder);
        menu.setCreatedBy(actorId);
        menu.setUpdatedBy(actorId);
        return menuRepository.save(menu);
    }

    private MenuSection createSection(Menu menu, String name, String description, boolean active, int displayOrder) {
        MenuSection section = new MenuSection();
        section.setMenu(menu);
        section.setName(name);
        section.setDescription(description);
        section.setActive(active);
        section.setDisplayOrder(displayOrder);
        return menuSectionRepository.save(section);
    }

    private MenuItem createItem(MenuSection section, String sku, String name, BigDecimal basePrice, boolean available, int displayOrder) {
        MenuItem item = new MenuItem();
        item.setSection(section);
        item.setSku(sku);
        item.setName(name);
        item.setDescription(name + " description");
        item.setBasePrice(basePrice);
        item.setAvailable(available);
        item.setDisplayOrder(displayOrder);
        return menuItemRepository.save(item);
    }

    private String accessTokenFor(String identifier, String password, String userAgent) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/web/login")
                        .header(HttpHeaders.USER_AGENT, userAgent)
                        .header("X-Forwarded-For", nextIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", identifier,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        return bodyOf(result).get("accessToken").asText();
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String messageOf(MvcResult result) throws Exception {
        return bodyOf(result).get("message").asText();
    }

    private String nextIp() {
        return UriComponentsBuilder.newInstance()
                .scheme("https")
                .host("198.51.100." + ipSequence.getAndIncrement())
                .build()
                .getHost();
    }
}
