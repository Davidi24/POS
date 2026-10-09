package pos.pos.integration.order;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.user.entity.User;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Orders end to end")
class OrderLifecycleIntegrationTest extends AbstractPosApiIntegrationTest {

    private static final String ORDER = "/restaurants/{r}/orders/{o}";

    private record Setup(World world, Map<String, UUID> menu, UUID pasta, UUID water, User waiter, String waiterToken,
                         User manager, String managerToken) {
    }

    private Setup setup(String label) throws Exception {
        World world = newWorld(label);
        Map<String, UUID> menu = menu(world, "Dinner");
        UUID pasta = item(world, menu, "Pasta", "12.00");
        UUID water = item(world, menu, "Water", "2.50", false);
        User waiter = staff(world, "waiter", "WAITER");
        User manager = staff(world, "manager", "MANAGER");
        return new Setup(world, menu, pasta, water, waiter, token(waiter), manager, token(manager));
    }

    private UUID table(World world, String number) throws Exception {
        Response response = post("/restaurants/{r}/branches/{b}/tables", world.restaurantId(), world.branchId()).as(world.ownerToken())
                .body(Map.of("tableNumber", number, "capacity", 4, "floor", "Main", "positionX", 10, "positionY", 20)).send();
        assertThat(response.status()).as(response.text()).isIn(200, 201);
        return id(response.json());
    }

    @Test
    @DisplayName("branch order pages are bounded, stable, and apply status and history filters in the database")
    void branchOrdersArePagedAndFiltered() throws Exception {
        Setup s = setup("branch-order-pages");
        UUID restaurantId = s.world().restaurantId();
        UUID branchId = s.world().branchId();
        List<String> orderIds = new ArrayList<>();
        List<String> orderNumbers = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            JsonNode created = order(s.world(), s.waiterToken(), Map.of(s.pasta(), 1));
            orderIds.add(created.get("id").asText());
            orderNumbers.add(created.get("orderNumber").asText());
            if (index < 2) {
                post(ORDER + "/payments", restaurantId, id(created)).as(s.waiterToken())
                        .body(Map.of("method", "CARD", "amount", new BigDecimal("12.00"))).expect(201);
            }
        }

        String path = "/restaurants/{r}/branches/{b}/orders/page";
        JsonNode first = get(path, restaurantId, branchId).as(s.waiterToken()).param("page", 0).param("size", 2).expect(200);
        JsonNode second = get(path, restaurantId, branchId).as(s.waiterToken()).param("page", 1).param("size", 2).expect(200);
        JsonNode last = get(path, restaurantId, branchId).as(s.waiterToken()).param("page", 2).param("size", 2).expect(200);

        assertThat(first.get("items")).hasSize(2);
        assertThat(first.get("items").get(0).get("itemCount").asInt()).isEqualTo(1);
        assertThat(first.get("totalElements").asLong()).isEqualTo(5);
        assertThat(first.get("hasNext").asBoolean()).isTrue();
        assertThat(second.get("page").asInt()).isEqualTo(1);
        assertThat(last.get("items")).hasSize(1);
        assertThat(last.get("hasNext").asBoolean()).isFalse();
        List<String> pagedIds = new ArrayList<>();
        for (JsonNode page : List.of(first, second, last)) page.get("items").forEach(item -> pagedIds.add(item.get("id").asText()));
        assertThat(pagedIds).containsExactlyElementsOf(new ArrayList<>(orderIds).reversed()).doesNotHaveDuplicates();

