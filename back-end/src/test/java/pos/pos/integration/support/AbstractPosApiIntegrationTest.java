package pos.pos.integration.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.support.TestJwtKeySupport;
import pos.pos.support.TestPostgresContainerSupport;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared base for the end-to-end API tests: the full application on a real PostgreSQL (prod profile), one schema and
 * one Spring context for every subclass. Tests are not wrapped in a transaction, so commits, after-commit listeners
 * and the order write filter run as in production; each test creates its own restaurant to stay independent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Import(SimulatedPaymentIntegrationConfiguration.class)
public abstract class AbstractPosApiIntegrationTest {

    protected static final String SCHEMA = "pos_api_it";
    protected static final String ADMIN_EMAIL = "it.superadmin@pos.example";
    protected static final String ADMIN_USERNAME = "itsuperadmin";
    protected static final String PASSWORD = "StrongPass123!";
    private static final String ACCESS_COOKIE = "access-token";

    // Every 5xx answer seen during a test; any one fails the test, whatever the test itself checked.
    private final java.util.List<String> serverErrors = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final AtomicInteger SEQUENCE = new AtomicInteger(ThreadLocalRandom.current().nextInt(1, 1_000_000));
    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestPostgresContainerSupport.registerProdDatabaseProperties(registry, SCHEMA);
        TestJwtKeySupport.registerJwtProperties(registry);
        registry.add("REFRESH_TOKEN_PEPPER", () -> "pos-api-it-refresh-token-pepper-0123456789");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "pos-api-it-password-reset-pepper");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "pos-api-it-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "pos-api-it-sms-code-pepper");
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
        registry.add("BOOTSTRAP_SUPER_ADMIN_PASSWORD", () -> PASSWORD);
        registry.add("BOOTSTRAP_SUPER_ADMIN_FIRST_NAME", () -> "Super");
        registry.add("BOOTSTRAP_SUPER_ADMIN_LAST_NAME", () -> "Admin");
        registry.add("SMS_DELIVERY_MODE", () -> "LOG_ONLY");
        // Scheduled jobs would race the tests' own data; the tests call what they need directly.
        registry.add("spring.task.scheduling.pool.size", () -> "1");
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
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** A restaurant with one branch, its owner and helpers to add staff. */
    public record World(Restaurant restaurant, Branch branch, User owner, String ownerToken) {
        public UUID restaurantId() {
            return restaurant.getId();
        }

        public UUID branchId() {
            return branch.getId();
        }
    }

    @org.junit.jupiter.api.AfterEach
    void noServerErrors() {
        assertThat(serverErrors).as("requests answered with a server error").isEmpty();
    }

    /** Allows a test to identify a deliberate, explicitly configured unavailable dependency response. */
    protected boolean isExpectedServerError(MvcResult result) throws Exception {
        return false;
    }

    protected static int next() {
        return SEQUENCE.incrementAndGet();
    }

    protected World newWorld(String label) throws Exception {
        return newWorld(label, "EUR", "Europe/Rome");
    }

    protected World newWorld(String label, String currency, String timezone) throws Exception {
        int sequence = next();
        User superAdmin = superAdmin();
        Restaurant restaurant = new Restaurant();
        restaurant.setName("IT " + label + " " + sequence);
        restaurant.setLegalName("IT " + label + " " + sequence + " LLC");
        restaurant.setCode(("it_" + label + "_" + sequence).toLowerCase().replaceAll("[^a-z0-9_]", "_"));
        restaurant.setSlug(("it-" + label + "-" + sequence).toLowerCase().replaceAll("[^a-z0-9-]", "-"));
        restaurant.setDescription("Integration test restaurant");
        restaurant.setCurrency(currency);
        restaurant.setTimezone(timezone);
        restaurant.setOwnerId(superAdmin.getId());
        restaurant.setCreatedBy(superAdmin.getId());
        restaurant.setUpdatedBy(superAdmin.getId());
        restaurant = restaurantRepository.saveAndFlush(restaurant);

        Branch branch = new Branch();
        branch.setRestaurant(restaurant);
        branch.setName("Main " + sequence);
        branch.setCode("MAIN" + sequence);
        branch.setCreatedBy(superAdmin.getId());
        branch.setUpdatedBy(superAdmin.getId());
        branch = branchRepository.saveAndFlush(branch);

        User owner = staff(restaurant, branch, "owner", "OWNER");
        restaurant.setOwnerId(owner.getId());
        restaurant = restaurantRepository.saveAndFlush(restaurant);
        return new World(restaurant, branch, owner, token(owner));
    }

    protected User superAdmin() {
        return userRepository.findByEmailAndDeletedAtIsNull(ADMIN_EMAIL).orElseThrow();
    }

    protected String superAdminToken() throws Exception {
        return login(ADMIN_USERNAME);
    }

    /** A staff member of the restaurant with the given system role(s). */
    protected User staff(World world, String label, String... roleCodes) {
        return staff(world.restaurant(), world.branch(), label, roleCodes);
    }

    protected User staff(Restaurant restaurant, Branch branch, String label, String... roleCodes) {
        int sequence = next();
        UUID adminId = superAdmin().getId();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        User user = userRepository.saveAndFlush(User.builder()
                .email("it." + label + "." + sequence + "@pos.example")
                .username("it." + label + "." + sequence)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .firstName(Character.toUpperCase(label.charAt(0)) + label.substring(1))
                .lastName("Tester" + sequence)
                .restaurantId(restaurant.getId())
                .defaultBranchId(branch == null ? null : branch.getId())
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .emailVerifiedAt(now)
                .createdBy(adminId)
                .updatedBy(adminId)
                .build());
        for (String roleCode : roleCodes) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(UserRole.builder()
                    .userId(user.getId())
                    .roleId(role.getId())
                    .assignedBy(adminId)
                    .build());
        }
        return user;
    }

    protected String token(User user) throws Exception {
        return login(user.getUsername());
    }

    protected String login(String identifier) throws Exception {
        String cached = TOKENS.get(identifier);
        if (cached != null) {
            return cached;
        }
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/auth/web/login")
                        .header(HttpHeaders.USER_AGENT, "pos-it")
                        .header("X-Forwarded-For", randomIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", identifier, "password", PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).as("login of " + identifier).isEqualTo(200);
        Cookie cookie = result.getResponse().getCookie(ACCESS_COOKIE);
        assertThat(cookie).as("access cookie").isNotNull();
        TOKENS.put(identifier, cookie.getValue());
        return cookie.getValue();
    }

    private static String randomIp() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "10." + random.nextInt(0, 255) + "." + random.nextInt(0, 255) + "." + random.nextInt(1, 254);
    }

    // ---- HTTP helpers ----

    protected Call call(HttpMethod method, String path, Object... uriVariables) {
        return new Call(MockMvcRequestBuilders.request(method, path, uriVariables), method, path);
    }

    protected Call get(String path, Object... uriVariables) {
        return call(HttpMethod.GET, path, uriVariables);
    }

    protected Call post(String path, Object... uriVariables) {
        return call(HttpMethod.POST, path, uriVariables);
    }

    protected Call postWithoutDefaultIdempotencyKey(String path, Object... uriVariables) {
        return call(HttpMethod.POST, path, uriVariables).withoutDefaultIdempotencyKey();
    }

    protected Call put(String path, Object... uriVariables) {
        return call(HttpMethod.PUT, path, uriVariables);
    }

    protected Call patch(String path, Object... uriVariables) {
        return call(HttpMethod.PATCH, path, uriVariables);
    }

    protected Call delete(String path, Object... uriVariables) {
        return call(HttpMethod.DELETE, path, uriVariables);
    }

    /** A request being built; {@link #send()} returns the response. */
    protected final class Call {
        private final MockHttpServletRequestBuilder builder;
        private final HttpMethod method;
        private final String path;
        private boolean hasExplicitIdempotencyKey;
        private boolean omitDefaultIdempotencyKey;

        private Call(MockHttpServletRequestBuilder builder, HttpMethod method, String path) {
            this.builder = builder;
            this.method = method;
            this.path = path;
        }

        public Call as(String token) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return this;
        }

        public Call body(Object body) throws Exception {
            builder.contentType(MediaType.APPLICATION_JSON)
                    .content(body instanceof String text ? text : objectMapper.writeValueAsString(body));
            return this;
        }

        public Call header(String name, String value) {
            builder.header(name, value);
            if ("Idempotency-Key".equalsIgnoreCase(name)) hasExplicitIdempotencyKey = true;
            return this;
        }

        private Call withoutDefaultIdempotencyKey() {
            omitDefaultIdempotencyKey = true;
            return this;
        }

        public Call param(String name, Object value) {
            builder.param(name, String.valueOf(value));
            return this;
        }

        public Response send() throws Exception {
            if (method == HttpMethod.POST && !hasExplicitIdempotencyKey && !omitDefaultIdempotencyKey
                    && (path.matches(".*/orders/[^/]+/payments(?:/[^/]+/(?:refund|void))?")
                    || path.matches(".*/inventory/(?:receive|waste|transfer|returns|adjustments)"))) {
                builder.header("Idempotency-Key", "integration-" + UUID.randomUUID());
            }
            MvcResult result = mockMvc.perform(builder).andReturn();
            if (result.getResponse().getStatus() >= 500 && !isExpectedServerError(result)) {
                serverErrors.add(result.getRequest().getMethod() + " " + result.getRequest().getRequestURI() + " -> "
                        + result.getResponse().getStatus() + " " + result.getResponse().getContentAsString());
            }
            return new Response(result);
        }

        /** Sends and checks the status, with the body in the failure message. */
        public JsonNode expect(int status) throws Exception {
            Response response = send();
            assertThat(response.status()).as("status of request; body: %s", response.text()).isEqualTo(status);
            return response.json();
        }
    }

    protected final class Response {
        private final MvcResult result;

        Response(MvcResult result) {
            this.result = result;
        }

        public int status() {
            return result.getResponse().getStatus();
        }

        public String text() throws Exception {
            return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        }

        public JsonNode json() throws Exception {
            String text = text();
            return text == null || text.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(text);
        }

        public String header(String name) {
            return result.getResponse().getHeader(name);
        }
    }

    // ---- Data helpers through the API ----

    /** Creates an active menu with one section and returns {menuId, sectionId}. */
    protected Map<String, UUID> menu(World world, String name) throws Exception {
        JsonNode menu = post("/menus").as(world.ownerToken()).body(Map.of(
                "restaurantId", world.restaurantId(),
                "name", name,
                "active", true)).expect(201);
        UUID menuId = UUID.fromString(menu.get("id").asText());
        JsonNode section = post("/menus/{menuId}/sections", menuId).as(world.ownerToken()).body(Map.of(
                "name", "Mains",
                "active", true)).expect(201);
        Map<String, UUID> ids = new LinkedHashMap<>();
        ids.put("menuId", menuId);
        ids.put("sectionId", UUID.fromString(section.get("id").asText()));
        return ids;
    }

    protected UUID item(World world, Map<String, UUID> menu, String name, String price) throws Exception {
        return item(world, menu, name, price, true);
    }

    protected UUID item(World world, Map<String, UUID> menu, String name, String price, boolean sendToKitchen) throws Exception {
        JsonNode item = post("/menus/{menuId}/sections/{sectionId}/items", menu.get("menuId"), menu.get("sectionId"))
                .as(world.ownerToken())
                .body(Map.of("name", name, "basePrice", new BigDecimal(price), "available", true, "sendToKitchen", sendToKitchen))
                .expect(201);
        return UUID.fromString(item.get("id").asText());
    }

    /** An open order at the branch with the given items (menu item id to quantity). */
    protected JsonNode order(World world, String token, Map<UUID, Integer> items) throws Exception {
        var lines = items.entrySet().stream()
                .map(entry -> Map.of("menuItemId", entry.getKey(), "quantity", entry.getValue()))
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderType", "TAKEAWAY");
        body.put("guestCount", 2);
        body.put("items", lines);
        return post("/restaurants/{r}/branches/{b}/orders", world.restaurantId(), world.branchId())
                .as(token).body(body).expect(201);
    }

    protected static UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }

    protected static BigDecimal money(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    /** Lets a test switch a restaurant setting straight in the database (the settings API has its own tests). */
    protected void setting(World world, String column, Object value) throws Exception {
        if (!column.matches("[a-z_0-9]+")) {
            throw new IllegalArgumentException(column);
        }
        // Reading the settings creates the restaurant's row with its defaults.
        get("/restaurants/{r}/settings", world.restaurantId()).as(world.ownerToken()).expect(200);
        int updated = jdbcTemplate.update("update " + SCHEMA + ".settings set " + column + " = ? where restaurant_id = ?",
                value, world.restaurantId());
        assertThat(updated).as("settings row for " + column).isEqualTo(1);
    }

    /** Switches one of the restaurant's order rules ("settings-order-rules") straight in the database. */
    protected void orderRule(World world, String column, Object value) throws Exception {
        if (!column.matches("[a-z_0-9]+")) {
            throw new IllegalArgumentException(column);
        }
        get("/restaurants/{r}/settings", world.restaurantId()).as(world.ownerToken()).expect(200);
        int updated = jdbcTemplate.update("update " + SCHEMA + ".\"settings-order-rules\" set " + column + " = ? where settings_id ="
                + " (select id from " + SCHEMA + ".settings where restaurant_id = ?)", value, world.restaurantId());
        assertThat(updated).as("order rules row for " + column).isEqualTo(1);
    }
}
