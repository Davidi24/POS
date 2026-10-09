package pos.pos.integration.staff;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Kitchen display and shifts end to end")
class KitchenAndShiftsIntegrationTest extends AbstractPosApiIntegrationTest {

    private static final String KDS = "/restaurants/{r}/branches/{b}/kds";
    private static final String SHIFTS = "/restaurants/{r}/branches/{b}/shifts";

    private List<JsonNode> tickets(JsonNode board) {
        List<JsonNode> tickets = new ArrayList<>();
        board.forEach(station -> station.path("tickets").forEach(tickets::add));
        return tickets;
    }

    @Test
    @DisplayName("scheduling cannot overlap the actual hours of a shift that ended late")
    void scheduleCannotOverlapLateAttendance() throws Exception {
        World world = newWorld("shift-late-attendance-overlap");
        UUID r = world.restaurantId();
        UUID b = world.branchId();
        User waiter = staff(world, "waiter", "WAITER");
        User manager = staff(world, "manager", "MANAGER");
        String managerToken = token(manager);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);

        JsonNode existing = post(SHIFTS, r, b).as(managerToken).body(Map.of(
                "userId", waiter.getId(),
                "scheduledStart", now.plusHours(6).toString(),
                "scheduledEnd", now.plusHours(8).toString(),
                "version", 0
        )).expect(200);
        OffsetDateTime scheduledStart = now.minusHours(4);
        OffsetDateTime scheduledEnd = now.minusHours(2);
        OffsetDateTime actualStart = scheduledStart;
        OffsetDateTime actualEnd = now.minusHours(1);
        jdbcTemplate.update(
                "update " + SCHEMA + ".shifts set status = 'CLOSED', scheduled_start = ?, scheduled_end = ?, started_at = ?, ended_at = ? where id = ?",
                scheduledStart,
                scheduledEnd,
                actualStart,
                actualEnd,
                id(existing)
        );

