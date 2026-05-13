package pos.pos.integration.settings;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.settings.entity.Settings;
import pos.pos.user.entity.User;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("Settings core integration test")
class SettingsCoreIntegrationTest extends AbstractSettingsIntegrationTest {

    @Test
    @DisplayName("SETTINGS-001 GET /restaurants/{restaurantId}/settings creates defaults on first access")
    void settings001GetSettingsCreatesDefaultsOnFirstAccess() throws Exception {
        Restaurant restaurant = createRestaurant("settings001");
        User admin = createRestaurantAdmin(restaurant, "settings001");
        String accessToken = accessTokenFor(admin, "SETTINGS-001");

        MvcResult result = mockMvc.perform(get("/restaurants/{restaurantId}/settings", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("restaurantId").asText()).isEqualTo(restaurant.getId().toString());
        assertThat(body.get("defaultLanguage").asText()).isEqualTo("en");
        assertThat(body.get("dateFormat").asText()).isEqualTo("yyyy-MM-dd");
        assertThat(body.get("timeFormat").asText()).isEqualTo("HH:mm");
        assertThat(body.get("orderSequencePrefix").asText()).isEqualTo("ORD");
        assertThat(body.get("invoiceSequencePrefix").asText()).isEqualTo("INV");
        assertThat(body.get("serviceChargeEnabled").asBoolean()).isFalse();
        assertThat(body.get("allowSplitBills").asBoolean()).isTrue();

        Settings savedSettings = settingsRepository.findByRestaurant_Id(restaurant.getId()).orElseThrow();
        assertThat(savedSettings.getRestaurant().getId()).isEqualTo(restaurant.getId());
    }

    @Test
    @DisplayName("SETTINGS-002 PUT /restaurants/{restaurantId}/settings updates all core fields")
    void settings002PutSettingsUpdatesAllCoreFields() throws Exception {
        Restaurant restaurant = createRestaurant("settings002");
        Branch branch = createBranch(restaurant, "settings002");
        User admin = createRestaurantAdmin(restaurant, "settings002");
        String accessToken = accessTokenFor(admin, "SETTINGS-002");

        MvcResult result = mockMvc.perform(put("/restaurants/{restaurantId}/settings", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("defaultBranchId", branch.getId().toString()),
                                Map.entry("defaultLanguage", "fr-FR"),
                                Map.entry("dateFormat", "dd/MM/yyyy"),
                                Map.entry("timeFormat", "HH:mm:ss"),
                                Map.entry("weekStartDay", "SUNDAY"),
                                Map.entry("orderSequencePrefix", " dine in "),
                                Map.entry("invoiceSequencePrefix", "invoice-2026"),
                                Map.entry("reservationSlotMinutes", 30),
                                Map.entry("defaultTableTurnTimeMinutes", 120),
                                Map.entry("serviceChargeEnabled", true),
                                Map.entry("serviceChargeType", "PERCENTAGE"),
                                Map.entry("serviceChargeValue", 12.5),
                                Map.entry("cashRoundingEnabled", true),
                                Map.entry("cashRoundingIncrement", 0.05),
                                Map.entry("allowSplitBills", false),
                                Map.entry("allowOpenTickets", false),
                                Map.entry("requireCustomerForInvoice", true),
                                Map.entry("enableQrOrdering", true),
                                Map.entry("enableTakeaway", false),
                                Map.entry("enableDelivery", true)
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("defaultBranchId").asText()).isEqualTo(branch.getId().toString());
        assertThat(body.get("defaultLanguage").asText()).isEqualTo("fr-FR");
        assertThat(body.get("weekStartDay").asText()).isEqualTo("SUNDAY");
        assertThat(body.get("orderSequencePrefix").asText()).isEqualTo("DINE_IN");
        assertThat(body.get("invoiceSequencePrefix").asText()).isEqualTo("INVOICE_2026");
        assertThat(body.get("serviceChargeEnabled").asBoolean()).isTrue();
        assertThat(body.get("serviceChargeType").asText()).isEqualTo("PERCENTAGE");
        assertThat(body.get("serviceChargeValue").decimalValue()).isEqualByComparingTo("12.50");
        assertThat(body.get("cashRoundingIncrement").decimalValue()).isEqualByComparingTo("0.05");
        assertThat(body.get("enableDelivery").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("SETTINGS-003 PATCH /restaurants/{restaurantId}/settings/default-branch rejects branches from another restaurant")
    void settings003PatchDefaultBranchRejectsOtherRestaurantBranch() throws Exception {
        Restaurant restaurant = createRestaurant("settings003-a");
        Restaurant otherRestaurant = createRestaurant("settings003-b");
        Branch otherBranch = createBranch(otherRestaurant, "settings003-b");
        User admin = createRestaurantAdmin(restaurant, "settings003");
        String accessToken = accessTokenFor(admin, "SETTINGS-003");

        MvcResult result = mockMvc.perform(patch("/restaurants/{restaurantId}/settings/default-branch", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "defaultBranchId", otherBranch.getId().toString()
                        ))))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(bodyOf(result).get("message").asText()).isEqualTo("Branch not found");
    }

    @Test
    @DisplayName("SETTINGS-004 PATCH /restaurants/{restaurantId}/settings/localization rejects invalid patterns")
    void settings004PatchLocalizationRejectsInvalidPatterns() throws Exception {
        Restaurant restaurant = createRestaurant("settings004");
        User admin = createRestaurantAdmin(restaurant, "settings004");
        String accessToken = accessTokenFor(admin, "SETTINGS-004");

        MvcResult result = mockMvc.perform(patch("/restaurants/{restaurantId}/settings/localization", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "defaultLanguage", "en-US",
                                "dateFormat", "yyyy-MM-dd '",
                                "timeFormat", "HH:mm",
                                "weekStartDay", "MONDAY",
                                "reservationSlotMinutes", 15,
                                "defaultTableTurnTimeMinutes", 90
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(bodyOf(result).get("message").asText()).isEqualTo("dateFormat must be a valid date/time pattern");
    }

    @Test
    @DisplayName("SETTINGS-005 PATCH /restaurants/{restaurantId}/settings/billing clears dependent values when disabled")
    void settings005PatchBillingClearsDependentValuesWhenDisabled() throws Exception {
        Restaurant restaurant = createRestaurant("settings005");
        User admin = createRestaurantAdmin(restaurant, "settings005");
        String accessToken = accessTokenFor(admin, "SETTINGS-005");

        mockMvc.perform(patch("/restaurants/{restaurantId}/settings/billing", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "serviceChargeEnabled", true,
                                "serviceChargeType", "FIXED_AMOUNT",
                                "serviceChargeValue", 5.00,
                                "cashRoundingEnabled", true,
                                "cashRoundingIncrement", 0.10,
                                "allowSplitBills", false,
                                "requireCustomerForInvoice", true
                        ))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(patch("/restaurants/{restaurantId}/settings/billing", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "serviceChargeEnabled", false,
                                "serviceChargeType", "PERCENTAGE",
                                "serviceChargeValue", 12.00,
                                "cashRoundingEnabled", false,
                                "cashRoundingIncrement", 0.05,
                                "allowSplitBills", true,
                                "requireCustomerForInvoice", false
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("serviceChargeEnabled").asBoolean()).isFalse();
        assertThat(body.get("serviceChargeType").isNull()).isTrue();
        assertThat(body.get("serviceChargeValue").isNull()).isTrue();
        assertThat(body.get("cashRoundingEnabled").asBoolean()).isFalse();
        assertThat(body.get("cashRoundingIncrement").isNull()).isTrue();
        assertThat(body.get("allowSplitBills").asBoolean()).isTrue();
        assertThat(body.get("requireCustomerForInvoice").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("SETTINGS-006 PATCH /restaurants/{restaurantId}/settings/order-channels updates ticket and channel flags")
    void settings006PatchOrderChannelsUpdatesTicketAndChannelFlags() throws Exception {
        Restaurant restaurant = createRestaurant("settings006");
        User admin = createRestaurantAdmin(restaurant, "settings006");
        String accessToken = accessTokenFor(admin, "SETTINGS-006");

        MvcResult result = mockMvc.perform(patch("/restaurants/{restaurantId}/settings/order-channels", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "allowOpenTickets", false,
                                "enableQrOrdering", true,
                                "enableTakeaway", false,
                                "enableDelivery", true
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("allowOpenTickets").asBoolean()).isFalse();
        assertThat(body.get("enableQrOrdering").asBoolean()).isTrue();
        assertThat(body.get("enableTakeaway").asBoolean()).isFalse();
        assertThat(body.get("enableDelivery").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("SETTINGS-007 POST /restaurants/{restaurantId}/settings/reset restores defaults")
    void settings007PostResetRestoresDefaults() throws Exception {
        Restaurant restaurant = createRestaurant("settings007");
        Branch branch = createBranch(restaurant, "settings007");
        User admin = createRestaurantAdmin(restaurant, "settings007");
        String accessToken = accessTokenFor(admin, "SETTINGS-007");

        mockMvc.perform(put("/restaurants/{restaurantId}/settings", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("defaultBranchId", branch.getId().toString()),
                                Map.entry("defaultLanguage", "de-DE"),
                                Map.entry("dateFormat", "dd.MM.yyyy"),
                                Map.entry("timeFormat", "HH:mm:ss"),
                                Map.entry("weekStartDay", "SUNDAY"),
                                Map.entry("orderSequencePrefix", "sale"),
                                Map.entry("invoiceSequencePrefix", "bill"),
                                Map.entry("reservationSlotMinutes", 20),
                                Map.entry("defaultTableTurnTimeMinutes", 150),
                                Map.entry("serviceChargeEnabled", true),
                                Map.entry("serviceChargeType", "FIXED_AMOUNT"),
                                Map.entry("serviceChargeValue", 3.00),
                                Map.entry("cashRoundingEnabled", true),
                                Map.entry("cashRoundingIncrement", 0.05),
                                Map.entry("allowSplitBills", false),
                                Map.entry("allowOpenTickets", false),
                                Map.entry("requireCustomerForInvoice", true),
                                Map.entry("enableQrOrdering", true),
                                Map.entry("enableTakeaway", false),
                                Map.entry("enableDelivery", true)
                        ))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/restaurants/{restaurantId}/settings/reset", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("defaultBranchId").isNull()).isTrue();
        assertThat(body.get("defaultLanguage").asText()).isEqualTo("en");
        assertThat(body.get("dateFormat").asText()).isEqualTo("yyyy-MM-dd");
        assertThat(body.get("timeFormat").asText()).isEqualTo("HH:mm");
        assertThat(body.get("orderSequencePrefix").asText()).isEqualTo("ORD");
        assertThat(body.get("invoiceSequencePrefix").asText()).isEqualTo("INV");
        assertThat(body.get("serviceChargeEnabled").asBoolean()).isFalse();
        assertThat(body.get("enableDelivery").asBoolean()).isFalse();
        assertThat(body.get("allowSplitBills").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("SETTINGS-008 GET /restaurants/{restaurantId}/settings rejects admins from another restaurant")
    void settings008GetSettingsRejectsAdminsFromAnotherRestaurant() throws Exception {
        Restaurant restaurant = createRestaurant("settings008-a");
        Restaurant otherRestaurant = createRestaurant("settings008-b");
        User admin = createRestaurantAdmin(restaurant, "settings008");
        String accessToken = accessTokenFor(admin, "SETTINGS-008");

        MvcResult result = mockMvc.perform(get("/restaurants/{restaurantId}/settings", otherRestaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(bodyOf(result).get("message").asText())
                .isEqualTo("You are not allowed to manage settings for this restaurant");
    }
}
