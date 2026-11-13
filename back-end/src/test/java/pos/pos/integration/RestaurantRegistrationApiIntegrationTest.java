package pos.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.support.TestJwtKeySupport;
import pos.pos.support.TestPostgresContainerSupport;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("Restaurant registration API integration test")
class RestaurantRegistrationApiIntegrationTest {

    private static final String SCHEMA = "restaurant_reg_" + UUID.randomUUID().toString().replace("-", "");
    private static final String ACCESS_COOKIE_NAME = "access-token";
    private static final String ADMIN_EMAIL = "prod.admin@pos.example";
    private static final String ADMIN_USERNAME = "prodadmin";
    private static final String ADMIN_PASSWORD = "StrongPass123!";
    private static final String OWNER_SETUP_PASSWORD = "OwnerSetup123!";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        TestPostgresContainerSupport.registerProdDatabaseProperties(registry, SCHEMA);
        TestJwtKeySupport.registerJwtProperties(registry);
        registry.add("app.restaurants.public-registration-enabled", () -> "true");
        // Every request here comes from the same address; the limits have their own test.
        registry.add("app.restaurants.applications.max-per-ip", () -> "1000");
        registry.add("app.restaurants.applications.status-checks-per-ip", () -> "1000");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "restaurant-reg-test-refresh-token-pepper");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "restaurant-reg-test-password-reset-pepper");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "restaurant-reg-test-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "restaurant-reg-test-sms-pepper");
        registry.add("app.storage.menu-images.provider", () -> "disabled");
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
    private RestaurantRepository restaurantRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void muteMailSender() {
        reset(javaMailSender);
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Should create a pending application, email its code, and let a super admin approve it")
    void shouldRegisterAndApproveRestaurant() throws Exception {
        assertThat(List.of(environment.getActiveProfiles())).containsExactly("prod");

        String uid = uid();
        String ownerEmail = "reg.owner." + uid + "@pos.example";
        String ownerUsername = "reg.owner." + uid;

        JsonNode pending = bodyOf(mockMvc.perform(post("/restaurants/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(application("Reg Restaurant " + uid, ownerEmail, ownerUsername, "Europe/Tirane"))))
                .andExpect(status().isCreated())
                .andReturn());

        UUID restaurantId = UUID.fromString(pending.get("id").asText());
        String code = pending.get("statusCode").asText();
        assertThat(pending.get("status").asText()).isEqualTo("PENDING");
        assertThat(code).matches("[0-9A-Z]{5}(-[0-9A-Z]{5}){3}");
        assertThat(pending.has("ownerUserId")).isFalse();

        Restaurant saved = restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RestaurantStatus.PENDING);
        assertThat(saved.getPendingOwnerEmail()).isEqualTo(ownerEmail.toLowerCase());
        assertThat(saved.getOwnerId()).isNull();
        assertThat(saved.getEmail()).isEqualTo("contact." + uid + "@restaurant.example");
        assertThat(saved.getApplicationCodeHash()).isNotBlank().doesNotContain(code);
        assertThat(saved.getApplicationCodeHash()).doesNotContain(code.replace("-", ""));

        // The applicant got their code by email; no account exists yet.
        ArgumentCaptor<SimpleMailMessage> received = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(received.capture());
        assertThat(received.getValue().getTo()).contains(ownerEmail.toLowerCase());
        assertThat(received.getValue().getText()).contains(code);
        assertThat(userRepository.findByEmailAndDeletedAtIsNull(ownerEmail.toLowerCase())).isEmpty();
        assertThat(applicationStatus(code).get("status").asText()).isEqualTo("PENDING");
        reset(javaMailSender);
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));

        String adminToken = accessTokenOf(webLogin(ADMIN_USERNAME, ADMIN_PASSWORD));
        JsonNode queue = bodyOf(mockMvc.perform(MockMvcRequestBuilders.get("/platform/restaurant-applications")
                        .param("search", uid)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(queue.get("items")).hasSize(1);
        assertThat(queue.get("items").get(0).get("owner").get("username").asText()).isEqualTo(ownerUsername);

        JsonNode approved = bodyOf(mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APPROVE"))))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("reviewedAt").isNull()).isFalse();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage inviteMail = captor.getValue();
        assertThat(inviteMail.getTo()).contains(ownerEmail.toLowerCase());

        Restaurant approvedDb = restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId).orElseThrow();
        assertThat(approvedDb.getStatus()).isEqualTo(RestaurantStatus.ACTIVE);
        assertThat(approvedDb.getOwnerId()).isNotNull();
        assertThat(approvedDb.getPendingOwnerEmail()).isNull();
        assertThat(approvedDb.getReviewedBy()).isNotNull();
        // Approved restaurants open with a first branch, like the ones super admins create.
        assertThat(jdbcTemplate.queryForObject("SELECT code FROM branches WHERE restaurant_id = ? AND deleted_at IS NULL",
                String.class, restaurantId)).isEqualTo("MAIN");
        assertThat(applicationStatus(code).get("status").asText()).isEqualTo("APPROVED");

        User owner = userRepository.findByEmailAndDeletedAtIsNull(ownerEmail.toLowerCase()).orElseThrow();
        assertThat(owner.getRestaurantId()).isEqualTo(restaurantId);

        String inviteToken = extractToken(inviteMail.getText());
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", inviteToken,
                                "newPassword", OWNER_SETUP_PASSWORD
                        ))))
                .andExpect(status().isNoContent());

        String ownerToken = accessTokenOf(webLogin(ownerUsername, OWNER_SETUP_PASSWORD));
        JsonNode ownerView = bodyOf(mockMvc.perform(
                        MockMvcRequestBuilders.get("/restaurants/{restaurantId}", restaurantId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(ownerView.get("id").asText()).isEqualTo(restaurantId.toString());
    }

    @Test
    @DisplayName("Should need a reason to reject, then show it to the applicant and email it, creating no account")
    void shouldRegisterAndRejectRestaurant() throws Exception {
        String uid = uid();
        String ownerEmail = "rej.owner." + uid + "@pos.example";
        String ownerUsername = "rej.owner." + uid;

        JsonNode pending = bodyOf(mockMvc.perform(post("/restaurants/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(application("Rejected Restaurant " + uid, ownerEmail, ownerUsername, "America/New_York"))))
                .andExpect(status().isCreated())
                .andReturn());

        UUID restaurantId = UUID.fromString(pending.get("id").asText());
        String code = pending.get("statusCode").asText();
        String adminToken = accessTokenOf(webLogin(ADMIN_USERNAME, ADMIN_PASSWORD));
        reset(javaMailSender);
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));

        mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "REJECT"))))
                .andExpect(status().isBadRequest());

        JsonNode rejected = bodyOf(mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "REJECT", "rejectionReason", "We need your business licence"))))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.get("rejectionReason").asText()).isEqualTo("We need your business licence");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).contains(ownerEmail.toLowerCase());
        assertThat(captor.getValue().getText()).contains("We need your business licence");
        assertThat(userRepository.findByEmailAndDeletedAtIsNull(ownerEmail.toLowerCase())).isEmpty();

        JsonNode status = applicationStatus(code);
        assertThat(status.get("status").asText()).isEqualTo("REJECTED");
        assertThat(status.get("rejectionReason").asText()).isEqualTo("We need your business licence");
    }

    @Test
    @DisplayName("Should refuse a second decision on an application with 409")
    void shouldRejectDoubleReview() throws Exception {
        String uid = uid();

        JsonNode pending = bodyOf(mockMvc.perform(post("/restaurants/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(application("Double Review " + uid,
                                "double.owner." + uid + "@pos.example", "double.owner." + uid, "Europe/Berlin"))))
                .andExpect(status().isCreated())
                .andReturn());

        UUID restaurantId = UUID.fromString(pending.get("id").asText());
        String adminToken = accessTokenOf(webLogin(ADMIN_USERNAME, ADMIN_PASSWORD));

        mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "REJECT", "rejectionReason", "Duplicate"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APPROVE"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Should reject registration review by non-super-admin owner")
    void shouldRejectNonSuperAdminReview() throws Exception {
        String uid = uid();
        String ownerEmail = "owner.rev." + uid + "@pos.example";
        String ownerUsername = "owner.rev." + uid;

        String adminToken = accessTokenOf(webLogin(ADMIN_USERNAME, ADMIN_PASSWORD));

        bodyOf(mockMvc.perform(post("/restaurants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Owner Rev Restaurant " + uid,
                                "legalName", "Owner Rev Restaurant " + uid + " LLC",
                                "currency", "USD",
                                "timezone", "Europe/Berlin",
                                "email", "contact@pos.example",
                                "phone", "+355690001111",
                                "owner", Map.of(
                                        "email", ownerEmail,
                                        "username", ownerUsername,
                                        "firstName", "Owner",
                                        "lastName", "Rev"
                                )
                        ))))
                .andExpect(status().isCreated())
                .andReturn());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        String ownerToken = extractTokenAndLogin(captor.getValue().getText(), ownerUsername);

        UUID fakeRegistrationId = UUID.randomUUID();
        mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", fakeRegistrationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APPROVE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should reject registration with invalid timezone")
    void shouldRejectInvalidTimezone() throws Exception {
        String uid = uid();
        mockMvc.perform(post("/restaurants/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(application("Bad Timezone Restaurant " + uid,
                                "bad.tz." + uid + "@pos.example", "bad.tz." + uid, "Mars/Phobos"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should cascade soft-delete branches when restaurant is deleted")
    void shouldCascadeSoftDeleteBranchesWhenRestaurantDeleted() throws Exception {
        String uid = uid();
        String ownerEmail = "cascade." + uid + "@pos.example";
        String ownerUsername = "cascade." + uid;

        String adminToken = accessTokenOf(webLogin(ADMIN_USERNAME, ADMIN_PASSWORD));

        JsonNode created = bodyOf(mockMvc.perform(post("/restaurants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Cascade Restaurant " + uid,
                                "legalName", "Cascade Restaurant " + uid + " LLC",
                                "currency", "USD",
                                "timezone", "Europe/Berlin",
                                "email", "contact@pos.example",
                                "phone", "+355690001111",
                                "owner", Map.of(
                                        "email", ownerEmail,
                                        "username", ownerUsername,
                                        "firstName", "Cascade",
                                        "lastName", "Owner"
                                )
                        ))))
                .andExpect(status().isCreated())
                .andReturn());

        UUID restaurantId = UUID.fromString(created.get("id").asText());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        reset(javaMailSender);
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));

        String ownerToken = extractTokenAndLogin(captor.getValue().getText(), ownerUsername);

        JsonNode branch = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/branches", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Cascade Branch",
                                "code", "CASCADE_BRANCH"
                        ))))
                .andExpect(status().isCreated())
                .andReturn());
        UUID branchId = UUID.fromString(branch.get("id").asText());

        mockMvc.perform(delete("/restaurants/{restaurantId}", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        Restaurant deletedRestaurant = restaurantRepository.findById(restaurantId).orElseThrow();
        assertThat(deletedRestaurant.getDeletedAt()).isNotNull();
        assertThat(deletedRestaurant.getStatus()).isEqualTo(RestaurantStatus.ARCHIVED);

        Integer activeBranchCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM branches WHERE id = ? AND deleted_at IS NULL",
                Integer.class,
                branchId
        );
        assertThat(activeBranchCount).isEqualTo(0);

        Integer archivedBranchCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM branches WHERE id = ? AND status = 'ARCHIVED'",
                Integer.class,
                branchId
        );
        assertThat(archivedBranchCount).isEqualTo(1);
    }

    private Map<String, Object> application(String name, String ownerEmail, String ownerUsername, String timezone) {
        String uid = uid();
        return Map.of(
                "name", name,
                "legalName", name + " LLC",
                "email", "contact." + name.substring(name.lastIndexOf(' ') + 1) + "@restaurant.example",
                "phone", "+35569" + (1_000_000 + Math.abs(uid.hashCode() % 8_000_000)),
                "currency", "USD",
                "timezone", timezone,
                "owner", Map.of(
                        "email", ownerEmail,
                        "username", ownerUsername,
                        "firstName", "Reg",
                        "lastName", "Owner"
                )
        );
    }

    private JsonNode applicationStatus(String code) throws Exception {
        return bodyOf(mockMvc.perform(post("/restaurants/registrations/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk())
                .andReturn());
    }

    private String extractTokenAndLogin(String messageText, String username) throws Exception {
        String token = extractToken(messageText);
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", token,
                                "newPassword", OWNER_SETUP_PASSWORD
                        ))))
                .andExpect(status().isNoContent());
        return accessTokenOf(webLogin(username, OWNER_SETUP_PASSWORD));
    }

    private String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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

    private String accessTokenOf(MvcResult result) {
        return extractCookieValue(result, ACCESS_COOKIE_NAME);
    }

    private String extractCookieValue(MvcResult result, String cookieName) {
        String prefix = cookieName + "=";
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.contains(prefix))
                .findFirst()
                .map(header -> {
                    int start = header.indexOf(prefix) + prefix.length();
                    int end = header.indexOf(';', start);
                    return end >= 0 ? header.substring(start, end) : header.substring(start);
                })
                .orElseThrow();
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String extractToken(String messageText) {
        String marker = "token=";
        int start = messageText.indexOf(marker);
        assertThat(start).isNotNegative();
        int tokenStart = start + marker.length();
        int end = tokenStart;
        while (end < messageText.length() && !Character.isWhitespace(messageText.charAt(end))) {
            end++;
        }
        return messageText.substring(tokenStart, end);
    }
}
