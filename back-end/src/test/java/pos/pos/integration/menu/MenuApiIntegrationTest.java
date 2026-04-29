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
import pos.pos.menu.entity.MenuItemOptionGroup;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.entity.MenuVariant;
import pos.pos.menu.entity.OptionGroup;
import pos.pos.menu.entity.OptionGroupType;
import pos.pos.menu.repository.MenuItemOptionGroupRepository;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.menu.repository.MenuVariantRepository;
import pos.pos.menu.repository.OptionGroupRepository;
import pos.pos.menu.repository.OptionGroupTypeRepository;
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
        registry.add("SPRINGDOC_API_DOCS_ENABLED", () -> "true");
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
    private MenuVariantRepository menuVariantRepository;

    @Autowired
    private MenuItemOptionGroupRepository menuItemOptionGroupRepository;

    @Autowired
    private OptionGroupRepository optionGroupRepository;

    @Autowired
    private OptionGroupTypeRepository optionGroupTypeRepository;

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
        assertThat(paths.has("/menus/{menuId}/sections/{sectionId}/items/{itemId}/variants")).isTrue();
        assertThat(paths.has("/option-group-types")).isTrue();
        assertThat(paths.has("/public/restaurants/{restaurantId}/menus")).isTrue();
        assertThat(paths.has("/public/restaurants/{restaurantId}/menus/{menuId}")).isTrue();
    }

    @Test
    @DisplayName("Public menu endpoints return active customer-safe data without authentication")
    void shouldExposePublicMenuEndpointsWithoutAuthentication() throws Exception {
        User admin = adminUser();
        Restaurant restaurant = createRestaurant("public", admin.getId());
        Menu breakfast = createMenu(restaurant, "breakfast", "Breakfast Menu", true, 1, admin.getId());
        createMenu(restaurant, "hidden", "Hidden Menu", false, 2, admin.getId());

        MenuSection mains = createSection(breakfast, "Mains", "Main dishes", true, 1);
        createSection(breakfast, "Archived", "Hidden section", false, 2);
        createItem(mains, "BRG-001", "House Burger", new BigDecimal("12.50"), true, 1);
        createItem(mains, "BRG-002", "Sold Out Burger", new BigDecimal("13.50"), false, 2);

        MvcResult listResult = mockMvc.perform(get("/public/restaurants/{restaurantId}/menus", restaurant.getId()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode listBody = bodyOf(listResult);
        assertThat(listBody).hasSize(1);
        assertThat(listBody.get(0).get("id").asText()).isEqualTo(breakfast.getId().toString());
        assertThat(listBody.get(0).has("createdBy")).isFalse();
        assertThat(listBody.get(0).has("active")).isFalse();

        MvcResult detailResult = mockMvc.perform(get("/public/restaurants/{restaurantId}/menus/{menuId}", restaurant.getId(), breakfast.getId()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode detailBody = bodyOf(detailResult);
        assertThat(detailBody.get("sections")).hasSize(1);
        assertThat(detailBody.get("sections").get(0).get("name").asText()).isEqualTo("Mains");
        assertThat(detailBody.get("sections").get(0).get("items")).hasSize(1);
        assertThat(detailBody.get("sections").get(0).get("items").get(0).get("name").asText()).isEqualTo("House Burger");
        assertThat(detailBody.has("createdBy")).isFalse();
        assertThat(detailBody.has("updatedAt")).isFalse();
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
        manager.setRestaurantId(restaurant.getId());
        userRepository.save(manager);
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
    @DisplayName("MENU-011 restricts menu access to the actor restaurant scope")
    void shouldRestrictMenuAccessToActorRestaurantScope() throws Exception {
        User admin = adminUser();
        Restaurant alpha = createRestaurant("scope-alpha", admin.getId());
        Restaurant beta = createRestaurant("scope-beta", admin.getId());
        User manager = createUser("scope-manager", role("MANAGER"));
        manager.setRestaurantId(alpha.getId());
        userRepository.save(manager);

        Menu alphaMenu = createMenu(alpha, "alpha_breakfast", "Alpha Breakfast", true, 1, admin.getId());
        Menu betaMenu = createMenu(beta, "beta_breakfast", "Beta Breakfast", true, 1, admin.getId());
        String accessToken = accessTokenFor(manager.getUsername(), DEFAULT_PASSWORD, "MENU-SCOPE");

        MvcResult listResult = mockMvc.perform(get("/menus")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode listBody = bodyOf(listResult);
        assertThat(listBody.get("totalElements").asInt()).isEqualTo(1);
        assertThat(listBody.get("items")).hasSize(1);
        assertThat(listBody.get("items").get(0).get("id").asText()).isEqualTo(alphaMenu.getId().toString());

        MvcResult foreignListResult = mockMvc.perform(get("/menus")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("restaurantId", beta.getId().toString()))
                .andExpect(status().isForbidden())
                .andReturn();

        MvcResult foreignDetailResult = mockMvc.perform(get("/menus/{menuId}", betaMenu.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isForbidden())
                .andReturn();

        MvcResult foreignStatusResult = mockMvc.perform(patch("/menus/{menuId}/status", betaMenu.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(messageOf(foreignListResult)).isEqualTo("You are not allowed to access this restaurant");
        assertThat(messageOf(foreignDetailResult)).isEqualTo("You are not allowed to access this restaurant");
        assertThat(messageOf(foreignStatusResult)).isEqualTo("You are not allowed to manage this restaurant");
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

    @Test
    @DisplayName("MENU-SECTION-001, MENU-SECTION-002, and MENU-SECTION-003 list sections with filtering and item expansion")
    void shouldListSectionsWithFilteringAndExpansion() throws Exception {
        User admin = adminUser();
        Restaurant restaurant = createRestaurant("sections-list", admin.getId());
        Menu menu = createMenu(restaurant, "breakfast", "Breakfast Menu", true, 1, admin.getId());

        MenuSection mains = createSection(menu, "Mains", "Main dishes", true, 1);
        MenuSection hidden = createSection(menu, "Hidden", "Hidden dishes", false, 2);
        createItem(mains, "BRG-001", "House Burger", new BigDecimal("12.50"), true, 1);
        createItem(hidden, "ARC-001", "Archived Plate", new BigDecimal("8.50"), true, 1);

        String accessToken = accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "MENU-SECTIONS-LIST");

        MvcResult allSectionsResult = mockMvc.perform(get("/menus/{menuId}/sections", menu.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("includeItems", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode allSections = bodyOf(allSectionsResult);
        assertThat(allSections).hasSize(2);
        assertThat(allSections.get(0).get("name").asText()).isEqualTo("Mains");
        assertThat(allSections.get(0).get("items")).hasSize(1);
        assertThat(allSections.get(0).get("items").get(0).get("name").asText()).isEqualTo("House Burger");
        assertThat(allSections.get(1).get("name").asText()).isEqualTo("Hidden");
        assertThat(allSections.get(1).get("items")).hasSize(1);

        MvcResult activeSectionsResult = mockMvc.perform(get("/menus/{menuId}/sections", menu.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode activeSections = bodyOf(activeSectionsResult);
        assertThat(activeSections).hasSize(1);
        assertThat(activeSections.get(0).get("name").asText()).isEqualTo("Mains");
        assertThat(activeSections.get(0).has("items")).isFalse();
    }

    @Test
    @DisplayName("MENU-SECTION-004 through MENU-SECTION-011 create, read, update, validate, and scope sections")
    void shouldCreateReadUpdateValidateAndScopeSections() throws Exception {
        User admin = adminUser();
        Restaurant restaurant = createRestaurant("sections-write", admin.getId());
        Menu breakfast = createMenu(restaurant, "breakfast", "Breakfast Menu", true, 1, admin.getId());
        Menu lunch = createMenu(restaurant, "lunch", "Lunch Menu", true, 2, admin.getId());
        MenuSection otherMenuSection = createSection(lunch, "Sides", "Lunch sides", true, 1);

        String accessToken = accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "MENU-SECTIONS-WRITE");

        MvcResult createResult = mockMvc.perform(post("/menus/{menuId}/sections", breakfast.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", " Brunch Favorites ",
                                "description", " Weekend dishes "
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createBody = bodyOf(createResult);
        UUID createdSectionId = UUID.fromString(createBody.get("id").asText());
        assertThat(createBody.get("name").asText()).isEqualTo("Brunch Favorites");
        assertThat(createBody.get("description").asText()).isEqualTo("Weekend dishes");
        assertThat(createBody.get("active").asBoolean()).isTrue();
        assertThat(createBody.get("displayOrder").asInt()).isZero();

        MenuSection createdSection = menuSectionRepository.findById(createdSectionId).orElseThrow();
        createItem(createdSection, "PAN-001", "Pancakes", new BigDecimal("7.50"), true, 1);

        MvcResult detailResult = mockMvc.perform(get("/menus/{menuId}/sections/{sectionId}", breakfast.getId(), createdSectionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("includeItems", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode detailBody = bodyOf(detailResult);
        assertThat(detailBody.get("name").asText()).isEqualTo("Brunch Favorites");
        assertThat(detailBody.get("items")).hasSize(1);
        assertThat(detailBody.get("items").get(0).get("name").asText()).isEqualTo("Pancakes");

        MvcResult updateResult = mockMvc.perform(put("/menus/{menuId}/sections/{sectionId}", breakfast.getId(), createdSectionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", " Chef Specials ",
                                "description", " Curated plates ",
                                "active", true,
                                "displayOrder", 5
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode updateBody = bodyOf(updateResult);
        assertThat(updateBody.get("name").asText()).isEqualTo("Chef Specials");
        assertThat(updateBody.get("displayOrder").asInt()).isEqualTo(5);

        MvcResult statusResult = mockMvc.perform(patch("/menus/{menuId}/sections/{sectionId}/status", breakfast.getId(), createdSectionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(statusResult).get("active").asBoolean()).isFalse();

        MvcResult invalidDisplayOrderResult = mockMvc.perform(post("/menus/{menuId}/sections", breakfast.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Invalid",
                                "displayOrder", -1
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(messageOf(invalidDisplayOrderResult)).isEqualTo("displayOrder: displayOrder must be greater than or equal to 0");

        MvcResult duplicateCreateResult = mockMvc.perform(post("/menus/{menuId}/sections", breakfast.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", " Chef Specials ",
                                "active", true,
                                "displayOrder", 6
                        ))))
                .andExpect(status().isConflict())
                .andReturn();

        MvcResult mismatchReadResult = mockMvc.perform(get("/menus/{menuId}/sections/{sectionId}", breakfast.getId(), otherMenuSection.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isConflict())
                .andReturn();

        MvcResult mismatchWriteResult = mockMvc.perform(put("/menus/{menuId}/sections/{sectionId}", breakfast.getId(), otherMenuSection.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Wrong Menu",
                                "description", "Mismatch",
                                "active", true,
                                "displayOrder", 1
                        ))))
                .andExpect(status().isConflict())
                .andReturn();

        assertThat(messageOf(duplicateCreateResult)).isEqualTo("Menu section name already in use for this menu");
        assertThat(messageOf(mismatchReadResult)).isEqualTo("Menu section does not belong to this menu");
        assertThat(messageOf(mismatchWriteResult)).isEqualTo("Menu section does not belong to this menu");
    }

    @Test
    @DisplayName("MENU-SECTION-008 deletes clear sections and returns conflict when items still exist")
    void shouldDeleteSectionsAndRejectBlockedDeletes() throws Exception {
        User admin = adminUser();
        Restaurant restaurant = createRestaurant("sections-delete", admin.getId());
        Menu menu = createMenu(restaurant, "breakfast", "Breakfast Menu", true, 1, admin.getId());
        MenuSection blocked = createSection(menu, "Blocked", "Has items", true, 1);
        MenuSection clear = createSection(menu, "Clear", "No items", true, 2);
        createItem(blocked, "BLK-001", "Blocked Item", new BigDecimal("9.50"), true, 1);

        String accessToken = accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "MENU-SECTIONS-DELETE");

        MvcResult blockedDeleteResult = mockMvc.perform(delete("/menus/{menuId}/sections/{sectionId}", menu.getId(), blocked.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isConflict())
                .andReturn();

        mockMvc.perform(delete("/menus/{menuId}/sections/{sectionId}", menu.getId(), clear.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNoContent());

        assertThat(messageOf(blockedDeleteResult)).isEqualTo("Menu section cannot be deleted while it still has items");
        assertThat(menuSectionRepository.findById(blocked.getId())).isPresent();
        assertThat(menuSectionRepository.findById(clear.getId())).isEmpty();
    }

    @Test
    @DisplayName("MENU-ITEM-001 through MENU-ITEM-004 list items with availability filtering and nested expansions")
    void shouldListItemsWithFilteringAndExpansions() throws Exception {
        User admin = adminUser();
        Restaurant restaurant = createRestaurant("items-list", admin.getId());
        Menu menu = createMenu(restaurant, "breakfast", "Breakfast Menu", true, 1, admin.getId());
        MenuSection section = createSection(menu, "Mains", "Main dishes", true, 1);

        MenuItem burger = createItem(section, "BRG-001", "House Burger", new BigDecimal("12.50"), true, 1);
        MenuItem archived = createItem(section, "ARC-001", "Archived Plate", new BigDecimal("9.50"), false, 2);
        createVariant(burger, "Large", "BRG-L", new BigDecimal("2.00"), false, true, 1);
        OptionGroupType type = createOptionGroupType("single", "Single Select");
        OptionGroup sauces = createOptionGroup(restaurant, type, "Sauces", "Choose a sauce", 0, 2, false, true, 1);
        linkOptionGroup(burger, sauces, 1, null, null, null);

        String accessToken = accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "MENU-ITEMS-LIST");

        MvcResult expandedResult = mockMvc.perform(get("/menus/{menuId}/sections/{sectionId}/items", menu.getId(), section.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("includeVariants", "true")
                        .param("includeOptionGroups", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode expandedBody = bodyOf(expandedResult);
        assertThat(expandedBody).hasSize(2);
        assertThat(expandedBody.get(0).get("name").asText()).isEqualTo("House Burger");
        assertThat(expandedBody.get(0).get("variants")).hasSize(1);
        assertThat(expandedBody.get(0).get("variants").get(0).get("name").asText()).isEqualTo("Large");
        assertThat(expandedBody.get(0).get("optionGroups")).hasSize(1);
        assertThat(expandedBody.get(0).get("optionGroups").get(0).get("name").asText()).isEqualTo("Sauces");

        MvcResult availableOnlyResult = mockMvc.perform(get("/menus/{menuId}/sections/{sectionId}/items", menu.getId(), section.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("available", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode availableOnlyBody = bodyOf(availableOnlyResult);
        assertThat(availableOnlyBody).hasSize(1);
        assertThat(availableOnlyBody.get(0).get("id").asText()).isEqualTo(burger.getId().toString());
        assertThat(availableOnlyBody.get(0).has("variants")).isFalse();
        assertThat(availableOnlyBody.get(0).has("optionGroups")).isFalse();
        assertThat(menuItemRepository.findById(archived.getId())).isPresent();
    }

    @Test
    @DisplayName("MENU-ITEM-005 through MENU-ITEM-008 and MENU-ITEM-010 through MENU-ITEM-012 create, update, validate, and scope items")
    void shouldCreateUpdateValidateAndScopeItems() throws Exception {
        User admin = adminUser();
        Restaurant restaurant = createRestaurant("items-write", admin.getId());
        Menu breakfast = createMenu(restaurant, "breakfast", "Breakfast Menu", true, 1, admin.getId());
        Menu lunch = createMenu(restaurant, "lunch", "Lunch Menu", true, 2, admin.getId());
        MenuSection breakfastSection = createSection(breakfast, "Mains", "Breakfast mains", true, 1);
        MenuSection lunchSection = createSection(lunch, "Mains", "Lunch mains", true, 1);
        MenuItem lunchItem = createItem(lunchSection, "LUNCH-001", "Lunch Plate", new BigDecimal("15.00"), true, 1);

        String accessToken = accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "MENU-ITEMS-WRITE");

        MvcResult createResult = mockMvc.perform(post("/menus/{menuId}/sections/{sectionId}/items", breakfast.getId(), breakfastSection.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sku", " brg-001 ",
                                "name", " House Burger ",
                                "description", " Signature burger ",
                                "basePrice", "12.50",
                                "imageUrl", " https://img.example/burger ",
                                "available", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createBody = bodyOf(createResult);
        UUID createdItemId = UUID.fromString(createBody.get("id").asText());
        assertThat(createBody.get("sku").asText()).isEqualTo("BRG-001");
        assertThat(createBody.get("name").asText()).isEqualTo("House Burger");
        assertThat(createBody.get("basePrice").decimalValue()).isEqualByComparingTo("12.50");
        assertThat(createBody.get("available").asBoolean()).isTrue();
        assertThat(createBody.get("displayOrder").asInt()).isZero();

        MvcResult detailResult = mockMvc.perform(get("/menus/{menuId}/sections/{sectionId}/items/{itemId}", breakfast.getId(), breakfastSection.getId(), createdItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(detailResult).get("name").asText()).isEqualTo("House Burger");

        MvcResult updateResult = mockMvc.perform(put("/menus/{menuId}/sections/{sectionId}/items/{itemId}", breakfast.getId(), breakfastSection.getId(), createdItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sku", " brg-002 ",
                                "name", " Double Burger ",
                                "description", " Bigger burger ",
                                "basePrice", "14.75",
                                "imageUrl", " https://img.example/double-burger ",
                                "available", true,
                                "displayOrder", 5
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode updateBody = bodyOf(updateResult);
        assertThat(updateBody.get("sku").asText()).isEqualTo("BRG-002");
        assertThat(updateBody.get("name").asText()).isEqualTo("Double Burger");
        assertThat(updateBody.get("displayOrder").asInt()).isEqualTo(5);

        MvcResult availabilityResult = mockMvc.perform(patch("/menus/{menuId}/sections/{sectionId}/items/{itemId}/availability", breakfast.getId(), breakfastSection.getId(), createdItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("available", false))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(availabilityResult).get("available").asBoolean()).isFalse();

        MvcResult negativePriceResult = mockMvc.perform(post("/menus/{menuId}/sections/{sectionId}/items", breakfast.getId(), breakfastSection.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Invalid Price",
                                "basePrice", "-1.00"
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn();

        MvcResult negativeDisplayOrderResult = mockMvc.perform(put("/menus/{menuId}/sections/{sectionId}/items/{itemId}", breakfast.getId(), breakfastSection.getId(), createdItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Still Invalid",
                                "basePrice", "12.00",
                                "available", true,
                                "displayOrder", -1
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn();

        MvcResult foreignSectionResult = mockMvc.perform(get("/menus/{menuId}/sections/{sectionId}/items", breakfast.getId(), lunchSection.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isConflict())
                .andReturn();

        MvcResult foreignItemWriteResult = mockMvc.perform(put("/menus/{menuId}/sections/{sectionId}/items/{itemId}", breakfast.getId(), breakfastSection.getId(), lunchItem.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Wrong Item",
                                "basePrice", "11.00",
                                "available", true,
                                "displayOrder", 1
                        ))))
                .andExpect(status().isConflict())
                .andReturn();

        assertThat(messageOf(negativePriceResult)).isEqualTo("basePrice: basePrice must be greater than or equal to 0");
        assertThat(messageOf(negativeDisplayOrderResult)).isEqualTo("displayOrder: displayOrder must be greater than or equal to 0");
        assertThat(messageOf(foreignSectionResult)).isEqualTo("Menu section does not belong to this menu");
        assertThat(messageOf(foreignItemWriteResult)).isEqualTo("Menu item does not belong to this section");
    }

    @Test
    @DisplayName("MENU-ITEM-009 deletes clear items and returns conflict when dependents still exist")
    void shouldDeleteItemsAndRejectBlockedDeletes() throws Exception {
        User admin = adminUser();
        Restaurant restaurant = createRestaurant("items-delete", admin.getId());
        Menu menu = createMenu(restaurant, "breakfast", "Breakfast Menu", true, 1, admin.getId());
        MenuSection section = createSection(menu, "Mains", "Main dishes", true, 1);
        MenuItem blocked = createItem(section, "BLK-001", "Blocked Item", new BigDecimal("9.50"), true, 1);
        MenuItem clear = createItem(section, "CLR-001", "Clear Item", new BigDecimal("8.50"), true, 2);
        createVariant(blocked, "Large", "BLK-L", new BigDecimal("1.00"), false, true, 1);

        String accessToken = accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "MENU-ITEMS-DELETE");

        MvcResult blockedDeleteResult = mockMvc.perform(delete("/menus/{menuId}/sections/{sectionId}/items/{itemId}", menu.getId(), section.getId(), blocked.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isConflict())
                .andReturn();

        mockMvc.perform(delete("/menus/{menuId}/sections/{sectionId}/items/{itemId}", menu.getId(), section.getId(), clear.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNoContent());

        assertThat(messageOf(blockedDeleteResult)).isEqualTo("Menu item cannot be deleted while it still has variants or option groups");
        assertThat(menuItemRepository.findById(blocked.getId())).isPresent();
        assertThat(menuItemRepository.findById(clear.getId())).isEmpty();
    }

    @Test
    @DisplayName("MENU-VARIANT-001 lists variants for one item")
    void shouldListVariantsForItem() throws Exception {
        User admin = adminUser();
        Restaurant restaurant = createRestaurant("variants-list", admin.getId());
        Menu menu = createMenu(restaurant, "breakfast", "Breakfast Menu", true, 1, admin.getId());
        MenuSection section = createSection(menu, "Mains", "Main dishes", true, 1);
        MenuItem item = createItem(section, "BRG-001", "House Burger", new BigDecimal("12.50"), true, 1);
        createVariant(item, "Small", "BRG-S", BigDecimal.ZERO, true, true, 1);
        createVariant(item, "Large", "BRG-L", new BigDecimal("2.50"), false, true, 2);

        String accessToken = accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "MENU-VARIANTS-LIST");

        MvcResult result = mockMvc.perform(get("/menus/{menuId}/sections/{sectionId}/items/{itemId}/variants", menu.getId(), section.getId(), item.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body).hasSize(2);
        assertThat(body.get(0).get("name").asText()).isEqualTo("Small");
        assertThat(body.get(0).get("default").asBoolean()).isTrue();
        assertThat(body.get(1).get("name").asText()).isEqualTo("Large");
    }

    @Test
    @DisplayName("MENU-VARIANT-002 through MENU-VARIANT-008 create, update, validate, scope, and enforce default variants")
    void shouldCreateUpdateValidateScopeAndEnforceVariantDefaults() throws Exception {
        User admin = adminUser();
        Restaurant restaurant = createRestaurant("variants-write", admin.getId());
        Menu breakfast = createMenu(restaurant, "breakfast", "Breakfast Menu", true, 1, admin.getId());
        Menu lunch = createMenu(restaurant, "lunch", "Lunch Menu", true, 2, admin.getId());
        MenuSection breakfastSection = createSection(breakfast, "Mains", "Breakfast mains", true, 1);
        MenuSection lunchSection = createSection(lunch, "Mains", "Lunch mains", true, 1);
        MenuItem breakfastItem = createItem(breakfastSection, "BRG-001", "House Burger", new BigDecimal("12.50"), true, 1);
        MenuItem lunchItem = createItem(lunchSection, "LUNCH-001", "Lunch Plate", new BigDecimal("15.00"), true, 1);
        MenuVariant existingDefault = createVariant(breakfastItem, "Small", "BRG-S", BigDecimal.ZERO, true, true, 1);
        MenuVariant foreignVariant = createVariant(lunchItem, "Regular", "LUNCH-R", BigDecimal.ZERO, false, true, 1);

        String accessToken = accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "MENU-VARIANTS-WRITE");

        MvcResult createResult = mockMvc.perform(post("/menus/{menuId}/sections/{sectionId}/items/{itemId}/variants", breakfast.getId(), breakfastSection.getId(), breakfastItem.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", " Large ",
                                "sku", " brg-l ",
                                "priceDelta", "2.50",
                                "default", true,
                                "active", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createBody = bodyOf(createResult);
        UUID createdVariantId = UUID.fromString(createBody.get("id").asText());
        assertThat(createBody.get("name").asText()).isEqualTo("Large");
        assertThat(createBody.get("sku").asText()).isEqualTo("BRG-L");
        assertThat(createBody.get("default").asBoolean()).isTrue();
        assertThat(createBody.get("displayOrder").asInt()).isZero();
        assertThat(menuVariantRepository.findById(existingDefault.getId()).orElseThrow().isDefault()).isFalse();

        MvcResult updateResult = mockMvc.perform(put("/menus/{menuId}/sections/{sectionId}/items/{itemId}/variants/{variantId}",
                        breakfast.getId(), breakfastSection.getId(), breakfastItem.getId(), createdVariantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", " XL ",
                                "sku", " brg-xl ",
                                "priceDelta", "3.75",
                                "default", false,
                                "active", false,
                                "displayOrder", 5
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode updateBody = bodyOf(updateResult);
        assertThat(updateBody.get("name").asText()).isEqualTo("XL");
        assertThat(updateBody.get("sku").asText()).isEqualTo("BRG-XL");
        assertThat(updateBody.get("priceDelta").decimalValue()).isEqualByComparingTo("3.75");
        assertThat(updateBody.get("default").asBoolean()).isFalse();
        assertThat(updateBody.get("active").asBoolean()).isFalse();
        assertThat(updateBody.get("displayOrder").asInt()).isEqualTo(5);

        MvcResult negativeDisplayOrderResult = mockMvc.perform(post("/menus/{menuId}/sections/{sectionId}/items/{itemId}/variants", breakfast.getId(), breakfastSection.getId(), breakfastItem.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Invalid Variant",
                                "displayOrder", -1
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn();

        MvcResult duplicateNameResult = mockMvc.perform(post("/menus/{menuId}/sections/{sectionId}/items/{itemId}/variants", breakfast.getId(), breakfastSection.getId(), breakfastItem.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", " Small "
                        ))))
                .andExpect(status().isConflict())
                .andReturn();

        MvcResult foreignVariantResult = mockMvc.perform(put("/menus/{menuId}/sections/{sectionId}/items/{itemId}/variants/{variantId}",
                        breakfast.getId(), breakfastSection.getId(), breakfastItem.getId(), foreignVariant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Wrong Variant",
                                "default", false,
                                "active", true,
                                "displayOrder", 1
                        ))))
                .andExpect(status().isConflict())
                .andReturn();

        assertThat(messageOf(negativeDisplayOrderResult)).isEqualTo("displayOrder: displayOrder must be greater than or equal to 0");
        assertThat(messageOf(duplicateNameResult)).isEqualTo("Menu variant name already in use for this item");
        assertThat(messageOf(foreignVariantResult)).isEqualTo("Menu variant does not belong to this item");
    }

    @Test
    @DisplayName("MENU-VARIANT-004 deletes variants")
    void shouldDeleteVariant() throws Exception {
        User admin = adminUser();
        Restaurant restaurant = createRestaurant("variants-delete", admin.getId());
        Menu menu = createMenu(restaurant, "breakfast", "Breakfast Menu", true, 1, admin.getId());
        MenuSection section = createSection(menu, "Mains", "Main dishes", true, 1);
        MenuItem item = createItem(section, "BRG-001", "House Burger", new BigDecimal("12.50"), true, 1);
        MenuVariant variant = createVariant(item, "Large", "BRG-L", new BigDecimal("2.00"), false, true, 1);

        String accessToken = accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "MENU-VARIANTS-DELETE");

        mockMvc.perform(delete("/menus/{menuId}/sections/{sectionId}/items/{itemId}/variants/{variantId}",
                        menu.getId(), section.getId(), item.getId(), variant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNoContent());

        assertThat(menuVariantRepository.findById(variant.getId())).isEmpty();
    }

    @Test
    @DisplayName("OPTION-GROUP-TYPE-001 through OPTION-GROUP-TYPE-008 list, search, create, update, validate uniqueness, and delete option group types")
    void shouldManageOptionGroupTypes() throws Exception {
        User admin = adminUser();
        Restaurant restaurant = createRestaurant("types", admin.getId());
        OptionGroupType single = createOptionGroupType("single_select", "Single Select");
        OptionGroupType used = createOptionGroupType("used_type", "Used Type");
        createOptionGroup(restaurant, used, "Sauces", "Choose a sauce", 0, 2, false, true, 1);

        String accessToken = accessTokenFor(ADMIN_USERNAME, ADMIN_PASSWORD, "OPTION-GROUP-TYPES");

        MvcResult searchResult = mockMvc.perform(get("/option-group-types")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("search", "single"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode searchBody = bodyOf(searchResult);
        assertThat(searchBody).hasSize(1);
        assertThat(searchBody.get(0).get("id").asText()).isEqualTo(single.getId().toString());

        MvcResult createResult = mockMvc.perform(post("/option-group-types")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", " Multi Select ",
                                "description", " Multiple choices "
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createBody = bodyOf(createResult);
        UUID createdTypeId = UUID.fromString(createBody.get("id").asText());
        assertThat(createBody.get("code").asText()).isEqualTo("MULTI_SELECT");
        assertThat(createBody.get("name").asText()).isEqualTo("Multi Select");

        MvcResult updateResult = mockMvc.perform(put("/option-group-types/{typeId}", createdTypeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", " combo_select ",
                                "name", " Combo Select ",
                                "description", " Combo choices "
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode updateBody = bodyOf(updateResult);
        assertThat(updateBody.get("code").asText()).isEqualTo("COMBO_SELECT");
        assertThat(updateBody.get("name").asText()).isEqualTo("Combo Select");
        assertThat(updateBody.get("description").asText()).isEqualTo("Combo choices");

        MvcResult duplicateCodeResult = mockMvc.perform(post("/option-group-types")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", " single_select ",
                                "name", "Different Name"
                        ))))
                .andExpect(status().isConflict())
                .andReturn();

        MvcResult duplicateNameResult = mockMvc.perform(put("/option-group-types/{typeId}", createdTypeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "combo_select",
                                "name", " Single Select "
                        ))))
                .andExpect(status().isConflict())
                .andReturn();

        MvcResult blockedDeleteResult = mockMvc.perform(delete("/option-group-types/{typeId}", used.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isConflict())
                .andReturn();

        mockMvc.perform(delete("/option-group-types/{typeId}", createdTypeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNoContent());

        assertThat(messageOf(duplicateCodeResult)).isEqualTo("Option group type code already in use");
        assertThat(messageOf(duplicateNameResult)).isEqualTo("Option group type name already in use");
        assertThat(messageOf(blockedDeleteResult)).isEqualTo("Option group type cannot be deleted while it is still used by option groups");
        assertThat(optionGroupTypeRepository.findById(createdTypeId)).isEmpty();
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
        restaurant.setOwnerId(actorId);
        restaurant.setCreatedBy(actorId);
        restaurant.setUpdatedBy(actorId);
        return restaurantRepository.save(restaurant);
    }

    private Menu createMenu(Restaurant restaurant, String code, String name, boolean active, int displayOrder, UUID actorId) {
        User actor = userRepository.findById(actorId).orElseThrow();
        Menu menu = new Menu();
        menu.setRestaurant(restaurant);
        menu.setCode(code);
        menu.setName(name);
        menu.setDescription(name + " description");
        menu.setActive(active);
        menu.setDisplayOrder(displayOrder);
        menu.setCreatedBy(actor);
        menu.setUpdatedBy(actor);
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

    private MenuVariant createVariant(
            MenuItem item,
            String name,
            String sku,
            BigDecimal priceDelta,
            boolean isDefault,
            boolean active,
            int displayOrder
    ) {
        MenuVariant variant = new MenuVariant();
        variant.setMenuItem(item);
        variant.setName(name);
        variant.setSku(sku);
        variant.setPriceDelta(priceDelta);
        variant.setDefault(isDefault);
        variant.setActive(active);
        variant.setDisplayOrder(displayOrder);
        return menuVariantRepository.save(variant);
    }

    private OptionGroupType createOptionGroupType(String code, String name) {
        OptionGroupType type = new OptionGroupType();
        type.setCode(code);
        type.setName(name);
        type.setDescription(name + " description");
        return optionGroupTypeRepository.save(type);
    }

    private OptionGroup createOptionGroup(
            Restaurant restaurant,
            OptionGroupType type,
            String name,
            String description,
            Integer minSelect,
            Integer maxSelect,
            boolean required,
            boolean active,
            int displayOrder
    ) {
        OptionGroup optionGroup = new OptionGroup();
        optionGroup.setRestaurant(restaurant);
        optionGroup.setType(type);
        optionGroup.setName(name);
        optionGroup.setDescription(description);
        optionGroup.setMinSelect(minSelect);
        optionGroup.setMaxSelect(maxSelect);
        optionGroup.setRequired(required);
        optionGroup.setActive(active);
        optionGroup.setDisplayOrder(displayOrder);
        return optionGroupRepository.save(optionGroup);
    }

    private MenuItemOptionGroup linkOptionGroup(
            MenuItem item,
            OptionGroup optionGroup,
            int displayOrder,
            Integer minSelectOverride,
            Integer maxSelectOverride,
            Boolean requiredOverride
    ) {
        MenuItemOptionGroup link = new MenuItemOptionGroup();
        link.setMenuItem(item);
        link.setOptionGroup(optionGroup);
        link.setDisplayOrder(displayOrder);
        link.setMinSelectOverride(minSelectOverride);
        link.setMaxSelectOverride(maxSelectOverride);
        link.setRequiredOverride(requiredOverride);
        return menuItemOptionGroupRepository.save(link);
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
