package pos.pos.integration.tables;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Table availability API integration tests")
class TableAvailabilityApiIntegrationTest extends AbstractPosApiIntegrationTest {

    @Test
    @DisplayName("Availability routes reject zero and negative party sizes and honor valid capacity")
    void availabilityRequiresAPositivePartySize() throws Exception {
        World world = newWorld("table-availability-party-size");
        String tables = "/restaurants/{r}/branches/{b}/tables";
        JsonNode table = post(tables, world.restaurantId(), world.branchId())
                .as(world.ownerToken())
                .body(Map.of("tableNumber", "A1", "capacity", 4, "shape", "RECTANGLE", "status", "AVAILABLE", "active", true,
                        "floor", "Main", "positionX", new BigDecimal("10.00"), "positionY", new BigDecimal("20.00")))
                .expect(201);
        UUID tableId = id(table);
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2).withNano(0);
        OffsetDateTime to = from.plusHours(1);

        JsonNode available = get(tables + "/available", world.restaurantId(), world.branchId())
                .as(world.ownerToken()).param("from", from).param("to", to).param("partySize", 4).expect(200);
        assertThat(available).hasSize(1);
        assertThat(available.get(0).get("tableId").asText()).isEqualTo(tableId.toString());
        assertThat(available.get(0).get("availableForRequestedWindow").asBoolean()).isTrue();

        assertThat(get(tables + "/availability", world.restaurantId(), world.branchId())
                .as(world.ownerToken()).param("from", from).param("to", to).param("partySize", 0).send().status()).isEqualTo(400);
        assertThat(get(tables + "/available", world.restaurantId(), world.branchId())
                .as(world.ownerToken()).param("from", from).param("to", to).param("partySize", -1).send().status()).isEqualTo(400);
        assertThat(get(tables + "/{tableId}/availability", world.restaurantId(), world.branchId(), tableId)
                .as(world.ownerToken()).param("from", from).param("to", to).param("partySize", 0).send().status()).isEqualTo(400);
    }

    @Test
    @DisplayName("Overlapping table merges lock participants in stable order and cannot deadlock")
    void overlappingMergesDoNotDeadlock() throws Exception {
        World world = newWorld("table-merge-lock-order");
        String tables = "/restaurants/{r}/branches/{b}/tables";
        UUID first = id(post(tables, world.restaurantId(), world.branchId()).as(world.ownerToken())
                .body(Map.of("tableNumber", "M1", "capacity", 2, "floor", "Main", "positionX", 10, "positionY", 10)).expect(201));
        UUID second = id(post(tables, world.restaurantId(), world.branchId()).as(world.ownerToken())
                .body(Map.of("tableNumber", "M2", "capacity", 2, "floor", "Main", "positionX", 20, "positionY", 10)).expect(201));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstIntoSecond = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting to start merge race");
                return post(tables + "/{tableId}/merge", world.restaurantId(), world.branchId(), first)
                        .as(world.ownerToken()).body(Map.of("tableIds", List.of(second))).send().status();
            });
            Future<Integer> secondIntoFirst = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting to start merge race");
                return post(tables + "/{tableId}/merge", world.restaurantId(), world.branchId(), second)
                        .as(world.ownerToken()).body(Map.of("tableIds", List.of(first))).send().status();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(
                    firstIntoSecond.get(30, TimeUnit.SECONDS),
                    secondIntoFirst.get(30, TimeUnit.SECONDS)));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);

            JsonNode firstAfter = get(tables + "/{tableId}", world.restaurantId(), world.branchId(), first)
                    .as(world.ownerToken()).expect(200);
            JsonNode secondAfter = get(tables + "/{tableId}", world.restaurantId(), world.branchId(), second)
                    .as(world.ownerToken()).expect(200);
            assertThat(firstAfter.get("mergedIntoTableId").isNull()
                    ^ secondAfter.get("mergedIntoTableId").isNull()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }
}
