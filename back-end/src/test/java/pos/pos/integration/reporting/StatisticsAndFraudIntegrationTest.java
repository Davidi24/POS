package pos.pos.integration.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One realistic day at a restaurant — paid, discounted, refunded, cancelled and hand-marked orders — then checks that
 * Statistics adds it up exactly and Fraud Detection flags exactly the risky actions.
 */
@DisplayName("Statistics and fraud detection on real data")
class StatisticsAndFraudIntegrationTest extends AbstractPosApiIntegrationTest {

    private record Day(World world, User waiter, String waiterToken, User manager, String managerToken, UUID itemId,
                       Map<String, UUID> orders, Map<String, UUID> payments) {
    }

    private Day day(String label) throws Exception {
        World world = newWorld(label);
        Map<String, UUID> menu = menu(world, "Lunch");
        UUID itemId = item(world, menu, "Burger", "12.50");
        User waiter = staff(world, "waiter", "WAITER");
        User manager = staff(world, "manager", "MANAGER");
        String waiterToken = token(waiter);
        String managerToken = token(manager);
        UUID r = world.restaurantId();
        Map<String, UUID> orders = new HashMap<>();
        Map<String, UUID> payments = new HashMap<>();

        // A: two burgers, card with a 5.00 tip, later 20.00 refunded by the manager.
        UUID a = id(order(world, waiterToken, Map.of(itemId, 2)));
        payments.put("A", paymentId(post("/restaurants/{r}/orders/{o}/payments", r, a).as(waiterToken)
                .body(Map.of("method", "CARD", "amount", new BigDecimal("25.00"), "tipAmount", new BigDecimal("5.00"))).expect(201)));
        post("/restaurants/{r}/orders/{o}/payments/{p}/refund", r, a, payments.get("A")).as(managerToken)
                .body(Map.of("amount", new BigDecimal("20.00"), "reason", "Burger was cold")).expect(200);
        orders.put("A", a);

        // B: one burger with a 50% discount from the owner, paid cash, 1.00 refunded in cash.
        UUID b = id(order(world, waiterToken, Map.of(itemId, 1)));
        post("/restaurants/{r}/orders/{o}/discounts", r, b).as(world.ownerToken())
                .body(Map.of("name", "Friend", "discountType", "PERCENTAGE", "discountValue", 50, "reason", "Friend of the owner")).expect(201);
        payments.put("B", paymentId(post("/restaurants/{r}/orders/{o}/payments", r, b).as(waiterToken)
                .body(Map.of("method", "CASH", "amount", new BigDecimal("6.25"))).expect(201)));
        post("/restaurants/{r}/orders/{o}/payments/{p}/refund", r, b, payments.get("B")).as(managerToken)
                .body(Map.of("amount", new BigDecimal("1.00"), "reason", "Coins")).expect(200);
        orders.put("B", b);

        // C: sent to the kitchen, the burger removed by the manager, then the order cancelled.
        JsonNode c = order(world, waiterToken, Map.of(itemId, 1));
        UUID cId = id(c);
        post("/restaurants/{r}/orders/{o}/send-to-kitchen", r, cId).as(waiterToken).expect(200);
        UUID lineId = UUID.fromString(c.get("lineItems").get(0).get("id").asText());
        post("/restaurants/{r}/orders/{o}/items/{l}/void", r, cId, lineId).as(managerToken).body(Map.of("reason", "Wrong table")).expect(200);
        post("/restaurants/{r}/orders/{o}/cancel", r, cId).as(managerToken).body(Map.of("reason", "Guest left")).expect(200);
        orders.put("C", cId);

        // D: one burger with a 50% tip.
        UUID d = id(order(world, waiterToken, Map.of(itemId, 1)));
        payments.put("D", paymentId(post("/restaurants/{r}/orders/{o}/payments", r, d).as(waiterToken)
                .body(Map.of("method", "CARD", "amount", new BigDecimal("12.50"), "tipAmount", new BigDecimal("6.25"))).expect(201)));
        orders.put("D", d);

        // H: bill and tip are both fully refunded; reports must not count the refunded tip as staff earnings.
        UUID h = id(order(world, waiterToken, Map.of(itemId, 1)));
        payments.put("H", paymentId(post("/restaurants/{r}/orders/{o}/payments", r, h).as(waiterToken)
                .body(Map.of("method", "CARD", "amount", new BigDecimal("12.50"), "tipAmount", new BigDecimal("2.00"))).expect(201)));
        post("/restaurants/{r}/orders/{o}/payments/{p}/refund", r, h, payments.get("H")).as(managerToken)
                .body(Map.of("amount", new BigDecimal("14.50"), "reason", "Full refund including tip")).expect(200);
        orders.put("H", h);

        // E: marked paid by hand without any payment (stays open).
        UUID e = id(order(world, waiterToken, Map.of(itemId, 1)));
        post("/restaurants/{r}/orders/{o}/mark-paid", r, e).as(waiterToken).body(Map.of("note", "Paid at the bar")).expect(200);
        orders.put("E", e);

        // F: closed as an open tab without paying.
        UUID f = id(order(world, waiterToken, Map.of(itemId, 1)));
        post("/restaurants/{r}/orders/{o}/close", r, f).as(waiterToken).body(Map.of()).expect(200);
        orders.put("F", f);

        // G: paid then the payment cancelled the same day by the manager (order opens again).
        UUID g = id(order(world, waiterToken, Map.of(itemId, 1)));
        payments.put("G", paymentId(post("/restaurants/{r}/orders/{o}/payments", r, g).as(waiterToken)
                .body(Map.of("method", "CASH", "amount", new BigDecimal("12.50"))).expect(201)));
        post("/restaurants/{r}/orders/{o}/payments/{p}/void", r, g, payments.get("G")).as(managerToken)
                .body(Map.of("reason", "Rang it on the wrong order")).expect(200);
        orders.put("G", g);

        return new Day(world, waiter, waiterToken, manager, managerToken, itemId, orders, payments);
    }

