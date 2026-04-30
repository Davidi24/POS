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
import pos.pos.user.entity.User;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

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
@DisplayName("Settings extended integration test")
class SettingsExtendedIntegrationTest extends AbstractSettingsIntegrationTest {

    @Test
    @DisplayName("SETTINGS-101 receipt, order rules, and audit endpoints should work end to end")
    void settings101ReceiptOrderRulesAndAuditShouldWorkEndToEnd() throws Exception {
        Restaurant restaurant = createRestaurant("settings101");
        User admin = createRestaurantAdmin(restaurant, "settings101");
        String accessToken = accessTokenFor(admin, "SETTINGS-101");

        MvcResult receiptResult = mockMvc.perform(put("/restaurants/{restaurantId}/settings/receipt", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("autoPrintCustomerReceipt", true),
                                Map.entry("autoPrintKitchenTicket", true),
                                Map.entry("receiptCopies", 2),
                                Map.entry("showLogo", true),
                                Map.entry("showTaxBreakdown", true),
                                Map.entry("showServerName", true),
                                Map.entry("showTableName", true),
                                Map.entry("showOrderNumber", true),
                                Map.entry("showQrCode", true),
                                Map.entry("printVoidedItems", false),
                                Map.entry("footerNote", "Thank you for dining with us")
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(receiptResult).get("receiptCopies").asInt()).isEqualTo(2);

        MvcResult previewResult = mockMvc.perform(post("/restaurants/{restaurantId}/settings/receipt/preview", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(previewResult).get("previewLines").toString()).contains("Thank you for dining with us");

        MvcResult testPrintResult = mockMvc.perform(post("/restaurants/{restaurantId}/settings/receipt/test-print", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(testPrintResult).get("printPayload").asText()).contains("Demo Receipt");

        mockMvc.perform(put("/restaurants/{restaurantId}/settings/order-rules", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "autoFireToKitchen", true,
                                "allowItemVoid", true,
                                "allowDiscountWithoutManager", false,
                                "allowBackdatedOrders", false,
                                "requireReasonForVoid", true,
                                "requireReasonForDiscount", true,
                                "mergeOrdersEnabled", true,
                                "transferOrdersEnabled", true,
                                "reopenClosedOrdersEnabled", false
                        ))))
                .andExpect(status().isOk());

        MvcResult workflowResult = mockMvc.perform(patch("/restaurants/{restaurantId}/settings/order-rules/workflow", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "autoFireToKitchen", false,
                                "allowBackdatedOrders", true,
                                "mergeOrdersEnabled", false,
                                "transferOrdersEnabled", false,
                                "reopenClosedOrdersEnabled", true
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(workflowResult).get("allowBackdatedOrders").asBoolean()).isTrue();
        assertThat(bodyOf(workflowResult).get("reopenClosedOrdersEnabled").asBoolean()).isTrue();

        MvcResult auditResult = mockMvc.perform(get("/restaurants/{restaurantId}/settings/audit-logs", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode auditBody = bodyOf(auditResult);
        assertThat(auditBody.get("totalElements").asInt()).isGreaterThanOrEqualTo(4);
        assertThat(auditBody.get("items").toString()).contains("SETTINGS_RECEIPT");
    }

    @Test
    @DisplayName("SETTINGS-102 reservation rules should support CRUD reorder and effective views")
    void settings102ReservationRulesShouldSupportCrudReorderAndEffectiveViews() throws Exception {
        Restaurant restaurant = createRestaurant("settings102");
        Branch mainBranch = createBranch(restaurant, "settings102-main");
        Branch patioBranch = createBranch(restaurant, "settings102-patio");
        User admin = createRestaurantAdmin(restaurant, "settings102");
        String accessToken = accessTokenFor(admin, "SETTINGS-102");

        JsonNode globalRule = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/settings/reservation-rules", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("ruleName", "Global Default"),
                                Map.entry("priority", 10),
                                Map.entry("active", true),
                                Map.entry("advanceBookingDays", 30),
                                Map.entry("minPartySize", 1),
                                Map.entry("maxPartySize", 8),
                                Map.entry("defaultDurationMinutes", 90),
                                Map.entry("bufferMinutes", 15),
                                Map.entry("allowOnlineReservations", true),
                                Map.entry("requireDeposit", false),
                                Map.entry("autoConfirmReservations", false),
                                Map.entry("cancellationWindowHours", 24)
                        ))))
                .andExpect(status().isCreated())
                .andReturn());