        JsonNode open = get(path, restaurantId, branchId).as(s.waiterToken())
                .param("status", "OPEN").expect(200);
        assertThat(open.get("totalElements").asLong()).isEqualTo(3);
        JsonNode openOnly = get(path, restaurantId, branchId).as(s.waiterToken())
                .param("openOnly", true).param("page", 0).param("size", 2).expect(200);
        assertThat(openOnly.get("totalElements").asLong()).isEqualTo(3);
        assertThat(openOnly.get("items")).hasSize(2);
        openOnly.get("items").forEach(item -> assertThat(item.get("status").asText()).isIn("DRAFT", "OPEN"));
        assertThat(openOnly.get("hasNext").asBoolean()).isTrue();
        JsonNode history = get(path, restaurantId, branchId).as(s.waiterToken())
                .param("historyOnly", true).expect(200);
        assertThat(history.get("totalElements").asLong()).isEqualTo(2);
        JsonNode emptyForNonHistoryStatus = get(path, restaurantId, branchId).as(s.waiterToken())
                .param("historyOnly", true).param("status", "OPEN").expect(200);
        assertThat(emptyForNonHistoryStatus.get("items")).isEmpty();
        JsonNode search = get(path, restaurantId, branchId).as(s.waiterToken())
                .param("search", orderNumbers.get(3)).expect(200);
        assertThat(search.get("items")).hasSize(1);
        assertThat(search.get("items").get(0).get("orderNumber").asText()).isEqualTo(orderNumbers.get(3));