    private static UUID paymentId(JsonNode taken) {
        return UUID.fromString(taken.get("payment").get("id").asText());
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Europe/Rome"));
    }

    @Test
    @DisplayName("statistics add up sales, tips, refunds, discounts, removals and staff exactly")
    void statistics() throws Exception {
        Day day = day("stats");
        UUID r = day.world().restaurantId();
        String owner = day.world().ownerToken();
        LocalDate from = today().minusDays(1);
        LocalDate to = today().plusDays(1);

        JsonNode overview = get("/restaurants/{r}/statistics/overview", r).as(owner).param("from", from).param("to", to).expect(200);
        JsonNode kpis = overview.get("kpis");
        // Closed orders: A 25.00, B 6.25, D 12.50, F 12.50, H 12.50.
        assertThat(money(kpis.get("sales"), "value")).isEqualByComparingTo("68.75");
        assertThat(kpis.get("orders").get("value").asInt()).isEqualTo(5);
        assertThat(money(kpis.get("averageTicket"), "value")).isEqualByComparingTo("13.75");
        assertThat(money(kpis.get("tips"), "value")).isEqualByComparingTo("11.25");
        assertThat(money(kpis.get("refunds"), "value")).isEqualByComparingTo("35.50");
        // Taken: A 30.00 + B 6.25 + D 18.75 + H 14.50 (G was cancelled) = 69.50, minus 35.50 refunded.
        assertThat(money(kpis.get("collected"), "value")).isEqualByComparingTo("34.00");
        assertThat(money(kpis.get("discounts"), "value")).isEqualByComparingTo("6.25");
        assertThat(money(kpis.get("removedItemsValue"), "value")).isEqualByComparingTo("12.50");
        assertThat(kpis.get("cancelledOrders").get("value").asInt()).isEqualTo(1);
        assertThat(kpis.get("openOrders").asInt()).isEqualTo(2);
        assertThat(money(kpis.get("sales"), "previous")).isEqualByComparingTo("0");
        assertThat(overview.get("salesByDay")).hasSize(3);
        assertThat(overview.get("period").get("currency").asText()).isEqualTo("EUR");
        List<String> methods = new ArrayList<>();
        overview.get("paymentMethods").forEach(method -> methods.add(method.get("method").asText()));
        assertThat(methods).containsExactlyInAnyOrder("CARD", "CASH");
        assertThat(overview.get("topItems").get(0).get("name").asText()).isEqualTo("Burger");
        assertThat(overview.get("topItems").get(0).get("quantity").asInt()).isEqualTo(6);

        JsonNode sales = get("/restaurants/{r}/statistics/sales", r).as(owner).param("from", from).param("to", to)
                .param("branchId", day.world().branchId()).expect(200);
        assertThat(sales.get("salesByHour")).hasSize(24);
        assertThat(sales.get("salesByWeekday")).hasSize(7);
        assertThat(sales.get("sections").get(0).get("section").asText()).isEqualTo("Mains");
        assertThat(sales.get("orderTypes").get(0).get("key").asText()).isEqualTo("TAKEAWAY");
        assertThat(sales.get("slowItems")).isNotEmpty();

        JsonNode staff = get("/restaurants/{r}/statistics/staff", r).as(owner).param("from", from).param("to", to).expect(200);
        Map<String, JsonNode> byId = new HashMap<>();
        staff.get("staff").forEach(person -> byId.put(person.get("staffId").asText(), person));
        JsonNode waiter = byId.get(day.waiter().getId().toString());
        assertThat(waiter.get("orders").asInt()).isEqualTo(5);
        assertThat(money(waiter, "sales")).isEqualByComparingTo("68.75");
        assertThat(money(waiter, "tips")).isEqualByComparingTo("11.25");
        JsonNode manager = byId.get(day.manager().getId().toString());
        assertThat(manager.get("removedItems").asInt()).isEqualTo(1);
        assertThat(money(manager, "refunds")).isEqualByComparingTo("35.50");
        JsonNode ownerRow = byId.get(day.world().owner().getId().toString());
        assertThat(money(ownerRow, "discounts")).isEqualByComparingTo("6.25");

        // CSV downloads, with a dish name that tries to be a spreadsheet formula.
        Map<String, UUID> menu = menu(day.world(), "Formula menu");
        UUID evil = item(day.world(), menu, "=HYPERLINK(\"http://evil\",\"x\")", "1.00");
        UUID evilOrder = id(order(day.world(), day.waiterToken(), Map.of(evil, 1)));
        post("/restaurants/{r}/orders/{o}/payments", r, evilOrder).as(day.waiterToken())
                .body(Map.of("method", "CARD", "amount", new BigDecimal("1.00"))).expect(201);
        Response items = get("/restaurants/{r}/statistics/reports/{code}.csv", r, "items").as(owner).param("from", from).param("to", to).send();
        assertThat(items.status()).isEqualTo(200);
        assertThat(items.header("Content-Disposition")).contains("attachment").contains(".csv");
        String csv = items.text();
        assertThat(csv).startsWith("﻿dish,section,quantity,sales,currency");
        assertThat(csv).contains("\"'=HYPERLINK(");
        assertThat(csv).doesNotContain("\n=HYPERLINK");
        for (String code : List.of("daily-sales", "staff", "payments")) {
            assertThat(get("/restaurants/{r}/statistics/reports/{code}.csv", r, code).as(owner).param("from", from).param("to", to)
                    .send().status()).as(code).isEqualTo(200);
        }
        assertThat(get("/restaurants/{r}/statistics/reports/{code}.csv", r, "passwords").as(owner).param("from", from).param("to", to)
                .send().status()).isEqualTo(404);
        JsonNode reports = get("/restaurants/{r}/statistics/reports", r).as(owner).expect(200);
        assertThat(reports).hasSize(4);
    }

