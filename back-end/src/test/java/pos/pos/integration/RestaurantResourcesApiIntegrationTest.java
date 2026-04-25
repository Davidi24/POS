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
import pos.pos.support.TestPostgresContainerSupport;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("Restaurant resources API integration test")
class RestaurantResourcesApiIntegrationTest {

    private static final String SCHEMA = "restaurant_resources_" + UUID.randomUUID().toString().replace("-", "");
    private static final String ADMIN_EMAIL = "prod.admin@pos.example";
    private static final String ADMIN_USERNAME = "prodadmin";
    private static final String ADMIN_PASSWORD = "StrongPass123!";
    private static final String OWNER_SETUP_PASSWORD = "OwnerSetup123!";

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        TestPostgresContainerSupport.registerProdDatabaseProperties(registry, SCHEMA);
        registry.add("JWT_SECRET", () -> "restaurant-resources-test-secret-key-for-hs256");
        registry.add("REFRESH_TOKEN_PEPPER", () -> "restaurant-resources-test-refresh-token-pepper");
        registry.add("PASSWORD_RESET_TOKEN_PEPPER", () -> "restaurant-resources-test-password-reset-pepper");
        registry.add("EMAIL_VERIFICATION_TOKEN_PEPPER", () -> "restaurant-resources-test-email-verification-pepper");
        registry.add("SMS_CODE_PEPPER", () -> "restaurant-resources-test-sms-pepper");
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

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void muteMailSender() {
        reset(javaMailSender);
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Should manage restaurant resources and branch subresources end to end")
    void shouldManageRestaurantResourcesAndBranchSubresourcesEndToEnd() throws Exception {
        assertThat(List.of(environment.getActiveProfiles())).containsExactly("prod");
        Integer migrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '9'",
                Integer.class
        );
        assertThat(migrationCount).isEqualTo(1);

        OwnedRestaurantContext context = provisionOwnedRestaurant();

        JsonNode branding = bodyOf(mockMvc.perform(put("/restaurants/{restaurantId}/branding", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "logoUrl", "https://cdn.pos.example/logo.png",
                                "primaryColor", "#112233",
                                "secondaryColor", "#445566",
                                "receiptHeader", "Welcome to POS",
                                "receiptFooter", "Thanks for visiting"
                        ))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(branding.get("primaryColor").asText()).isEqualTo("#112233");

        JsonNode fetchedBranding = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/branding", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(fetchedBranding.get("logoUrl").asText()).isEqualTo("https://cdn.pos.example/logo.png");

        UUID addressOneId = UUID.fromString(bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/addresses", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addressType", "PHYSICAL",
                                "country", "Albania",
                                "city", "Tirana",
                                "postalCode", "1001",
                                "streetLine1", "Main Street 1",
                                "isPrimary", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText());

        UUID addressTwoId = UUID.fromString(bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/addresses", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addressType", "BILLING",
                                "country", "Albania",
                                "city", "Durres",
                                "postalCode", "2001",
                                "streetLine1", "Billing Street 2",
                                "isPrimary", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText());

        JsonNode restaurantAddresses = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/addresses", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk())
                .andReturn());
        assertSingleBooleanFlag(restaurantAddresses, "isPrimary", addressTwoId);

        mockMvc.perform(patch("/restaurants/{restaurantId}/addresses/{addressId}/primary", context.restaurantId(), addressOneId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk());

        restaurantAddresses = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/addresses", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk())
                .andReturn());
        assertSingleBooleanFlag(restaurantAddresses, "isPrimary", addressOneId);

        UUID contactOneId = UUID.fromString(bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/contacts", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contactType", "SUPPORT",
                                "fullName", "Support One",
                                "email", "support.one@pos.example",
                                "phone", "+355690000001",
                                "jobTitle", "Support Lead",
                                "isPrimary", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText());

        UUID contactTwoId = UUID.fromString(bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/contacts", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contactType", "MANAGER",
                                "fullName", "Manager Two",
                                "email", "manager.two@pos.example",
                                "phone", "+355690000002",
                                "jobTitle", "Operations Manager",
                                "isPrimary", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText());

        JsonNode restaurantContacts = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/contacts", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk())
                .andReturn());
        assertSingleBooleanFlag(restaurantContacts, "isPrimary", contactTwoId);

        mockMvc.perform(patch("/restaurants/{restaurantId}/contacts/{contactId}/primary", context.restaurantId(), contactOneId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk());

        restaurantContacts = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/contacts", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk())
                .andReturn());
        assertSingleBooleanFlag(restaurantContacts, "isPrimary", contactOneId);

        UUID taxProfileOneId = UUID.fromString(bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/tax-profiles", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "country", "Albania",
                                "taxNumber", "TAX-001",
                                "isDefault", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText());

        UUID taxProfileTwoId = UUID.fromString(bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/tax-profiles", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "country", "Kosovo",
                                "taxNumber", "TAX-002",
                                "isDefault", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText());

