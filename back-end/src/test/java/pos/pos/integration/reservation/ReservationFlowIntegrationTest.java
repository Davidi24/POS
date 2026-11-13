package pos.pos.integration.reservation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.reservation.entity.ReservationPayment;
import pos.pos.reservation.enums.ReservationEventType;
import pos.pos.reservation.enums.PaymentKind;
import pos.pos.reservation.enums.PaymentStatus;
import pos.pos.reservation.repository.ReservationEventRepository;
import pos.pos.reservation.repository.ReservationPaymentRepository;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.service.GuestBookingService;
import pos.pos.reservation.service.ReservationReminderDeliveryService;
import pos.pos.user.entity.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Bookings end to end")
class ReservationFlowIntegrationTest extends AbstractPosApiIntegrationTest {

    @Autowired
    private GuestBookingService guestBookingService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationPaymentRepository reservationPaymentRepository;

    @Autowired
    private ReservationEventRepository reservationEventRepository;

    @Autowired
    private ReservationReminderDeliveryService reminderDeliveryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final String BOOKINGS = "/restaurants/{r}/reservations";

    @Test
    @DisplayName("production profile never enables the simulated booking payment")
    void productionProfileDisablesTestPaymentsEvenWhenTheSettingIsOverridden() {
        assertThat(guestBookingService.testPayments()).isFalse();
        ReflectionTestUtils.setField(guestBookingService, "paymentProvider", "test");
        assertThat(guestBookingService.testPayments()).isFalse();
    }

    private UUID table(World world, String number, int capacity) throws Exception {
        Response response = post("/restaurants/{r}/branches/{b}/tables", world.restaurantId(), world.branchId()).as(world.ownerToken())
                .body(Map.of("tableNumber", number, "capacity", capacity, "floor", "Main", "positionX", 10, "positionY", 10)).send();
        assertThat(response.status()).as(response.text()).isIn(200, 201);
        return id(response.json());
    }