    @Test
    @DisplayName("statistics need REPORTS_READ, the restaurant's own staff and a sensible period")
    void statisticsAccess() throws Exception {
        World world = newWorld("stats-access");
        World other = newWorld("stats-other");
        User waiter = staff(world, "waiter", "WAITER");
        User manager = staff(world, "manager", "MANAGER");
        User admin = staff(world, "admin", "ADMIN");
        UUID r = world.restaurantId();
        LocalDate today = today();

        get("/restaurants/{r}/statistics/overview", r).as(token(waiter)).param("from", today).param("to", today).expect(403);
        get("/restaurants/{r}/statistics/overview", r).as(token(manager)).param("from", today).param("to", today).expect(403);
        get("/restaurants/{r}/statistics/overview", r).as(token(admin)).param("from", today).param("to", today).expect(200);
        assertThat(get("/restaurants/{r}/statistics/overview", r).as(other.ownerToken()).param("from", today).param("to", today)
                .send().status()).isEqualTo(403);
        get("/restaurants/{r}/statistics/overview", r).param("from", today).param("to", today).expect(401);

        String owner = world.ownerToken();
        get("/restaurants/{r}/statistics/overview", r).as(owner).param("from", today).expect(400);
        get("/restaurants/{r}/statistics/overview", r).as(owner).param("from", today).param("to", today.minusDays(1)).expect(400);
        get("/restaurants/{r}/statistics/overview", r).as(owner).param("from", today.minusDays(366)).param("to", today).expect(400);
        get("/restaurants/{r}/statistics/overview", r).as(owner).param("from", "yesterday").param("to", today).expect(400);
        get("/restaurants/{r}/statistics/overview", r).as(owner).param("from", today).param("to", today).param("branchId", "x").expect(400);
        assertThat(get("/restaurants/{r}/statistics/overview", r).as(owner).param("from", today).param("to", today)
                .param("branchId", other.branchId()).send().status()).isIn(403, 404);
        JsonNode empty = get("/restaurants/{r}/statistics/overview", r).as(owner).param("from", today.minusDays(365)).param("to", today).expect(200);
        assertThat(money(empty.get("kpis").get("sales"), "value")).isEqualByComparingTo("0");
        assertThat(empty.get("salesByDay")).hasSize(366);
    }

