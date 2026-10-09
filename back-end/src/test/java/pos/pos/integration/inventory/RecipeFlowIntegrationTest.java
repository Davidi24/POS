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

@DisplayName("Recipes end to end")
class RecipeFlowIntegrationTest extends AbstractPosApiIntegrationTest {

    private static final String RECIPES = "/restaurants/{r}/recipes";
    private static final String KDS = "/restaurants/{r}/branches/{b}/kds";

    private record KdsSaleFixture(World world, UUID stockItemId, UUID locationId,
                                  List<UUID> orderIds, List<UUID> lineIds,
                                  List<UUID> ticketIds, List<UUID> ticketItemIds) { }

    private KdsSaleFixture kdsSaleFixture(String worldName, String startingStock, int dishCount) throws Exception {
        World world = newWorld(worldName);
        UUID restaurantId = world.restaurantId();
        String token = world.ownerToken();
        UUID stockItemId = stock(world, "Chicken", "4.0000");
        UUID locationId = id(post("/restaurants/{r}/inventory/locations", restaurantId).as(token).body(Map.of(
                "name", "Kitchen", "locationType", "KITCHEN", "branchId", world.branchId())).expect(201));
        put("/restaurants/{r}/inventory/sale-sources/{branchId}/{itemId}", restaurantId, world.branchId(), stockItemId)
                .as(token).body(Map.of("locationId", locationId)).expect(200);
        post("/restaurants/{r}/inventory/receive", restaurantId).as(token).body(Map.of(
                "locationId", locationId, "inventoryItemId", stockItemId,
                "quantity", new BigDecimal(startingStock))).expect(201);

        Map<String, UUID> menu = menu(world, "KDS stock menu");
        List<UUID> dishIds = new java.util.ArrayList<>();
        List<UUID> orderIds = new java.util.ArrayList<>();
        List<UUID> lineIds = new java.util.ArrayList<>();
        for (int index = 0; index < dishCount; index++) {
            UUID dish = item(world, menu, "Chicken " + index, "8.00");
            dishIds.add(dish);
            Map<String, Object> recipeRequest = recipe("Chicken recipe " + index, "FINISHED_DISH", dish);
            recipeRequest.put("status", "ACTIVE");
            UUID recipeId = id(post(RECIPES, restaurantId).as(token).body(recipeRequest).expect(201));
            put(RECIPES + "/{id}/components", restaurantId, recipeId)
                    .as(token).body(ingredient(stockItemId, "0.100", null)).expect(200);

            JsonNode createdOrder = order(world, token, Map.of(dish, 1));
            UUID orderId = id(createdOrder);
            orderIds.add(orderId);
            lineIds.add(UUID.fromString(createdOrder.get("lineItems").get(0).get("id").asText()));
        }

        List<Map<String, Object>> routings = dishIds.stream()
                .map(dish -> Map.<String, Object>of("menuItemId", dish)).toList();
        JsonNode station = post(KDS + "/stations", restaurantId, world.branchId()).as(token).body(Map.of(
                "name", "Grill", "stationType", "GRILL", "routings", routings)).expect(201);
        for (UUID orderId : orderIds) {
            post("/restaurants/{r}/orders/{o}/send-to-kitchen", restaurantId, orderId).as(token).expect(200);
        }

        JsonNode board = get(KDS + "/board", restaurantId, world.branchId()).as(token)
                .param("stationId", id(station)).expect(200);
        List<UUID> ticketIds = new java.util.ArrayList<>();
        List<UUID> ticketItemIds = new java.util.ArrayList<>();
        for (UUID orderId : orderIds) {
            JsonNode ticket = null;
            for (JsonNode stationBoard : board) {
                for (JsonNode candidate : stationBoard.path("tickets")) {
                    if (orderId.toString().equals(candidate.path("orderId").asText())) {
                        ticket = candidate;
                        break;
                    }
                }
            }
            assertThat(ticket).as("KDS ticket for order %s", orderId).isNotNull();
            ticketIds.add(id(ticket));
            ticketItemIds.add(id(ticket.path("items").get(0)));
        }
        return new KdsSaleFixture(world, stockItemId, locationId, orderIds, lineIds, ticketIds, ticketItemIds);
    }

    private UUID stock(World world, String name, String cost) throws Exception {
        return id(post("/restaurants/{r}/inventory/items", world.restaurantId()).as(world.ownerToken()).body(Map.of(
                "name", name, "itemType", "INGREDIENT", "baseUnit", "KILOGRAM", "costPerUnit", new BigDecimal(cost))).expect(201));
    }

    private Map<String, Object> recipe(String name, String type, UUID menuItemId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("recipeType", type);
        if (menuItemId != null) {
            body.put("menuItemId", menuItemId);
        }
        return body;
    }