        JsonNode branchRule = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/settings/reservation-rules", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("branchId", patioBranch.getId().toString()),
                                Map.entry("ruleName", "Patio Large Party"),
                                Map.entry("priority", 1),
                                Map.entry("active", true),
                                Map.entry("advanceBookingDays", 14),
                                Map.entry("minPartySize", 4),
                                Map.entry("maxPartySize", 12),
                                Map.entry("defaultDurationMinutes", 120),
                                Map.entry("bufferMinutes", 20),
                                Map.entry("allowOnlineReservations", true),
                                Map.entry("requireDeposit", true),
                                Map.entry("depositType", "FIXED_AMOUNT"),
                                Map.entry("depositValue", 25.00),
                                Map.entry("autoConfirmReservations", true),
                                Map.entry("cancellationWindowHours", 12)
                        ))))
                .andExpect(status().isCreated())
                .andReturn());

        MvcResult effectivePatioResult = mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/reservation-rules/effective",
                        restaurant.getId(), patioBranch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(effectivePatioResult).get("items")).hasSize(2);

        MvcResult effectiveMainResult = mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/reservation-rules/effective",
                        restaurant.getId(), mainBranch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(effectiveMainResult).get("items")).hasSize(1);

        MvcResult reorderResult = mockMvc.perform(post("/restaurants/{restaurantId}/settings/reservation-rules/reorder", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ruleIds", List.of(
                                        globalRule.get("id").asText(),
                                        branchRule.get("id").asText()
                                )
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(reorderResult).get(0).get("id").asText()).isEqualTo(globalRule.get("id").asText());

        mockMvc.perform(patch("/restaurants/{restaurantId}/settings/reservation-rules/{ruleId}/status",
                        restaurant.getId(), branchRule.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().isOk());

        MvcResult effectiveAfterDisableResult = mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/reservation-rules/effective",
                        restaurant.getId(), patioBranch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(effectiveAfterDisableResult).get("items")).hasSize(1);
    }

    @Test
    @DisplayName("SETTINGS-103 business hours should support replace copy today and effective views")
    void settings103BusinessHoursShouldSupportReplaceCopyTodayAndEffectiveViews() throws Exception {
        Restaurant restaurant = createRestaurant("settings103");
        Branch alphaBranch = createBranch(restaurant, "settings103-alpha");
        Branch betaBranch = createBranch(restaurant, "settings103-beta");
        User admin = createRestaurantAdmin(restaurant, "settings103");
        String accessToken = accessTokenFor(admin, "SETTINGS-103");

        mockMvc.perform(put("/restaurants/{restaurantId}/branches/{branchId}/business-hours", restaurant.getId(), alphaBranch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", openAllWeek("09:00", "17:00")))))
                .andExpect(status().isOk());

        MvcResult copyResult = mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/business-hours/copy-to-other-branches",
                        restaurant.getId(), alphaBranch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetBranchIds", List.of(betaBranch.getId().toString())
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(copyResult).get("copiedDaysPerBranch").asInt()).isEqualTo(7);

        MvcResult betaHoursResult = mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/business-hours", restaurant.getId(), betaBranch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(betaHoursResult)).hasSize(7);
        assertThat(bodyOf(betaHoursResult).get(0).get("openTime").asText()).startsWith("09:00");

        MvcResult todayResult = mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/business-hours/today",
                        restaurant.getId(), betaBranch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(todayResult).get("specialHoursApplied").asBoolean()).isFalse();
        assertThat(bodyOf(todayResult).get("openTime").asText()).startsWith("09:00");

        MvcResult effectiveResult = mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/business-hours/effective",
                        restaurant.getId(), betaBranch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(effectiveResult).get("weeklySchedule")).hasSize(7);
    }

    @Test
    @DisplayName("SETTINGS-104 special hours should support CRUD bulk calendar and today override")
    void settings104SpecialHoursShouldSupportCrudBulkCalendarAndTodayOverride() throws Exception {
        Restaurant restaurant = createRestaurant("settings104");
        Branch branch = createBranch(restaurant, "settings104");
        User admin = createRestaurantAdmin(restaurant, "settings104");
        String accessToken = accessTokenFor(admin, "SETTINGS-104");
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Berlin"));
        LocalDate tomorrow = today.plusDays(1);
        LocalDate nextDay = today.plusDays(2);

        mockMvc.perform(put("/restaurants/{restaurantId}/branches/{branchId}/business-hours", restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", openAllWeek("09:00", "17:00")))))
                .andExpect(status().isOk());

        JsonNode todaySpecialHour = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/special-hours",
                        restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "specialDate", today.toString(),
                                "closed", true,
                                "note", "Public holiday"
                        ))))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/special-hours/bulk",
                        restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(
                                        Map.of(
                                                "specialDate", tomorrow.toString(),
                                                "openTime", "10:00",
                                                "closeTime", "15:00",
                                                "closed", false,
                                                "note", "Short day"
                                        ),
                                        Map.of(
                                                "specialDate", nextDay.toString(),
                                                "closed", true,
                                                "note", "Maintenance"
                                        )
                                )
                        ))))
                .andExpect(status().isOk());

        MvcResult todayResult = mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/business-hours/today",
                        restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(todayResult).get("specialHoursApplied").asBoolean()).isTrue();
        assertThat(bodyOf(todayResult).get("closed").asBoolean()).isTrue();

        MvcResult calendarResult = mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/special-hours/calendar",
                        restaurant.getId(), branch.getId())
                        .queryParam("startDate", today.toString())
                        .queryParam("endDate", nextDay.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(calendarResult).get("items")).hasSize(3);

        mockMvc.perform(delete("/restaurants/{restaurantId}/branches/{branchId}/special-hours/bulk", restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "specialHourIds", List.of(todaySpecialHour.get("id").asText())
                        ))))
                .andExpect(status().isNoContent());

        MvcResult afterDeleteResult = mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/special-hours", restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(afterDeleteResult)).hasSize(2);
    }

    @Test
    @DisplayName("SETTINGS-105 export import clone validate and templates should share the same payload format")
    void settings105ExportImportCloneValidateAndTemplatesShouldShareTheSamePayloadFormat() throws Exception {
        Restaurant sourceRestaurant = createRestaurant("settings105-source");
        Restaurant importTargetRestaurant = createRestaurant("settings105-import");
        Restaurant cloneTargetRestaurant = createRestaurant("settings105-clone");
        Restaurant templateTargetRestaurant = createRestaurant("settings105-template");

        Branch sourceBranch = setBranchCode(createBranch(sourceRestaurant, "settings105-source"), "MAIN");
        Branch importTargetBranch = setBranchCode(createBranch(importTargetRestaurant, "settings105-import"), "MAIN");
        Branch cloneTargetBranch = setBranchCode(createBranch(cloneTargetRestaurant, "settings105-clone"), "MAIN");
        Branch templateTargetBranch = setBranchCode(createBranch(templateTargetRestaurant, "settings105-template"), "MAIN");

        User superAdmin = superAdminUser();
        String accessToken = accessTokenFor(superAdmin, "SETTINGS-105");

        mockMvc.perform(put("/restaurants/{restaurantId}/settings", sourceRestaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("defaultBranchId", sourceBranch.getId().toString()),
                                Map.entry("defaultLanguage", "de-DE"),
                                Map.entry("dateFormat", "dd.MM.yyyy"),
                                Map.entry("timeFormat", "HH:mm:ss"),
                                Map.entry("weekStartDay", "MONDAY"),
                                Map.entry("orderSequencePrefix", "sale"),
                                Map.entry("invoiceSequencePrefix", "invoice"),
                                Map.entry("reservationSlotMinutes", 20),
                                Map.entry("defaultTableTurnTimeMinutes", 100),
                                Map.entry("serviceChargeEnabled", true),
                                Map.entry("serviceChargeType", "FIXED_AMOUNT"),
                                Map.entry("serviceChargeValue", 3.50),
                                Map.entry("cashRoundingEnabled", true),
                                Map.entry("cashRoundingIncrement", 0.05),
                                Map.entry("allowSplitBills", false),
                                Map.entry("allowOpenTickets", false),
                                Map.entry("requireCustomerForInvoice", true),
                                Map.entry("enableQrOrdering", true),
                                Map.entry("enableTakeaway", true),
                                Map.entry("enableDelivery", false)
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/restaurants/{restaurantId}/settings/receipt", sourceRestaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("autoPrintCustomerReceipt", true),
                                Map.entry("autoPrintKitchenTicket", false),
                                Map.entry("receiptCopies", 2),
                                Map.entry("showLogo", true),
                                Map.entry("showTaxBreakdown", true),
                                Map.entry("showServerName", true),
                                Map.entry("showTableName", true),
                                Map.entry("showOrderNumber", true),
                                Map.entry("showQrCode", false),
                                Map.entry("printVoidedItems", false),
                                Map.entry("footerNote", "Portable payload footer")
                        ))))
                .andExpect(status().isOk());

        JsonNode exportBody = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/settings/export", sourceRestaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn());

        JsonNode payload = exportBody.get("payload");
        assertThat(payload.get("core").get("defaultBranchCode").asText()).isEqualTo("MAIN");

        MvcResult validateResult = mockMvc.perform(post("/restaurants/{restaurantId}/settings/validate", importTargetRestaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.toString()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(validateResult).get("valid").asBoolean()).isTrue();

        mockMvc.perform(post("/restaurants/{restaurantId}/settings/import", importTargetRestaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.toString()))
                .andExpect(status().isOk());

        MvcResult importedSettingsResult = mockMvc.perform(get("/restaurants/{restaurantId}/settings", importTargetRestaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(importedSettingsResult).get("defaultBranchId").asText()).isEqualTo(importTargetBranch.getId().toString());
        assertThat(bodyOf(importedSettingsResult).get("defaultLanguage").asText()).isEqualTo("de-DE");

        mockMvc.perform(post("/restaurants/{restaurantId}/settings/clone-from-restaurant/{sourceRestaurantId}",
                        cloneTargetRestaurant.getId(), sourceRestaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk());

        MvcResult clonedSettingsResult = mockMvc.perform(get("/restaurants/{restaurantId}/settings", cloneTargetRestaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(clonedSettingsResult).get("defaultBranchId").asText()).isEqualTo(cloneTargetBranch.getId().toString());

        JsonNode templateBody = bodyOf(mockMvc.perform(post("/settings/templates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "templateName", "German Default",
                                "description", "Portable settings template",
                                "payload", objectMapper.readTree(payload.toString())
                        ))))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/restaurants/{restaurantId}/settings/apply-template/{templateId}",
                        templateTargetRestaurant.getId(), templateBody.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk());

        MvcResult templateSettingsResult = mockMvc.perform(get("/restaurants/{restaurantId}/settings", templateTargetRestaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(templateSettingsResult).get("defaultBranchId").asText()).isEqualTo(templateTargetBranch.getId().toString());
        assertThat(bodyOf(templateSettingsResult).get("defaultLanguage").asText()).isEqualTo("de-DE");
    }

    @Test
    @DisplayName("SETTINGS-106 printers and branch devices should support CRUD routing and status updates")
    void settings106PrintersAndBranchDevicesShouldSupportCrudRoutingAndStatusUpdates() throws Exception {
        Restaurant restaurant = createRestaurant("settings106");
        Branch branch = createBranch(restaurant, "settings106");
        User admin = createRestaurantAdmin(restaurant, "settings106");
        String accessToken = accessTokenFor(admin, "SETTINGS-106");

        JsonNode printer = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/settings/printers", restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("code", "PRN_MAIN"),
                                Map.entry("name", "Main Receipt Printer"),
                                Map.entry("deviceType", "PRINTER"),
                                Map.entry("status", "ACTIVE"),
                                Map.entry("active", true),
                                Map.entry("online", true),
                                Map.entry("printerConnectionType", "NETWORK"),
                                Map.entry("paperWidthMm", 80),
                                Map.entry("printerIp", "192.168.1.20"),
                                Map.entry("printerPort", 9100),
                                Map.entry("autoCut", true),
                                Map.entry("cashDrawerKickEnabled", true)
                        ))))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(put("/restaurants/{restaurantId}/branches/{branchId}/printer-routes", restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "printerIds", List.of(printer.get("id").asText())
                        ))))
                .andExpect(status().isOk());

        MvcResult routesResult = mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/printer-routes",
                        restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(routesResult).get("printerIds")).hasSize(1);

        MvcResult routeTestResult = mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/printer-routes/test",
                        restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(routeTestResult).get("printerCount").asInt()).isEqualTo(1);

        JsonNode device = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/devices",
                        restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("code", "TERM_1"),
                                Map.entry("name", "POS Terminal 1"),
                                Map.entry("deviceType", "TERMINAL"),
                                Map.entry("status", "PROVISIONING"),
                                Map.entry("active", true),
                                Map.entry("online", false)
                        ))))
                .andExpect(status().isCreated())
                .andReturn());

        MvcResult updatedDeviceResult = mockMvc.perform(patch("/restaurants/{restaurantId}/branches/{branchId}/devices/{deviceId}/status",
                        restaurant.getId(), branch.getId(), device.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "ACTIVE",
                                "active", true,
                                "online", true
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(updatedDeviceResult).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(bodyOf(updatedDeviceResult).get("online").asBoolean()).isTrue();
    }

    private Branch setBranchCode(Branch branch, String code) {
        branch.setCode(code);
        return branchRepository.save(branch);
    }

    private List<Map<String, Object>> openAllWeek(String openTime, String closeTime) {
        return IntStream.rangeClosed(1, 7)
                .mapToObj(dayOfWeek -> Map.<String, Object>of(
                        "dayOfWeek", dayOfWeek,
                        "openTime", openTime,
                        "closeTime", closeTime,
                        "closed", false,
                        "overnight", false
                ))
                .toList();
    }
}
