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
    private static final String ADMIN_EMAIL = "prod.admin@pos.example";
    private static final String ADMIN_USERNAME = "prodadmin";
    private static final String ADMIN_PASSWORD = "StrongPass123!";
    private static final String OWNER_SETUP_PASSWORD = "OwnerSetup123!";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        TestPostgresContainerSupport.registerProdDatabaseProperties(registry, SCHEMA);
        registry.add("JWT_SECRET", () -> "restaurant-reg-test-secret-key-for-hs256");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "restaurant-reg-test-refresh-token-pepper");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "restaurant-reg-test-password-reset-pepper");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "restaurant-reg-test-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "restaurant-reg-test-sms-pepper");
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
    @DisplayName("Should create a pending registration and allow super admin to approve it")
    void shouldRegisterAndApproveRestaurant() throws Exception {
        assertThat(List.of(environment.getActiveProfiles())).containsExactly("prod");

        String uid = uid();
        String ownerEmail = "reg.owner." + uid + "@pos.example";
        String ownerUsername = "reg.owner." + uid;

        JsonNode pending = bodyOf(mockMvc.perform(post("/restaurants/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Reg Restaurant " + uid,
                                "legalName", "Reg Restaurant " + uid + " LLC",
                                "currency", "EUR",
                                "timezone", "Europe/Tirane",
                                "owner", Map.of(
                                        "email", ownerEmail,
                                        "username", ownerUsername,
                                        "firstName", "Reg",
                                        "lastName", "Owner"
                                )
                        ))))
                .andExpect(status().isCreated())
                .andReturn());

        UUID restaurantId = UUID.fromString(pending.get("id").asText());
        assertThat(pending.get("status").asText()).isEqualTo("PENDING");
        assertThat(pending.get("isActive").asBoolean()).isFalse();
        assertThat(pending.get("ownerUserId").isNull()).isTrue();

        Restaurant saved = restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RestaurantStatus.PENDING);
        assertThat(saved.getPendingOwnerEmail()).isEqualTo(ownerEmail.toLowerCase());
        assertThat(saved.getOwnerId()).isNull();

        verify(javaMailSender, never()).send(any(SimpleMailMessage.class));

        String adminToken = bodyOf(webLogin(ADMIN_USERNAME, ADMIN_PASSWORD)).get("accessToken").asText();
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        JsonNode approved = bodyOf(mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APPROVE"))))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(approved.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(approved.get("isActive").asBoolean()).isTrue();
        assertThat(approved.get("ownerUserId").isNull()).isFalse();

        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage inviteMail = captor.getValue();
        assertThat(inviteMail.getTo()).contains(ownerEmail.toLowerCase());

        Restaurant approvedDb = restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId).orElseThrow();
        assertThat(approvedDb.getStatus()).isEqualTo(RestaurantStatus.ACTIVE);
        assertThat(approvedDb.getOwnerId()).isNotNull();
        assertThat(approvedDb.getPendingOwnerEmail()).isNull();

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

        String ownerToken = bodyOf(webLogin(ownerUsername, OWNER_SETUP_PASSWORD)).get("accessToken").asText();
        JsonNode ownerView = bodyOf(mockMvc.perform(
                        MockMvcRequestBuilders.get("/restaurants/{restaurantId}", restaurantId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(ownerView.get("id").asText()).isEqualTo(restaurantId.toString());
    }

    @Test
    @DisplayName("Should create a pending registration and allow super admin to reject it")
    void shouldRegisterAndRejectRestaurant() throws Exception {
        String uid = uid();
        String ownerEmail = "rej.owner." + uid + "@pos.example";
        String ownerUsername = "rej.owner." + uid;

        JsonNode pending = bodyOf(mockMvc.perform(post("/restaurants/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Rejected Restaurant " + uid,
                                "legalName", "Rejected Restaurant " + uid + " LLC",
                                "currency", "USD",
                                "timezone", "America/New_York",
                                "owner", Map.of(
                                        "email", ownerEmail,
                                        "username", ownerUsername,
                                        "firstName", "Rej",
                                        "lastName", "Owner"
                                )
                        ))))
                .andExpect(status().isCreated())
                .andReturn());

        UUID restaurantId = UUID.fromString(pending.get("id").asText());
        String adminToken = bodyOf(webLogin(ADMIN_USERNAME, ADMIN_PASSWORD)).get("accessToken").asText();

        JsonNode rejected = bodyOf(mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "REJECT"))))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.get("isActive").asBoolean()).isFalse();
        assertThat(rejected.get("ownerUserId").isNull()).isTrue();

        verify(javaMailSender, never()).send(any(SimpleMailMessage.class));
        assertThat(userRepository.findByEmailAndDeletedAtIsNull(ownerEmail.toLowerCase())).isEmpty();
    }

    @Test
    @DisplayName("Should reject a second review attempt on an already reviewed registration")
    void shouldRejectDoubleReview() throws Exception {
        String uid = uid();

        JsonNode pending = bodyOf(mockMvc.perform(post("/restaurants/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Double Review " + uid,
                                "legalName", "Double Review " + uid + " LLC",
                                "currency", "USD",
                                "timezone", "Europe/Berlin",
                                "owner", Map.of(
                                        "email", "double.owner." + uid + "@pos.example",
                                        "username", "double.owner." + uid,
                                        "firstName", "Double",
                                        "lastName", "Owner"
                                )
                        ))))
                .andExpect(status().isCreated())
                .andReturn());

        UUID restaurantId = UUID.fromString(pending.get("id").asText());
        String adminToken = bodyOf(webLogin(ADMIN_USERNAME, ADMIN_PASSWORD)).get("accessToken").asText();

        mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "REJECT"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APPROVE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject registration review by non-super-admin owner")
    void shouldRejectNonSuperAdminReview() throws Exception {
        String uid = uid();
        String ownerEmail = "owner.rev." + uid + "@pos.example";
        String ownerUsername = "owner.rev." + uid;

        String adminToken = bodyOf(webLogin(ADMIN_USERNAME, ADMIN_PASSWORD)).get("accessToken").asText();

        bodyOf(mockMvc.perform(post("/restaurants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Owner Rev Restaurant " + uid,
                                "legalName", "Owner Rev Restaurant " + uid + " LLC",
                                "currency", "USD",
                                "timezone", "Europe/Berlin",
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
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Bad Timezone Restaurant " + uid,
                                "legalName", "Bad Timezone LLC",
                                "currency", "USD",
                                "timezone", "Mars/Phobos",
                                "owner", Map.of(
                                        "email", "bad.tz." + uid + "@pos.example",
                                        "username", "bad.tz." + uid,
                                        "firstName", "Bad",
                                        "lastName", "Tz"
                                )
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should cascade soft-delete branches when restaurant is deleted")
    void shouldCascadeSoftDeleteBranchesWhenRestaurantDeleted() throws Exception {
        String uid = uid();
        String ownerEmail = "cascade." + uid + "@pos.example";
        String ownerUsername = "cascade." + uid;

        String adminToken = bodyOf(webLogin(ADMIN_USERNAME, ADMIN_PASSWORD)).get("accessToken").asText();

        JsonNode created = bodyOf(mockMvc.perform(post("/restaurants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Cascade Restaurant " + uid,
                                "legalName", "Cascade Restaurant " + uid + " LLC",
                                "currency", "USD",
                                "timezone", "Europe/Berlin",
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
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
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

    private String extractTokenAndLogin(String messageText, String username) throws Exception {
        String token = extractToken(messageText);
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", token,
                                "newPassword", OWNER_SETUP_PASSWORD
                        ))))
                .andExpect(status().isNoContent());
        return bodyOf(webLogin(username, OWNER_SETUP_PASSWORD)).get("accessToken").asText();
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