    @Test
    @DisplayName("fraud detection flags exactly the risky actions, and owners review them")
    void fraud() throws Exception {
        Day day = day("fraud");
        UUID r = day.world().restaurantId();
        String owner = day.world().ownerToken();
        setting(day.world(), "fraud_refund_amount", new BigDecimal("10.00"));
        setting(day.world(), "fraud_voids_per_day", 1);
        setting(day.world(), "fraud_cash_refunds_per_day", 1);
        LocalDate from = today().minusDays(1);
        LocalDate to = today().plusDays(1);

        JsonNode page = get("/restaurants/{r}/fraud/alerts", r).as(owner).param("from", from).param("to", to).param("size", 100).expect(200);
        Map<String, List<JsonNode>> byRule = new HashMap<>();
        page.get("items").forEach(alert -> byRule.computeIfAbsent(alert.get("rule").asText(), key -> new ArrayList<>()).add(alert));

        assertThat(byRule.get("LARGE_DISCOUNT")).singleElement()
                .satisfies(alert -> assertThat(alert.get("detail").asText()).contains("50% discount").contains("Friend of the owner"));
        assertThat(byRule.get("ITEM_VOID_AFTER_KITCHEN")).singleElement()
                .satisfies(alert -> assertThat(alert.get("staffId").asText()).isEqualTo(day.manager().getId().toString()));
        assertThat(byRule.get("CANCELLED_AFTER_KITCHEN")).singleElement()
                .satisfies(alert -> assertThat(alert.get("orderId").asText()).isEqualTo(day.orders().get("C").toString()));
        assertThat(byRule.get("MANY_VOIDS")).singleElement();
        assertThat(byRule.get("LARGE_REFUND")).hasSize(2)
                .extracting(alert -> alert.get("amount").asDouble())
                .containsExactlyInAnyOrder(20.0, 14.5);
        assertThat(byRule.get("CASH_REFUNDS")).singleElement();
        assertThat(byRule.get("PAYMENT_VOIDED")).singleElement()
                .satisfies(alert -> assertThat(alert.get("detail").asText()).contains("Rang it on the wrong order"));
        assertThat(byRule.get("HIGH_TIP")).singleElement()
                .satisfies(alert -> assertThat(alert.get("detail").asText()).contains("50% tip"));
        assertThat(byRule.get("MARKED_PAID_MANUALLY")).singleElement()
                .satisfies(alert -> assertThat(alert.get("orderId").asText()).isEqualTo(day.orders().get("E").toString()));
        assertThat(byRule.get("CLOSED_WITHOUT_PAYMENT")).singleElement()
                .satisfies(alert -> assertThat(alert.get("orderId").asText()).isEqualTo(day.orders().get("F").toString()));
        assertThat(byRule).doesNotContainKeys("ORDER_VOIDED_AFTER_KITCHEN", "REOPENED_PAID_ORDER", "OFF_SHIFT_ACTION");
        assertThat(page.get("truncated").asBoolean()).isFalse();

        // Review one: it stays reviewed on the next read.
        String key = byRule.get("LARGE_REFUND").stream()
                .filter(alert -> money(alert, "amount").compareTo(new BigDecimal("20.00")) == 0)
                .findFirst().orElseThrow().get("key").asText();
        JsonNode reviewed = put("/restaurants/{r}/fraud/alerts/{key}/review", r, key).as(owner)
                .body(Map.of("status", "DISMISSED", "note", "Guest complained, fine")).expect(200);
        assertThat(reviewed.get("status").asText()).isEqualTo("DISMISSED");
        JsonNode open = get("/restaurants/{r}/fraud/alerts", r).as(owner).param("from", from).param("to", to).param("status", "OPEN")
                .param("size", 100).expect(200);
        assertThat(open.get("items")).noneMatch(alert -> alert.get("key").asText().equals(key));
        JsonNode dismissed = get("/restaurants/{r}/fraud/alerts", r).as(owner).param("from", from).param("to", to).param("status", "DISMISSED").expect(200);
        assertThat(dismissed.get("items")).singleElement()
                .satisfies(alert -> assertThat(alert.get("reviewNote").asText()).isEqualTo("Guest complained, fine"));

        JsonNode overview = get("/restaurants/{r}/fraud/overview", r).as(owner).param("from", from).param("to", to).expect(200);
        assertThat(overview.get("totalAlerts").asInt()).isEqualTo(page.get("totalElements").asInt());
        assertThat(overview.get("openAlerts").asInt()).isEqualTo(page.get("totalElements").asInt() - 1);
        assertThat(overview.get("staff")).isNotEmpty();

        // The activity feed lists every sensitive action, flagged or not.
        JsonNode activity = get("/restaurants/{r}/fraud/activity", r).as(owner).param("from", from).param("to", to).param("size", 100).expect(200);
        List<String> types = new ArrayList<>();
        activity.get("items").forEach(item -> types.add(item.get("type").asText()));
        assertThat(types).contains("DISCOUNT", "ITEM_REMOVED", "ORDER_CANCELLED", "REFUND", "PAYMENT_CANCELLED", "ORDER_REOPENED");
        assertThat(types).filteredOn("REFUND"::equals).hasSize(3);
        JsonNode refunds = get("/restaurants/{r}/fraud/activity", r).as(owner).param("from", from).param("to", to).param("type", "refund").expect(200);
        assertThat(refunds.get("totalElements").asInt()).isEqualTo(3);
        JsonNode pageOne = get("/restaurants/{r}/fraud/activity", r).as(owner).param("from", from).param("to", to).param("size", 2).expect(200);
        assertThat(pageOne.get("items")).hasSize(2);
        assertThat(pageOne.get("hasNext").asBoolean()).isTrue();

        // Switching a check off removes its alerts; a clock-in history makes actions off the clock show up.
        patch("/restaurants/{r}/settings/fraud-checks", r).as(owner).body(Map.of(
                "fraudDiscountPercent", 30, "fraudRefundAmount", 10, "fraudVoidsPerDay", 1, "fraudTipPercent", 30,
                "fraudCashRefundsPerDay", 1, "fraudDisabledRules", List.of("HIGH_TIP"))).expect(200);
        jdbcTemplate.update("insert into " + SCHEMA + ".shifts (id, restaurant_id, branch_id, user_id, status, started_at, ended_at,"
                        + " regular_minutes, overtime_minutes, declared_cash_tips, declared_card_tips, sales_total, cash_sales_total,"
                        + " card_sales_total, opening_drawer_amount, expected_drawer_amount, created_at, updated_at, version)"
                        + " values (?, ?, ?, ?, 'CLOSED', now() - interval '3 days', now() - interval '3 days' + interval '4 hours',"
                        + " 0, 0, 0, 0, 0, 0, 0, 0, 0, now(), now(), 0)",
                UUID.randomUUID(), r, day.world().branchId(), day.manager().getId());
        JsonNode after = get("/restaurants/{r}/fraud/alerts", r).as(owner).param("from", from).param("to", to).param("size", 100).expect(200);
        List<String> rules = new ArrayList<>();
        after.get("items").forEach(alert -> rules.add(alert.get("rule").asText()));
        assertThat(rules).doesNotContain("HIGH_TIP").contains("OFF_SHIFT_ACTION");
        JsonNode ruleList = get("/restaurants/{r}/fraud/rules", r).as(owner).expect(200);
        assertThat(ruleList.get("rules")).anySatisfy(rule -> {
            assertThat(rule.get("rule").asText()).isEqualTo("HIGH_TIP");
            assertThat(rule.get("enabled").asBoolean()).isFalse();
        });
        patch("/restaurants/{r}/settings/fraud-checks", r).as(owner).body(Map.of(
                "fraudDiscountPercent", 30, "fraudRefundAmount", 10, "fraudVoidsPerDay", 1, "fraudTipPercent", 30,
                "fraudCashRefundsPerDay", 1, "fraudDisabledRules", List.of("NOT_A_RULE"))).expect(400);
        patch("/restaurants/{r}/settings/fraud-checks", r).as(owner).body(Map.of(
                "fraudDiscountPercent", 0, "fraudRefundAmount", 10, "fraudVoidsPerDay", 1, "fraudTipPercent", 30,
                "fraudCashRefundsPerDay", 1, "fraudDisabledRules", List.of())).expect(400);
    }

