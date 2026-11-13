package pos.pos.integration.reservation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Food pre-orders end to end")
class PreOrderFlowIntegrationTest extends AbstractPosApiIntegrationTest {

    private UUID booking(World world, OffsetDateTime start) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("branchId", world.branchId());
        body.put("partySize", 2);
        body.put("reservationStart", start.toString());
        body.put("contactName", "Pre Order Guest");
        body.put("contactPhone", "+39 333 2222222");
        return id(post("/restaurants/{r}/reservations", world.restaurantId()).as(world.ownerToken()).body(body).expect(201));
    }

    private void enable(World world, int leadMinutes) throws Exception {
        patch("/restaurants/{r}/settings/pre-orders", world.restaurantId()).as(world.ownerToken())
                .body(Map.of("preOrdersEnabled", true, "preOrderLeadMinutes", leadMinutes)).expect(200);
    }

    private static Map<String, Object> dishes(UUID menuItemId, int quantity, String notes) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("menuItemId", menuItemId);
        line.put("quantity", quantity);
        if (notes != null) {
            line.put("notes", notes);
        }
        return Map.of("items", List.of(line), "notes", "Birthday 🎂");
    }

    private static OffsetDateTime inDays(int days) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(days).truncatedTo(ChronoUnit.HOURS);
    }

    @Test
    @DisplayName("staff take, change and send a pre-order; once in the kitchen it is locked")
    void lifecycle() throws Exception {
        World world = newWorld("preorder");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        enable(world, 60);
        Map<String, UUID> menu = menu(world, "Dinner");
        UUID pasta = item(world, menu, "Carbonara", "12.50");
        UUID wine = item(world, menu, "House wine", "5.00");
        UUID reservation = booking(world, inDays(2));
        String path = "/restaurants/{r}/reservations/{id}/pre-order";

        assertThat(get(path, r, reservation).as(t).send().status()).isEqualTo(404);
        JsonNode placed = put(path, r, reservation).as(t).body(dishes(pasta, 2, "No pepper")).expect(200);
        assertThat(placed.get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(placed.get("source").asText()).isEqualTo("STAFF");
        assertThat(money(placed, "subtotal")).isEqualByComparingTo("25.00");
        assertThat(placed.get("items").get(0).get("notes").asText()).isEqualTo("No pepper");

        JsonNode changed = put(path, r, reservation).as(t).body(Map.of("items", List.of(
                Map.of("menuItemId", pasta, "quantity", 1), Map.of("menuItemId", wine, "quantity", 2)))).expect(200);
        assertThat(changed.get("id").asText()).isEqualTo(placed.get("id").asText());
        assertThat(money(changed, "subtotal")).isEqualByComparingTo("22.50");

        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC);
        JsonNode list = get("/restaurants/{r}/branches/{b}/pre-orders", r, world.branchId()).as(t)
                .param("from", from.toString()).param("to", from.plusDays(7).toString()).param("status", "SCHEDULED").expect(200);
        assertThat(list.toString()).contains(changed.get("id").asText());
        get("/restaurants/{r}/branches/{b}/pre-orders", r, world.branchId()).as(t)
                .param("from", from.toString()).param("to", from.plusDays(63).toString()).expect(400);
        get("/restaurants/{r}/branches/{b}/pre-orders", r, world.branchId()).as(t)
                .param("from", from.toString()).param("to", from.minusDays(1).toString()).expect(400);

        JsonNode sent = post(path + "/send", r, reservation).as(t).expect(200);
        assertThat(sent.get("status").asText()).isEqualTo("SENT");
        assertThat(sent.get("orderNumber").asText()).isNotBlank();
        JsonNode order = get("/restaurants/{r}/orders/{o}", r, UUID.fromString(sent.get("orderId").asText())).as(t).expect(200);
        assertThat(order.get("lineItems")).hasSize(2);

        put(path, r, reservation).as(t).body(dishes(pasta, 1, null)).expect(400);
        post(path + "/cancel", r, reservation).as(t).body(Map.of("reason", "Too late")).expect(400);
        post(path + "/send", r, reservation).as(t).expect(400);
    }

    @Test
    @DisplayName("cancelling before the kitchen starts refunds the guest")
    void cancelRefunds() throws Exception {
        World world = newWorld("preorder-cancel");
        UUID r = world.restaurantId();
        enable(world, 30);
        UUID dish = item(world, menu(world, "Lunch"), "Risotto", "14.00");
        UUID reservation = booking(world, inDays(3));
        String path = "/restaurants/{r}/reservations/{id}/pre-order";
        put(path, r, reservation).as(world.ownerToken()).body(dishes(dish, 1, null)).expect(200);
        JsonNode cancelled = post(path + "/cancel", r, reservation).as(world.ownerToken()).body(Map.of("reason", "Plans changed")).expect(200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("paymentStatus").asText()).isEqualTo("REFUNDED");
        post(path + "/cancel", r, reservation).as(world.ownerToken()).body(Map.of()).expect(404);
        // A new pre-order can be taken after a cancel.
        assertThat(put(path, r, reservation).as(world.ownerToken()).body(dishes(dish, 2, null)).expect(200).get("status").asText())
                .isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("impossible pre-orders are refused and nothing is saved")
    void refusesBadInput() throws Exception {
        World world = newWorld("preorder-bad");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        UUID dish = item(world, menu(world, "Menu"), "Soup", "6.00");
        UUID reservation = booking(world, inDays(2));
        String path = "/restaurants/{r}/reservations/{id}/pre-order";

        JsonNode off = put(path, r, reservation).as(t).body(dishes(dish, 1, null)).expect(400);
        assertThat(off.get("message").asText()).contains("not available");
        enable(world, 60);

        put(path, r, reservation).as(t).body(Map.of("items", List.of())).expect(400);
        put(path, r, reservation).as(t).body(Map.of()).expect(400);
        put(path, r, reservation).as(t).body(dishes(dish, 0, null)).expect(400);
        put(path, r, reservation).as(t).body(dishes(dish, 51, null)).expect(400);
        put(path, r, reservation).as(t).body(dishes(dish, 1, "n".repeat(201))).expect(400);
        put(path, r, reservation).as(t).body(dishes(UUID.randomUUID(), 1, null)).send();
        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            many.add(Map.of("menuItemId", dish, "quantity", 1));
        }
        put(path, r, reservation).as(t).body(Map.of("items", many)).expect(400);
        put(path, r, reservation).as(t).body(Map.of("items", List.of(Map.of("menuItemId", dish, "quantity", 1)), "notes", "n".repeat(501))).expect(400);
        assertThat(get(path, r, reservation).as(t).send().status()).isEqualTo(404);

        // Too close to the booking: the kitchen would already be cooking.
        UUID soon = booking(world, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30).truncatedTo(ChronoUnit.MINUTES));
        assertThat(put(path, r, soon).as(t).body(dishes(dish, 1, null)).expect(400).get("message").asText()).contains("too late");
    }

    @Test
    @DisplayName("other restaurants can't see or change a booking's pre-order")
    void access() throws Exception {
        World world = newWorld("preorder-own");
        World other = newWorld("preorder-other");
        enable(world, 60);
        UUID dish = item(world, menu(world, "Menu"), "Pizza", "8.00");
        UUID reservation = booking(world, inDays(2));
        String path = "/restaurants/{r}/reservations/{id}/pre-order";
        put(path, world.restaurantId(), reservation).as(world.ownerToken()).body(dishes(dish, 1, null)).expect(200);
        assertThat(get(path, world.restaurantId(), reservation).as(other.ownerToken()).send().status()).isIn(403, 404);
        assertThat(get(path, other.restaurantId(), reservation).as(other.ownerToken()).send().status()).isEqualTo(404);
        assertThat(post(path + "/send", world.restaurantId(), reservation).as(other.ownerToken()).send().status()).isIn(403, 404);
        assertThat(get(path, world.restaurantId(), reservation).send().status()).isEqualTo(401);
        assertThat(get(path, world.restaurantId(), reservation).as(world.ownerToken()).expect(200).get("status").asText()).isEqualTo("SCHEDULED");
    }
}
