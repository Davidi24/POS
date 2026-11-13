package pos.pos.integration.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.user.entity.User;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Payments end to end")
class PaymentFlowIntegrationTest extends AbstractPosApiIntegrationTest {

    private static final String PAYMENTS = "/restaurants/{r}/orders/{o}/payments";

    private record Setup(World world, UUID itemId, User waiter, String waiterToken, User manager, String managerToken) {
    }

    private Setup setup(String label, String price) throws Exception {
        World world = newWorld(label);
        Map<String, UUID> menu = menu(world, "Dinner");
        UUID itemId = item(world, menu, "Pasta " + label, price);
        User waiter = staff(world, "waiter", "WAITER");
        User manager = staff(world, "manager", "MANAGER");
        return new Setup(world, itemId, waiter, token(waiter), manager, token(manager));
    }

    private Map<String, Object> pay(String method, String amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("method", method);
        body.put("amount", new BigDecimal(amount));
        return body;
    }

    private Map<String, Object> with(Map<String, Object> body, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        copy.put(key, value);
        return copy;
    }

    @Test
    @DisplayName("A bill paid by card then cash is split, gives change, closes the order and prints a receipt")
    void splitBillCardThenCashClosesOrder() throws Exception {
        Setup s = setup("split", "12.50");
        JsonNode order = order(s.world(), s.waiterToken(), Map.of(s.itemId(), 2));
        UUID orderId = id(order);
        assertThat(money(order, "total")).isEqualByComparingTo("25.00");

        JsonNode before = get(PAYMENTS, s.world().restaurantId(), orderId).as(s.waiterToken()).expect(200);
        assertThat(money(before, "balanceDue")).isEqualByComparingTo("25.00");
        assertThat(before.get("paymentStatus").asText()).isEqualTo("UNPAID");
        assertThat(before.get("tipSuggestions")).hasSize(3);

        JsonNode card = post(PAYMENTS, s.world().restaurantId(), orderId).as(s.waiterToken())
                .body(with(with(pay("CARD", "10.00"), "tipAmount", new BigDecimal("1.00")), "cardLast4", "4242"))
                .expect(201);
        assertThat(card.get("orderClosed").asBoolean()).isFalse();
        assertThat(money(card.get("summary"), "balanceDue")).isEqualByComparingTo("15.00");
        assertThat(card.get("summary").get("paymentStatus").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(card.get("payment").get("status").asText()).isEqualTo("CAPTURED");
        assertThat(card.get("payment").get("referenceNumber").asText()).startsWith("PAY-");
        assertThat(card.get("payment").get("receiptNumber").asText()).startsWith("INV-");
        assertThat(card.get("payment").get("takenByName").asText()).startsWith("Waiter");

        JsonNode cash = post(PAYMENTS, s.world().restaurantId(), orderId).as(s.waiterToken())
                .body(with(pay("CASH", "15.00"), "tenderedAmount", new BigDecimal("20.00")))
                .expect(201);
        assertThat(cash.get("orderClosed").asBoolean()).isTrue();
        assertThat(money(cash.get("payment"), "changeAmount")).isEqualByComparingTo("5.00");
        assertThat(cash.get("summary").get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(cash.get("summary").get("orderStatus").asText()).isEqualTo("CLOSED");
        assertThat(money(cash.get("summary"), "paidTotal")).isEqualByComparingTo("25.00");
        assertThat(money(cash.get("summary"), "tipTotal")).isEqualByComparingTo("1.00");
        assertThat(money(cash.get("summary"), "balanceDue")).isEqualByComparingTo("0.00");

        JsonNode orderAfter = get("/restaurants/{r}/orders/{o}", s.world().restaurantId(), orderId).as(s.waiterToken()).expect(200);
        assertThat(orderAfter.get("status").asText()).isEqualTo("CLOSED");
        assertThat(orderAfter.get("paymentStatus").asText()).isEqualTo("PAID");

        JsonNode receipt = get("/restaurants/{r}/orders/{o}/receipt", s.world().restaurantId(), orderId).as(s.waiterToken()).expect(200);
        assertThat(receipt.get("lines")).hasSize(1);
        assertThat(receipt.get("lines").get(0).get("quantity").asInt()).isEqualTo(2);
        assertThat(receipt.get("payments")).hasSize(2);
        assertThat(money(receipt, "total")).isEqualByComparingTo("25.00");
        assertThat(money(receipt, "paidTotal")).isEqualByComparingTo("25.00");
        assertThat(receipt.get("serverName").asText()).startsWith("Waiter");

        UUID cardPaymentId = UUID.fromString(card.get("payment").get("id").asText());
        JsonNode detail = get("/restaurants/{r}/payments/{p}", s.world().restaurantId(), cardPaymentId).as(s.waiterToken()).expect(200);
        assertThat(detail.get("transactions")).hasSize(1);
        assertThat(detail.get("transactions").get(0).get("type").asText()).isEqualTo("SALE");
        assertThat(money(detail.get("transactions").get(0), "amount")).isEqualByComparingTo("11.00");

        JsonNode ownList = get("/restaurants/{r}/branches/{b}/payments", s.world().restaurantId(), s.world().branchId())
                .as(s.waiterToken()).expect(200);
        assertThat(ownList.get("items")).hasSize(2);
        JsonNode search = get("/restaurants/{r}/branches/{b}/payments", s.world().restaurantId(), s.world().branchId())
                .as(s.world().ownerToken()).param("method", "CASH").expect(200);
        assertThat(search.get("items")).hasSize(1);
        // Without ORDER_AUDIT a waiter can't look at someone else's payments.
        get("/restaurants/{r}/branches/{b}/payments", s.world().restaurantId(), s.world().branchId())
                .as(s.waiterToken()).param("staffId", s.manager().getId()).expect(403);

        // Nothing is left to pay.
        post(PAYMENTS, s.world().restaurantId(), orderId).as(s.waiterToken()).body(pay("CARD", "1.00")).expect(400);
    }

    @Test
    @DisplayName("Bad amounts, tips, cash and card details are refused with 400 and never stored")
    void refusesBadInput() throws Exception {
        Setup s = setup("bad", "25.00");
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        UUID r = s.world().restaurantId();
        String t = s.waiterToken();

        post(PAYMENTS, r, orderId).as(t).body(pay("CARD", "25.01")).expect(400);
        post(PAYMENTS, r, orderId).as(t).body(pay("CARD", "0")).expect(400);
        post(PAYMENTS, r, orderId).as(t).body(pay("CARD", "-5")).expect(400);
        post(PAYMENTS, r, orderId).as(t).body(pay("CARD", "1.005")).expect(400);
        post(PAYMENTS, r, orderId).as(t).body(pay("CARD", "1000000000000")).expect(400);
        post(PAYMENTS, r, orderId).as(t).body("{\"method\":\"CARD\",\"amount\":1e400}").expect(400);
        post(PAYMENTS, r, orderId).as(t).body("{\"method\":\"CARD\",\"amount\":\"ten\"}").expect(400);
        post(PAYMENTS, r, orderId).as(t).body("{\"method\":\"BITCOIN\",\"amount\":5}").expect(400);
        post(PAYMENTS, r, orderId).as(t).body("{\"amount\":5}").expect(400);
        post(PAYMENTS, r, orderId).as(t).body("{\"method\":\"CARD\",").expect(400);
        post(PAYMENTS, r, orderId).as(t).body("").expect(400);
        post(PAYMENTS, r, orderId).as(t).body("[]").expect(400);
        // Tip above 50% of the payment (the default limit).
        post(PAYMENTS, r, orderId).as(t).body(with(pay("CARD", "10.00"), "tipAmount", new BigDecimal("5.01"))).expect(400);
        post(PAYMENTS, r, orderId).as(t).body(with(pay("CARD", "10.00"), "tipAmount", new BigDecimal("-1"))).expect(400);
        // Cash handed over must cover amount and tip; only cash has it.
        post(PAYMENTS, r, orderId).as(t).body(with(pay("CASH", "10.00"), "tenderedAmount", new BigDecimal("9.99"))).expect(400);
        post(PAYMENTS, r, orderId).as(t).body(with(pay("CARD", "10.00"), "tenderedAmount", new BigDecimal("20.00"))).expect(400);
        post(PAYMENTS, r, orderId).as(t).body(with(pay("CARD", "10.00"), "cardLast4", "42a2")).expect(400);
        post(PAYMENTS, r, orderId).as(t).body(with(pay("CARD", "10.00"), "cardLast4", "42424")).expect(400);
        post(PAYMENTS, r, orderId).as(t).body(with(pay("CASH", "10.00"), "cardLast4", "4242")).expect(400);
        post(PAYMENTS, r, orderId).as(t).body(with(pay("CARD", "10.00"), "notes", "x".repeat(1001))).expect(400);
        post(PAYMENTS, r, orderId).as(t).body(with(pay("CARD", "10.00"), "cardBrand", "V".repeat(41))).expect(400);
        post(PAYMENTS, r, orderId).as(t).body(with(pay("CARD", "10.00"), "externalReference", "R".repeat(101))).expect(400);
        post(PAYMENTS, r, "not-a-uuid").as(t).body(pay("CARD", "10.00")).expect(400);
        post(PAYMENTS, r, UUID.randomUUID()).as(t).body(pay("CARD", "10.00")).expect(404);
        post(PAYMENTS, r, orderId).body(pay("CARD", "10.00")).expect(401);

        JsonNode after = get(PAYMENTS, r, orderId).as(t).expect(200);
        assertThat(after.get("payments")).isEmpty();
        assertThat(money(after, "balanceDue")).isEqualByComparingTo("25.00");

        // Long, unusual but valid text is kept exactly.
        String notes = "Ünïcødé ✓ 🍝 '; DROP TABLE payments; -- " + "n".repeat(900);
        JsonNode ok = post(PAYMENTS, r, orderId).as(t)
                .body(with(with(pay("CARD", "10.00"), "notes", notes), "cardBrand", "V".repeat(40)))
                .expect(201);
        assertThat(ok.get("payment").get("notes").asText()).isEqualTo(notes);

        // Tips can be switched off for the restaurant.
        setting(s.world(), "tips_enabled", false);
        post(PAYMENTS, r, orderId).as(t).body(with(pay("CARD", "1.00"), "tipAmount", new BigDecimal("0.10"))).expect(400);
        post(PAYMENTS, r, orderId).as(t).body(with(pay("CARD", "1.00"), "tipAmount", BigDecimal.ZERO)).expect(201);
    }

    @Test
    @DisplayName("Refunds need PAYMENT_REFUND and a reason, can be partial, and never exceed what was paid")
    void refunds() throws Exception {
        Setup s = setup("refund", "25.00");
        UUID r = s.world().restaurantId();
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        JsonNode paid = post(PAYMENTS, r, orderId).as(s.waiterToken())
                .body(with(pay("CARD", "25.00"), "tipAmount", new BigDecimal("5.00"))).expect(201);
        UUID paymentId = UUID.fromString(paid.get("payment").get("id").asText());
        String refund = PAYMENTS + "/{p}/refund";

        post(refund, r, orderId, paymentId).as(s.waiterToken()).body(Map.of("amount", 5, "reason", "Cold food")).expect(403);
        post(refund, r, orderId, paymentId).as(s.managerToken()).body(Map.of("amount", 5)).expect(400);
        post(refund, r, orderId, paymentId).as(s.managerToken()).body(Map.of("amount", 5, "reason", "  ")).expect(400);
        post(refund, r, orderId, paymentId).as(s.managerToken()).body(Map.of("amount", 5, "reason", "x".repeat(501))).expect(400);
        post(refund, r, orderId, paymentId).as(s.managerToken()).body(Map.of("amount", 0, "reason", "Cold food")).expect(400);

        JsonNode first = post(refund, r, orderId, paymentId).as(s.managerToken())
                .body(Map.of("amount", new BigDecimal("10.00"), "reason", "Cold food")).expect(200);
        assertThat(first.get("paymentStatus").asText()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(money(first, "refundedTotal")).isEqualByComparingTo("10.00");
        // The bill part goes back first: 25 - 10 still counts towards the bill, the tip is untouched.
        assertThat(money(first, "paidTotal")).isEqualByComparingTo("15.00");
        assertThat(money(first, "tipTotal")).isEqualByComparingTo("5.00");

        post(refund, r, orderId, paymentId).as(s.managerToken()).body(Map.of("amount", new BigDecimal("20.01"), "reason", "Too much")).expect(400);
        JsonNode all = post(refund, r, orderId, paymentId).as(s.managerToken())
                .body(Map.of("amount", new BigDecimal("20.00"), "reason", "Whole bill back")).expect(200);
        assertThat(all.get("paymentStatus").asText()).isEqualTo("REFUNDED");
        assertThat(all.get("payments").get(0).get("status").asText()).isEqualTo("REFUNDED");
        assertThat(money(all.get("payments").get(0), "refundableAmount")).isEqualByComparingTo("0");
        post(refund, r, orderId, paymentId).as(s.managerToken()).body(Map.of("amount", 1, "reason", "Again")).expect(400);
        // A refunded payment can't be cancelled.
        post(PAYMENTS + "/{p}/void", r, orderId, paymentId).as(s.managerToken()).body(Map.of("reason", "Mistake")).expect(400);

        JsonNode detail = get("/restaurants/{r}/payments/{p}", r, paymentId).as(s.managerToken()).expect(200);
        assertThat(detail.get("transactions")).hasSize(3);
        assertThat(detail.get("transactions").get(1).get("reason").asText()).isEqualTo("Cold food");
        assertThat(detail.get("transactions").get(1).get("createdByName").asText()).startsWith("Manager");

        // Outside the refund window nothing can be given back.
        UUID second = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        JsonNode secondPaid = post(PAYMENTS, r, second).as(s.waiterToken()).body(pay("CARD", "25.00")).expect(201);
        UUID secondPayment = UUID.fromString(secondPaid.get("payment").get("id").asText());
        jdbcTemplate.update("update " + SCHEMA + ".payments set paid_at = now() - interval '31 days' where id = ?", secondPayment);
        post(refund, r, second, secondPayment).as(s.managerToken()).body(Map.of("amount", 1, "reason", "Late")).expect(400);
        setting(s.world(), "refund_window_days", 0);
        post(refund, r, second, secondPayment).as(s.managerToken()).body(Map.of("amount", 1, "reason", "No limit now")).expect(200);
    }

    @Test
    @DisplayName("Concurrent refunds never return more than the captured payment")
    void concurrentRefunds() throws Exception {
        Setup s = setup("refund-race", "25.00");
        UUID restaurantId = s.world().restaurantId();
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        JsonNode captured = post(PAYMENTS, restaurantId, orderId).as(s.waiterToken())
                .body(pay("CARD", "25.00")).expect(201);
        UUID paymentId = UUID.fromString(captured.get("payment").get("id").asText());

        int tills = 6;
        ExecutorService pool = Executors.newFixedThreadPool(tills);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < tills; i++) {
                String key = "refund-race-" + UUID.randomUUID();
                Callable<Integer> task = () -> {
                    start.await();
                    return post(PAYMENTS + "/{p}/refund", restaurantId, orderId, paymentId)
                            .as(s.managerToken())
                            .header("Idempotency-Key", key)
                            .body(Map.of("amount", new BigDecimal("10.00"), "reason", "Concurrent refund"))
                            .send().status();
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(status -> status == 200).hasSize(2);
            assertThat(statuses).allMatch(status -> status == 200 || status == 400 || status == 409);
        } finally {
            pool.shutdownNow();
        }

        JsonNode summary = get(PAYMENTS, restaurantId, orderId).as(s.managerToken()).expect(200);
        assertThat(money(summary, "refundedTotal")).isEqualByComparingTo("20.00");
        assertThat(money(summary, "paidTotal")).isEqualByComparingTo("5.00");
        assertThat(money(summary.get("payments").get(0), "refundableAmount")).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("A payment taken by mistake is cancelled the same day by a manager and the order opens again")
    void voidPayment() throws Exception {
        Setup s = setup("void", "25.00");
        UUID r = s.world().restaurantId();
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        JsonNode paid = post(PAYMENTS, r, orderId).as(s.waiterToken()).body(pay("CASH", "25.00")).expect(201);
        assertThat(paid.get("orderClosed").asBoolean()).isTrue();
        UUID paymentId = UUID.fromString(paid.get("payment").get("id").asText());
        String voidPath = PAYMENTS + "/{p}/void";

        post(voidPath, r, orderId, paymentId).as(s.waiterToken()).body(Map.of("reason", "Wrong method")).expect(403);
        post(voidPath, r, orderId, paymentId).as(s.managerToken()).body(Map.of()).expect(400);
        JsonNode voided = post(voidPath, r, orderId, paymentId).as(s.managerToken()).body(Map.of("reason", "Wrong method")).expect(200);
        assertThat(voided.get("orderStatus").asText()).isEqualTo("OPEN");
        assertThat(voided.get("paymentStatus").asText()).isEqualTo("UNPAID");
        assertThat(money(voided, "balanceDue")).isEqualByComparingTo("25.00");
        assertThat(voided.get("payments").get(0).get("status").asText()).isEqualTo("VOIDED");
        assertThat(voided.get("payments").get(0).get("voidReason").asText()).isEqualTo("Wrong method");
        post(voidPath, r, orderId, paymentId).as(s.managerToken()).body(Map.of("reason", "Again")).expect(400);

        // Pay again properly with a card.
        post(PAYMENTS, r, orderId).as(s.waiterToken()).body(pay("CARD", "25.00")).expect(201);

        // A payment from an earlier day must be refunded instead.
        UUID other = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        JsonNode otherPaid = post(PAYMENTS, r, other).as(s.waiterToken()).body(pay("CARD", "10.00")).expect(201);
        UUID otherPayment = UUID.fromString(otherPaid.get("payment").get("id").asText());
        jdbcTemplate.update("update " + SCHEMA + ".payments set paid_at = now() - interval '2 days' where id = ?", otherPayment);
        post(voidPath, r, other, otherPayment).as(s.managerToken()).body(Map.of("reason", "Old")).expect(400);
        // Unknown payment or a payment of another order is not found.
        post(voidPath, r, orderId, otherPayment).as(s.managerToken()).body(Map.of("reason", "Wrong order")).expect(404);
        post(voidPath, r, orderId, UUID.randomUUID()).as(s.managerToken()).body(Map.of("reason", "Nope")).expect(404);
    }

    @Test
    @DisplayName("Orders holding money can't be voided or cancelled; paid orders must be reopened first")
    void orderGuardsWithMoney() throws Exception {
        Setup s = setup("guards", "25.00");
        UUID r = s.world().restaurantId();
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        post(PAYMENTS, r, orderId).as(s.waiterToken()).body(pay("CARD", "10.00")).expect(201);

        post("/restaurants/{r}/orders/{o}/void", r, orderId).as(s.managerToken()).body(Map.of("reason", "Test")).expect(400);
        post("/restaurants/{r}/orders/{o}/cancel", r, orderId).as(s.managerToken()).body(Map.of("reason", "Test")).expect(400);

        post(PAYMENTS, r, orderId).as(s.waiterToken()).body(pay("CARD", "15.00")).expect(201);
        post("/restaurants/{r}/orders/{o}/void", r, orderId).as(s.managerToken()).body(Map.of("reason", "Test")).expect(400);
        post("/restaurants/{r}/orders/{o}/cancel", r, orderId).as(s.managerToken()).body(Map.of("reason", "Test")).expect(400);

        // A cancelled order can't be paid and can't be cancelled twice.
        UUID cancelled = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        post("/restaurants/{r}/orders/{o}/cancel", r, cancelled).as(s.managerToken()).body(Map.of("reason", "Guest left")).expect(200);
        post("/restaurants/{r}/orders/{o}/cancel", r, cancelled).as(s.managerToken()).body(Map.of("reason", "Again")).expect(400);
        post(PAYMENTS, r, cancelled).as(s.waiterToken()).body(pay("CARD", "25.00")).expect(400);
    }

    @Test
    @DisplayName("Retrying with the same Idempotency-Key replays the first answer and never charges twice")
    void idempotentRetry() throws Exception {
        Setup s = setup("idem", "25.00");
        UUID r = s.world().restaurantId();
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        String key = "pay-" + UUID.randomUUID();

        Response first = post(PAYMENTS, r, orderId).as(s.waiterToken()).header("Idempotency-Key", key).body(pay("CARD", "10.00")).send();
        Response retry = post(PAYMENTS, r, orderId).as(s.waiterToken()).header("Idempotency-Key", key).body(pay("CARD", "10.00")).send();
        assertThat(first.status()).isEqualTo(201);
        assertThat(retry.status()).isEqualTo(201);
        assertThat(retry.header("Idempotency-Replayed")).isEqualTo("true");
        assertThat(retry.json().get("payment").get("id").asText()).isEqualTo(first.json().get("payment").get("id").asText());

        Response reused = post(PAYMENTS, r, orderId).as(s.waiterToken()).header("Idempotency-Key", key).body(pay("CARD", "11.00")).send();
        assertThat(reused.status()).isEqualTo(409);
        post(PAYMENTS, r, orderId).as(s.waiterToken()).header("Idempotency-Key", "short").body(pay("CARD", "1.00")).expect(400);

        JsonNode summary = get(PAYMENTS, r, orderId).as(s.waiterToken()).expect(200);
        assertThat(summary.get("payments")).hasSize(1);
        assertThat(money(summary, "balanceDue")).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("Retrying a refund with the same key replays it once and rejects a changed refund")
    void idempotentRefundRetry() throws Exception {
        Setup s = setup("refund-idem", "25.00");
        UUID restaurantId = s.world().restaurantId();
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        JsonNode captured = post(PAYMENTS, restaurantId, orderId).as(s.waiterToken())
                .body(pay("CARD", "25.00")).expect(201);
        UUID paymentId = UUID.fromString(captured.get("payment").get("id").asText());
        String key = "refund-" + UUID.randomUUID();
        Map<String, Object> refund = Map.of("amount", new BigDecimal("5.00"), "reason", "Guest changed mind");

        Response first = post(PAYMENTS + "/{p}/refund", restaurantId, orderId, paymentId).as(s.managerToken())
                .header("Idempotency-Key", key).body(refund).send();
        Response retry = post(PAYMENTS + "/{p}/refund", restaurantId, orderId, paymentId).as(s.managerToken())
                .header("Idempotency-Key", key).body(refund).send();
        Response changed = post(PAYMENTS + "/{p}/refund", restaurantId, orderId, paymentId).as(s.managerToken())
                .header("Idempotency-Key", key)
                .body(Map.of("amount", new BigDecimal("6.00"), "reason", "Different refund")).send();

        assertThat(first.status()).isEqualTo(200);
        assertThat(retry.status()).isEqualTo(200);
        assertThat(retry.header("Idempotency-Replayed")).isEqualTo("true");
        assertThat(changed.status()).isEqualTo(409);
        assertThat(money(retry.json(), "refundedTotal")).isEqualByComparingTo("5.00");

        JsonNode detail = get("/restaurants/{r}/payments/{p}", restaurantId, paymentId)
                .as(s.managerToken()).expect(200);
        assertThat(money(detail, "refundedAmount")).isEqualByComparingTo("5.00");
        assertThat(detail.get("transactions")).hasSize(2);
        assertThat(detail.get("transactions").findValuesAsText("type")).containsExactly("SALE", "REFUND");
    }

    @Test
    @DisplayName("Retrying a void with the same key replays it once and rejects a changed reason")
    void idempotentVoidRetry() throws Exception {
        Setup s = setup("void-idem", "25.00");
        UUID restaurantId = s.world().restaurantId();
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        JsonNode captured = post(PAYMENTS, restaurantId, orderId).as(s.waiterToken())
                .body(pay("CASH", "25.00")).expect(201);
        UUID paymentId = UUID.fromString(captured.get("payment").get("id").asText());
        String key = "void-payment-" + UUID.randomUUID();
        String path = PAYMENTS + "/{p}/void";
        Map<String, Object> request = Map.of("reason", "Wrong method");

        Response first = post(path, restaurantId, orderId, paymentId).as(s.managerToken())
                .header("Idempotency-Key", key).body(request).send();
        Response retry = post(path, restaurantId, orderId, paymentId).as(s.managerToken())
                .header("Idempotency-Key", key).body(request).send();
        Response changed = post(path, restaurantId, orderId, paymentId).as(s.managerToken())
                .header("Idempotency-Key", key).body(Map.of("reason", "Different reason")).send();

        assertThat(first.status()).isEqualTo(200);
        assertThat(retry.status()).isEqualTo(200);
        assertThat(retry.header("Idempotency-Replayed")).isEqualTo("true");
        assertThat(changed.status()).isEqualTo(409);
        assertThat(retry.json().get("orderStatus").asText()).isEqualTo("OPEN");

        JsonNode detail = get("/restaurants/{r}/payments/{p}", restaurantId, paymentId)
                .as(s.managerToken()).expect(200);
        assertThat(detail.get("status").asText()).isEqualTo("VOIDED");
        assertThat(detail.get("transactions")).hasSize(2);
        assertThat(detail.get("transactions").findValuesAsText("type")).containsExactly("SALE", "VOID");
    }

    @Test
    @DisplayName("taking, refunding and cancelling payments require an idempotency key")
    void paymentMutationsRequireIdempotencyKeys() throws Exception {
        Setup s = setup("payment-key-required", "25.00");
        UUID restaurantId = s.world().restaurantId();
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));

        postWithoutDefaultIdempotencyKey(PAYMENTS, restaurantId, orderId).as(s.waiterToken())
                .body(pay("CARD", "25.00")).expect(400);
        JsonNode captured = post(PAYMENTS, restaurantId, orderId).as(s.waiterToken())
                .body(pay("CARD", "25.00")).expect(201);
        UUID paymentId = UUID.fromString(captured.get("payment").get("id").asText());

        postWithoutDefaultIdempotencyKey(PAYMENTS + "/{p}/refund", restaurantId, orderId, paymentId)
                .as(s.managerToken()).body(Map.of("amount", new BigDecimal("5.00"), "reason", "Key test")).expect(400);
        postWithoutDefaultIdempotencyKey(PAYMENTS + "/{p}/void", restaurantId, orderId, paymentId)
                .as(s.managerToken()).body(Map.of("reason", "Key test")).expect(400);

        JsonNode detail = get("/restaurants/{r}/payments/{p}", restaurantId, paymentId)
                .as(s.managerToken()).expect(200);
        assertThat(detail.get("status").asText()).isEqualTo("CAPTURED");
        assertThat(detail.get("transactions")).hasSize(1);
        assertThat(money(detail, "refundedAmount")).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Two tills paying the same bill at once never take more than the bill")
    void concurrentPayments() throws Exception {
        Setup s = setup("race", "25.00");
        UUID r = s.world().restaurantId();
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        int tills = 6;
        ExecutorService pool = Executors.newFixedThreadPool(tills);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < tills; i++) {
                String token = i % 2 == 0 ? s.waiterToken() : s.managerToken();
                Callable<Integer> task = () -> {
                    start.await();
                    return post(PAYMENTS, r, orderId).as(token).header("Idempotency-Key", "race-" + UUID.randomUUID())
                            .body(pay("CARD", "25.00")).send().status();
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
            assertThat(statuses).allMatch(status -> status == 201 || status == 400 || status == 409);
        } finally {
            pool.shutdownNow();
        }
        JsonNode summary = get(PAYMENTS, r, orderId).as(s.waiterToken()).expect(200);
        assertThat(summary.get("payments")).hasSize(1);
        assertThat(money(summary, "paidTotal")).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("concurrent retries with the same payment key create and return one payment")
    void concurrentSameKeyPaymentRetriesReplayTheFirstResult() throws Exception {
        Setup s = setup("same-key-payment-race", "25.00");
        UUID restaurantId = s.world().restaurantId();
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        String key = "same-payment-" + UUID.randomUUID();
        int retries = 6;
        ExecutorService pool = Executors.newFixedThreadPool(retries);
        CountDownLatch ready = new CountDownLatch(retries);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response>> responses = new ArrayList<>();
        try {
            for (int index = 0; index < retries; index++) {
                responses.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Same-key retry start timed out");
                    }
                    return post(PAYMENTS, restaurantId, orderId).as(s.waiterToken())
                            .header("Idempotency-Key", key).body(pay("CARD", "10.00")).send();
                }));
            }
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Response> completed = new ArrayList<>();
            for (Future<Response> response : responses) completed.add(response.get());

            assertThat(completed).allSatisfy(response -> assertThat(response.status()).isEqualTo(201));
            List<String> paymentIds = new ArrayList<>();
            for (Response response : completed) {
                paymentIds.add(response.json().get("payment").get("id").asText());
            }
            assertThat(paymentIds.stream().distinct()).hasSize(1);
            assertThat(completed.stream().filter(response -> "true".equals(response.header("Idempotency-Replayed")))
                    .count()).isEqualTo(retries - 1);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }

        JsonNode summary = get(PAYMENTS, restaurantId, orderId).as(s.waiterToken()).expect(200);
        assertThat(summary.get("payments")).hasSize(1);
        assertThat(money(summary, "paidTotal")).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("With split bills off the whole bill is paid at once; cash is rounded to the restaurant's step")
    void restaurantPaymentRules() throws Exception {
        Setup s = setup("rules", "10.02");
        UUID r = s.world().restaurantId();
        setting(s.world(), "allow_split_bills", false);
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        post(PAYMENTS, r, orderId).as(s.waiterToken()).body(pay("CARD", "5.00")).expect(400);
        post(PAYMENTS, r, orderId).as(s.waiterToken()).body(pay("CARD", "10.02")).expect(201);

        setting(s.world(), "allow_split_bills", true);
        setting(s.world(), "cash_rounding_enabled", true);
        setting(s.world(), "cash_rounding_increment", new BigDecimal("0.05"));
        UUID cashOrder = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        JsonNode summary = get(PAYMENTS, r, cashOrder).as(s.waiterToken()).expect(200);
        assertThat(money(summary, "balanceDue")).isEqualByComparingTo("10.02");
        assertThat(money(summary, "cashBalanceDue")).isEqualByComparingTo("10.00");
        JsonNode cash = post(PAYMENTS, r, cashOrder).as(s.waiterToken())
                .body(with(pay("CASH", "10.00"), "tenderedAmount", new BigDecimal("10.00"))).expect(201);
        assertThat(cash.get("summary").get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(cash.get("orderClosed").asBoolean()).isTrue();
        assertThat(money(cash.get("payment"), "changeAmount")).isEqualByComparingTo("0.00");

        // Asked not to close: paid but still open.
        UUID keepOpen = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        JsonNode open = post(PAYMENTS, r, keepOpen).as(s.waiterToken())
                .body(with(pay("CARD", "10.02"), "closeOrderWhenPaid", false)).expect(201);
        assertThat(open.get("orderClosed").asBoolean()).isFalse();
        assertThat(open.get("summary").get("orderStatus").asText()).isEqualTo("OPEN");
        assertThat(open.get("summary").get("paymentStatus").asText()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("Another restaurant's staff can't see, take, refund or cancel payments")
    void tenantIsolation() throws Exception {
        Setup s = setup("tenant", "25.00");
        World other = newWorld("tenant-other");
        UUID r = s.world().restaurantId();
        UUID orderId = id(order(s.world(), s.waiterToken(), Map.of(s.itemId(), 1)));
        JsonNode paid = post(PAYMENTS, r, orderId).as(s.waiterToken()).body(pay("CARD", "10.00")).expect(201);
        UUID paymentId = UUID.fromString(paid.get("payment").get("id").asText());
        String outsider = other.ownerToken();

        assertThat(get(PAYMENTS, r, orderId).as(outsider).send().status()).isIn(403, 404);
        assertThat(post(PAYMENTS, r, orderId).as(outsider).body(pay("CARD", "1.00")).send().status()).isIn(403, 404);
        assertThat(post(PAYMENTS + "/{p}/refund", r, orderId, paymentId).as(outsider)
                .body(Map.of("amount", 1, "reason", "Steal")).send().status()).isIn(403, 404);
        assertThat(post(PAYMENTS + "/{p}/void", r, orderId, paymentId).as(outsider)
                .body(Map.of("reason", "Steal")).send().status()).isIn(403, 404);
        assertThat(get("/restaurants/{r}/payments/{p}", r, paymentId).as(outsider).send().status()).isIn(403, 404);
        assertThat(get("/restaurants/{r}/orders/{o}/receipt", r, orderId).as(outsider).send().status()).isIn(403, 404);
        // Their own restaurant id with our order id finds nothing.
        assertThat(get(PAYMENTS, other.restaurantId(), orderId).as(outsider).send().status()).isEqualTo(404);
        assertThat(get("/restaurants/{r}/payments/{p}", other.restaurantId(), paymentId).as(outsider).send().status()).isEqualTo(404);

        JsonNode summary = get(PAYMENTS, r, orderId).as(s.waiterToken()).expect(200);
        assertThat(summary.get("payments")).hasSize(1);
        assertThat(money(summary, "paidTotal")).isEqualByComparingTo("10.00");
    }
}