        get(path, restaurantId, branchId).as(s.waiterToken()).param("page", -1).expect(400);
        get(path, restaurantId, branchId).as(s.waiterToken()).param("size", 101).expect(400);
        get(path, restaurantId, branchId).as(s.waiterToken()).param("openOnly", true).param("status", "CLOSED").expect(400);
        get(path, restaurantId, branchId).as(s.waiterToken()).param("openOnly", true).param("historyOnly", true).expect(400);
    }

    @Test
    @DisplayName("items are added, changed, sent to the kitchen, served and paid; totals follow every step")
    void fullService() throws Exception {
        Setup s = setup("service");
        UUID r = s.world().restaurantId();
        JsonNode order = order(s.world(), s.waiterToken(), Map.of(s.pasta(), 2));
        UUID orderId = id(order);
        assertThat(money(order, "subtotal")).isEqualByComparingTo("24.00");
        assertThat(order.get("status").asText()).isEqualTo("OPEN");
        assertThat(order.get("orderNumber").asText()).isNotBlank();

        JsonNode water = post(ORDER + "/items", r, orderId).as(s.waiterToken())
                .body(Map.of("menuItemId", s.water(), "quantity", 3, "notes", "No ice 🧊, room temperature")).expect(201);
        UUID waterLine = id(water);
        assertThat(water.get("notes").asText()).isEqualTo("No ice 🧊, room temperature");
        patch(ORDER + "/items/{l}/quantity", r, orderId, waterLine).as(s.waiterToken()).body(Map.of("quantity", 4)).expect(200);
        JsonNode totals = get(ORDER + "/totals", r, orderId).as(s.waiterToken()).expect(200);
        assertThat(money(totals, "subtotal")).isEqualByComparingTo("34.00");

        patch(ORDER + "/items/{l}/quantity", r, orderId, waterLine).as(s.waiterToken()).body(Map.of("quantity", 0)).expect(400);
        patch(ORDER + "/items/{l}/quantity", r, orderId, waterLine).as(s.waiterToken()).body(Map.of("quantity", 1000)).expect(400);
        patch(ORDER + "/items/{l}/quantity", r, orderId, waterLine).as(s.waiterToken()).body(Map.of("quantity", -3)).expect(400);
        patch(ORDER + "/items/{l}/notes", r, orderId, waterLine).as(s.waiterToken()).body(Map.of("notes", "n".repeat(1001))).expect(400);
        post(ORDER + "/items", r, orderId).as(s.waiterToken()).body(Map.of("menuItemId", UUID.randomUUID(), "quantity", 1)).send();
        post(ORDER + "/items", r, orderId).as(s.waiterToken()).body(Map.of("quantity", 1)).expect(400);

        JsonNode sent = post(ORDER + "/send-to-kitchen", r, orderId).as(s.waiterToken()).expect(200);
        assertThat(sent.get("fulfillmentStatus").asText()).isEqualTo("IN_PREPARATION");
        List<String> statuses = new ArrayList<>();
        get(ORDER + "/items", r, orderId).as(s.waiterToken()).expect(200).forEach(line -> statuses.add(line.get("status").asText()));
        // Water isn't cooked: it doesn't go to the kitchen.
        assertThat(statuses).contains("FIRED");

        post(ORDER + "/ready", r, orderId).as(s.waiterToken()).expect(200);
        JsonNode fulfilled = post(ORDER + "/fulfill", r, orderId).as(s.waiterToken()).expect(200);
        assertThat(fulfilled.get("fulfillmentStatus").asText()).isEqualTo("FULFILLED");

        JsonNode paid = post(ORDER + "/payments", r, orderId).as(s.waiterToken())
                .body(Map.of("method", "CARD", "amount", new BigDecimal("34.00"))).expect(201);
        assertThat(paid.get("orderClosed").asBoolean()).isTrue();
        // A closed order can't be changed.
        post(ORDER + "/items", r, orderId).as(s.waiterToken()).body(Map.of("menuItemId", s.pasta(), "quantity", 1)).expect(400);
        post(ORDER + "/items/{l}/void", r, orderId, waterLine).as(s.managerToken()).body(Map.of("reason", "late")).expect(400);

        JsonNode events = get(ORDER + "/events", r, orderId).as(s.waiterToken()).expect(200);
        List<String> types = new ArrayList<>();
        events.forEach(event -> types.add(event.get("eventType").asText()));
        assertThat(types).contains("CREATED", "ITEM_ADDED", "SENT_TO_KITCHEN", "PAYMENT_UPDATED", "CLOSED");
    }

    @Test
    @DisplayName("discounts cannot be added, changed, or removed after an order is paid and closed")
    void closedOrderDiscountsCannotChange() throws Exception {
        Setup s = setup("closed-order-discounts");
        UUID r = s.world().restaurantId();
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.pasta(), 1)));
        Map<String, Object> discount = new LinkedHashMap<>();
        discount.put("name", "Birthday");
        discount.put("discountType", "PERCENTAGE");
        discount.put("discountValue", new BigDecimal("10"));
        discount.put("reason", "Birthday offer");
        JsonNode applied = post(ORDER + "/discounts", r, orderId).as(s.managerToken()).body(discount).expect(201);
        UUID discountId = id(applied);

        post(ORDER + "/payments", r, orderId).as(s.waiterToken())
                .body(Map.of("method", "CARD", "amount", new BigDecimal("10.80"))).expect(201);

        Map<String, Object> anotherDiscount = new LinkedHashMap<>(discount);
        anotherDiscount.put("name", "Late discount");
        post(ORDER + "/discounts", r, orderId).as(s.managerToken()).body(anotherDiscount).expect(400);

        Map<String, Object> updatedDiscount = new LinkedHashMap<>(discount);
        updatedDiscount.put("discountValue", new BigDecimal("20"));
        put(ORDER + "/discounts/{discountId}", r, orderId, discountId)
                .as(s.managerToken()).body(updatedDiscount).expect(400);
        delete(ORDER + "/discounts/{discountId}", r, orderId, discountId)
                .as(s.managerToken()).expect(400);

        JsonNode unchanged = get(ORDER, r, orderId).as(s.waiterToken()).expect(200);
        assertThat(unchanged.get("status").asText()).isEqualTo("CLOSED");
        assertThat(unchanged.get("discounts")).hasSize(1);
        assertThat(UUID.fromString(unchanged.get("discounts").get(0).get("id").asText())).isEqualTo(discountId);
        assertThat(money(unchanged, "discountTotal")).isEqualByComparingTo("1.20");
        assertThat(money(unchanged, "total")).isEqualByComparingTo("10.80");
    }

    @Test
    @DisplayName("removing items needs a manager and a reason; a removed item can't be removed twice")
    void itemVoids() throws Exception {
        Setup s = setup("voids");
        UUID r = s.world().restaurantId();
        JsonNode order = order(s.world(), s.waiterToken(), Map.of(s.pasta(), 1, s.water(), 1));
        UUID orderId = id(order);
        UUID line = UUID.fromString(order.get("lineItems").get(0).get("id").asText());

        post(ORDER + "/items/{l}/void", r, orderId, line).as(s.waiterToken()).body(Map.of("reason", "x")).expect(403);
        post(ORDER + "/items/{l}/void", r, orderId, line).as(s.managerToken()).body(Map.of()).expect(400);
        JsonNode voided = post(ORDER + "/items/{l}/void", r, orderId, line).as(s.managerToken()).body(Map.of("reason", "Guest changed mind")).expect(200);
        assertThat(voided.get("status").asText()).isEqualTo("VOIDED");
        post(ORDER + "/items/{l}/void", r, orderId, line).as(s.managerToken()).body(Map.of("reason", "Again")).expect(400);
        JsonNode after = get(ORDER, r, orderId).as(s.waiterToken()).expect(200);
        assertThat(money(after, "subtotal")).isLessThan(new BigDecimal("14.50"));
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + SCHEMA + ".order_line_items where id = ? and voided_by = ? and voided_at is not null",
                Integer.class, line, s.manager().getId());
        assertThat(count).isEqualTo(1);

        post(ORDER + "/items/{l}/void", r, orderId, UUID.randomUUID()).as(s.managerToken()).body(Map.of("reason", "Nope")).expect(404);
    }

    @Test
    @DisplayName("discounts: a reason is required, at most 100%, and only managers give them unless the restaurant allows it")
    void discounts() throws Exception {
        Setup s = setup("discounts");
        UUID r = s.world().restaurantId();
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.pasta(), 2)));
        Map<String, Object> discount = new LinkedHashMap<>();
        discount.put("name", "Birthday");
        discount.put("discountType", "PERCENTAGE");
        discount.put("discountValue", 10);
        discount.put("reason", "It's their birthday");

        post(ORDER + "/discounts", r, orderId).as(s.waiterToken()).body(discount).expect(403);
        JsonNode applied = post(ORDER + "/discounts", r, orderId).as(s.managerToken()).body(discount).expect(201);
        assertThat(money(applied, "amountApplied")).isEqualByComparingTo("2.40");

        Map<String, Object> noReason = new LinkedHashMap<>(discount);
        noReason.remove("reason");
        post(ORDER + "/discounts", r, orderId).as(s.managerToken()).body(noReason).expect(400);
        Map<String, Object> tooMuch = new LinkedHashMap<>(discount);
        tooMuch.put("discountValue", 101);
        post(ORDER + "/discounts", r, orderId).as(s.managerToken()).body(tooMuch).expect(400);
        Map<String, Object> negative = new LinkedHashMap<>(discount);
        negative.put("discountValue", -5);
        post(ORDER + "/discounts", r, orderId).as(s.managerToken()).body(negative).expect(400);
        Map<String, Object> longName = new LinkedHashMap<>(discount);
        longName.put("name", "D".repeat(101));
        post(ORDER + "/discounts", r, orderId).as(s.managerToken()).body(longName).expect(400);

        // A fixed discount bigger than the bill only brings it to zero.
        Map<String, Object> huge = new LinkedHashMap<>(discount);
        huge.put("discountType", "FIXED_AMOUNT");
        huge.put("discountValue", new BigDecimal("999999.99"));
        post(ORDER + "/discounts", r, orderId).as(s.managerToken()).body(huge).expect(201);
        JsonNode zero = get(ORDER, r, orderId).as(s.waiterToken()).expect(200);
        assertThat(money(zero, "total")).isEqualByComparingTo("0");

        orderRule(s.world(), "allow_discount_without_manager", true);
        UUID other = id(order(s.world(), s.waiterToken(), Map.of(s.pasta(), 1)));
        post(ORDER + "/discounts", r, other).as(s.waiterToken()).body(discount).expect(201);
    }

    @Test
    @DisplayName("updating and removing stacked discounts recalculates totals and keeps manager permissions")
    void discountUpdatesAndRemovalRecalculateTotals() throws Exception {
        Setup s = setup("discount-update-delete");
        UUID r = s.world().restaurantId();
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.pasta(), 2)));

        Map<String, Object> percentage = new LinkedHashMap<>();
        percentage.put("name", "Birthday");
        percentage.put("discountType", "PERCENTAGE");
        percentage.put("discountValue", new BigDecimal("10"));
        percentage.put("reason", "Birthday offer");
        JsonNode first = post(ORDER + "/discounts", r, orderId).as(s.managerToken()).body(percentage).expect(201);
        UUID firstId = id(first);
        assertThat(money(first, "amountApplied")).isEqualByComparingTo("2.40");

        Map<String, Object> fixed = new LinkedHashMap<>();
        fixed.put("name", "Voucher");
        fixed.put("discountType", "FIXED_AMOUNT");
        fixed.put("discountValue", new BigDecimal("5"));
        fixed.put("reason", "Voucher applied");
        JsonNode second = post(ORDER + "/discounts", r, orderId).as(s.managerToken()).body(fixed).expect(201);
        UUID secondId = id(second);

        // Timestamp precision can collapse two requests onto the same stored instant. The applied order
        // must remain the order the staff member chose: percentage first, then fixed amount.
        jdbcTemplate.update("update " + SCHEMA + ".order_discounts set created_at = '2026-10-07T12:00:00Z' where id in (?, ?)",
                firstId, secondId);
        JsonNode sameTimestampReload = get(ORDER, r, orderId).as(s.waiterToken()).expect(200);
        assertThat(UUID.fromString(sameTimestampReload.get("discounts").get(0).get("id").asText())).isEqualTo(firstId);
        assertThat(money(sameTimestampReload.get("discounts").get(0), "amountApplied")).isEqualByComparingTo("2.40");
        assertThat(UUID.fromString(sameTimestampReload.get("discounts").get(1).get("id").asText())).isEqualTo(secondId);
        assertThat(money(sameTimestampReload.get("discounts").get(1), "amountApplied")).isEqualByComparingTo("5.00");
        assertThat(money(get(ORDER, r, orderId).as(s.waiterToken()).expect(200), "total")).isEqualByComparingTo("16.60");

        // Order PATCH used to bypass discount permission checks when replacing discounts with an empty list.
        patch(ORDER, r, orderId).as(s.waiterToken()).body(Map.of("discounts", List.of())).expect(403);
        JsonNode afterDeniedClear = get(ORDER, r, orderId).as(s.waiterToken()).expect(200);
        assertThat(afterDeniedClear.get("discounts")).hasSize(2);
        assertThat(money(afterDeniedClear, "total")).isEqualByComparingTo("16.60");

        Map<String, Object> updatedPercentage = new LinkedHashMap<>(percentage);
        updatedPercentage.put("discountValue", new BigDecimal("20"));
        put(ORDER + "/discounts/{discountId}", r, orderId, firstId).as(s.waiterToken()).body(updatedPercentage).expect(403);
        JsonNode updated = put(ORDER + "/discounts/{discountId}", r, orderId, firstId)
                .as(s.managerToken()).body(updatedPercentage).expect(200);
        assertThat(money(updated, "amountApplied")).isEqualByComparingTo("4.80");
        JsonNode recalculated = get(ORDER, r, orderId).as(s.waiterToken()).expect(200);
        assertThat(money(recalculated, "discountTotal")).isEqualByComparingTo("9.80");
        assertThat(money(recalculated, "total")).isEqualByComparingTo("14.20");

        assertThat(delete(ORDER + "/discounts/{discountId}", r, orderId, firstId).as(s.waiterToken()).send().status())
                .isEqualTo(403);
        delete(ORDER + "/discounts/{discountId}", r, orderId, firstId).as(s.managerToken()).expect(204);
        JsonNode afterRemoval = get(ORDER, r, orderId).as(s.waiterToken()).expect(200);
        assertThat(money(afterRemoval, "discountTotal")).isEqualByComparingTo("5.00");
        assertThat(money(afterRemoval, "total")).isEqualByComparingTo("19.00");
        assertThat(afterRemoval.get("discounts")).hasSize(1);
        assertThat(UUID.fromString(afterRemoval.get("discounts").get(0).get("id").asText())).isEqualTo(secondId);

        orderRule(s.world(), "allow_discount_without_manager", true);
        delete(ORDER + "/discounts/{discountId}", r, orderId, secondId).as(s.waiterToken()).expect(204);
        JsonNode afterStaffRemoval = get(ORDER, r, orderId).as(s.waiterToken()).expect(200);
        assertThat(afterStaffRemoval.get("discounts")).isEmpty();
        assertThat(money(afterStaffRemoval, "total")).isEqualByComparingTo("24.00");
    }

    @Test
    @DisplayName("with automatic kitchen sending on, new items go straight to the kitchen")
    void autoFire() throws Exception {
        Setup s = setup("autofire");
        UUID r = s.world().restaurantId();
        orderRule(s.world(), "auto_fire_to_kitchen", true);
        JsonNode order = order(s.world(), s.waiterToken(), Map.of(s.pasta(), 1, s.water(), 1));
        assertThat(order.get("fulfillmentStatus").asText()).isEqualTo("IN_PREPARATION");
        List<String> statuses = new ArrayList<>();
        order.get("lineItems").forEach(line -> statuses.add(line.get("itemNameSnapshot") == null ? line.get("status").asText()
                : line.get("status").asText()));
        assertThat(statuses).contains("FIRED");
        JsonNode added = post(ORDER + "/items", r, id(order)).as(s.waiterToken()).body(Map.of("menuItemId", s.pasta(), "quantity", 1)).expect(201);
        assertThat(added.get("status").asText()).isEqualTo("FIRED");
    }

    @Test
    @DisplayName("tax and service charge are worked out from the restaurant's settings and kept on the order")
    void taxAndServiceCharge() throws Exception {
        Setup s = setup("tax");
        UUID r = s.world().restaurantId();
        setting(s.world(), "order_tax_rate", new BigDecimal("10.00"));
        setting(s.world(), "order_tax_inclusive", false);
        setting(s.world(), "service_charge_type", "PERCENTAGE");
        setting(s.world(), "service_charge_value", new BigDecimal("5.00"));
        setting(s.world(), "service_charge_enabled", true);
        JsonNode order = order(s.world(), s.waiterToken(), Map.of(s.pasta(), 1));
        // 12.00 + 10% tax 1.20 + 5% service 0.60.
        assertThat(money(order, "taxTotal")).isEqualByComparingTo("1.20");
        assertThat(money(order, "serviceChargeTotal")).isEqualByComparingTo("0.60");
        assertThat(money(order, "total")).isEqualByComparingTo("13.80");

        // Changing the tax later doesn't change an order already open.
        setting(s.world(), "order_tax_rate", new BigDecimal("25.00"));
        post(ORDER + "/items", r, id(order)).as(s.waiterToken()).body(Map.of("menuItemId", s.water(), "quantity", 2)).expect(201);
        JsonNode after = get(ORDER, r, id(order)).as(s.waiterToken()).expect(200);
        assertThat(money(after, "taxTotal")).isEqualByComparingTo("1.70");

        setting(s.world(), "service_charge_enabled", false);
        setting(s.world(), "order_tax_inclusive", true);
        setting(s.world(), "order_tax_rate", new BigDecimal("20.00"));
        JsonNode inclusive = order(s.world(), s.waiterToken(), Map.of(s.pasta(), 1));
        assertThat(money(inclusive, "total")).isEqualByComparingTo("12.00");
        assertThat(money(inclusive, "taxTotal")).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("orders move tables, merge and split; closed orders reopen only when allowed")
    void tablesMergeSplitReopen() throws Exception {
        Setup s = setup("tables");
        UUID r = s.world().restaurantId();
        UUID t1 = table(s.world(), "T1");
        UUID t2 = table(s.world(), "T2");
        JsonNode first = post("/restaurants/{r}/branches/{b}/tables/{t}/orders", r, s.world().branchId(), t1).as(s.waiterToken())
                .body(Map.of("guestCount", 2, "items", List.of(Map.of("menuItemId", s.pasta(), "quantity", 2)))).expect(201);
        assertThat(first.get("tableNumber").asText()).isEqualTo("T1");
        JsonNode moved = post(ORDER + "/transfer/table", r, id(first)).as(s.waiterToken()).body(Map.of("tableId", t2)).expect(200);
        assertThat(moved.get("tableNumber").asText()).isEqualTo("T2");
        post(ORDER + "/transfer/table", r, id(first)).as(s.waiterToken()).body(Map.of("tableId", UUID.randomUUID())).send();

        JsonNode second = order(s.world(), s.waiterToken(), Map.of(s.water(), 2));
        JsonNode merged = post(ORDER + "/merge", r, id(first)).as(s.waiterToken()).body(Map.of("sourceOrderId", id(second))).expect(200);
        assertThat(money(merged, "subtotal")).isEqualByComparingTo("29.00");
        JsonNode source = get(ORDER, r, id(second)).as(s.waiterToken()).expect(200);
        assertThat(source.get("status").asText()).isEqualTo("VOIDED");
        post(ORDER + "/merge", r, id(first)).as(s.waiterToken()).body(Map.of("sourceOrderId", id(first))).expect(400);

        JsonNode full = get(ORDER, r, id(first)).as(s.waiterToken()).expect(200);
        UUID pastaLine = null;
        for (JsonNode line : full.get("lineItems")) {
            if (line.get("status").asText().equals("PENDING") && pastaLine == null) {
                pastaLine = id(line);
            }
        }
        JsonNode split = post(ORDER + "/split", r, id(first)).as(s.waiterToken()).body(Map.of("lineItemIds", List.of(pastaLine))).expect(200);
        assertThat(id(split)).isNotEqualTo(id(first));
        post(ORDER + "/split", r, id(first)).as(s.waiterToken()).body(Map.of("lineItemIds", List.of())).expect(400);

        // Closing and reopening.
        post(ORDER + "/close", r, id(split)).as(s.waiterToken()).body(Map.of()).expect(200);
        post(ORDER + "/reopen", r, id(split)).as(s.managerToken()).body(Map.of()).expect(400);
        orderRule(s.world(), "reopen_closed_orders_enabled", true);
        post(ORDER + "/reopen", r, id(split)).as(s.waiterToken()).body(Map.of()).expect(403);
        JsonNode reopened = post(ORDER + "/reopen", r, id(split)).as(s.managerToken()).body(Map.of()).expect(200);
        assertThat(reopened.get("status").asText()).isEqualTo("OPEN");
        // Voiding a whole order needs the reason the restaurant asks for, and can't happen twice.
        post(ORDER + "/void", r, id(split)).as(s.managerToken()).body(Map.of()).expect(400);
        post(ORDER + "/void", r, id(split)).as(s.managerToken()).body(Map.of("reason", "Test order")).expect(200);
        post(ORDER + "/void", r, id(split)).as(s.managerToken()).body(Map.of("reason", "Again")).expect(400);
    }

    @Test
    @DisplayName("orders cannot be created on unavailable or merged tables")
    void ordersCannotBeCreatedOnUnavailableOrMergedTables() throws Exception {
        Setup s = setup("table-order-eligibility");
        UUID r = s.world().restaurantId();
        UUID branch = s.world().branchId();
        UUID outOfService = table(s.world(), "OOS");
        UUID primary = table(s.world(), "P1");
        UUID child = table(s.world(), "C1");

        patch("/restaurants/{r}/branches/{b}/tables/{t}/status", r, branch, outOfService)
                .as(s.waiterToken()).body(Map.of("status", "OUT_OF_SERVICE")).expect(200);
        post("/restaurants/{r}/branches/{b}/tables/{t}/orders", r, branch, outOfService)
                .as(s.waiterToken()).body(Map.of("items", List.of(Map.of("menuItemId", s.pasta(), "quantity", 1)))).expect(409);

        post("/restaurants/{r}/branches/{b}/tables/{t}/merge", r, branch, primary)
                .as(s.waiterToken()).body(Map.of("tableIds", List.of(child))).expect(200);
        post("/restaurants/{r}/branches/{b}/tables/{t}/orders", r, branch, child)
                .as(s.waiterToken()).body(Map.of("items", List.of(Map.of("menuItemId", s.pasta(), "quantity", 1)))).expect(409);
    }

    @Test
    @DisplayName("a table merge and new order cannot leave an order attached to a merged child")
    void concurrentMergeAndTableOrderKeepAssociationConsistent() throws Exception {
        Setup s = setup("table-order-merge-race");
        UUID r = s.world().restaurantId();
        UUID branch = s.world().branchId();
        UUID primary = table(s.world(), "RACE-P");
        UUID child = table(s.world(), "RACE-C");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> createOrder = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting to start table-order race");
                return post("/restaurants/{r}/branches/{b}/tables/{t}/orders", r, branch, child)
                        .as(s.waiterToken()).body(Map.of("items", List.of(Map.of("menuItemId", s.pasta(), "quantity", 1))))
                        .send().status();
            });
            Future<Integer> merge = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting to start table-order race");
                return post("/restaurants/{r}/branches/{b}/tables/{t}/merge", r, branch, primary)
                        .as(s.waiterToken()).body(Map.of("tableIds", List.of(child))).send().status();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int createStatus = createOrder.get(30, TimeUnit.SECONDS);
            assertThat(createStatus).isIn(201, 409);
            assertThat(merge.get(30, TimeUnit.SECONDS)).isEqualTo(200);

            JsonNode openOrders = get("/restaurants/{r}/branches/{b}/orders/page", r, branch)
                    .as(s.waiterToken()).param("openOnly", true).expect(200);
            openOrders.get("items").forEach(order -> assertThat(order.get("tableId").asText()).isNotEqualTo(child.toString()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("odd but valid input is stored exactly; impossible input is refused, never a server error")
    void edgeInput() throws Exception {
        Setup s = setup("edge");
        UUID r = s.world().restaurantId();
        UUID b = s.world().branchId();
        String notes = "Allergy: 🥜 peanuts! <script>alert(1)</script> ' OR 1=1 -- " + "ñ".repeat(1500);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderType", "TAKEAWAY");
        body.put("guestCount", 500);
        body.put("notes", notes);
        body.put("items", List.of(Map.of("menuItemId", s.pasta(), "quantity", 999)));
        JsonNode big = post("/restaurants/{r}/branches/{b}/orders", r, b).as(s.waiterToken()).body(body).expect(201);
        assertThat(big.get("notes").asText()).isEqualTo(notes);
        assertThat(money(big, "subtotal")).isEqualByComparingTo("11988.00");

        body.put("guestCount", 501);
        post("/restaurants/{r}/branches/{b}/orders", r, b).as(s.waiterToken()).body(body).expect(400);
        body.put("guestCount", 0);
        post("/restaurants/{r}/branches/{b}/orders", r, b).as(s.waiterToken()).body(body).expect(400);
        body.put("guestCount", 2);
        body.put("notes", "n".repeat(2001));
        post("/restaurants/{r}/branches/{b}/orders", r, b).as(s.waiterToken()).body(body).expect(400);
        body.put("notes", null);
        body.put("orderType", "SPACESHIP");
        post("/restaurants/{r}/branches/{b}/orders", r, b).as(s.waiterToken()).body(body).expect(400);
        body.put("orderType", "TAKEAWAY");
        body.put("items", List.of(Map.of("menuItemId", s.pasta(), "quantity", 1000)));
        post("/restaurants/{r}/branches/{b}/orders", r, b).as(s.waiterToken()).body(body).expect(400);
        List<Map<String, Object>> manyItems = new ArrayList<>();
        for (int i = 0; i < 301; i++) {
            manyItems.add(Map.of("menuItemId", s.pasta(), "quantity", 1));
        }
        body.put("items", manyItems);
        post("/restaurants/{r}/branches/{b}/orders", r, b).as(s.waiterToken()).body(body).expect(400);
        // A body over 1 MB is refused before it's read.
        body.put("items", List.of(Map.of("menuItemId", s.pasta(), "quantity", 1)));
        body.put("notes", "x".repeat(1_100_000));
        post("/restaurants/{r}/branches/{b}/orders", r, b).as(s.waiterToken()).body(body).expect(413);

        // An order at another restaurant's branch, or for a branch that doesn't exist.
        World other = newWorld("edge-other");
        body.put("notes", null);
        assertThat(post("/restaurants/{r}/branches/{b}/orders", r, other.branchId()).as(s.waiterToken()).body(body).send().status()).isIn(403, 404);
        assertThat(post("/restaurants/{r}/branches/{b}/orders", other.restaurantId(), other.branchId()).as(s.waiterToken()).body(body).send().status())
                .isEqualTo(403);
        assertThat(get(ORDER, r, UUID.randomUUID()).as(s.waiterToken()).send().status()).isEqualTo(404);
        assertThat(get(ORDER, r, "123").as(s.waiterToken()).send().status()).isEqualTo(400);
    }
}