        // The new planned interval starts after the old scheduled end but overlaps its actual late clock-out.
        OffsetDateTime overlappingStart = now.minusMinutes(90);
        OffsetDateTime overlappingEnd = now.plusHours(2);
        post(SHIFTS, r, b).as(managerToken).body(Map.of(
                "userId", waiter.getId(),
                "scheduledStart", overlappingStart.toString(),
                "scheduledEnd", overlappingEnd.toString(),
                "version", 0
        )).expect(409);
        Integer shiftCount = jdbcTemplate.queryForObject(
                "select count(*) from " + SCHEMA + ".shifts where user_id = ?",
                Integer.class,
                waiter.getId()
        );
        assertThat(shiftCount).isEqualTo(1);
    }

    @Test
    @DisplayName("an order sent to the kitchen shows up on its station and moves through cooking to picked up")
    void kitchenTicketLifecycle() throws Exception {
        World world = newWorld("kds");
        Map<String, UUID> menu = menu(world, "Kitchen menu");
        UUID burger = item(world, menu, "Burger", "11.00");
        UUID r = world.restaurantId();
        UUID b = world.branchId();
        JsonNode station = post(KDS + "/stations", r, b).as(world.ownerToken()).body(Map.of(
                "name", "Grill", "stationType", "GRILL", "routings", List.of(Map.of("menuItemId", burger)))).expect(201);
        UUID stationId = id(station);

        User waiter = staff(world, "waiter", "WAITER");
        User cook = staff(world, "cook", "KITCHEN");
        String waiterToken = token(waiter);
        String cookToken = token(cook);
        JsonNode order = order(world, waiterToken, Map.of(burger, 2));
        post("/restaurants/{r}/orders/{o}/send-to-kitchen", r, id(order)).as(waiterToken).expect(200);

        JsonNode board = get(KDS + "/board", r, b).as(cookToken).param("stationId", stationId).expect(200);
        List<JsonNode> onBoard = tickets(board);
        assertThat(onBoard).hasSize(1);
        UUID ticketId = id(onBoard.getFirst());
        assertThat(onBoard.getFirst().get("orderNumber").asText()).isEqualTo(order.get("orderNumber").asText());

        // Waiters see the kitchen, but only the kitchen cooks.
        get(KDS + "/board", r, b).as(waiterToken).expect(200);
        post(KDS + "/tickets/{t}/start", r, b, ticketId).as(waiterToken).body(Map.of()).expect(403);

        post(KDS + "/tickets/{t}/start", r, b, ticketId).as(cookToken).body(Map.of()).expect(200);
        JsonNode ready = post(KDS + "/tickets/{t}/ready", r, b, ticketId).as(cookToken).body(Map.of("note", "Pass 1")).expect(200);
        assertThat(ready.get("status").asText()).isEqualTo("READY");
        post(KDS + "/tickets/{t}/picked-up", r, b, ticketId).as(waiterToken).body(Map.of()).expect(200);
        JsonNode served = get("/restaurants/{r}/orders/{o}", r, id(order)).as(waiterToken).expect(200);
        assertThat(served.get("fulfillmentStatus").asText()).isEqualTo("FULFILLED");

        // Bad input never breaks the board.
        post(KDS + "/tickets/{t}/ready", r, b, UUID.randomUUID()).as(cookToken).body(Map.of()).expect(404);
        post(KDS + "/tickets/{t}/start", r, b, ticketId).as(cookToken).body(Map.of("note", "n".repeat(1001))).expect(400);
        post(KDS + "/stations", r, b).as(world.ownerToken()).body(Map.of("name", "S".repeat(200), "stationType", "GRILL")).expect(400);
        post(KDS + "/stations", r, b).as(world.ownerToken()).body(Map.of("name", "Oven", "stationType", "OVEN")).expect(400);
        assertThat(get(KDS + "/board", r, b).as(newWorld("kds-other").ownerToken()).send().status()).isIn(403, 404);
    }

    @Test
    @DisplayName("staff clock in, take a break and clock out; payments taken meanwhile count for that shift")
    void clockingAndPayments() throws Exception {
        World world = newWorld("shift");
        Map<String, UUID> menu = menu(world, "Shift menu");
        UUID soup = item(world, menu, "Soup", "6.00");
        UUID r = world.restaurantId();
        UUID b = world.branchId();
        User waiter = staff(world, "waiter", "WAITER");
        User manager = staff(world, "manager", "MANAGER");
        String waiterToken = token(waiter);
        String managerToken = token(manager);

        put(SHIFTS + "/pay-rates/{u}", r, b, waiter.getId()).as(managerToken).body(Map.of("hourlyRate", new BigDecimal("12.50"))).expect(200);
        put(SHIFTS + "/pay-rates/{u}", r, b, waiter.getId()).as(waiterToken).body(Map.of("hourlyRate", new BigDecimal("99"))).expect(403);
        put(SHIFTS + "/pay-rates/{u}", r, b, waiter.getId()).as(managerToken).body(Map.of("hourlyRate", new BigDecimal("10000.01"))).expect(400);

        JsonNode shift = post(SHIFTS + "/clock-in", r, b).as(waiterToken).body(Map.of()).expect(200);
        UUID shiftId = id(shift);
        assertThat(shift.get("status").asText()).isEqualTo("OPEN");
        post(SHIFTS + "/clock-in", r, b).as(waiterToken).body(Map.of()).expect(409);

        JsonNode order = order(world, waiterToken, Map.of(soup, 1));
        JsonNode paid = post("/restaurants/{r}/orders/{o}/payments", r, id(order)).as(waiterToken)
                .body(Map.of("method", "CASH", "amount", new BigDecimal("6.00"), "tipAmount", new BigDecimal("1.00"))).expect(201);
        assertThat(paid.get("payment").get("shiftId").asText()).isEqualTo(shiftId.toString());

        JsonNode onBreak = post(SHIFTS + "/{id}/break", r, b, shiftId).as(waiterToken)
                .body(Map.of("version", shift.get("version").asLong(), "type", "MEAL")).expect(200);
        // A stale version (someone else changed the shift) is refused.
        post(SHIFTS + "/{id}/resume", r, b, shiftId).as(waiterToken).body(Map.of("version", shift.get("version").asLong())).expect(409);
        JsonNode back = post(SHIFTS + "/{id}/resume", r, b, shiftId).as(waiterToken).body(Map.of("version", onBreak.get("version").asLong())).expect(200);
        JsonNode closed = post(SHIFTS + "/{id}/close", r, b, shiftId).as(waiterToken).body(Map.of("version", back.get("version").asLong())).expect(200);
        assertThat(closed.get("status").asText()).isEqualTo("CLOSED");

        LocalDate today = LocalDate.now(ZoneId.of("Europe/Rome"));
        JsonNode mine = get(SHIFTS + "/pay", r, b).as(waiterToken).param("from", today.minusDays(1)).param("to", today.plusDays(1)).param("mine", true).expect(200);
        JsonNode me = mine.get("staff").get(0);
        assertThat(money(me, "hourlyRate")).isEqualByComparingTo("12.50");
        assertThat(money(me, "tips")).isEqualByComparingTo("1.00");
        get(SHIFTS + "/pay", r, b).as(waiterToken).param("from", today).param("to", today).param("mine", false).expect(403);
        get(SHIFTS + "/pay", r, b).as(managerToken).param("from", today.minusDays(70)).param("to", today).param("mine", false).expect(400);

        JsonNode mySales = get("/restaurants/{r}/branches/{b}/sales/mine", r, b).as(waiterToken).param("date", today).expect(200);
        JsonNode totals = mySales.get("currencies").get(0);
        assertThat(totals.get("currency").asText()).isEqualTo("EUR");
        assertThat(totals.get("ordersServed").asInt()).isEqualTo(1);
        assertThat(money(totals, "sales")).isEqualByComparingTo("6.00");
        assertThat(totals.get("paymentCount").asInt()).isEqualTo(1);
        assertThat(money(totals, "recordedTips")).isEqualByComparingTo("1.00");
        assertThat(money(totals, "collected")).isEqualByComparingTo("7.00");
        assertThat(totals.get("ordersWithoutPayments").asInt()).isZero();
        assertThat(totals.get("paymentMethods").get(0).get("name").asText()).isEqualTo("CASH");
        assertThat(money(totals.get("paymentMethods").get(0), "collected")).isEqualByComparingTo("7.00");
        assertThat(totals.get("topItems").get(0).get("name").asText()).isEqualTo("Soup");
        assertThat(mySales.get("shifts")).anySatisfy(option -> assertThat(option.get("id").asText()).isEqualTo(shiftId.toString()));

        JsonNode shiftSales = get("/restaurants/{r}/branches/{b}/sales/mine", r, b).as(waiterToken)
                .param("date", today).param("shiftId", shiftId).expect(200);
        assertThat(shiftSales.get("currencies").get(0).get("paymentCount").asInt()).isEqualTo(1);
        assertThat(get("/restaurants/{r}/branches/{b}/sales/mine", r, b).as(waiterToken)
                .param("date", today.plusDays(1)).param("shiftId", shiftId).send().status()).isEqualTo(400);
        JsonNode managerView = get("/restaurants/{r}/branches/{b}/sales/mine", r, b).as(managerToken)
                .param("date", today).param("staffId", waiter.getId()).expect(200);
        assertThat(money(managerView.get("currencies").get(0), "collected")).isEqualByComparingTo("7.00");
        get("/restaurants/{r}/branches/{b}/sales/mine", r, b).as(waiterToken)
                .param("date", today).param("staffId", manager.getId()).expect(403);
    }

    @Test
    @DisplayName("staff pay reports only the tip amount retained after refunds")
    void refundedTipsAreRemovedFromStaffPay() throws Exception {
        World world = newWorld("shift-refunded-tip");
        Map<String, UUID> menu = menu(world, "Shift refund menu");
        UUID soup = item(world, menu, "Soup", "6.00");
        UUID r = world.restaurantId();
        UUID b = world.branchId();
        User waiter = staff(world, "waiter", "WAITER");
        User manager = staff(world, "manager", "MANAGER");
        String waiterToken = token(waiter);
        String managerToken = token(manager);

        JsonNode order = order(world, waiterToken, Map.of(soup, 1));
        JsonNode paid = post("/restaurants/{r}/orders/{o}/payments", r, id(order)).as(waiterToken)
                .body(Map.of("method", "CASH", "amount", new BigDecimal("6.00"), "tipAmount", new BigDecimal("1.00"))).expect(201);
        post("/restaurants/{r}/orders/{o}/payments/{p}/refund", r, id(order), id(paid.get("payment")))
                .as(managerToken).body(Map.of("amount", new BigDecimal("6.50"), "reason", "Refunded half the tip")).expect(200);

        // Refunding only the bill must preserve the tip; refunding the whole payment must remove it.
        JsonNode billOnlyOrder = order(world, waiterToken, Map.of(soup, 1));
        JsonNode billOnlyPaid = post("/restaurants/{r}/orders/{o}/payments", r, id(billOnlyOrder)).as(waiterToken)
                .body(Map.of("method", "CASH", "amount", new BigDecimal("6.00"), "tipAmount", new BigDecimal("1.00"))).expect(201);
        post("/restaurants/{r}/orders/{o}/payments/{p}/refund", r, id(billOnlyOrder), id(billOnlyPaid.get("payment")))
                .as(managerToken).body(Map.of("amount", new BigDecimal("6.00"), "reason", "Refunded the bill")).expect(200);

        JsonNode fullRefundOrder = order(world, waiterToken, Map.of(soup, 1));
        JsonNode fullRefundPaid = post("/restaurants/{r}/orders/{o}/payments", r, id(fullRefundOrder)).as(waiterToken)
                .body(Map.of("method", "CASH", "amount", new BigDecimal("6.00"), "tipAmount", new BigDecimal("1.00"))).expect(201);
        post("/restaurants/{r}/orders/{o}/payments/{p}/refund", r, id(fullRefundOrder), id(fullRefundPaid.get("payment")))
                .as(managerToken).body(Map.of("amount", new BigDecimal("7.00"), "reason", "Refunded bill and tip")).expect(200);

        LocalDate today = LocalDate.now(ZoneId.of("Europe/Rome"));
        JsonNode report = get(SHIFTS + "/pay", r, b).as(managerToken)
                .param("from", today.minusDays(1)).param("to", today.plusDays(1)).param("mine", false).expect(200);
        JsonNode waiterPay = null;
        for (JsonNode staffPay : report.get("staff")) {
            if (waiter.getId().toString().equals(staffPay.get("userId").asText())) waiterPay = staffPay;
        }
        assertThat(waiterPay).isNotNull();
        assertThat(money(waiterPay, "tips")).isEqualByComparingTo("1.50");
    }

    @Test
    @DisplayName("managers schedule shifts; overlaps, reversed times and stale edits are refused")
    void scheduling() throws Exception {
        World world = newWorld("schedule");
        UUID r = world.restaurantId();
        UUID b = world.branchId();
        User waiter = staff(world, "waiter", "WAITER");
        User manager = staff(world, "manager", "MANAGER");
        String managerToken = token(manager);
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0);

        JsonNode planned = post(SHIFTS, r, b).as(managerToken).body(Map.of("userId", waiter.getId(), "scheduledStart", start.toString(),
                "scheduledEnd", start.plusHours(8).toString(), "notes", "Opening ✓", "version", 0)).expect(200);
        assertThat(planned.get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(post(SHIFTS, r, b).as(managerToken).body(Map.of("userId", waiter.getId(), "scheduledStart", start.plusHours(2).toString(),
                "scheduledEnd", start.plusHours(10).toString(), "version", 0)).send().status()).isBetween(400, 409);
        assertThat(post(SHIFTS, r, b).as(managerToken).body(Map.of("userId", waiter.getId(), "scheduledStart", start.plusDays(1).toString(),
                "scheduledEnd", start.plusDays(1).minusHours(1).toString(), "version", 0)).send().status()).isEqualTo(400);
        post(SHIFTS, r, b).as(token(waiter)).body(Map.of("userId", waiter.getId(), "scheduledStart", start.plusDays(3).toString(),
                "scheduledEnd", start.plusDays(3).plusHours(4).toString(), "version", 0)).expect(403);
        post(SHIFTS, r, b).as(managerToken).body(Map.of("userId", waiter.getId(), "scheduledStart", start.plusDays(3).toString(),
                "scheduledEnd", start.plusDays(3).plusHours(4).toString(), "notes", "n".repeat(2001), "version", 0)).expect(400);
        assertThat(post(SHIFTS, r, b).as(managerToken).body(Map.of("userId", UUID.randomUUID(), "scheduledStart", start.plusDays(4).toString(),
                "scheduledEnd", start.plusDays(4).plusHours(4).toString(), "version", 0)).send().status()).isBetween(400, 404);

        put(SHIFTS + "/{id}", r, b, id(planned)).as(managerToken).body(Map.of("userId", waiter.getId(), "scheduledStart", start.toString(),
                "scheduledEnd", start.plusHours(6).toString(), "version", planned.get("version").asLong() + 7)).expect(409);
        JsonNode board = get(SHIFTS, r, b).as(managerToken).param("from", start.toLocalDate().minusDays(1)).param("to", start.toLocalDate().plusDays(1)).param("mine", false).expect(200);
        assertThat(board.get("items").toString()).contains(id(planned).toString());
    }
}