    @Test
    @DisplayName("fraud detection is only for owners and co-owners of that restaurant")
    void fraudAccess() throws Exception {
        World world = newWorld("fraud-access");
        World other = newWorld("fraud-other");
        UUID r = world.restaurantId();
        LocalDate today = today();
        User coOwner = staff(world, "coowner", "CO_OWNER");
        User admin = staff(world, "admin", "ADMIN");
        User manager = staff(world, "manager", "MANAGER");

        get("/restaurants/{r}/fraud/overview", r).as(token(coOwner)).param("from", today).param("to", today).expect(200);
        get("/restaurants/{r}/fraud/overview", r).as(token(admin)).param("from", today).param("to", today).expect(403);
        get("/restaurants/{r}/fraud/overview", r).as(token(manager)).param("from", today).param("to", today).expect(403);
        put("/restaurants/{r}/fraud/alerts/{key}/review", r, "HIGH_TIP:" + UUID.randomUUID()).as(token(manager))
                .body(Map.of("status", "REVIEWED")).expect(403);
        assertThat(get("/restaurants/{r}/fraud/overview", r).as(other.ownerToken()).param("from", today).param("to", today)
                .send().status()).isEqualTo(403);
        get("/restaurants/{r}/fraud/overview", r).as(world.ownerToken()).param("from", today.minusDays(92)).param("to", today).expect(400);
        put("/restaurants/{r}/fraud/alerts/{key}/review", r, "nonsense").as(world.ownerToken()).body(Map.of("status", "REVIEWED")).expect(400);
        put("/restaurants/{r}/fraud/alerts/{key}/review", r, "HIGH_TIP:" + UUID.randomUUID()).as(world.ownerToken())
                .body(Map.of("status", "MAYBE")).expect(400);
        put("/restaurants/{r}/fraud/alerts/{key}/review", r, "HIGH_TIP:" + UUID.randomUUID()).as(world.ownerToken())
                .body(Map.of("status", "REVIEWED", "note", "n".repeat(1001))).expect(400);
    }
}