        JsonNode taxProfiles = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/tax-profiles", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk())
                .andReturn());
        assertSingleBooleanFlag(taxProfiles, "isDefault", taxProfileTwoId);

        mockMvc.perform(patch("/restaurants/{restaurantId}/tax-profiles/{taxProfileId}/default", context.restaurantId(), taxProfileOneId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk());

        taxProfiles = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/tax-profiles", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk())
                .andReturn());
        assertSingleBooleanFlag(taxProfiles, "isDefault", taxProfileOneId);

        JsonNode createdBranch = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/branches", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Downtown Branch",
                                "code", "downtown",
                                "email", "downtown@pos.example",
                                "phone", "+355690000010"
                        ))))
                .andExpect(status().isCreated())
                .andReturn());
        UUID branchId = UUID.fromString(createdBranch.get("id").asText());

        JsonNode branchesPage = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/branches", context.restaurantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(branchesPage.get("items")).hasSize(1);
        assertThat(branchesPage.get("items").get(0).get("id").asText()).isEqualTo(branchId.toString());

        UUID branchAddressOneId = UUID.fromString(bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/addresses", context.restaurantId(), branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addressType", "PHYSICAL",
                                "country", "Albania",
                                "city", "Tirana",
                                "streetLine1", "Branch Street 1",
                                "isPrimary", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText());

        UUID branchAddressTwoId = UUID.fromString(bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/addresses", context.restaurantId(), branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addressType", "SHIPPING",
                                "country", "Albania",
                                "city", "Vlore",
                                "streetLine1", "Branch Street 2",
                                "isPrimary", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText());

        JsonNode branchAddresses = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/addresses", context.restaurantId(), branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk())
                .andReturn());
        assertSingleBooleanFlag(branchAddresses, "isPrimary", branchAddressTwoId);

        mockMvc.perform(patch("/restaurants/{restaurantId}/branches/{branchId}/addresses/{addressId}/primary", context.restaurantId(), branchId, branchAddressOneId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk());

        branchAddresses = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/addresses", context.restaurantId(), branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk())
                .andReturn());
        assertSingleBooleanFlag(branchAddresses, "isPrimary", branchAddressOneId);

        UUID branchContactOneId = UUID.fromString(bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/contacts", context.restaurantId(), branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contactType", "GENERAL",
                                "fullName", "Branch General",
                                "email", "branch.general@pos.example",
                                "phone", "+355690000011",
                                "isPrimary", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText());

        UUID branchContactTwoId = UUID.fromString(bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/contacts", context.restaurantId(), branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contactType", "SUPPORT",
                                "fullName", "Branch Support",
                                "email", "branch.support@pos.example",
                                "phone", "+355690000012",
                                "isPrimary", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText());

        JsonNode branchContacts = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/contacts", context.restaurantId(), branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk())
                .andReturn());
        assertSingleBooleanFlag(branchContacts, "isPrimary", branchContactTwoId);

        mockMvc.perform(patch("/restaurants/{restaurantId}/branches/{branchId}/contacts/{contactId}/primary", context.restaurantId(), branchId, branchContactOneId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk());

        branchContacts = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/contacts", context.restaurantId(), branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.ownerAccessToken())))
                .andExpect(status().isOk())
                .andReturn());
        assertSingleBooleanFlag(branchContacts, "isPrimary", branchContactOneId);
    }

    private OwnedRestaurantContext provisionOwnedRestaurant() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        String ownerEmail = "owner." + unique + "@pos.example";
        String ownerUsername = "owner." + unique;
        String restaurantName = "Resource Restaurant " + unique.substring(0, 8);

        String adminAccessToken = bodyOf(webLogin(ADMIN_USERNAME, ADMIN_PASSWORD)).get("accessToken").asText();

        JsonNode createdRestaurant = bodyOf(mockMvc.perform(post("/restaurants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", restaurantName,
                                "legalName", restaurantName + " LLC",
                                "currency", "USD",
                                "timezone", "Europe/Berlin",
                                "owner", Map.of(
                                        "email", ownerEmail,
                                        "username", ownerUsername,
                                        "firstName", "Owner",
                                        "lastName", "Resource",
                                        "clientTarget", "WEB"
                                )
                        ))))
                .andExpect(status().isCreated())
                .andReturn());

        ArgumentCaptor<SimpleMailMessage> inviteMailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(inviteMailCaptor.capture());
        String inviteToken = extractToken(inviteMailCaptor.getValue().getText());

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", inviteToken,
                                "newPassword", OWNER_SETUP_PASSWORD
                        ))))
                .andExpect(status().isNoContent());

        String ownerAccessToken = bodyOf(webLogin(ownerUsername, OWNER_SETUP_PASSWORD)).get("accessToken").asText();
        return new OwnedRestaurantContext(UUID.fromString(createdRestaurant.get("id").asText()), ownerAccessToken);
    }

    private void assertSingleBooleanFlag(JsonNode items, String flagField, UUID expectedTrueId) {
        int trueCount = 0;
        JsonNode expected = null;
        Iterator<JsonNode> iterator = items.elements();
        while (iterator.hasNext()) {
            JsonNode item = iterator.next();
            if (item.get(flagField).asBoolean()) {
                trueCount++;
            }
            if (item.get("id").asText().equals(expectedTrueId.toString())) {
                expected = item;
            }
        }

        assertThat(trueCount).isEqualTo(1);
        assertThat(expected).isNotNull();
        assertThat(expected.get(flagField).asBoolean()).isTrue();
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

    private record OwnedRestaurantContext(UUID restaurantId, String ownerAccessToken) {
    }
}