    private Map<String, Object> ingredient(UUID itemId, String quantity, String loss) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("componentType", "INVENTORY_ITEM");
        body.put("inventoryItemId", itemId);
        body.put("quantity", new BigDecimal(quantity));
        body.put("unit", "KILOGRAM");
        if (loss != null) {
            body.put("yieldLossPercent", new BigDecimal(loss));
        }
        return body;
    }

    private Map<String, Object> subRecipe(UUID recipeId, String quantity) {
        return Map.of("componentType", "SUB_RECIPE", "childRecipeId", recipeId, "quantity", new BigDecimal(quantity), "unit", "PORTION");
    }

    @Test
    @DisplayName("a dish made of stock and a prep batch is priced exactly, including waste")
    void costing() throws Exception {
        World world = newWorld("recipes");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        UUID flour = stock(world, "Flour", "0.9000");
        UUID tomatoes = stock(world, "Tomatoes", "2.5000");
        Map<String, UUID> menu = menu(world, "Pizza menu");
        UUID margherita = item(world, menu, "Margherita", "9.00");

        UUID dough = id(post(RECIPES, r).as(t).body(recipe("Dough", "PREP_BATCH", null)).expect(201));
        put(RECIPES + "/{id}/components", r, dough).as(t).body(ingredient(flour, "0.250", null)).expect(200);
        UUID pizza = id(post(RECIPES, r).as(t).body(recipe("Margherita 🍕", "FINISHED_DISH", margherita)).expect(201));
        put(RECIPES + "/{id}/components", r, pizza).as(t).body(ingredient(tomatoes, "0.120", "20")).expect(200);
        JsonNode withDough = put(RECIPES + "/{id}/components", r, pizza).as(t).body(subRecipe(dough, "1")).expect(200);
        assertThat(withDough.get("components")).hasSize(2);

        // 0.120 kg with 20% waste needs 0.150 kg (0.375) plus the dough's 0.250 kg of flour (0.225).
        JsonNode priced = post(RECIPES + "/{id}/recalculate-cost", r, pizza).as(t).expect(200);
        assertThat(money(priced, "theoreticalCost")).isEqualByComparingTo("0.60");

        // Changing an ingredient that is already there updates it instead of adding another.
        JsonNode changed = put(RECIPES + "/{id}/components", r, pizza).as(t).body(ingredient(tomatoes, "0.200", "0")).expect(200);
        assertThat(changed.get("components")).hasSize(2);
        assertThat(money(post(RECIPES + "/{id}/recalculate-cost", r, pizza).as(t).expect(200), "theoreticalCost")).isEqualByComparingTo("0.73");

        UUID componentId = null;
        for (JsonNode component : changed.get("components")) {
            if ("SUB_RECIPE".equals(component.get("componentType").asText())) {
                componentId = id(component);
            }
        }
        JsonNode withoutDough = delete(RECIPES + "/{id}/components/{c}", r, pizza, componentId).as(t).expect(200);
        assertThat(withoutDough.get("components")).hasSize(1);

        JsonNode archived = post(RECIPES + "/{id}/archive", r, pizza).as(t).expect(200);
        assertThat(archived.get("status").asText()).isEqualTo("ARCHIVED");
        assertThat(get(RECIPES, r).as(t).param("status", "ARCHIVED").expect(200).toString()).contains(pizza.toString());
    }

    @Test
    @DisplayName("recipe costing converts compatible units and refuses incompatible units")
    void convertsIngredientUnits() throws Exception {
        World world = newWorld("recipe-units");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        UUID flour = stock(world, "Flour", "2.5000");
        Map<String, UUID> menu = menu(world, "Unit menu");
        UUID dish = item(world, menu, "Bread", "5.00");
        UUID recipeId = id(post(RECIPES, r).as(t).body(recipe("Bread recipe", "FINISHED_DISH", dish)).expect(201));

        Map<String, Object> grams = ingredient(flour, "250", null);
        grams.put("unit", "GRAM");
        JsonNode withComponent = put(RECIPES + "/{id}/components", r, recipeId).as(t).body(grams).expect(200);
        assertThat(withComponent.get("components").get(0).get("quantity").decimalValue()).isEqualByComparingTo("250");
        assertThat(money(post(RECIPES + "/{id}/recalculate-cost", r, recipeId).as(t).expect(200), "theoreticalCost"))
                .isEqualByComparingTo("0.63");

        Map<String, Object> incompatible = ingredient(flour, "250", null);
        incompatible.put("unit", "LITER");
        assertThat(put(RECIPES + "/{id}/components", r, recipeId).as(t).body(incompatible).send().status()).isEqualTo(400);
        assertThat(get(RECIPES + "/{id}", r, recipeId).as(t).expect(200).get("components")).hasSize(1);
    }

    @Test
    @DisplayName("sub-recipe costs are prorated by their declared yield")
    void proratesSubRecipeBatchYield() throws Exception {
        World world = newWorld("recipe-yield");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        UUID flour = stock(world, "Flour", "4.0000");
        Map<String, Object> batchRequest = recipe("Sauce batch", "PREP_BATCH", null);
        batchRequest.put("yieldQuantity", new BigDecimal("4"));
        batchRequest.put("yieldUnit", "PORTION");
        UUID batch = id(post(RECIPES, r).as(t).body(batchRequest).expect(201));
        put(RECIPES + "/{id}/components", r, batch).as(t).body(ingredient(flour, "0.200", null)).expect(200);

        Map<String, UUID> menu = menu(world, "Yield menu");
        UUID dish = item(world, menu, "Soup", "8.00");
        UUID recipeId = id(post(RECIPES, r).as(t).body(recipe("Soup recipe", "FINISHED_DISH", dish)).expect(201));
        put(RECIPES + "/{id}/components", r, recipeId).as(t).body(subRecipe(batch, "1")).expect(200);

        // The entire four-portion batch costs 0.80; one portion of it costs 0.20.
        assertThat(money(post(RECIPES + "/{id}/recalculate-cost", r, recipeId).as(t).expect(200), "theoreticalCost"))
                .isEqualByComparingTo("0.20");

        UUID wrongUnits = id(post(RECIPES, r).as(t).body(recipe("Wrong units", "PREP_BATCH", null)).expect(201));
        Map<String, Object> incompatible = new LinkedHashMap<>(subRecipe(batch, "1"));
        incompatible.put("unit", "KILOGRAM");
        assertThat(put(RECIPES + "/{id}/components", r, wrongUnits).as(t).body(incompatible).send().status()).isEqualTo(400);
    }

    @Test
    @DisplayName("fulfilling a dish consumes its recipe stock once, including prorated prep batches")
    void saleConsumesRecipeStockExactlyOnce() throws Exception {
        World world = newWorld("recipe-sale-consumption");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        UUID flour = stock(world, "Flour", "4.0000");
        UUID kitchen = id(post("/restaurants/{r}/inventory/locations", r).as(t).body(Map.of(
                "name", "Kitchen", "locationType", "KITCHEN", "branchId", world.branchId())).expect(201));
        put("/restaurants/{r}/inventory/sale-sources/{branchId}/{itemId}", r, world.branchId(), flour)
                .as(t).body(Map.of("locationId", kitchen)).expect(200);
        post("/restaurants/{r}/inventory/receive", r).as(t).body(Map.of(
                "locationId", kitchen, "inventoryItemId", flour, "quantity", new BigDecimal("1.000"))).expect(201);

        Map<String, UUID> menu = menu(world, "Stock menu");
        UUID soup = item(world, menu, "Soup", "8.00");
        UUID water = item(world, menu, "Water", "2.00", false);
        Map<String, Object> batchRequest = recipe("Soup prep", "PREP_BATCH", null);
        batchRequest.put("status", "ACTIVE");
        batchRequest.put("yieldQuantity", new BigDecimal("4"));
        batchRequest.put("yieldUnit", "PORTION");
        UUID batch = id(post(RECIPES, r).as(t).body(batchRequest).expect(201));
        put(RECIPES + "/{id}/components", r, batch).as(t).body(ingredient(flour, "0.200", null)).expect(200);

        Map<String, Object> dishRequest = recipe("Soup", "FINISHED_DISH", soup);
        dishRequest.put("status", "ACTIVE");
        UUID dishRecipe = id(post(RECIPES, r).as(t).body(dishRequest).expect(201));
        put(RECIPES + "/{id}/components", r, dishRecipe).as(t).body(ingredient(flour, "0.100", "20")).expect(200);
        put(RECIPES + "/{id}/components", r, dishRecipe).as(t).body(subRecipe(batch, "1")).expect(200);

        JsonNode order = order(world, t, Map.of(soup, 2, water, 1));
        UUID orderId = id(order);
        UUID lineId = null;
        for (JsonNode line : order.get("lineItems")) {
            if (soup.equals(UUID.fromString(line.get("menuItemId").asText()))) {
                lineId = UUID.fromString(line.get("id").asText());
                break;
            }
        }
        assertThat(lineId).isNotNull();
        post("/restaurants/{r}/orders/{o}/fulfill", r, orderId).as(t).body(Map.of()).expect(200);
        assertThat(money(get("/restaurants/{r}/inventory/levels/{l}/{i}", r, kitchen, flour).as(t).expect(200), "onHandQuantity"))
                .isEqualByComparingTo("0.650");

        // A retry and a repeated fulfilled status do not create another consumption movement.
        post("/restaurants/{r}/orders/{o}/fulfill", r, orderId).as(t).body(Map.of()).expect(200);
        post("/restaurants/{r}/orders/{o}/items/{lineId}/fulfill", r, orderId, lineId).as(t).body(Map.of()).expect(200);
        assertThat(post("/restaurants/{r}/orders/{o}/split", r, orderId).as(t)
                .body(Map.of("lineItemIds", List.of(lineId))).send().status()).isEqualTo(409);
        UUID otherOrder = id(order(world, t, Map.of(water, 1)));
        assertThat(post("/restaurants/{r}/orders/{o}/merge", r, orderId).as(t)
                .body(Map.of("sourceOrderId", otherOrder)).send().status()).isEqualTo(409);
        JsonNode movements = get("/restaurants/{r}/inventory/movements", r).as(t)
                .param("orderLineItemId", lineId.toString()).param("type", "SALE_CONSUMPTION").expect(200);
        assertThat(movements).hasSize(1);
        assertThat(money(movements.get(0), "quantityDelta")).isEqualByComparingTo("-0.350");
    }

    @Test
    @DisplayName("KDS ticket completion, item completion, and waiter pickup each consume sale stock exactly once")
    void kdsFulfillmentConsumesSaleStockOnceAcrossAllCompletionActions() throws Exception {
        KdsSaleFixture fixture = kdsSaleFixture("kds-recipe-sale", "1.000", 3);
        World world = fixture.world();
        String token = world.ownerToken();
        UUID r = world.restaurantId();
        UUID b = world.branchId();

        // Completing a whole ticket consumes its linked order line.
        post(KDS + "/tickets/{t}/complete", r, b, fixture.ticketIds().get(0)).as(token).body(Map.of()).expect(200);
        // Completing one ticket item follows the same inventory path; a retry is idempotent.
        post(KDS + "/tickets/{t}/items/{i}/complete", r, b, fixture.ticketIds().get(1), fixture.ticketItemIds().get(1))
                .as(token).body(Map.of()).expect(200);
        post(KDS + "/tickets/{t}/items/{i}/complete", r, b, fixture.ticketIds().get(1), fixture.ticketItemIds().get(1))
                .as(token).body(Map.of()).expect(200);
        // Waiter pickup only fulfills READY lines and must consume those lines too.
        post(KDS + "/tickets/{t}/ready", r, b, fixture.ticketIds().get(2)).as(token).body(Map.of()).expect(200);
        post(KDS + "/tickets/{t}/picked-up", r, b, fixture.ticketIds().get(2)).as(token).body(Map.of()).expect(200);

        assertThat(money(get("/restaurants/{r}/inventory/levels/{l}/{i}", r, fixture.locationId(), fixture.stockItemId())
                .as(token).expect(200), "onHandQuantity")).isEqualByComparingTo("0.700");
        for (int index = 0; index < fixture.lineIds().size(); index++) {
            UUID lineId = fixture.lineIds().get(index);
            assertThat(get("/restaurants/{r}/inventory/movements", r).as(token)
                    .param("orderLineItemId", lineId.toString()).param("type", "SALE_CONSUMPTION").expect(200))
                    .as("sale movement for line %s", lineId).hasSize(1);
            assertThat(get("/restaurants/{r}/orders/{o}", r, fixture.orderIds().get(index)).as(token).expect(200)
                    .path("fulfillmentStatus").asText()).isEqualTo("FULFILLED");
        }
    }

    @Test
    @DisplayName("KDS fulfillment rolls back when recipe stock is insufficient")
    void kdsFulfillmentDoesNotCompleteOrderWhenStockIsInsufficient() throws Exception {
        KdsSaleFixture fixture = kdsSaleFixture("kds-recipe-sale-short-stock", "0.050", 1);
        World world = fixture.world();
        String token = world.ownerToken();
        UUID r = world.restaurantId();
        UUID b = world.branchId();

        assertThat(post(KDS + "/tickets/{t}/complete", r, b, fixture.ticketIds().getFirst()).as(token)
                .body(Map.of()).send().status()).isEqualTo(409);
        JsonNode orderAfterFailure = get("/restaurants/{r}/orders/{o}", r, fixture.orderIds().getFirst())
                .as(token).expect(200);
        assertThat(orderAfterFailure.get("fulfillmentStatus").asText()).isNotEqualTo("FULFILLED");
        assertThat(orderAfterFailure.get("lineItems").get(0).get("status").asText()).isNotEqualTo("FULFILLED");
        assertThat(money(get("/restaurants/{r}/inventory/levels/{l}/{i}", r, fixture.locationId(), fixture.stockItemId())
                .as(token).expect(200), "onHandQuantity")).isEqualByComparingTo("0.050");
        assertThat(get("/restaurants/{r}/inventory/movements", r).as(token)
                .param("orderLineItemId", fixture.lineIds().getFirst().toString())
                .param("type", "SALE_CONSUMPTION").expect(200)).isEmpty();
    }

    @Test
    @DisplayName("simultaneous POS and KDS fulfillment consume the same line only once")
    void simultaneousPosAndKdsFulfillmentAreIdempotent() throws Exception {
        KdsSaleFixture fixture = kdsSaleFixture("kds-pos-fulfillment-race", "1.000", 1);
        World world = fixture.world();
        String token = world.ownerToken();
        UUID restaurantId = world.restaurantId();
        UUID branchId = world.branchId();
        UUID orderId = fixture.orderIds().getFirst();
        UUID ticketId = fixture.ticketIds().getFirst();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> posFulfillment = pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent POS fulfillment start timed out");
                }
                return post("/restaurants/{r}/orders/{o}/fulfill", restaurantId, orderId)
                        .as(token).body(Map.of()).send().status();
            });
            Future<Integer> kdsFulfillment = pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent KDS fulfillment start timed out");
                }
                return post(KDS + "/tickets/{t}/complete", restaurantId, branchId, ticketId)
                        .as(token).body(Map.of()).send().status();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(posFulfillment.get(30, TimeUnit.SECONDS), kdsFulfillment.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 200);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }

        assertThat(money(get("/restaurants/{r}/inventory/levels/{l}/{i}", restaurantId,
                fixture.locationId(), fixture.stockItemId()).as(token).expect(200), "onHandQuantity"))
                .isEqualByComparingTo("0.900");
        assertThat(get("/restaurants/{r}/inventory/movements", restaurantId).as(token)
                .param("orderLineItemId", fixture.lineIds().getFirst().toString())
                .param("type", "SALE_CONSUMPTION").expect(200)).hasSize(1);
    }

    @Test
    @DisplayName("refund and later order void do not put already-prepared ingredients back into stock")
    void refundAndVoidKeepConsumedIngredientsConsumed() throws Exception {
        KdsSaleFixture fixture = kdsSaleFixture("refund-keeps-sale-consumption", "1.000", 1);
        World world = fixture.world();
        String token = world.ownerToken();
        UUID restaurantId = world.restaurantId();
        UUID orderId = fixture.orderIds().getFirst();
        UUID lineId = fixture.lineIds().getFirst();

        post("/restaurants/{r}/orders/{o}/fulfill", restaurantId, orderId).as(token).body(Map.of()).expect(200);
        // An individual served line cannot be voided as if its ingredients were unused.
        assertThat(post("/restaurants/{r}/orders/{o}/items/{l}/void", restaurantId, orderId, lineId)
                .as(token).body(Map.of("reason", "Customer changed mind")).send().status()).isEqualTo(409);

        JsonNode paid = post("/restaurants/{r}/orders/{o}/payments", restaurantId, orderId).as(token).body(Map.of(
                "method", "CASH", "amount", new BigDecimal("8.00"), "tenderedAmount", new BigDecimal("8.00"))).expect(201);
        UUID paymentId = id(paid.get("payment"));
        post("/restaurants/{r}/orders/{o}/payments/{p}/refund", restaurantId, orderId, paymentId).as(token)
                .body(Map.of("amount", new BigDecimal("8.00"), "reason", "Refund after meal was served")).expect(200);

        orderRule(world, "reopen_closed_orders_enabled", true);
        post("/restaurants/{r}/orders/{o}/reopen", restaurantId, orderId).as(token).body(Map.of()).expect(200);
        post("/restaurants/{r}/orders/{o}/void", restaurantId, orderId).as(token)
                .body(Map.of("reason", "Refunded after fulfillment")).expect(200);

        assertThat(money(get("/restaurants/{r}/inventory/levels/{l}/{i}", restaurantId,
                fixture.locationId(), fixture.stockItemId()).as(token).expect(200), "onHandQuantity"))
                .isEqualByComparingTo("0.900");
        assertThat(get("/restaurants/{r}/inventory/movements", restaurantId).as(token)
                .param("orderLineItemId", lineId.toString()).param("type", "SALE_CONSUMPTION").expect(200))
                .hasSize(1);
        assertThat(get("/restaurants/{r}/orders/{o}", restaurantId, orderId).as(token).expect(200)
                .get("lineItems").get(0).get("status").asText()).isEqualTo("VOIDED");
    }

    @Test
    @DisplayName("a missing sale source fails fulfillment before consuming any stock")
    void missingSaleSourceDoesNotPartiallyConsumeStock() throws Exception {
        World world = newWorld("recipe-sale-missing-source");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        UUID flour = stock(world, "Flour", "4.0000");
        UUID tomatoes = stock(world, "Tomatoes", "2.0000");
        UUID kitchen = id(post("/restaurants/{r}/inventory/locations", r).as(t).body(Map.of(
                "name", "Kitchen", "locationType", "KITCHEN", "branchId", world.branchId())).expect(201));
        put("/restaurants/{r}/inventory/sale-sources/{branchId}/{itemId}", r, world.branchId(), flour)
                .as(t).body(Map.of("locationId", kitchen)).expect(200);
        post("/restaurants/{r}/inventory/receive", r).as(t).body(Map.of(
                "locationId", kitchen, "inventoryItemId", flour, "quantity", new BigDecimal("0.500"))).expect(201);
        post("/restaurants/{r}/inventory/receive", r).as(t).body(Map.of(
                "locationId", kitchen, "inventoryItemId", tomatoes, "quantity", new BigDecimal("0.500"))).expect(201);

        Map<String, UUID> menu = menu(world, "Missing source menu");
        UUID dish = item(world, menu, "Pasta", "12.00");
        Map<String, Object> dishRecipe = recipe("Pasta recipe", "FINISHED_DISH", dish);
        dishRecipe.put("status", "ACTIVE");
        UUID recipeId = id(post(RECIPES, r).as(t).body(dishRecipe).expect(201));
        put(RECIPES + "/{id}/components", r, recipeId).as(t).body(ingredient(flour, "0.100", null)).expect(200);
        put(RECIPES + "/{id}/components", r, recipeId).as(t).body(ingredient(tomatoes, "0.100", null)).expect(200);

        JsonNode createdOrder = order(world, t, Map.of(dish, 1));
        UUID orderId = id(createdOrder);
        UUID lineId = UUID.fromString(createdOrder.get("lineItems").get(0).get("id").asText());
        assertThat(post("/restaurants/{r}/orders/{o}/fulfill", r, orderId).as(t).body(Map.of()).send().status())
                .isEqualTo(409);

        assertThat(money(get("/restaurants/{r}/inventory/levels/{l}/{i}", r, kitchen, flour).as(t).expect(200), "onHandQuantity"))
                .isEqualByComparingTo("0.500");
        assertThat(get("/restaurants/{r}/inventory/movements", r).as(t)
                .param("orderLineItemId", lineId.toString()).param("type", "SALE_CONSUMPTION").expect(200))
                .isEmpty();
        assertThat(get("/restaurants/{r}/orders/{o}", r, orderId).as(t).expect(200)
                .get("lineItems").get(0).get("status").asText()).isNotEqualTo("FULFILLED");
    }

    @Test
    @DisplayName("insufficient stock rolls back all recipe deductions and fulfillment")
    void insufficientSaleStockRollsBackAtomically() throws Exception {
        World world = newWorld("recipe-sale-insufficient-stock");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        UUID flour = stock(world, "Flour", "4.0000");
        UUID tomatoes = stock(world, "Tomatoes", "2.0000");
        UUID kitchen = id(post("/restaurants/{r}/inventory/locations", r).as(t).body(Map.of(
                "name", "Kitchen", "locationType", "KITCHEN", "branchId", world.branchId())).expect(201));
        for (UUID itemId : List.of(flour, tomatoes)) {
            put("/restaurants/{r}/inventory/sale-sources/{branchId}/{itemId}", r, world.branchId(), itemId)
                    .as(t).body(Map.of("locationId", kitchen)).expect(200);
        }
        // Flour is sufficient, while tomatoes are configured but out of stock. This exercises rollback
        // after at least one deduction may have been attempted, independent of component ordering.
        post("/restaurants/{r}/inventory/receive", r).as(t).body(Map.of(
                "locationId", kitchen, "inventoryItemId", flour, "quantity", new BigDecimal("0.500"))).expect(201);

        Map<String, UUID> menu = menu(world, "Short stock menu");
        UUID dish = item(world, menu, "Pasta", "12.00");
        Map<String, Object> dishRecipe = recipe("Pasta recipe", "FINISHED_DISH", dish);
        dishRecipe.put("status", "ACTIVE");
        UUID recipeId = id(post(RECIPES, r).as(t).body(dishRecipe).expect(201));
        put(RECIPES + "/{id}/components", r, recipeId).as(t).body(ingredient(flour, "0.100", null)).expect(200);
        put(RECIPES + "/{id}/components", r, recipeId).as(t).body(ingredient(tomatoes, "0.100", null)).expect(200);

        JsonNode createdOrder = order(world, t, Map.of(dish, 1));
        UUID orderId = id(createdOrder);
        UUID lineId = UUID.fromString(createdOrder.get("lineItems").get(0).get("id").asText());
        assertThat(post("/restaurants/{r}/orders/{o}/fulfill", r, orderId).as(t).body(Map.of()).send().status())
                .isEqualTo(409);

        assertThat(money(get("/restaurants/{r}/inventory/levels/{l}/{i}", r, kitchen, flour).as(t).expect(200), "onHandQuantity"))
                .isEqualByComparingTo("0.500");
        assertThat(get("/restaurants/{r}/inventory/movements", r).as(t)
                .param("orderLineItemId", lineId.toString()).param("type", "SALE_CONSUMPTION").expect(200))
                .isEmpty();
        assertThat(get("/restaurants/{r}/orders/{o}", r, orderId).as(t).expect(200)
                .get("lineItems").get(0).get("status").asText()).isNotEqualTo("FULFILLED");
    }

    @Test
    @DisplayName("selected modifier recipes consume the order-time recipe snapshot using option and item quantities")
    void modifierRecipesConsumeFromTheOrderSnapshot() throws Exception {
        World world = newWorld("recipe-modifier-sale");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        UUID cheese = stock(world, "Cheese", "8.0000");
        UUID kitchen = id(post("/restaurants/{r}/inventory/locations", r).as(t).body(Map.of(
                "name", "Kitchen", "locationType", "KITCHEN", "branchId", world.branchId())).expect(201));
        put("/restaurants/{r}/inventory/sale-sources/{branchId}/{itemId}", r, world.branchId(), cheese)
                .as(t).body(Map.of("locationId", kitchen)).expect(200);
        post("/restaurants/{r}/inventory/receive", r).as(t).body(Map.of(
                "locationId", kitchen, "inventoryItemId", cheese, "quantity", new BigDecimal("0.500"))).expect(201);

        Map<String, UUID> menu = menu(world, "Modifier menu");
        UUID pizza = item(world, menu, "Pizza", "12.00", false);
        Map<String, Object> firstRecipeRequest = recipe("Cheese topping", "PREP_BATCH", null);
        firstRecipeRequest.put("status", "ACTIVE");
        firstRecipeRequest.put("yieldQuantity", new BigDecimal("4"));
        firstRecipeRequest.put("yieldUnit", "PORTION");
        UUID firstRecipe = id(post(RECIPES, r).as(t).body(firstRecipeRequest).expect(201));
        put(RECIPES + "/{id}/components", r, firstRecipe).as(t).body(ingredient(cheese, "0.200", null)).expect(200);

        Map<String, Object> changedRecipeRequest = recipe("New cheese topping", "PREP_BATCH", null);
        changedRecipeRequest.put("status", "ACTIVE");
        changedRecipeRequest.put("yieldQuantity", new BigDecimal("4"));
        changedRecipeRequest.put("yieldUnit", "PORTION");
        UUID changedRecipe = id(post(RECIPES, r).as(t).body(changedRecipeRequest).expect(201));
        put(RECIPES + "/{id}/components", r, changedRecipe).as(t).body(ingredient(cheese, "0.400", null)).expect(200);

        UUID typeId = id(post("/option-group-types").as(t).body(Map.of("name", "Modifier type")).expect(201));
        UUID groupId = id(post("/option-groups").as(t).body(Map.of(
                "restaurantId", r, "typeId", typeId, "name", "Toppings", "minSelect", 0,
                "maxSelect", 5, "required", false, "active", true)).expect(201));
        UUID extraCheese = id(post("/option-groups/{g}/items", groupId).as(t).body(Map.of(
                "name", "Extra cheese", "available", true, "displayOrder", 0,
                "inventoryRecipeId", firstRecipe, "inventoryRecipeQuantity", new BigDecimal("1"))).expect(201));
        JsonNode configuredModifiers = get("/option-groups/{g}", groupId).as(t)
                .param("includeItems", "true").expect(200);
        assertThat(configuredModifiers.get("items").get(0).get("id").asText()).isEqualTo(extraCheese.toString());
        assertThat(configuredModifiers.get("items").get(0).get("inventoryRecipeId").asText()).isEqualTo(firstRecipe.toString());
        assertThat(new BigDecimal(configuredModifiers.get("items").get(0).get("inventoryRecipeQuantity").asText()))
                .isEqualByComparingTo("1.000");
        post("/menus/{menuId}/sections/{sectionId}/items/{itemId}/option-groups", menu.get("menuId"), menu.get("sectionId"), pizza)
                .as(t).body(Map.of("optionGroupId", groupId, "displayOrder", 0)).expect(201);

        JsonNode createdOrder = order(world, t, Map.of(pizza, 2));
        UUID orderId = id(createdOrder);
        UUID lineId = UUID.fromString(createdOrder.get("lineItems").get(0).get("id").asText());
        post("/restaurants/{r}/orders/{o}/items/{lineId}/options", r, orderId, lineId).as(t)
                .body(Map.of("optionItemId", extraCheese, "quantity", 2)).expect(201);

        // Change the menu configuration after the order was placed. This order must retain its 0.050 kg
        // per-selected-portion recipe snapshot instead of switching to the new 0.100 kg recipe.
        put("/option-groups/{g}/items/{i}", groupId, extraCheese).as(t).body(Map.of(
                "name", "Extra cheese", "available", true, "displayOrder", 0,
                "inventoryRecipeId", changedRecipe, "inventoryRecipeQuantity", new BigDecimal("1"))).expect(200);
        post("/restaurants/{r}/orders/{o}/fulfill", r, orderId).as(t).body(Map.of()).expect(200);

        // 2 menu items × 2 portions of the modifier × 0.050 kg = 0.200 kg.
        assertThat(money(get("/restaurants/{r}/inventory/levels/{l}/{i}", r, kitchen, cheese).as(t).expect(200), "onHandQuantity"))
                .isEqualByComparingTo("0.300");
        JsonNode movements = get("/restaurants/{r}/inventory/movements", r).as(t)
                .param("orderLineItemId", lineId.toString()).param("type", "SALE_CONSUMPTION").expect(200);
        assertThat(movements).hasSize(1);
        assertThat(money(movements.get(0), "quantityDelta")).isEqualByComparingTo("-0.200");
    }

    @Test
    @DisplayName("a recipe can't end up inside itself, directly or through another")
    void noCycles() throws Exception {
        World world = newWorld("recipes-cycle");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        UUID sauce = id(post(RECIPES, r).as(t).body(recipe("Sauce", "SUB_RECIPE", null)).expect(201));
        UUID base = id(post(RECIPES, r).as(t).body(recipe("Base", "PREP_BATCH", null)).expect(201));
        UUID top = id(post(RECIPES, r).as(t).body(recipe("Top", "PREP_BATCH", null)).expect(201));
        put(RECIPES + "/{id}/components", r, base).as(t).body(subRecipe(sauce, "2")).expect(200);
        put(RECIPES + "/{id}/components", r, top).as(t).body(subRecipe(base, "1")).expect(200);

        put(RECIPES + "/{id}/components", r, sauce).as(t).body(subRecipe(sauce, "1")).expect(400);
        put(RECIPES + "/{id}/components", r, sauce).as(t).body(subRecipe(base, "1")).expect(400);
        JsonNode refused = put(RECIPES + "/{id}/components", r, sauce).as(t).body(subRecipe(top, "1")).expect(400);
        assertThat(refused.get("message").asText()).contains("already contains");
        // Pricing still works because nothing was saved.
        post(RECIPES + "/{id}/recalculate-cost", r, top).as(t).expect(200);
    }

    @Test
    @DisplayName("impossible recipes are refused with clear messages")
    void refusesBadInput() throws Exception {
        World world = newWorld("recipes-bad");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        UUID flour = stock(world, "Flour", "1");
        post(RECIPES, r).as(t).body(recipe("Dish without a dish", "FINISHED_DISH", null)).expect(400);
        post(RECIPES, r).as(t).body(recipe("N".repeat(151), "PREP_BATCH", null)).expect(400);
        post(RECIPES, r).as(t).body(recipe("Ok", "SOUP", null)).expect(400);
        Map<String, Object> slow = recipe("Slow", "PREP_BATCH", null);
        slow.put("prepTimeMinutes", 10081);
        post(RECIPES, r).as(t).body(slow).expect(400);
        Map<String, Object> wordy = recipe("Wordy", "PREP_BATCH", null);
        wordy.put("description", "d".repeat(2001));
        post(RECIPES, r).as(t).body(wordy).expect(400);
        wordy.put("description", "d".repeat(2000));
        wordy.put("instructions", "i".repeat(10001));
        post(RECIPES, r).as(t).body(wordy).expect(400);

        UUID batch = id(post(RECIPES, r).as(t).body(recipe("Batch", "PREP_BATCH", null)).expect(201));
        put(RECIPES + "/{id}/components", r, batch).as(t).body(ingredient(flour, "1", "100")).expect(400);
        put(RECIPES + "/{id}/components", r, batch).as(t).body(ingredient(flour, "1", "-1")).expect(400);
        put(RECIPES + "/{id}/components", r, batch).as(t).body(ingredient(flour, "0", null)).expect(400);
        put(RECIPES + "/{id}/components", r, batch).as(t).body(ingredient(flour, "1.0001", null)).expect(400);
        put(RECIPES + "/{id}/components", r, batch).as(t).body(ingredient(UUID.randomUUID(), "1", null)).send();
        put(RECIPES + "/{id}/components", r, batch).as(t).body(ingredient(flour, "1", "99.99")).expect(200);
        get(RECIPES + "/{id}", r, UUID.randomUUID()).as(t).expect(404);
    }

    @Test
    @DisplayName("other restaurants and staff without the right are kept out")
    void access() throws Exception {
        World world = newWorld("recipes-own");
        World other = newWorld("recipes-other");
        UUID r = world.restaurantId();
        UUID secret = id(post(RECIPES, r).as(world.ownerToken()).body(recipe("Secret sauce", "SUB_RECIPE", null)).expect(201));
        assertThat(get(RECIPES + "/{id}", r, secret).as(other.ownerToken()).send().status()).isIn(403, 404);
        assertThat(get(RECIPES + "/{id}", other.restaurantId(), secret).as(other.ownerToken()).send().status()).isEqualTo(404);
        assertThat(post(RECIPES + "/{id}/archive", r, secret).as(other.ownerToken()).send().status()).isIn(403, 404);
        UUID otherBatch = id(post(RECIPES, other.restaurantId()).as(other.ownerToken()).body(recipe("Theirs", "PREP_BATCH", null)).expect(201));
        assertThat(put(RECIPES + "/{id}/components", r, secret).as(world.ownerToken()).body(subRecipe(otherBatch, "1")).send().status())
                .isIn(400, 404);
        User waiter = staff(world, "waiter", "WAITER");
        get(RECIPES, r).as(token(waiter)).expect(403);
        assertThat(get(RECIPES + "/{id}", r, secret).as(world.ownerToken()).expect(200).get("status").asText()).isNotEqualTo("ARCHIVED");
    }
}