    private Map<String, Object> booking(World world, OffsetDateTime start, int guests) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("branchId", world.branchId());
        body.put("partySize", guests);
        body.put("reservationStart", start.toString());
        body.put("contactName", "Giulia Rossi");
        body.put("contactPhone", "+39 333 1234567");
        return body;
    }

    private static OffsetDateTime soon(long minutes) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(minutes).truncatedTo(ChronoUnit.MINUTES);
    }

    @Test
    @DisplayName("a guest-reminded event is committed once after mail delivery")
    void guestReminderDeliveryEventIsPersistentAndIdempotent() throws Exception {
        World world = newWorld("guest-reminder-delivered");
        Map<String, Object> body = booking(world, soon(240), 2);
        body.put("contactEmail", "guest.reminder@pos.example");
        JsonNode created = post(BOOKINGS, world.restaurantId()).as(world.ownerToken()).body(body).expect(201);
        UUID reservationId = id(created);

        reminderDeliveryService.recordGuestReminderSent(reservationId);
        reminderDeliveryService.recordGuestReminderSent(reservationId);

        assertThat(reservationEventRepository.existsByReservation_IdAndType(
                reservationId, ReservationEventType.GUEST_REMINDED)).isTrue();
        assertThat(reservationEventRepository.findAllByReservation_IdOrderByCreatedAtAsc(reservationId))
                .filteredOn(event -> event.getType() == ReservationEventType.GUEST_REMINDED)
                .hasSize(1);
    }

    @Test
    @DisplayName("a booking with a table goes confirmed → arrived → seated → finished, and the table follows")
    void serviceLifecycle() throws Exception {
        World world = newWorld("booking");
        User waiter = staff(world, "waiter", "WAITER");
        String t = token(waiter);
        UUID r = world.restaurantId();
        UUID tableId = table(world, "B1", 4);
        Map<String, Object> body = booking(world, soon(60), 3);
        body.put("initialTableIds", List.of(tableId));
        body.put("specialRequests", "Window seat 🌅, gluten free");
        JsonNode created = post(BOOKINGS, r).as(t).body(body).expect(201);
        UUID id = id(created);
        assertThat(created.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(created.get("reservationCode").asText()).isNotBlank();
        assertThat(created.get("specialRequests").asText()).isEqualTo("Window seat 🌅, gluten free");

        assertThat(post(BOOKINGS + "/{id}/check-in", r, id).as(t).body(Map.of("arrivedGuests", 3)).expect(200).get("status").asText())
                .isEqualTo("CHECKED_IN");
        assertThat(post(BOOKINGS + "/{id}/seat", r, id).as(t).body(Map.of()).expect(200).get("status").asText()).isEqualTo("SEATED");
        JsonNode table = get("/restaurants/{r}/branches/{b}/tables/{t}", r, world.branchId(), tableId).as(world.ownerToken()).expect(200);
        assertThat(table.get("status").asText()).isEqualTo("OCCUPIED");
        // Someone seated can't be cancelled; finishing frees the visit.
        assertThat(post(BOOKINGS + "/{id}/cancel", r, id).as(t).body(Map.of("reason", "Test")).send().status()).isBetween(400, 409);
        assertThat(post(BOOKINGS + "/{id}/complete", r, id).as(t).body(Map.of()).expect(200).get("status").asText()).isEqualTo("COMPLETED");

        JsonNode history = get(BOOKINGS + "/{id}/status-history", r, id).as(t).expect(200);
        assertThat(history.toString()).contains("CHECKED_IN").contains("SEATED").contains("COMPLETED");
    }

    @Test
    @DisplayName("branch reservation lists are paged in stable order and filter in the database")
    void branchReservationListPagingAndFilters() throws Exception {
        World world = newWorld("branch-reservation-pages");
        UUID restaurantId = world.restaurantId();
        UUID branchId = world.branchId();
        String owner = world.ownerToken();
        List<JsonNode> created = new ArrayList<>();
        for (int minutes : List.of(210, 240, 270)) {
            created.add(post(BOOKINGS, restaurantId).as(owner).body(booking(world, soon(minutes), 2)).expect(201));
        }

        String branchReservations = "/restaurants/{r}/branches/{b}/reservations";
        JsonNode first = get(branchReservations, restaurantId, branchId).as(owner)
                .param("page", 0).param("size", 2).expect(200);
        assertThat(first.get("items")).hasSize(2);
        assertThat(first.get("page").asInt()).isZero();
        assertThat(first.get("size").asInt()).isEqualTo(2);
        assertThat(first.get("totalElements").asLong()).isEqualTo(3);
        assertThat(first.get("hasNext").asBoolean()).isTrue();
        assertThat(first.get("items").get(0).get("id").asText()).isEqualTo(created.get(0).get("id").asText());
        assertThat(first.get("items").get(1).get("id").asText()).isEqualTo(created.get(1).get("id").asText());

        JsonNode second = get(branchReservations, restaurantId, branchId).as(owner)
                .param("page", 1).param("size", 2).expect(200);
        assertThat(second.get("items")).hasSize(1);
        assertThat(second.get("items").get(0).get("id").asText()).isEqualTo(created.get(2).get("id").asText());
        assertThat(second.get("hasNext").asBoolean()).isFalse();

        JsonNode filtered = get(branchReservations, restaurantId, branchId).as(owner)
                .param("from", soon(210).toString())
                .param("to", soon(240).toString())
                .param("status", created.get(0).get("status").asText())
                .param("page", 0).param("size", 10).expect(200);
        assertThat(filtered.get("items")).hasSize(2);
        assertThat(filtered.get("totalElements").asLong()).isEqualTo(2);

        assertThat(get(branchReservations, restaurantId, branchId).as(owner).param("size", 999).expect(200)
                .get("size").asInt()).isEqualTo(200);
        get(branchReservations, restaurantId, branchId).as(owner).param("page", -1).expect(400);
        get(branchReservations, restaurantId, branchId).as(owner).param("size", 0).expect(400);
        get(branchReservations, restaurantId, branchId).as(owner).param("from", soon(210).toString()).expect(400);
        World other = newWorld("branch-reservation-pages-other");
        get(branchReservations, restaurantId, other.branchId()).as(owner).expect(404);
    }

    @Test
    @DisplayName("impossible bookings are refused with clear messages")
    void refusesBadBookings() throws Exception {
        World world = newWorld("booking-bad");
        String t = world.ownerToken();
        UUID r = world.restaurantId();
        for (Object guests : List.of(0, -2, 501, "many")) {
            Map<String, Object> body = booking(world, soon(120), 2);
            body.put("partySize", guests);
            post(BOOKINGS, r).as(t).body(body).expect(400);
        }
        Map<String, Object> reversed = booking(world, soon(180), 2);
        reversed.put("reservationEnd", soon(120).toString());
        post(BOOKINGS, r).as(t).body(reversed).expect(400);
        Map<String, Object> longName = booking(world, soon(120), 2);
        longName.put("contactName", "N".repeat(151));
        post(BOOKINGS, r).as(t).body(longName).expect(400);
        Map<String, Object> badPhone = booking(world, soon(120), 2);
        badPhone.put("contactPhone", "call me maybe");
        post(BOOKINGS, r).as(t).body(badPhone).expect(400);
        Map<String, Object> badEmail = booking(world, soon(120), 2);
        badEmail.put("contactEmail", "not-an-email");
        post(BOOKINGS, r).as(t).body(badEmail).expect(400);
        Map<String, Object> options = booking(world, soon(120), 2);
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) many.add("option " + i);
        options.put("occasionOptions", many);
        post(BOOKINGS, r).as(t).body(options).expect(400);
        Map<String, Object> otherBranch = booking(world, soon(120), 2);
        otherBranch.put("branchId", newWorld("booking-elsewhere").branchId());
        assertThat(post(BOOKINGS, r).as(t).body(otherBranch).send().status()).isIn(400, 403, 404);
        Map<String, Object> notTable = booking(world, soon(120), 2);
        notTable.put("initialTableIds", List.of(UUID.randomUUID()));
        assertThat(post(BOOKINGS, r).as(t).body(notTable).send().status()).isIn(400, 404);
        get(BOOKINGS + "/{id}", r, UUID.randomUUID()).as(t).expect(404);
    }

    @Test
    @DisplayName("concurrent reservations cannot claim the same table and time slot")
    void concurrentReservationsCannotDoubleBookTable() throws Exception {
        World world = newWorld("reservation-table-race");
        UUID restaurantId = world.restaurantId();
        UUID tableId = table(world, "RACE-1", 4);
        OffsetDateTime startAt = soon(180);
        Map<String, Object> request = booking(world, startAt, 2);
        request.put("initialTableIds", List.of(tableId));

        int requests = 6;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to start same-table reservation race");
                    }
                    return post(BOOKINGS, restaurantId).as(world.ownerToken())
                            .body(new LinkedHashMap<>(request)).send().status();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
            assertThat(statuses).allMatch(status -> status == 201 || status == 409);
        } finally {
            pool.shutdownNow();
        }

        Integer assignmentCount = jdbcTemplate.queryForObject(
                "select count(*) from " + SCHEMA + ".reservation_tables where table_id = ?",
                Integer.class,
                tableId
        );
        assertThat(assignmentCount).isEqualTo(1);
    }

    @Test
    @DisplayName("malformed goodwill payment line identifiers return a client error")
    void malformedGoodwillLineId() throws Exception {
        World world = newWorld("goodwill-invalid-line");
        JsonNode created = post(BOOKINGS, world.restaurantId()).as(world.ownerToken())
                .body(booking(world, soon(120), 2)).expect(201);
        UUID reservationId = id(created);
        for (String lineId : List.of("not-a-uuid", "pre-order-not-a-uuid")) {
            post(BOOKINGS + "/{id}/money/{lineId}/goodwill", world.restaurantId(), reservationId, lineId)
                    .as(world.ownerToken())
                    .body(Map.of("amount", new BigDecimal("1.00"), "reason", "Invalid line regression"))
                    .expect(400);
        }
    }

    @Test
    @DisplayName("concurrent goodwill refunds cannot return more than the kept booking payment")
    void concurrentGoodwillRefunds() throws Exception {
        World world = newWorld("goodwill-race");
        JsonNode created = post(BOOKINGS, world.restaurantId()).as(world.ownerToken())
                .body(booking(world, soon(120), 2)).expect(201);
        UUID reservationId = id(created);
        UUID paymentId = new TransactionTemplate(transactionManager).execute(status -> {
            ReservationPayment kept = new ReservationPayment();
            kept.setReservation(reservationRepository.getReferenceById(reservationId));
            kept.setKind(PaymentKind.DEPOSIT);
            kept.setDescription("Kept deposit");
            kept.setAmount(new BigDecimal("50.00"));
            kept.setCurrency("EUR");
            kept.setStatus(PaymentStatus.KEPT);
            kept.setRefundedAmount(BigDecimal.ZERO);
            return reservationPaymentRepository.saveAndFlush(kept).getId();
        });

        int requests = 6;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                Callable<Integer> task = () -> {
                    start.await();
                    return post(BOOKINGS + "/{id}/money/{lineId}/goodwill", world.restaurantId(), reservationId, paymentId)
                            .as(world.ownerToken())
                            .body(Map.of("amount", new BigDecimal("30.00"), "reason", "Concurrent goodwill regression"))
                            .send().status();
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
            assertThat(statuses).allMatch(status -> status == 200 || status == 400 || status == 409);
        } finally {
            pool.shutdownNow();
        }

        ReservationPayment updated = reservationPaymentRepository.findById(paymentId).orElseThrow();
        assertThat(updated.getRefundedAmount()).isEqualByComparingTo("30.00");
        JsonNode lines = get(BOOKINGS + "/{id}/money", world.restaurantId(), reservationId)
                .as(world.ownerToken()).expect(200);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).get("refundedAmount").decimalValue()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("guests book online, look up and cancel by code, and can't decide their own deposit")
    void publicBooking() throws Exception {
        World world = newWorld("booking-public");
        table(world, "P1", 6);
        String slug = world.restaurant().getSlug();
        String branchCode = world.branch().getCode();
        OffsetDateTime start = soon(3 * 24 * 60).withHour(19).withMinute(0);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partySize", 2);
        body.put("reservationStart", start.toString());
        body.put("reservationEnd", start.plusHours(2).toString());
        body.put("contactName", "Guest Online");
        body.put("contactEmail", "guest.online@pos.example");
        body.put("contactPhone", "+39 333 0000000");
        body.put("depositRequired", false);
        body.put("depositAmount", 0);
        Response created = post("/public/restaurants/{slug}/branches/{code}/reservations", slug, branchCode).body(body).send();
        assertThat(created.status()).as(created.text()).isIn(200, 201);
        String code = created.json().get("reservationCode").asText();

        JsonNode found = get("/public/reservations/{code}", code).expect(200);
        assertThat(found.get("partySize").asInt()).isEqualTo(2);
        assertThat(found.has("internalNotes") && !found.get("internalNotes").isNull()).isFalse();
        post("/public/reservations/{code}/cancel", code).body(Map.of("reason", "Plans changed")).expect(200);
        assertThat(get("/public/reservations/{code}", code).expect(200).get("status").asText()).isEqualTo("CANCELLED");

        get("/public/reservations/{code}", "NOPE-" + next()).expect(404);
        assertThat(post("/public/restaurants/{slug}/branches/{code}/reservations", "no-such-place", branchCode).body(body).send().status())
                .isEqualTo(404);
        body.put("partySize", 9999);
        post("/public/restaurants/{slug}/branches/{code}/reservations", slug, branchCode).body(body).expect(400);
    }

    @Test
    @DisplayName("walk-ins wait on the list, get seated at a free table or leave")
    void waitlist() throws Exception {
        World world = newWorld("waitlist");
        UUID tableId = table(world, "W1", 4);
        String path = "/restaurants/{r}/branches/{b}/waitlist";
        UUID r = world.restaurantId();
        UUID b = world.branchId();
        String t = world.ownerToken();
        JsonNode entry = post(path, r, b).as(t).body(Map.of("guestName", "Marco", "partySize", 2, "phone", "+39 333 1111111")).send().json();
        if (entry.has("id")) {
            UUID entryId = id(entry);
            JsonNode list = get(path, r, b).as(t).expect(200);
            assertThat(list.toString()).contains("Marco");
            JsonNode seated = post(path + "/{e}/seat", r, b, entryId).as(t).body(Map.of("tableId", tableId)).expect(200);
            assertThat(seated.toString()).contains("SEATED");
            assertThat(post(path + "/{e}/seat", r, b, entryId).as(t).body(Map.of("tableId", tableId)).send().status()).isBetween(400, 409);
        }
        post(path, r, b).as(t).body(Map.of("guestName", "", "partySize", 2)).expect(400);
        post(path, r, b).as(t).body(Map.of("guestName", "X".repeat(500), "partySize", 2)).expect(400);
    }
}
