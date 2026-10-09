package pos.pos.integration.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.user.entity.User;

import java.math.BigDecimal;
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

@DisplayName("Inventory end to end")
class InventoryFlowIntegrationTest extends AbstractPosApiIntegrationTest {

    private static final String BASE = "/restaurants/{r}/inventory";
    private static final String SALE_SOURCES = BASE + "/sale-sources";

    private UUID location(World world, String name, String type) throws Exception {
        return id(post(BASE + "/locations", world.restaurantId()).as(world.ownerToken())
                .body(Map.of("name", name, "locationType", type, "branchId", world.branchId())).expect(201));
    }

    private UUID stockItem(World world, String name, String unit, String cost) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("itemType", "INGREDIENT");
        body.put("baseUnit", unit);
        body.put("costPerUnit", new BigDecimal(cost));
        body.put("reorderPoint", new BigDecimal("5"));
        body.put("parLevel", new BigDecimal("20"));
        body.put("supplierName", "Green Farm");
        return id(post(BASE + "/items", world.restaurantId()).as(world.ownerToken()).body(body).expect(201));
    }

    private BigDecimal onHand(World world, UUID locationId, UUID itemId) throws Exception {
        JsonNode level = get(BASE + "/levels/{l}/{i}", world.restaurantId(), locationId, itemId).as(world.ownerToken()).expect(200);
        return money(level, "onHandQuantity");
    }

    @Test
    @DisplayName("stock is received, wasted, moved, adjusted and counted, and levels follow every step")
    void stockMovesAndLevels() throws Exception {
        World world = newWorld("stock");
        UUID kitchen = location(world, "Kitchen", "KITCHEN");
        UUID walkIn = location(world, "Walk-in", "WALK_IN");
        UUID tomatoes = stockItem(world, "Tomatoes", "KILOGRAM", "2.5000");
        UUID r = world.restaurantId();
        String t = world.ownerToken();

        post(BASE + "/receive", r).as(t).body(Map.of("locationId", walkIn, "inventoryItemId", tomatoes, "quantity", new BigDecimal("30.500"),
                "reason", "Delivery #81")).expect(201);
        assertThat(onHand(world, walkIn, tomatoes)).isEqualByComparingTo("30.500");

        post(BASE + "/transfer", r).as(t).body(Map.of("fromLocationId", walkIn, "toLocationId", kitchen, "inventoryItemId", tomatoes,
                "quantity", new BigDecimal("10"), "reason", "Prep for dinner")).expect(201);
        assertThat(onHand(world, walkIn, tomatoes)).isEqualByComparingTo("20.500");
        assertThat(onHand(world, kitchen, tomatoes)).isEqualByComparingTo("10");

        post(BASE + "/waste", r).as(t).body(Map.of("locationId", kitchen, "inventoryItemId", tomatoes, "quantity", new BigDecimal("1.25"),
                "reason", "Bruised")).expect(201);
        post(BASE + "/adjustments", r).as(t).body(Map.of("locationId", kitchen, "inventoryItemId", tomatoes, "quantityDelta", new BigDecimal("-0.75"),
                "reason", "Scale was off")).expect(201);
        assertThat(onHand(world, kitchen, tomatoes)).isEqualByComparingTo("8.000");

        JsonNode total = get(BASE + "/levels/total/{i}", r, tomatoes).as(t).expect(200);
        assertThat(total.toString()).contains("28.5");

        JsonNode movements = get(BASE + "/movements", r).as(t).param("itemId", tomatoes).expect(200);
        assertThat(movements.toString()).contains("RECEIPT").contains("WASTE");
        // History comes newest first, a page at a time.
        JsonNode newest = get(BASE + "/movements", r).as(t).param("itemId", tomatoes).param("size", 2).expect(200);
        assertThat(newest).hasSize(2);
        assertThat(newest.get(0).get("movementType").asText()).isEqualTo("MANUAL_ADJUSTMENT");
        JsonNode older = get(BASE + "/movements", r).as(t).param("itemId", tomatoes).param("size", 2).param("page", 1).expect(200);
        assertThat(older.toString()).doesNotContain(newest.get(0).get("id").asText());
        assertThat(get(BASE + "/movements", r).as(t).param("itemId", tomatoes).param("page", 50).expect(200)).isEmpty();
        get(BASE + "/movements", r).as(t).param("size", 501).expect(400);
        get(BASE + "/movements", r).as(t).param("page", -1).expect(400);
        get(BASE + "/movements", r).as(t).param("type", "STOLEN").expect(400);

        // Impossible movements are refused and change nothing.
        post(BASE + "/waste", r).as(t).body(Map.of("locationId", kitchen, "inventoryItemId", tomatoes, "quantity", new BigDecimal("-1"),
                "reason", "x")).expect(400);
        post(BASE + "/waste", r).as(t).body(Map.of("locationId", kitchen, "inventoryItemId", tomatoes, "quantity", new BigDecimal("1"))).expect(400);
        post(BASE + "/transfer", r).as(t).body(Map.of("fromLocationId", kitchen, "toLocationId", kitchen, "inventoryItemId", tomatoes,
                "quantity", BigDecimal.ONE)).send();
        post(BASE + "/receive", r).as(t).body(Map.of("locationId", walkIn, "inventoryItemId", tomatoes, "quantity", new BigDecimal("1.2345"))).expect(400);
        post(BASE + "/receive", r).as(t).body(Map.of("locationId", walkIn, "inventoryItemId", tomatoes, "quantity", new BigDecimal("1000000000"))).expect(400);
        post(BASE + "/receive", r).as(t).body(Map.of("locationId", UUID.randomUUID(), "inventoryItemId", tomatoes, "quantity", BigDecimal.ONE)).send();
        assertThat(onHand(world, kitchen, tomatoes)).isEqualByComparingTo("8.000");
    }

    @Test
    @DisplayName("stock movement retries replay once and reject a changed request")
    void stockMovementRetriesAreIdempotent() throws Exception {
        World world = newWorld("stock-movement-idempotency");
        UUID kitchen = location(world, "Kitchen", "KITCHEN");
        UUID flour = stockItem(world, "Flour", "KILOGRAM", "1.2500");
        String key = "inventory-receipt-" + UUID.randomUUID();
        Map<String, Object> receipt = Map.of(
                "locationId", kitchen,
                "inventoryItemId", flour,
                "quantity", new BigDecimal("2.500"),
                "reason", "Delivery #42"
        );

        Response first = post(BASE + "/receive", world.restaurantId()).as(world.ownerToken())
                .header("Idempotency-Key", key).body(receipt).send();
        Response retry = post(BASE + "/receive", world.restaurantId()).as(world.ownerToken())
                .header("Idempotency-Key", key).body(receipt).send();
        Response changed = post(BASE + "/receive", world.restaurantId()).as(world.ownerToken())
                .header("Idempotency-Key", key).body(Map.of(
                        "locationId", kitchen,
                        "inventoryItemId", flour,
                        "quantity", new BigDecimal("3.500"),
                        "reason", "Different delivery"
                )).send();
        Response missingKey = postWithoutDefaultIdempotencyKey(BASE + "/receive", world.restaurantId())
                .as(world.ownerToken()).body(receipt).send();

        assertThat(first.status()).isEqualTo(201);
        assertThat(retry.status()).isEqualTo(201);
        assertThat(retry.header("Idempotency-Replayed")).isEqualTo("true");
        assertThat(retry.json().get("id").asText()).isEqualTo(first.json().get("id").asText());
        assertThat(changed.status()).isEqualTo(409);
        assertThat(missingKey.status()).isEqualTo(400);
        assertThat(onHand(world, kitchen, flour)).isEqualByComparingTo("2.500");
        JsonNode movements = get(BASE + "/movements", world.restaurantId()).as(world.ownerToken())
                .param("itemId", flour).expect(200);
        assertThat(movements).hasSize(1);
    }

    @Test
    @DisplayName("simultaneous stock requests with the same key apply one movement")
    void concurrentSameKeyStockMovementAppliesOnce() throws Exception {
        World world = newWorld("stock-movement-idempotency-race");
        UUID kitchen = location(world, "Kitchen", "KITCHEN");
        UUID flour = stockItem(world, "Flour", "KILOGRAM", "1.2500");
        String key = "inventory-race-" + UUID.randomUUID();
        Map<String, Object> receipt = Map.of(
                "locationId", kitchen,
                "inventoryItemId", flour,
                "quantity", new BigDecimal("2.500"),
                "reason", "Delivery #43"
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Response>> pending = new java.util.ArrayList<>();
        try {
            for (int index = 0; index < 2; index++) {
                pending.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Same-key inventory requests did not start");
                    }
                    return post(BASE + "/receive", world.restaurantId()).as(world.ownerToken())
                            .header("Idempotency-Key", key).body(receipt).send();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Response> responses = pending.stream().map(future -> {
                try {
                    return future.get(20, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).toList();

            assertThat(responses).allMatch(response -> response.status() == 201);
            assertThat(responses).filteredOn(response -> "true".equals(response.header("Idempotency-Replayed"))).hasSize(1);
            assertThat(responses.get(0).json().get("id").asText())
                    .isEqualTo(responses.get(1).json().get("id").asText());
            assertThat(onHand(world, kitchen, flour)).isEqualByComparingTo("2.500");
            assertThat(get(BASE + "/movements", world.restaurantId()).as(world.ownerToken()).param("itemId", flour).expect(200))
                    .hasSize(1);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("concurrent inventory item codes and barcodes remain unique")
    void concurrentInventoryIdentifiersRemainUnique() throws Exception {
        World world = newWorld("inventory-identifiers-race");
        UUID restaurantId = world.restaurantId();
        String token = world.ownerToken();

        Map<String, Object> duplicateCode = new LinkedHashMap<>();
        duplicateCode.put("name", "Code Race Item");
        duplicateCode.put("code", "RACE-CODE");
        duplicateCode.put("itemType", "INGREDIENT");
        duplicateCode.put("baseUnit", "EACH");
        duplicateCode.put("costPerUnit", BigDecimal.ZERO);
        List<Integer> codeStatuses = concurrentItemCreates(restaurantId, token, duplicateCode, true);
        assertThat(codeStatuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(codeStatuses).allMatch(status -> status == 201 || status == 409);
        Integer codesStored = jdbcTemplate.queryForObject(
                "select count(*) from " + SCHEMA + ".inventory_items where restaurant_id = ? and code = ?",
                Integer.class,
                restaurantId,
                "RACE_CODE"
        );
        assertThat(codesStored).isEqualTo(1);

        List<Integer> barcodeStatuses = concurrentItemCreates(restaurantId, token, Map.of(
                "name", "Barcode Race Item",
                "itemType", "INGREDIENT",
                "baseUnit", "EACH",
                "costPerUnit", BigDecimal.ZERO,
                "barcode", "race-123"
        ), false);
        assertThat(barcodeStatuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(barcodeStatuses).allMatch(status -> status == 201 || status == 409);
        Integer barcodesStored = jdbcTemplate.queryForObject(
                "select count(*) from " + SCHEMA + ".inventory_items where restaurant_id = ? and upper(barcode) = ? and deleted_at is null",
                Integer.class,
                restaurantId,
                "RACE-123"
        );
        assertThat(barcodesStored).isEqualTo(1);
    }

    private List<Integer> concurrentItemCreates(
            UUID restaurantId,
            String token,
            Map<String, Object> baseRequest,
            boolean duplicateCode
    ) throws Exception {
        int callers = 6;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<Integer>> results = new java.util.ArrayList<>();
            for (int i = 0; i < callers; i++) {
                int index = i;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Inventory identifier race start timed out");
                    }
                    Map<String, Object> body = new LinkedHashMap<>(baseRequest);
                    body.put("name", baseRequest.get("name") + " " + index);
                    if (!duplicateCode) {
                        body.put("code", "BARCODE_RACE_" + index);
                    }
                    return post(BASE + "/items", restaurantId).as(token).body(body).send().status();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new java.util.ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("concurrent stock movements are atomic and cannot oversell")
    void concurrentMovementsAreAtomicAndCannotOversell() throws Exception {
        World world = newWorld("stock-concurrency");
        UUID kitchen = location(world, "Kitchen", "KITCHEN");
        UUID flour = stockItem(world, "Flour", "KILOGRAM", "1.2500");
        int receipts = 20;
        CountDownLatch ready = new CountDownLatch(receipts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(receipts);
        try {
            var pending = new java.util.ArrayList<Future<?>>();
            for (int index = 0; index < receipts; index++) {
                pending.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent receipt start timed out");
                    }
                    post(BASE + "/receive", world.restaurantId()).as(world.ownerToken())
                            .body(Map.of(
                                    "locationId", kitchen,
                                    "inventoryItemId", flour,
                                    "quantity", new BigDecimal("0.250")
                            )).expect(201);
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> request : pending) {
                request.get(30, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            pool.shutdownNow();
        }

        assertThat(onHand(world, kitchen, flour)).isEqualByComparingTo("5.000");
        JsonNode movementNotifications = get("/restaurants/{r}/notifications", world.restaurantId())
                .as(world.ownerToken())
                .param("eventCode", "INVENTORY_INVENTORY_MOVEMENT_UPSERT")
                .param("size", 100)
                .expect(200);
        assertThat(movementNotifications.get("totalElements").asLong()).isEqualTo(receipts);

        CountDownLatch deductionsReady = new CountDownLatch(2);
        CountDownLatch deductTogether = new CountDownLatch(1);
        ExecutorService deductionPool = Executors.newFixedThreadPool(2);
        try {
            var deductions = new java.util.ArrayList<Future<Integer>>();
            for (int index = 0; index < 2; index++) {
                deductions.add(deductionPool.submit(() -> {
                    deductionsReady.countDown();
                    if (!deductTogether.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent deduction start timed out");
                    }
                    return post(BASE + "/waste", world.restaurantId()).as(world.ownerToken())
                            .body(Map.of(
                                    "locationId", kitchen,
                                    "inventoryItemId", flour,
                                    "quantity", new BigDecimal("3"),
                                    "reason", "Concurrent deduction"
                            )).send().status();
                }));
            }
            assertThat(deductionsReady.await(10, TimeUnit.SECONDS)).isTrue();
            deductTogether.countDown();
            assertThat(deductions.stream().map(request -> {
                try {
                    return request.get(30, TimeUnit.SECONDS);
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            }).toList()).containsExactlyInAnyOrder(201, 409);
        } finally {
            deductTogether.countDown();
            deductionPool.shutdownNow();
        }
        assertThat(onHand(world, kitchen, flour)).isEqualByComparingTo("2.000");
    }

    @Test
    @DisplayName("negative movements cannot create or drive a stock level below zero")
    void negativeMovementRequiresSufficientStock() throws Exception {
        World world = newWorld("stock-floor");
        UUID kitchen = location(world, "Kitchen", "KITCHEN");
        UUID flour = stockItem(world, "Flour", "KILOGRAM", "1.2500");
        Map<String, Object> movement = new LinkedHashMap<>();
        movement.put("locationId", kitchen);
        movement.put("inventoryItemId", flour);
        movement.put("reason", "Negative stock edge case");

        movement.put("quantity", new BigDecimal("1"));
        assertThat(post(BASE + "/waste", world.restaurantId()).as(world.ownerToken())
                .body(movement).send().status()).isEqualTo(409);
        post(BASE + "/receive", world.restaurantId()).as(world.ownerToken())
                .body(Map.of("locationId", kitchen, "inventoryItemId", flour, "quantity", new BigDecimal("2"))).expect(201);

        movement.put("quantity", new BigDecimal("3"));
        assertThat(post(BASE + "/waste", world.restaurantId()).as(world.ownerToken())
                .body(movement).send().status()).isEqualTo(409);
        assertThat(onHand(world, kitchen, flour)).isEqualByComparingTo("2.000");
    }

    @Test
    @DisplayName("inactive locations reject new movements and count approvals")
    void inactiveLocationsRejectMovementsAndCountApproval() throws Exception {
        World world = newWorld("inactive-inventory-location");
        UUID kitchen = location(world, "Kitchen", "KITCHEN");
        UUID bar = location(world, "Bar", "BAR");
        UUID flour = stockItem(world, "Flour", "KILOGRAM", "1.2500");
        UUID restaurantId = world.restaurantId();
        String token = world.ownerToken();

        post(BASE + "/receive", restaurantId).as(token)
                .body(Map.of("locationId", kitchen, "inventoryItemId", flour,
                        "quantity", new BigDecimal("5"))).expect(201);

        UUID countId = id(post(BASE + "/counts", restaurantId).as(token)
                .body(Map.of("locationId", kitchen, "notes", "Must not approve after deactivation")).expect(201));
        post(BASE + "/counts/{c}/start", restaurantId, countId).as(token).expect(200);
        put(BASE + "/counts/{c}/lines/{i}", restaurantId, countId, flour).as(token)
                .body(Map.of("countedQuantity", new BigDecimal("5"))).expect(200);
        post(BASE + "/counts/{c}/complete", restaurantId, countId).as(token)
                .body(Map.of()).expect(200);

        delete(BASE + "/locations/{l}", restaurantId, kitchen).as(token).expect(204);
        post(BASE + "/receive", restaurantId).as(token)
                .body(Map.of("locationId", kitchen, "inventoryItemId", flour,
                        "quantity", BigDecimal.ONE)).expect(409);
        post(BASE + "/waste", restaurantId).as(token)
                .body(Map.of("locationId", kitchen, "inventoryItemId", flour,
                        "quantity", BigDecimal.ONE, "reason", "Inactive location regression")).expect(409);
        post(BASE + "/transfer", restaurantId).as(token)
                .body(Map.of("fromLocationId", kitchen, "toLocationId", bar,
                        "inventoryItemId", flour, "quantity", BigDecimal.ONE)).expect(409);
        post(BASE + "/counts/{c}/approve", restaurantId, countId).as(token).expect(409);

        assertThat(onHand(world, kitchen, flour)).isEqualByComparingTo("5");
        assertThat(get(BASE + "/movements", restaurantId).as(token).param("itemId", flour).expect(200))
                .hasSize(1);
        assertThat(get(BASE + "/counts/{c}", restaurantId, countId).as(token).expect(200)
                .get("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("sale stock sources are branch-scoped, replaceable and protected from cross-restaurant access")
    void saleSources() throws Exception {
        World world = newWorld("sale-sources");
        World other = newWorld("sale-sources-other");
        UUID restaurantId = world.restaurantId();
        UUID flour = stockItem(world, "Flour", "KILOGRAM", "1.2500");
        UUID kitchen = location(world, "Kitchen", "KITCHEN");
        UUID bar = location(world, "Bar", "BAR");

        JsonNode created = put(SALE_SOURCES + "/{branchId}/{itemId}", restaurantId, world.branchId(), flour)
                .as(world.ownerToken()).body(Map.of("locationId", kitchen)).expect(200);
        UUID sourceId = id(created);
        assertThat(created.get("branchId").asText()).isEqualTo(world.branchId().toString());
        assertThat(created.get("locationId").asText()).isEqualTo(kitchen.toString());
        assertThat(delete(BASE + "/items/{i}", restaurantId, flour).as(world.ownerToken()).send().status()).isEqualTo(409);
        assertThat(delete(BASE + "/locations/{l}", restaurantId, kitchen).as(world.ownerToken()).send().status()).isEqualTo(409);

        JsonNode replaced = put(SALE_SOURCES + "/{branchId}/{itemId}", restaurantId, world.branchId(), flour)
                .as(world.ownerToken()).body(Map.of("locationId", bar)).expect(200);
        assertThat(id(replaced)).isEqualTo(sourceId);
        JsonNode list = get(SALE_SOURCES, restaurantId).as(world.ownerToken()).expect(200);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("locationId").asText()).isEqualTo(bar.toString());

        UUID otherFlour = stockItem(other, "Other flour", "KILOGRAM", "1.0000");
        assertThat(put(SALE_SOURCES + "/{branchId}/{itemId}", restaurantId, world.branchId(), otherFlour)
                .as(world.ownerToken()).body(Map.of("locationId", bar)).send().status()).isIn(400, 404);
        assertThat(get(SALE_SOURCES, restaurantId).as(other.ownerToken()).send().status()).isIn(403, 404);

        assertThat(put(SALE_SOURCES + "/{branchId}/{itemId}", restaurantId, UUID.randomUUID(), flour)
                .as(world.ownerToken()).body(Map.of("locationId", kitchen)).send().status()).isEqualTo(404);
        delete(SALE_SOURCES + "/{branchId}/{itemId}", restaurantId, world.branchId(), flour)
                .as(world.ownerToken()).expect(204);
        assertThat(get(SALE_SOURCES, restaurantId).as(world.ownerToken()).expect(200).size()).isZero();
    }

    @Test
    @DisplayName("stock under the reorder point shows as low, and items are found by barcode")
    void lowStockAndBarcodes() throws Exception {
        World world = newWorld("low-stock");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        UUID bar = location(world, "Bar", "BAR");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Lemons");
        body.put("itemType", "INGREDIENT");
        body.put("baseUnit", "EACH");
        body.put("costPerUnit", new BigDecimal("0.3000"));
        body.put("reorderPoint", new BigDecimal("10"));
        body.put("parLevel", new BigDecimal("40"));
        body.put("barcode", "8001234567890");
        UUID lemons = id(post(BASE + "/items", r).as(t).body(body).expect(201));
        UUID limes = stockItem(world, "Limes", "EACH", "0.4000");

        post(BASE + "/receive", r).as(t).body(Map.of("locationId", bar, "inventoryItemId", lemons, "quantity", new BigDecimal("3"))).expect(201);
        post(BASE + "/receive", r).as(t).body(Map.of("locationId", bar, "inventoryItemId", limes, "quantity", new BigDecimal("50"))).expect(201);
        JsonNode low = get(BASE + "/levels/low-stock", r).as(t).expect(200);
        assertThat(low.toString()).contains(lemons.toString()).doesNotContain(limes.toString());
        JsonNode level = get(BASE + "/levels/{l}/{i}", r, bar, lemons).as(t).expect(200);
        assertThat(level.get("lowStock").asBoolean()).isTrue();
        assertThat(money(level, "reorderQuantity")).isEqualByComparingTo("10");
        assertThat(money(level, "parQuantity")).isEqualByComparingTo("40");

        // Items switched off don't nag.
        UUID mint = stockItem(world, "Mint", "EACH", "0.1000");
        post(BASE + "/receive", r).as(t).body(Map.of("locationId", bar, "inventoryItemId", mint, "quantity", new BigDecimal("1"))).expect(201);
        assertThat(get(BASE + "/levels/low-stock", r).as(t).expect(200).toString()).contains(mint.toString());
        delete(BASE + "/items/{i}", r, mint).as(t).send();
        assertThat(get(BASE + "/levels/low-stock", r).as(t).expect(200).toString()).doesNotContain(mint.toString());

        post(BASE + "/receive", r).as(t).body(Map.of("locationId", bar, "inventoryItemId", lemons, "quantity", new BigDecimal("30"))).expect(201);
        assertThat(get(BASE + "/levels/low-stock", r).as(t).expect(200).toString()).doesNotContain(lemons.toString());

        assertThat(get(BASE + "/items/by-barcode/{b}", r, "8001234567890").as(t).expect(200).get("name").asText()).isEqualTo("Lemons");
        get(BASE + "/items/by-barcode/{b}", r, "0000000000000").as(t).expect(404);
        assertThat(get(BASE + "/items/by-barcode/{b}", newWorld("low-stock-other").restaurantId(), "8001234567890")
                .as(t).send().status()).isIn(403, 404);
    }

    @Test
    @DisplayName("a stock count records the difference and, once approved, corrects the level")
    void stockCount() throws Exception {
        World world = newWorld("count");
        UUID store = location(world, "Dry store", "DRY_STORAGE");
        UUID flour = stockItem(world, "Flour", "KILOGRAM", "0.9000");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        post(BASE + "/receive", r).as(t).body(Map.of("locationId", store, "inventoryItemId", flour, "quantity", new BigDecimal("25"))).expect(201);

        JsonNode count = post(BASE + "/counts", r).as(t).body(Map.of("locationId", store, "notes", "Monthly count")).expect(201);
        UUID countId = id(count);
        post(BASE + "/counts/{c}/start", r, countId).as(t).expect(200);
        put(BASE + "/counts/{c}/lines/{i}", r, countId, flour).as(t).body(Map.of("countedQuantity", new BigDecimal("23.500"))).expect(200);
        put(BASE + "/counts/{c}/lines/{i}", r, countId, flour).as(t).body(Map.of("countedQuantity", new BigDecimal("-1"))).expect(400);
        post(BASE + "/counts/{c}/complete", r, countId).as(t).body(Map.of()).send();
        Response approved = post(BASE + "/counts/{c}/approve", r, countId).as(t).send();
        assertThat(approved.status()).as(approved.text()).isBetween(200, 299);
        assertThat(onHand(world, store, flour)).isEqualByComparingTo("23.500");
        // A finished count can't change any more.
        assertThat(put(BASE + "/counts/{c}/lines/{i}", r, countId, flour).as(t).body(Map.of("countedQuantity", BigDecimal.ONE)).send().status())
                .isBetween(400, 409);
    }

    @Test
    @DisplayName("generated count numbers stay unique under concurrent creation and duplicate custom numbers conflict")
    void countNumbersAreUniqueUnderConcurrency() throws Exception {
        World world = newWorld("count-number-race");
        UUID store = location(world, "Dry store", "DRY_STORAGE");
        int callers = 6;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<JsonNode>> results = new java.util.ArrayList<>();
            for (int index = 0; index < callers; index++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Inventory count number race start timed out");
                    }
                    return post(BASE + "/counts", world.restaurantId()).as(world.ownerToken())
                            .body(Map.of("locationId", store)).expect(201);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> countNumbers = new java.util.ArrayList<>();
            for (Future<JsonNode> result : results) {
                countNumbers.add(result.get(60, TimeUnit.SECONDS).get("countNumber").asText());
            }
            assertThat(countNumbers).hasSize(callers).doesNotHaveDuplicates()
                    .allMatch(number -> number.matches("IC-[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}"));
        } finally {
            start.countDown();
            pool.shutdownNow();
        }

        Map<String, Object> custom = Map.of("locationId", store, "countNumber", "WEEKLY-COUNT-001");
        post(BASE + "/counts", world.restaurantId()).as(world.ownerToken()).body(custom).expect(201);
        post(BASE + "/counts", world.restaurantId()).as(world.ownerToken()).body(custom).expect(409);
    }

    @Test
    @DisplayName("stock count history is filtered and paged inside the restaurant")
    void stockCountListPagingAndFilters() throws Exception {
        World world = newWorld("count-list-pages");
        UUID store = location(world, "Dry store", "DRY_STORAGE");
        String token = world.ownerToken();
        UUID first = id(post(BASE + "/counts", world.restaurantId()).as(token)
                .body(Map.of("locationId", store, "notes", "first")).expect(201));
        UUID second = id(post(BASE + "/counts", world.restaurantId()).as(token)
                .body(Map.of("locationId", store, "notes", "second")).expect(201));
        UUID third = id(post(BASE + "/counts", world.restaurantId()).as(token)
                .body(Map.of("locationId", store, "notes", "third")).expect(201));
        post(BASE + "/counts/{c}/start", world.restaurantId(), second).as(token).expect(200);

        JsonNode firstPage = get(BASE + "/counts", world.restaurantId()).as(token).param("size", 2).expect(200);
        assertThat(firstPage.get("page").asInt()).isZero();
        assertThat(firstPage.get("size").asInt()).isEqualTo(2);
        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(3);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.get("hasNext").asBoolean()).isTrue();
        assertThat(firstPage.get("items")).hasSize(2);

        JsonNode secondPage = get(BASE + "/counts", world.restaurantId()).as(token)
                .param("page", 1).param("size", 2).expect(200);
        assertThat(secondPage.get("items")).hasSize(1);
        assertThat(secondPage.get("hasNext").asBoolean()).isFalse();
        assertThat(List.of(firstPage.get("items").get(0).get("id").asText(),
                firstPage.get("items").get(1).get("id").asText(), secondPage.get("items").get(0).get("id").asText()))
                .containsExactlyInAnyOrder(first.toString(), second.toString(), third.toString());
        assertThat(firstPage.get("items").toString()).doesNotContain(secondPage.get("items").get(0).get("id").asText());

        JsonNode filtered = get(BASE + "/counts", world.restaurantId()).as(token)
                .param("status", "IN_PROGRESS").expect(200);
        assertThat(filtered.get("totalElements").asLong()).isEqualTo(1);
        assertThat(filtered.get("items").get(0).get("id").asText()).isEqualTo(second.toString());

        JsonNode capped = get(BASE + "/counts", world.restaurantId()).as(token).param("size", 500).expect(200);
        assertThat(capped.get("size").asInt()).isEqualTo(200);
        get(BASE + "/counts", world.restaurantId()).as(token).param("page", -1).expect(400);
        get(BASE + "/counts", world.restaurantId()).as(token).param("size", 0).expect(400);
        assertThat(get(BASE + "/counts", newWorld("count-list-other").restaurantId()).as(token).send().status())
                .isIn(403, 404);
    }

    @Test
    @DisplayName("simultaneous approvals apply a physical count adjustment only once")
    void simultaneousCountApprovalsAreIdempotent() throws Exception {
        World world = newWorld("count-approval-race");
        UUID store = location(world, "Dry store", "DRY_STORAGE");
        UUID flour = stockItem(world, "Flour", "KILOGRAM", "0.9000");
        post(BASE + "/receive", world.restaurantId()).as(world.ownerToken())
                .body(Map.of("locationId", store, "inventoryItemId", flour, "quantity", new BigDecimal("25")))
                .expect(201);

        UUID countId = id(post(BASE + "/counts", world.restaurantId()).as(world.ownerToken())
                .body(Map.of("locationId", store, "notes", "Concurrent approval test")).expect(201));
        post(BASE + "/counts/{c}/start", world.restaurantId(), countId).as(world.ownerToken()).expect(200);
        put(BASE + "/counts/{c}/lines/{i}", world.restaurantId(), countId, flour).as(world.ownerToken())
                .body(Map.of("countedQuantity", new BigDecimal("23.500"))).expect(200);
        post(BASE + "/counts/{c}/complete", world.restaurantId(), countId).as(world.ownerToken())
                .body(Map.of()).expect(200);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch approveTogether = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var approvals = new java.util.ArrayList<Future<Integer>>();
            for (int index = 0; index < 2; index++) {
                approvals.add(pool.submit(() -> {
                    ready.countDown();
                    if (!approveTogether.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent count approval start timed out");
                    }
                    return post(BASE + "/counts/{c}/approve", world.restaurantId(), countId)
                            .as(world.ownerToken()).send().status();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            approveTogether.countDown();
            assertThat(approvals.stream().map(request -> {
                try {
                    return request.get(30, TimeUnit.SECONDS);
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            }).toList()).containsExactlyInAnyOrder(200, 409);
        } finally {
            approveTogether.countDown();
            pool.shutdownNow();
        }

        assertThat(onHand(world, store, flour)).isEqualByComparingTo("23.500");
        JsonNode adjustments = get(BASE + "/movements", world.restaurantId()).as(world.ownerToken())
                .param("type", "COUNT_ADJUSTMENT").expect(200);
        assertThat(adjustments).hasSize(1);
    }

    @Test
    @DisplayName("items: unusual names are kept, overlong ones refused, and other restaurants and staff without rights are kept out")
    void itemsAndAccess() throws Exception {
        World world = newWorld("items");
        World other = newWorld("items-other");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        String name = "Peperoncino 🌶️ «extra» ' OR 1=1 --";
        UUID chili = stockItem(world, name, "GRAM", "0.0100");
        JsonNode read = get(BASE + "/items/{i}", r, chili).as(t).expect(200);
        assertThat(read.get("name").asText()).isEqualTo(name);
        assertThat(read.get("supplierName").asText()).isEqualTo("Green Farm");

        Map<String, Object> tooLong = new LinkedHashMap<>();
        tooLong.put("name", "N".repeat(151));
        tooLong.put("itemType", "INGREDIENT");
        tooLong.put("baseUnit", "GRAM");
        tooLong.put("costPerUnit", BigDecimal.ONE);
        assertThat(post(BASE + "/items", r).as(t).body(tooLong).send().status()).isEqualTo(400);
        tooLong.put("name", "Ok");
        tooLong.put("baseUnit", "BUCKET");
        post(BASE + "/items", r).as(t).body(tooLong).expect(400);
        tooLong.put("baseUnit", "GRAM");
        tooLong.put("costPerUnit", new BigDecimal("-1"));
        post(BASE + "/items", r).as(t).body(tooLong).expect(400);
        tooLong.put("costPerUnit", BigDecimal.ONE);
        tooLong.put("description", "d".repeat(2001));
        post(BASE + "/items", r).as(t).body(tooLong).expect(400);
        post(BASE + "/locations", r).as(t).body(Map.of("name", "Shelf", "locationType", "BAR", "notes", "n".repeat(2001))).expect(400);

        JsonNode search = get(BASE + "/items/search", r).as(t).param("keyword", "Peperoncino").send().json();
        assertThat(search.toString()).contains("Peperoncino");

        User waiter = staff(world, "waiter", "WAITER");
        get(BASE + "/items", r).as(token(waiter)).expect(403);
        assertThat(get(BASE + "/items/{i}", r, chili).as(other.ownerToken()).send().status()).isIn(403, 404);
        assertThat(get(BASE + "/items/{i}", other.restaurantId(), chili).as(other.ownerToken()).send().status()).isIn(400, 404);
        assertThat(delete(BASE + "/items/{i}", r, chili).as(other.ownerToken()).send().status()).isIn(403, 404);
        get(BASE + "/items/{i}", r, chili).as(t).expect(200);
    }
}
