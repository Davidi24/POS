package pos.pos.integration.customer;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.user.entity.User;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Customers end to end")
class CustomerFlowIntegrationTest extends AbstractPosApiIntegrationTest {

    private static final String BASE = "/restaurants/{r}/customers";

    private Map<String, Object> customer(String first, String last) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", first);
        body.put("lastName", last);
        body.put("email", first.toLowerCase().replaceAll("[^a-z]", "") + "@guest.example");
        body.put("phone", "+39 333 7654321");
        return body;
    }

    @Test
    @DisplayName("customers are added, listed by name, changed and removed")
    void lifecycle() throws Exception {
        World world = newWorld("customers");
        UUID r = world.restaurantId();
        String t = world.ownerToken();

        Map<String, Object> zoe = customer("Zoë", "O'Neil-Núñez 🙂");
        zoe.put("notes", "Allergic to nuts; prefers the window");
        JsonNode created = post(BASE, r).as(t).body(zoe).expect(201);
        UUID zoeId = id(created);
        assertThat(created.get("lastName").asText()).isEqualTo("O'Neil-Núñez 🙂");
        post(BASE, r).as(t).body(customer("Anna", "Bianchi")).expect(201);

        JsonNode list = get(BASE, r).as(t).expect(200);
        assertThat(list.get("items")).hasSize(2);
        assertThat(list.get("page").asInt()).isZero();
        assertThat(list.get("totalElements").asLong()).isEqualTo(2);
        assertThat(list.get("items").get(0).get("firstName").asText()).isEqualTo("Anna");

        Map<String, Object> changed = customer("Zoë", "Rossi");
        changed.put("active", false);
        JsonNode updated = put(BASE + "/{c}", r, zoeId).as(t).body(changed).expect(200);
        assertThat(updated.get("lastName").asText()).isEqualTo("Rossi");
        assertThat(updated.get("active").asBoolean()).isFalse();
        assertThat(get(BASE + "/{c}/reservations", r, zoeId).as(t).expect(200).get("items")).isEmpty();

        delete(BASE + "/{c}", r, zoeId).as(t).send();
        get(BASE + "/{c}", r, zoeId).as(t).expect(404);
        assertThat(get(BASE, r).as(t).expect(200).get("items")).hasSize(1);
    }

    @Test
    @DisplayName("customer pages are bounded, sorted consistently and reject invalid page input")
    void listIsBoundedAndStableAcrossPages() throws Exception {
        World world = newWorld("customers-pages");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        post(BASE, r).as(t).body(customer("Bia", "Zulu")).expect(201);
        post(BASE, r).as(t).body(customer("Anna", "Zulu")).expect(201);
        post(BASE, r).as(t).body(customer("Anna", "Aalto")).expect(201);

        JsonNode first = get(BASE, r).as(t).param("page", 0).param("size", 2).expect(200);
        assertThat(first.get("items")).hasSize(2);
        assertThat(first.get("size").asInt()).isEqualTo(2);
        assertThat(first.get("totalElements").asLong()).isEqualTo(3);
        assertThat(first.get("totalPages").asInt()).isEqualTo(2);
        assertThat(first.get("hasNext").asBoolean()).isTrue();
        assertThat(first.get("items").get(0).get("lastName").asText()).isEqualTo("Aalto");
        assertThat(first.get("items").get(1).get("firstName").asText()).isEqualTo("Anna");

        JsonNode second = get(BASE, r).as(t).param("page", 1).param("size", 2).expect(200);
        assertThat(second.get("items")).hasSize(1);
        assertThat(second.get("hasNext").asBoolean()).isFalse();
        assertThat(second.get("items").get(0).get("firstName").asText()).isEqualTo("Bia");

        get(BASE, r).as(t).param("page", -1).expect(400);
        get(BASE, r).as(t).param("size", 0).expect(400);
        JsonNode capped = get(BASE, r).as(t).param("size", 999).expect(200);
        assertThat(capped.get("size").asInt()).isEqualTo(200);
        assertThat(capped.get("items")).hasSize(3);
    }

    @Test
    @DisplayName("a customer's reservation history is paged newest first and stays restaurant scoped")
    void reservationHistoryIsPagedAndScoped() throws Exception {
        World world = newWorld("customer-reservations-pages");
        World other = newWorld("customer-reservations-other");
        UUID r = world.restaurantId();
        String t = world.ownerToken();
        UUID customerId = id(post(BASE, r).as(t).body(customer("History", "Guest")).expect(201));
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).truncatedTo(ChronoUnit.HOURS);

        for (int offset : List.of(0, 4, 8)) {
            Map<String, Object> reservation = new LinkedHashMap<>();
            reservation.put("branchId", world.branchId());
            reservation.put("customerId", customerId);
            reservation.put("partySize", 2);
            reservation.put("reservationStart", start.plusHours(offset).toString());
            reservation.put("contactName", "History Guest");
            reservation.put("contactPhone", "+39 333 7654321");
            post("/restaurants/{r}/reservations", r).as(t).body(reservation).expect(201);
        }

        JsonNode first = get(BASE + "/{c}/reservations", r, customerId).as(t)
                .param("page", 0).param("size", 2).expect(200);
        assertThat(first.get("items")).hasSize(2);
        assertThat(first.get("totalElements").asLong()).isEqualTo(3);
        assertThat(first.get("hasNext").asBoolean()).isTrue();
        assertThat(OffsetDateTime.parse(first.get("items").get(0).get("reservationStart").asText())
                .isAfter(OffsetDateTime.parse(first.get("items").get(1).get("reservationStart").asText()))).isTrue();

        JsonNode second = get(BASE + "/{c}/reservations", r, customerId).as(t)
                .param("page", 1).param("size", 2).expect(200);
        assertThat(second.get("items")).hasSize(1);
        assertThat(second.get("hasNext").asBoolean()).isFalse();
        get(BASE + "/{c}/reservations", r, customerId).as(t).param("page", -1).expect(400);
        get(BASE + "/{c}/reservations", r, customerId).as(t).param("size", 0).expect(400);
        assertThat(get(BASE + "/{c}/reservations", r, customerId).as(other.ownerToken()).send().status())
                .isIn(403, 404);
    }

    @Test
    @DisplayName("overlong or malformed details are refused and nothing is saved")
    void refusesBadInput() throws Exception {
        World world = newWorld("customers-bad");
        UUID r = world.restaurantId();
        String t = world.ownerToken();

        Map<String, Object> longName = customer("N".repeat(101), "Ok");
        post(BASE, r).as(t).body(longName).expect(400);
        Map<String, Object> badEmail = customer("Ok", "Ok");
        badEmail.put("email", "not-an-email");
        post(BASE, r).as(t).body(badEmail).expect(400);
        Map<String, Object> longNotes = customer("Ok", "Ok");
        longNotes.put("notes", "n".repeat(2001));
        post(BASE, r).as(t).body(longNotes).expect(400);
        Map<String, Object> longPhone = customer("Ok", "Ok");
        longPhone.put("phone", "1".repeat(51));
        post(BASE, r).as(t).body(longPhone).expect(400);
        assertThat(post(BASE, r).as(t).header("Content-Type", "application/json").send().status()).isEqualTo(400);
        get(BASE + "/{c}", r, UUID.randomUUID()).as(t).expect(404);
        assertThat(get(BASE, r).as(t).expect(200).get("items")).isEmpty();

        Map<String, Object> coded = customer("Code", "One");
        coded.put("code", "VIP-1");
        UUID codedId = id(post(BASE, r).as(t).body(coded).expect(201));
        Map<String, Object> sameCode = customer("Code", "Two");
        sameCode.put("code", "VIP-1");
        post(BASE, r).as(t).body(sameCode).expect(409);
        delete(BASE + "/{c}", r, codedId).as(t).expect(204);
        post(BASE, r).as(t).body(sameCode).expect(409);
    }

    @Test
    @DisplayName("concurrent customer creates with the same code leave one record and return a conflict")
    void concurrentDuplicateCodesAreConflicts() throws Exception {
        World world = newWorld("customer-code-race");
        UUID restaurantId = world.restaurantId();
        String token = world.ownerToken();
        Map<String, Object> request = customer("Race", "Guest");
        request.put("code", "RACE-1");

        int callers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to start duplicate-customer race");
                    }
                    return post(BASE, restaurantId).as(token).body(new LinkedHashMap<>(request)).send().status();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new java.util.ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
            assertThat(statuses).allMatch(status -> status == 201 || status == 409);
        } finally {
            pool.shutdownNow();
        }

        Integer stored = jdbcTemplate.queryForObject(
                "select count(*) from " + SCHEMA + ".customers where restaurant_id = ? and code = ?",
                Integer.class,
                restaurantId,
                "RACE_1"
        );
        assertThat(stored).isEqualTo(1);
    }

    @Test
    @DisplayName("other restaurants and staff without the right can't read or change customers")
    void access() throws Exception {
        World world = newWorld("customers-own");
        World other = newWorld("customers-other");
        UUID r = world.restaurantId();
        UUID id = id(post(BASE, r).as(world.ownerToken()).body(customer("Private", "Guest")).expect(201));

        assertThat(get(BASE, r).as(other.ownerToken()).send().status()).isIn(403, 404);
        assertThat(get(BASE + "/{c}", other.restaurantId(), id).as(other.ownerToken()).send().status()).isEqualTo(404);
        assertThat(put(BASE + "/{c}", r, id).as(other.ownerToken()).body(customer("Hacked", "X")).send().status()).isIn(403, 404);
        assertThat(delete(BASE + "/{c}", r, id).as(other.ownerToken()).send().status()).isIn(403, 404);

        User waiter = staff(world, "waiter", "WAITER");
        String waiterToken = token(waiter);
        post(BASE, r).as(waiterToken).body(customer("Nope", "Nope")).expect(403);
        get(BASE, r).send().status();
        assertThat(get(BASE, r).send().status()).isEqualTo(401);
        assertThat(get(BASE + "/{c}", r, id).as(world.ownerToken()).expect(200).get("firstName").asText()).isEqualTo("Private");
    }
}
