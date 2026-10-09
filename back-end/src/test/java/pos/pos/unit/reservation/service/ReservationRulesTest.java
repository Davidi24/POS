package pos.pos.unit.reservation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pos.pos.exception.auth.AuthException;
import pos.pos.order.repository.OrderRepository;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationEvent;
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.reservation.enums.ReservationEventType;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationEventRepository;
import pos.pos.reservation.service.ReservationActor;
import pos.pos.reservation.service.ReservationAvailabilitySupport;
import pos.pos.reservation.service.ReservationLifecycleService;
import pos.pos.reservation.service.ReservationPolicy;
import pos.pos.reservation.service.ReservationRuleResolver;
import pos.pos.reservation.service.ReservationSupport;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.settings.repository.SettingsReservationRuleRepository;
import pos.pos.settings.repository.SettingsRepository;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.enums.TableStatus;
import pos.pos.tables.repository.RestaurantTableRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The booking rules agreed with the owner (phase 1): who may move a booking where, and the Admin Hub time limits
// (defaults: check-in 2 h before, hold 30 min, reopen within 1 h, undo seating within 15 min, 7+ need approval).
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Reservation rules")
class ReservationRulesTest {

    // 20:00 in Berlin.
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 27, 18, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.randomUUID();
    private static final ReservationActor STAFF = new ReservationActor(ACTOR, false, false, false);
    private static final ReservationActor MANAGER = new ReservationActor(ACTOR, true, true, false);

    @Mock SettingsRepository settingsRepository;
    @Mock SettingsReservationRuleRepository ruleRepository;
    @Mock ReservationAvailabilitySupport availabilitySupport;
    @Mock ReservationEventRepository eventRepository;
    @Mock RestaurantTableRepository tableRepository;
    @Mock OrderRepository orderRepository;

    private ReservationLifecycleService lifecycle;
    private Branch branch;

    @BeforeEach
    void setUp() {
        ReservationPolicy policy = new ReservationPolicy(settingsRepository, new ReservationRuleResolver(ruleRepository));
        ReservationSupport support = new ReservationSupport(null, null, null, null, null, null, null, policy);
        lifecycle = new ReservationLifecycleService(
                null, support, null, policy, availabilitySupport, eventRepository, tableRepository, orderRepository, event -> { });
        lifecycle.useClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

        Restaurant restaurant = new Restaurant();
        restaurant.setId(UUID.randomUUID());
        restaurant.setTimezone("Europe/Berlin");
        branch = new Branch();
        branch.setId(UUID.randomUUID());
        branch.setRestaurant(restaurant);
        when(availabilitySupport.takenTableIds(any(), any(), any(), any())).thenReturn(Set.of());
        when(tableRepository.findByIdAndBranchIdForUpdate(any(), any())).thenAnswer(invocation -> Optional.ofNullable(tables.get(invocation.getArgument(0))));
    }

    private final java.util.Map<UUID, RestaurantTable> tables = new java.util.HashMap<>();

    private Reservation booking(ReservationStatus status, OffsetDateTime start, int guests) {
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setRestaurant(branch.getRestaurant());
        reservation.setBranch(branch);
        reservation.setStatus(status);
        reservation.setPartySize(guests);
        reservation.setReservationStart(start);
        reservation.setReservationEnd(start.plusHours(2));
        reservation.setContactName("Maria");
        if (status != ReservationStatus.PENDING) {
            reservation.setConfirmedAt(start.minusDays(1));
        }
        return reservation;
    }

    private RestaurantTable table(Reservation reservation, String number, TableStatus status) {
        RestaurantTable table = new RestaurantTable();
        table.setId(UUID.randomUUID());
        table.setTableNumber(number);
        table.setCapacity(4);
        table.setStatus(status);
        table.setActive(true);
        tables.put(table.getId(), table);
        ReservationTableAssignment assignment = new ReservationTableAssignment();
        assignment.setRestaurantTable(table);
        assignment.setPrimaryAssignment(reservation.getTableAssignments().isEmpty());
        reservation.addTableAssignment(assignment);
        return table;
    }

    // ---- Accepting requests ----

    @Test
    @DisplayName("a group of 7 or more needs someone who can approve bookings")
    void bigGroupNeedsApproval() {
        Reservation request = booking(ReservationStatus.PENDING, NOW.plusDays(2), 8);

        assertThatThrownBy(() -> lifecycle.confirm(request, null, STAFF))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("7 or more");

        lifecycle.confirm(request, null, MANAGER);
        assertThat(request.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(request.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("staff accept a normal request")
    void staffAcceptNormalRequest() {
        Reservation request = booking(ReservationStatus.PENDING, NOW.plusDays(2), 4);

        lifecycle.confirm(request, null, STAFF);

        assertThat(request.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    // ---- Cancelling ----

    @Test
    @DisplayName("checked-in guests are cancelled only with a reason, seated guests never")
    void cancelRules() {
        Reservation checkedIn = booking(ReservationStatus.CHECKED_IN, NOW.minusMinutes(10), 4);
        checkedIn.setCheckedInAt(NOW.minusMinutes(5));
        assertThatThrownBy(() -> lifecycle.cancel(checkedIn, null, STAFF)).hasMessageContaining("Say why");

        lifecycle.cancel(checkedIn, "Left before being seated", STAFF);
        assertThat(checkedIn.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(checkedIn.getCheckedInAt()).isNull();
        assertThat(checkedIn.getCancellationReason()).isEqualTo("Left before being seated");

        Reservation seated = booking(ReservationStatus.SEATED, NOW.minusMinutes(30), 4);
        assertThatThrownBy(() -> lifecycle.cancel(seated, "Changed their mind", MANAGER)).hasMessageContaining("Complete the visit");
    }

    // ---- Check-in ----

    @Test
    @DisplayName("check-in opens 2 hours before and keeps how many of the group arrived")
    void checkInWindowAndArrivedGuests() {
        Reservation early = booking(ReservationStatus.CONFIRMED, NOW.plusHours(3), 6);
        assertThatThrownBy(() -> lifecycle.checkIn(early, null, null, STAFF)).hasMessageContaining("Check-in opens at 21:00");

        Reservation soon = booking(ReservationStatus.CONFIRMED, NOW.plusMinutes(30), 6);
        lifecycle.checkIn(soon, 3, null, STAFF);

        assertThat(soon.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThat(soon.getArrivedGuests()).isEqualTo(3);
        assertThat(soon.getCheckedInAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> lifecycle.changeArrivedGuests(soon, 7, null, STAFF)).hasMessageContaining("between 1 and 6");

        lifecycle.changeArrivedGuests(soon, 6, null, STAFF);
        assertThat(soon.getArrivedGuests()).isEqualTo(6);
        verify(eventRepository).save(org.mockito.ArgumentMatchers.argThat(event -> event.getType() == ReservationEventType.ARRIVED_GUESTS_CHANGED));
    }

    @Test
    @DisplayName("checking in works without a table: the guests wait for one")
    void checkInWithoutTable() {
        Reservation booking = booking(ReservationStatus.CONFIRMED, NOW.minusMinutes(5), 2);

        lifecycle.checkIn(booking, null, null, STAFF);

        assertThat(booking.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThat(booking.getTableAssignments()).isEmpty();
    }

    @Test
    @DisplayName("a no-show marked by mistake: staff within 1 hour, then only a manager with a reason")
    void guestArrivedAfterNoShow() {
        Reservation recent = booking(ReservationStatus.NO_SHOW, NOW.minusMinutes(40), 2);
        recent.setNoShowAt(NOW.minusMinutes(10));
        lifecycle.checkIn(recent, null, null, STAFF);
        assertThat(recent.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThat(recent.getNoShowAt()).isNull();
        assertThat(recent.getStatusHistory().getLast().getReason()).contains("after being marked no-show");

        Reservation old = booking(ReservationStatus.NO_SHOW, NOW.minusMinutes(100), 2);
        old.setNoShowAt(NOW.minusMinutes(70));
        assertThatThrownBy(() -> lifecycle.checkIn(old, null, null, STAFF))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Only a manager");
        assertThatThrownBy(() -> lifecycle.checkIn(old, null, null, MANAGER)).hasMessageContaining("Give a reason");

        lifecycle.checkIn(old, null, "They were at the bar", MANAGER);
        assertThat(old.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
    }

    @Test
    @DisplayName("a corrected no-show never takes back a table given to someone else")
    void correctedNoShowReleasesTakenTable() {
        Reservation booking = booking(ReservationStatus.NO_SHOW, NOW.minusMinutes(40), 2);
        booking.setNoShowAt(NOW.minusMinutes(10));
        RestaurantTable taken = table(booking, "T3", TableStatus.AVAILABLE);
        when(availabilitySupport.takenTableIds(eq(branch), any(), any(), eq(booking.getId()))).thenReturn(Set.of(taken.getId()));

        lifecycle.checkIn(booking, null, null, STAFF);

        assertThat(booking.getTableAssignments()).isEmpty();
        verify(eventRepository).save(org.mockito.ArgumentMatchers.argThat((ReservationEvent event) ->
                event.getType() == ReservationEventType.TABLES_RELEASED && event.getDetail().contains("T3")));
    }

    // ---- Seating ----

    @Test
    @DisplayName("seating needs a table and marks it occupied with the arrived guests")
    void seatNeedsTable() {
        Reservation booking = booking(ReservationStatus.CHECKED_IN, NOW.minusMinutes(5), 6);
        booking.setCheckedInAt(NOW.minusMinutes(5));
        booking.setArrivedGuests(3);
        assertThatThrownBy(() -> lifecycle.seat(booking, null, STAFF)).hasMessageContaining("Choose a table");

        RestaurantTable table = table(booking, "T1", TableStatus.AVAILABLE);
        lifecycle.seat(booking, null, STAFF);

        assertThat(booking.getStatus()).isEqualTo(ReservationStatus.SEATED);
        assertThat(table.getStatus()).isEqualTo(TableStatus.OCCUPIED);
        assertThat(table.getGuestCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("undo seat: staff within 15 minutes, then a manager with a reason; never with orders on it")
    void undoSeatRules() {
        Reservation booking = booking(ReservationStatus.SEATED, NOW.minusMinutes(30), 2);
        booking.setCheckedInAt(NOW.minusMinutes(25));
        booking.setSeatedAt(NOW.minusMinutes(10));
        RestaurantTable table = table(booking, "T1", TableStatus.OCCUPIED);
        table.setGuestCount(2);
        table.setSeatedAt(NOW.minusMinutes(10));

        lifecycle.undoSeat(booking, null, STAFF);
        assertThat(booking.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThat(booking.getSeatedAt()).isNull();
        assertThat(table.getStatus()).isEqualTo(TableStatus.AVAILABLE);

        Reservation late = booking(ReservationStatus.SEATED, NOW.minusMinutes(40), 2);
        late.setCheckedInAt(NOW.minusMinutes(35));
        late.setSeatedAt(NOW.minusMinutes(20));
        table(late, "T2", TableStatus.OCCUPIED);
        assertThatThrownBy(() -> lifecycle.undoSeat(late, null, STAFF)).hasMessageContaining("Only a manager");

        when(orderRepository.existsByReservation_IdAndStatusIn(eq(late.getId()), any())).thenReturn(true);
        assertThatThrownBy(() -> lifecycle.undoSeat(late, "Wrong booking", MANAGER)).hasMessageContaining("has orders");
    }

    @Test
    @DisplayName("a visit is completed only from seated")
    void completeOnlyFromSeated() {
        Reservation checkedIn = booking(ReservationStatus.CHECKED_IN, NOW.minusMinutes(5), 2);
        checkedIn.setCheckedInAt(NOW);
        assertThatThrownBy(() -> lifecycle.complete(checkedIn, null, STAFF)).hasMessageContaining("Seat the guests first");

        Reservation seated = booking(ReservationStatus.SEATED, NOW.minusHours(1), 2);
        lifecycle.complete(seated, "Left without ordering", STAFF);
        assertThat(seated.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(seated.getStatusHistory().getLast().getReason()).isEqualTo("Left without ordering");
    }

    // ---- No-shows and reopening ----

    @Test
    @DisplayName("only a confirmed booking that has started can be a no-show")
    void noShowRules() {
        assertThatThrownBy(() -> lifecycle.noShow(booking(ReservationStatus.PENDING, NOW.minusMinutes(40), 2), null, STAFF))
                .hasMessageContaining("Decline it instead");
        assertThatThrownBy(() -> lifecycle.noShow(booking(ReservationStatus.CONFIRMED, NOW.plusMinutes(10), 2), null, STAFF))
                .hasMessageContaining("hasn't started");
        Reservation checkedIn = booking(ReservationStatus.CHECKED_IN, NOW.minusMinutes(40), 2);
        assertThatThrownBy(() -> lifecycle.noShow(checkedIn, null, MANAGER)).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("reopening goes back to confirmed and gives the table a new hold")
    void reopenConfirmedBooking() {
        Reservation booking = booking(ReservationStatus.NO_SHOW, NOW.minusMinutes(50), 2);
        booking.setNoShowAt(NOW.minusMinutes(20));

        lifecycle.reopen(booking, null, STAFF);

        assertThat(booking.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(booking.getNoShowAt()).isNull();
        assertThat(booking.getHoldUntil()).isEqualTo(NOW.plusMinutes(30));
    }

    @Test
    @DisplayName("an expired request reopens as a request")
    void reopenExpiredRequest() {
        Reservation request = booking(ReservationStatus.EXPIRED, NOW.plusHours(1), 2);
        request.setConfirmedAt(null);
        request.setExpiredAt(NOW.minusMinutes(5));

        lifecycle.reopen(request, null, STAFF);

        assertThat(request.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(request.getExpiredAt()).isNull();
    }

    @Test
    @DisplayName("a booking from an earlier day needs a manager and a reason")
    void earlierDayNeedsManager() {
        Reservation yesterday = booking(ReservationStatus.CONFIRMED, NOW.minusDays(1), 2);

        assertThatThrownBy(() -> lifecycle.cancel(yesterday, null, STAFF)).hasMessageContaining("earlier day");
        lifecycle.cancel(yesterday, "Guest called the next morning", MANAGER);
        assertThat(yesterday.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    @DisplayName("after midnight the evening's bookings are still today's")
    void serviceDayRunsPastMidnight() {
        // 01:30 in Berlin: a 23:30 booking belongs to the same restaurant day.
        lifecycle.useClock(Clock.fixed(OffsetDateTime.of(2026, 9, 27, 23, 30, 0, 0, ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
        Reservation lateBooking = booking(ReservationStatus.CONFIRMED, OffsetDateTime.of(2026, 9, 27, 21, 30, 0, 0, ZoneOffset.UTC), 2);

        lifecycle.noShow(lateBooking, null, STAFF);

        assertThat(lateBooking.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
    }

    // ---- Holding the table ----

    @Test
    @DisplayName("holding the table longer moves only the hold, never past the booking end")
    void extendHold() {
        Reservation booking = booking(ReservationStatus.CONFIRMED, NOW.minusMinutes(20), 2);

        lifecycle.extendHold(booking, 15, "Guest called", STAFF);

        assertThat(booking.getHoldUntil()).isEqualTo(booking.getReservationStart().plusMinutes(45));
        assertThat(booking.getReservationEnd()).isEqualTo(booking.getReservationStart().plusHours(2));
        verify(eventRepository).save(org.mockito.ArgumentMatchers.argThat((ReservationEvent event) ->
                event.getType() == ReservationEventType.HOLD_EXTENDED && event.getDetail().equals("Table held until 20:25")));
        assertThatThrownBy(() -> lifecycle.extendHold(booking, 120, null, STAFF)).hasMessageContaining("past the end");
    }

    @Test
    @DisplayName("a visit open past its time or from an earlier day needs review")
    void needsReview() {
        ReservationPolicy policy = new ReservationPolicy(settingsRepository, new ReservationRuleResolver(ruleRepository));
        ReservationSupport support = new ReservationSupport(null, null, null, null, null, null, null, policy);

        Reservation neverSeated = booking(ReservationStatus.CHECKED_IN, NOW.minusHours(3), 2);
        assertThat(support.reviewReason(neverSeated, NOW)).contains("never seated");

        Reservation stillSeated = booking(ReservationStatus.SEATED, NOW.minusDays(1), 2);
        assertThat(support.reviewReason(stillSeated, NOW)).contains("earlier day");

        Reservation onTime = booking(ReservationStatus.SEATED, NOW.minusHours(1), 2);
        assertThat(support.reviewReason(onTime, NOW)).isNull();
    }

    @Test
    @DisplayName("the booking length is 2 hours, 2 h 15 for 5 or more guests")
    void bookingLength() {
        ReservationPolicy policy = new ReservationPolicy(settingsRepository, new ReservationRuleResolver(ruleRepository));

        assertThat(policy.bookingLength(branch, NOW, 4)).isEqualTo(java.time.Duration.ofMinutes(120));
        assertThat(policy.bookingLength(branch, NOW, 5)).isEqualTo(java.time.Duration.ofMinutes(135));
    }

    @Test
    @DisplayName("the Admin Hub values apply to the rules")
    void settingsApply() {
        pos.pos.settings.entity.Settings settings = new pos.pos.settings.entity.Settings();
        settings.setHoldMinutes(45);
        settings.setApprovalGroupSize(10);
        when(settingsRepository.findByRestaurant_Id(branch.getRestaurant().getId())).thenReturn(Optional.of(settings));
        ReservationPolicy policy = new ReservationPolicy(settingsRepository, new ReservationRuleResolver(ruleRepository));
        ReservationSupport support = new ReservationSupport(null, null, null, null, null, null, null, policy);
        ReservationLifecycleService withSettings = new ReservationLifecycleService(
                null, support, null, policy, availabilitySupport, eventRepository, tableRepository, orderRepository, event -> { });
        withSettings.useClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

        Reservation eight = booking(ReservationStatus.PENDING, NOW.plusDays(1), 8);
        withSettings.confirm(eight, null, STAFF);
        assertThat(eight.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(policy.holdUntil(booking(ReservationStatus.CONFIRMED, NOW, 2))).isEqualTo(NOW.plusMinutes(45));
    }
}
