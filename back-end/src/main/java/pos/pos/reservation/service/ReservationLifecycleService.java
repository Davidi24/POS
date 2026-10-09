package pos.pos.reservation.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.repository.OrderRepository;
import pos.pos.reservation.dto.ExtendReservationHoldRequest;
import pos.pos.reservation.dto.ReservationActionRequest;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationEvent;
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.reservation.enums.AttendanceConfirmedVia;
import pos.pos.reservation.enums.ReservationEventType;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.event.ReservationStatusChangedEvent;
import pos.pos.reservation.repository.ReservationEventRepository;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.enums.TableStatus;
import pos.pos.tables.repository.RestaurantTableRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

// Moves bookings through their statuses under the rules agreed with the owner (2026-09-27):
//   Pending (a request) → Confirmed → Checked in → Seated → Completed; Cancelled, No show and Expired end it.
// Staff fix mistakes within the Admin Hub time limits; after that, or on a booking from an earlier day, only someone
// with RESERVATION_CORRECT can, and they must give a reason. Every change is kept with who, when and why.
@Service
@lombok.RequiredArgsConstructor
public class ReservationLifecycleService {

    // A request the restaurant said no to is cancelled with this reason, and the guest gets all money back.
    public static final String DECLINED = "Declined";
    private static final EnumSet<ReservationStatus> WAITING = EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
    private static final EnumSet<OrderStatus> ORDERS_ON_VISIT = EnumSet.of(OrderStatus.DRAFT, OrderStatus.OPEN, OrderStatus.CLOSED);
    private static final EnumSet<TableStatus> SEATABLE = EnumSet.of(TableStatus.AVAILABLE, TableStatus.RESERVED, TableStatus.OCCUPIED);
    private static final DateTimeFormatter CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final RestaurantScopeService restaurantScopeService;
    private final ReservationSupport reservationSupport;
    private final ReservationNotifications reservationNotifications;
    private final ReservationPolicy reservationPolicy;
    private final ReservationAvailabilitySupport reservationAvailabilitySupport;
    private final ReservationEventRepository reservationEventRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher events;
    private Clock clock = Clock.systemUTC();

    // ---- Staff actions ----

    @Transactional
    public ReservationResponse confirmReservation(Authentication authentication, UUID restaurantId, UUID reservationId, ReservationActionRequest request) {
        return act(authentication, restaurantId, reservationId, (reservation, actor) -> confirm(reservation, reason(request), actor));
    }

    @Transactional
    public ReservationResponse declineReservation(Authentication authentication, UUID restaurantId, UUID reservationId, ReservationActionRequest request) {
        return act(authentication, restaurantId, reservationId, (reservation, actor) -> decline(reservation, reason(request), actor));
    }

    @Transactional
    public ReservationResponse cancelReservation(Authentication authentication, UUID restaurantId, UUID reservationId, ReservationActionRequest request) {
        return act(authentication, restaurantId, reservationId, (reservation, actor) -> cancel(reservation, reason(request), actor));
    }

    @Transactional
    public ReservationResponse checkInReservation(Authentication authentication, UUID restaurantId, UUID reservationId, ReservationActionRequest request) {
        return act(authentication, restaurantId, reservationId,
                (reservation, actor) -> checkIn(reservation, request == null ? null : request.getArrivedGuests(), reason(request), actor));
    }

    @Transactional
    public ReservationResponse seatReservation(Authentication authentication, UUID restaurantId, UUID reservationId, ReservationActionRequest request) {
        return act(authentication, restaurantId, reservationId, (reservation, actor) -> seat(reservation, reason(request), actor));
    }

    @Transactional
    public ReservationResponse undoSeatReservation(Authentication authentication, UUID restaurantId, UUID reservationId, ReservationActionRequest request) {
        return act(authentication, restaurantId, reservationId, (reservation, actor) -> undoSeat(reservation, reason(request), actor));
    }

    @Transactional
    public ReservationResponse completeReservation(Authentication authentication, UUID restaurantId, UUID reservationId, ReservationActionRequest request) {
        return act(authentication, restaurantId, reservationId, (reservation, actor) -> complete(reservation, reason(request), actor));
    }

    @Transactional
    public ReservationResponse markNoShow(Authentication authentication, UUID restaurantId, UUID reservationId, ReservationActionRequest request) {
        return act(authentication, restaurantId, reservationId, (reservation, actor) -> noShow(reservation, reason(request), actor));
    }

    @Transactional
    public ReservationResponse reopenReservation(Authentication authentication, UUID restaurantId, UUID reservationId, ReservationActionRequest request) {
        return act(authentication, restaurantId, reservationId, (reservation, actor) -> reopen(reservation, reason(request), actor));
    }

    @Transactional
    public ReservationResponse updateArrivedGuests(Authentication authentication, UUID restaurantId, UUID reservationId, ReservationActionRequest request) {
        return act(authentication, restaurantId, reservationId,
                (reservation, actor) -> changeArrivedGuests(reservation, request == null ? null : request.getArrivedGuests(), reason(request), actor));
    }

    @Transactional
    public ReservationResponse confirmAttendance(Authentication authentication, UUID restaurantId, UUID reservationId, ReservationActionRequest request) {
        return act(authentication, restaurantId, reservationId,
                (reservation, actor) -> confirmAttendance(reservation, AttendanceConfirmedVia.STAFF, reason(request), actor));
    }

    @Transactional
    public ReservationResponse extendHold(Authentication authentication, UUID restaurantId, UUID reservationId, ExtendReservationHoldRequest request) {
        return act(authentication, restaurantId, reservationId,
                (reservation, actor) -> extendHold(reservation, request.getMinutes(), request.getReason(), actor));
    }

    // ---- Transitions (also used by the no-show job and the table listener) ----

    // Accepting a request. Big groups need someone who can approve bookings.
    public void confirm(Reservation reservation, String reason, ReservationActor actor) {
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw bad("Only a booking request can be accepted");
        }
        requireSameDayOrCorrection(reservation, reason, actor);
        int approvalFrom = values(reservation).approvalGroupSize();
        if (reservation.getPartySize() >= approvalFrom && !actor.canApprove()) {
            throw forbidden("Bookings of " + approvalFrom + " or more guests need someone who can approve bookings");
        }
        reservation.setConfirmedAt(now());
        change(reservation, ReservationStatus.CONFIRMED, reason, actor);
    }

    // The restaurant says no to a request (e.g. "Fully booked"). The guest is told and gets all money back.
    public void decline(Reservation reservation, String reason, ReservationActor actor) {
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw bad("Only a booking request can be declined");
        }
        String full = reason == null || reason.isBlank() ? DECLINED : DECLINED + ": " + reason.trim();
        reservation.setCancelledAt(now());
        reservation.setCancellationReason(full);
        change(reservation, ReservationStatus.CANCELLED, full, actor);
    }

    public static boolean isDeclined(Reservation reservation) {
        return reservation.getStatus() == ReservationStatus.CANCELLED && reservation.getCancellationReason() != null
                && reservation.getCancellationReason().startsWith(DECLINED);
    }

    public void cancel(Reservation reservation, String reason, ReservationActor actor) {
        switch (reservation.getStatus()) {
            case PENDING, CONFIRMED -> { }
            case CHECKED_IN -> requireReason(reason, "Say why the guests are leaving before being seated");
            case SEATED -> throw bad("Seated guests can't be cancelled. Complete the visit instead, e.g. \"Left without ordering\"");
            default -> throw bad("This booking is already " + label(reservation.getStatus()));
        }
        requireSameDayOrCorrection(reservation, reason, actor);
        // Cancelled keeps no check-in time; the check-in stays in the history.
        reservation.setCheckedInAt(null);
        reservation.setCancelledAt(now());
        reservation.setCancellationReason(reason);
        change(reservation, ReservationStatus.CANCELLED, reason, actor);
    }

    // "Guest arrived". Works without a table: they wait for one (Checked in, no table). From a no-show it corrects the
    // mistake, but never takes back a table that was given to someone else meanwhile.
    public void checkIn(Reservation reservation, Integer arrivedGuests, String reason, ReservationActor actor) {
        int arrived = arrivedGuests == null ? reservation.getPartySize() : arrivedGuests;
        if (arrived < 1 || arrived > reservation.getPartySize()) {
            throw bad("Arrived guests must be between 1 and " + reservation.getPartySize() + ". For a bigger group, change the booking first");
        }
        String note = reason;
        switch (reservation.getStatus()) {
            case PENDING, CONFIRMED -> {
                OffsetDateTime opens = reservation.getReservationStart().minusMinutes(values(reservation).checkInOpensMinutes());
                if (!actor.system() && now().isBefore(opens)) {
                    throw bad("Check-in opens at " + clockTime(opens, reservation));
                }
                requireSameDayOrCorrection(reservation, reason, actor);
            }
            case NO_SHOW -> {
                requireWithinWindow(reservation.getNoShowAt(), values(reservation).reopenWindowMinutes(), "marked no-show", reason, actor);
                reservation.setNoShowAt(null);
                releaseTakenTables(reservation, actor);
                if (note == null) {
                    note = "Guest arrived after being marked no-show";
                }
            }
            default -> throw bad("Only a booking that is waiting for its guests can be checked in");
        }
        reservation.setArrivedGuests(arrived);
        reservation.setCheckedInAt(now());
        if (reservation.getConfirmedAt() == null) {
            reservation.setConfirmedAt(now());
        }
        change(reservation, ReservationStatus.CHECKED_IN, note, actor);
    }

    // "3 of 6 arrived" → "6 of 6".
    public void changeArrivedGuests(Reservation reservation, Integer arrivedGuests, String reason, ReservationActor actor) {
        if (!EnumSet.of(ReservationStatus.CHECKED_IN, ReservationStatus.SEATED).contains(reservation.getStatus())) {
            throw bad("Only guests who have arrived can be counted");
        }
        if (arrivedGuests == null || arrivedGuests < 1 || arrivedGuests > reservation.getPartySize()) {
            throw bad("Arrived guests must be between 1 and " + reservation.getPartySize() + ". For a bigger group, change the booking first");
        }
        requireSameDayOrCorrection(reservation, reason, actor);
        Integer before = reservation.getArrivedGuests();
        reservation.setArrivedGuests(arrivedGuests);
        if (reservation.getStatus() == ReservationStatus.SEATED) {
            spreadGuests(reservation, arrivedGuests);
        }
        record(reservation, ReservationEventType.ARRIVED_GUESTS_CHANGED,
                (before == null ? reservation.getPartySize() : before) + " → " + arrivedGuests + " of " + reservation.getPartySize() + " arrived",
                reason, actor);
    }

    // Guests sit down at the booking's tables; the tables show as occupied.
    public void seat(Reservation reservation, String reason, ReservationActor actor) {
        if (reservation.getStatus() != ReservationStatus.CHECKED_IN) {
            throw bad("Check the guests in before seating them");
        }
        if (reservation.getTableAssignments().isEmpty()) {
            throw bad("Choose a table for the guests first");
        }
        requireSameDayOrCorrection(reservation, reason, actor);
        occupyTables(reservation);
        reservation.setSeatedAt(now());
        change(reservation, ReservationStatus.SEATED, reason, actor);
    }

    // Seated the wrong booking: back to Checked in. Only while nothing was ordered on it.
    public void undoSeat(Reservation reservation, String reason, ReservationActor actor) {
        if (reservation.getStatus() != ReservationStatus.SEATED) {
            throw bad("Only seated guests can be moved back to waiting");
        }
        if (hasOrders(reservation)) {
            throw new AuthException(
                    "This booking has orders on it. A manager has to move or cancel them before seating can be undone",
                    HttpStatus.CONFLICT
            );
        }
        requireWithinWindow(reservation.getSeatedAt(), values(reservation).undoSeatMinutes(), "seated", reason, actor);
        requireSameDayOrCorrection(reservation, reason, actor);
        freeTables(reservation);
        reservation.setSeatedAt(null);
        change(reservation, ReservationStatus.CHECKED_IN, reason == null ? "Seating undone" : reason, actor);
    }

    // Only staff end a visit (here or by clearing the table). Paying the bill doesn't.
    public void complete(Reservation reservation, String reason, ReservationActor actor) {
        switch (reservation.getStatus()) {
            case SEATED -> { }
            case CHECKED_IN -> throw bad("Seat the guests first, or cancel with a reason if they left before being seated");
            default -> throw bad("Only seated guests can finish their visit");
        }
        requireSameDayOrCorrection(reservation, reason, actor);
        reservation.setCompletedAt(now());
        change(reservation, ReservationStatus.COMPLETED, reason, actor);
    }

    // Only a confirmed booking can be a no-show; a request nobody accepted expires instead.
    public void noShow(Reservation reservation, String reason, ReservationActor actor) {
        switch (reservation.getStatus()) {
            case CONFIRMED -> { }
            case PENDING -> throw bad("A request that was never accepted can't be a no-show. Decline it instead");
            default -> throw bad("Only a confirmed booking that is still waiting for its guests can be a no-show");
        }
        if (now().isBefore(reservation.getReservationStart())) {
            throw bad("The booking hasn't started yet");
        }
        requireSameDayOrCorrection(reservation, reason, actor);
        reservation.setNoShowAt(now());
        change(reservation, ReservationStatus.NO_SHOW, reason, actor);
    }

    // A request nobody answered before the booking time.
    public void expire(Reservation reservation, String reason, ReservationActor actor) {
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw bad("Only a request can expire");
        }
        reservation.setExpiredAt(now());
        change(reservation, ReservationStatus.EXPIRED, reason, actor);
    }

    // Back to Confirmed (or Pending if it was never accepted). The table waits again if its hold has passed, and a
    // table given to someone else meanwhile is not taken back.
    public void reopen(Reservation reservation, String reason, ReservationActor actor) {
        OffsetDateTime since = switch (reservation.getStatus()) {
            case CANCELLED -> reservation.getCancelledAt();
            case NO_SHOW -> reservation.getNoShowAt();
            case EXPIRED -> reservation.getExpiredAt();
            default -> throw bad("Only a cancelled, no-show or expired booking can be reopened");
        };
        String what = switch (reservation.getStatus()) {
            case CANCELLED -> "cancelled";
            case NO_SHOW -> "marked no-show";
            default -> "expired";
        };
        requireWithinWindow(since, values(reservation).reopenWindowMinutes(), what, reason, actor);
        requireSameDayOrCorrection(reservation, reason, actor);
        boolean wasAccepted = reservation.getConfirmedAt() != null && reservation.getStatus() != ReservationStatus.EXPIRED;
        reservation.setCancelledAt(null);
        reservation.setCancellationReason(null);
        reservation.setNoShowAt(null);
        reservation.setExpiredAt(null);
        reservation.setCheckedInAt(null);
        reservation.setSeatedAt(null);
        reservation.setCompletedAt(null);
        reservation.setArrivedGuests(null);
        if (!wasAccepted) {
            reservation.setConfirmedAt(null);
        }
        OffsetDateTime hold = reservationPolicy.holdUntil(reservation);
        if (hold != null && hold.isBefore(now())) {
            reservation.setHoldUntil(now().plusMinutes(values(reservation).holdMinutes()));
        }
        releaseTakenTables(reservation, actor);
        change(reservation, wasAccepted ? ReservationStatus.CONFIRMED : ReservationStatus.PENDING, reason, actor);
    }

    // "✓ Attendance confirmed": the guest is still coming (staff after a call, or the guest's reminder link).
    // A mark on the booking; the status stays Confirmed.
    public void confirmAttendance(Reservation reservation, AttendanceConfirmedVia via, String reason, ReservationActor actor) {
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw bad("Only a confirmed booking can have its attendance confirmed");
        }
        if (reservation.getAttendanceConfirmedAt() != null) {
            return;
        }
        reservation.setAttendanceConfirmedAt(now());
        reservation.setAttendanceConfirmedBy(actor.id());
        reservation.setAttendanceConfirmedVia(via);
        record(reservation, ReservationEventType.ATTENDANCE_CONFIRMED,
                via == AttendanceConfirmedVia.GUEST ? "Attendance confirmed by the guest" : "Attendance confirmed after a call", reason, actor);
    }

    // The guest called: "we'll be 20 minutes late". Only the hold moves; the booking still ends on time.
    public void extendHold(Reservation reservation, Integer minutes, String reason, ReservationActor actor) {
        if (!WAITING.contains(reservation.getStatus())) {
            throw bad("Only a booking still waiting for its guests can be held longer");
        }
        if (minutes == null || minutes < 1) {
            throw bad("Say how many minutes to hold the table");
        }
        requireSameDayOrCorrection(reservation, reason, actor);
        OffsetDateTime current = reservationPolicy.holdUntil(reservation);
        OffsetDateTime from = current == null || current.isBefore(now()) ? now() : current;
        OffsetDateTime until = from.plusMinutes(minutes);
        if (until.isAfter(reservation.getReservationEnd())) {
            throw bad("The table can't be held past the end of the booking (" + clockTime(reservation.getReservationEnd(), reservation) + ")");
        }
        reservation.setHoldUntil(until);
        record(reservation, ReservationEventType.HOLD_EXTENDED, "Table held until " + clockTime(until, reservation), reason, actor);
    }

    // Lets other modules (e.g. pre-orders) follow the booking; for status changes made outside this service too.
    public void announceStatusChange(Reservation reservation, ReservationStatus previousStatus, UUID actorId) {
        events.publishEvent(new ReservationStatusChangedEvent(
                reservation.getId(),
                reservation.getRestaurant() == null ? null : reservation.getRestaurant().getId(),
                previousStatus,
                reservation.getStatus(),
                actorId
        ));
    }

    // ---- Rules ----

    // Within the Admin Hub time limit anyone who manages reservations may; after it, a manager with a reason.
    private void requireWithinWindow(OffsetDateTime since, int minutes, String what, String reason, ReservationActor actor) {
        if (actor.system() || (since != null && !now().isAfter(since.plusMinutes(minutes)))) {
            return;
        }
        requireCorrection("It's more than " + duration(minutes) + " since it was " + what + ".", reason, actor);
    }

    // Changing a booking from an earlier service day (06:00–02:00) always needs a manager and a reason.
    private void requireSameDayOrCorrection(Reservation reservation, String reason, ReservationActor actor) {
        if (actor.system() || reservation.getReservationStart() == null) {
            return;
        }
        ZoneId zone = reservationSupport.restaurantZone(reservation.getRestaurant());
        if (reservationPolicy.serviceDate(reservation.getReservationStart(), zone).isBefore(reservationPolicy.serviceDate(now(), zone))) {
            requireCorrection("This booking is from an earlier day.", reason, actor);
        }
    }

    private void requireCorrection(String why, String reason, ReservationActor actor) {
        if (!actor.canCorrect()) {
            throw forbidden(why + " Only a manager can change it now");
        }
        requireReason(reason, why + " Give a reason for the change");
    }

    private void requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            throw bad(message);
        }
    }

    // ---- Tables ----

    // Drops tables another booking now holds, so a corrected booking never takes a table back. The guests then wait
    // for a table (checked in without one) or staff pick another.
    private void releaseTakenTables(Reservation reservation, ReservationActor actor) {
        if (reservation.getTableAssignments().isEmpty()) {
            return;
        }
        Set<UUID> taken = reservationAvailabilitySupport.takenTableIds(
                reservation.getBranch(), reservation.getReservationStart(), reservation.getReservationEnd(), reservation.getId());
        OffsetDateTime now = now();
        boolean liveNow = !now.isBefore(reservation.getReservationStart().minusMinutes(values(reservation).checkInOpensMinutes()))
                && now.isBefore(reservation.getReservationEnd());
        List<ReservationTableAssignment> released = new ArrayList<>();
        for (ReservationTableAssignment assignment : reservation.getTableAssignments()) {
            RestaurantTable table = assignment.getRestaurantTable();
            boolean occupiedNow = liveNow && table != null && table.getStatus() == TableStatus.OCCUPIED;
            if (table != null && (taken.contains(table.getId()) || occupiedNow)) {
                released.add(assignment);
            }
        }
        if (released.isEmpty()) {
            return;
        }
        boolean primaryReleased = released.stream().anyMatch(ReservationTableAssignment::isPrimaryAssignment);
        released.forEach(reservation::removeTableAssignment);
        if (primaryReleased && !reservation.getTableAssignments().isEmpty()) {
            reservation.getTableAssignments().getFirst().setPrimaryAssignment(true);
        }
        String numbers = String.join(" + ", released.stream()
                .map(assignment -> assignment.getRestaurantTable().getTableNumber()).toList());
        record(reservation, ReservationEventType.TABLES_RELEASED, numbers + " went to another booking", null, actor);
    }

    // Tables staff already seated from the floor plan keep their own guest count; the rest take the arrived guests.
    private void occupyTables(Reservation reservation) {
        int guests = reservation.getArrivedGuests() == null ? reservation.getPartySize() : reservation.getArrivedGuests();
        List<ReservationTableAssignment> free = new ArrayList<>();
        for (ReservationTableAssignment assignment : reservation.getTableAssignments()) {
            RestaurantTable table = lockTable(reservation, assignment);
            if (!SEATABLE.contains(table.getStatus()) || !table.isActive()) {
                throw bad("Table " + table.getTableNumber() + " isn't ready for guests");
            }
            if (table.getStatus() != TableStatus.OCCUPIED) {
                free.add(assignment);
            }
        }
        if (free.size() == reservation.getTableAssignments().size()) {
            spreadGuests(reservation, free, guests);
        } else if (!free.isEmpty()) {
            spreadGuests(reservation, free, free.size());
        }
    }

    private void spreadGuests(Reservation reservation, int guests) {
        spreadGuests(reservation, reservation.getTableAssignments(), guests);
    }

    // Seats the guests across these tables, primary first, filling each up to its capacity, at least one per table.
    private void spreadGuests(Reservation reservation, List<ReservationTableAssignment> tables, int guests) {
        List<ReservationTableAssignment> assignments = tables.stream()
                .sorted((left, right) -> Boolean.compare(right.isPrimaryAssignment(), left.isPrimaryAssignment()))
                .toList();
        int left = guests;
        for (int index = 0; index < assignments.size(); index++) {
            RestaurantTable table = lockTable(reservation, assignments.get(index));
            int tablesAfter = assignments.size() - index - 1;
            int here = index == assignments.size() - 1
                    ? left
                    : Math.max(1, Math.min(Math.max(1, table.getCapacity()), left - tablesAfter));
            left -= here;
            table.setGuestCount(Math.max(1, here));
            if (table.getStatus() != TableStatus.OCCUPIED || table.getSeatedAt() == null) {
                table.setSeatedAt(now());
            }
            table.setStatus(TableStatus.OCCUPIED);
            restaurantTableRepository.save(table);
        }
    }

    // Tables the undone seating had taken become free again, unless something is open on them.
    private void freeTables(Reservation reservation) {
        for (ReservationTableAssignment assignment : reservation.getTableAssignments()) {
            RestaurantTable table = lockTable(reservation, assignment);
            if (table.getStatus() == TableStatus.OCCUPIED) {
                table.setStatus(TableStatus.AVAILABLE);
                table.setGuestCount(null);
                table.setSeatedAt(null);
                restaurantTableRepository.save(table);
            }
        }
    }

    private RestaurantTable lockTable(Reservation reservation, ReservationTableAssignment assignment) {
        UUID tableId = assignment.getRestaurantTable().getId();
        return restaurantTableRepository.findByIdAndBranchIdForUpdate(tableId, reservation.getBranch().getId())
                .orElseThrow(() -> bad("Table " + assignment.getRestaurantTable().getTableNumber() + " no longer exists"));
    }

    private boolean hasOrders(Reservation reservation) {
        if (orderRepository.existsByReservation_IdAndStatusIn(reservation.getId(), ORDERS_ON_VISIT)) {
            return true;
        }
        List<UUID> tableIds = reservation.getTableAssignments().stream().map(a -> a.getRestaurantTable().getId()).toList();
        return !tableIds.isEmpty() && reservation.getSeatedAt() != null && orderRepository
                .findAllByRestaurantTable_IdInAndStatusInOrderByOpenedAtAsc(tableIds, List.of(OrderStatus.DRAFT, OrderStatus.OPEN))
                .stream()
                .anyMatch(order -> order.getOpenedAt() == null || !order.getOpenedAt().isBefore(reservation.getSeatedAt()));
    }

    // ---- Helpers ----

    private ReservationResponse act(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            BiConsumer<Reservation, ReservationActor> action
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        ReservationActor actor = ReservationActor.of(authentication, restaurantScopeService.currentUserId(authentication));
        ReservationStatus before = reservation.getStatus();
        action.accept(reservation, actor);
        reservation.setUpdatedBy(actor.id());
        Reservation saved = reservationSupport.saveReservation(reservation);
        if (saved.getStatus() == ReservationStatus.CANCELLED && before != ReservationStatus.CANCELLED) {
            reservationNotifications.cancelled(saved, actor.id());
        }
        return reservationSupport.toResponse(saved);
    }

    private void change(Reservation reservation, ReservationStatus target, String reason, ReservationActor actor) {
        ReservationStatus previous = reservation.getStatus();
        reservation.setStatus(target);
        reservationSupport.addStatusHistory(reservation, previous, target, reason, actor.id());
        announceStatusChange(reservation, previous, actor.id());
    }

    private void record(Reservation reservation, ReservationEventType type, String detail, String reason, ReservationActor actor) {
        ReservationEvent event = new ReservationEvent();
        event.setReservation(reservation);
        event.setType(type);
        event.setDetail(detail);
        event.setReason(reason);
        event.setActorId(actor.id());
        reservationEventRepository.save(event);
    }

    private ReservationPolicy.Values values(Reservation reservation) {
        return reservationPolicy.values(reservation.getRestaurant());
    }

    private String clockTime(OffsetDateTime moment, Reservation reservation) {
        return CLOCK_TIME.format(moment.atZoneSameInstant(reservationSupport.restaurantZone(reservation.getRestaurant())));
    }

    static String duration(int minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }
        int hours = minutes / 60;
        int rest = minutes % 60;
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }

    private static String label(ReservationStatus status) {
        return switch (status) {
            case PENDING -> "a request";
            case CONFIRMED -> "confirmed";
            case CHECKED_IN -> "checked in";
            case SEATED -> "seated";
            case COMPLETED -> "completed";
            case CANCELLED -> "cancelled";
            case NO_SHOW -> "a no-show";
            case EXPIRED -> "expired";
        };
    }

    private static String reason(ReservationActionRequest request) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            return null;
        }
        return request.getReason().trim();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    // Tests move the clock to check the time limits.
    public void useClock(Clock clock) {
        this.clock = clock;
    }

    private static AuthException bad(String message) {
        return new AuthException(message, HttpStatus.BAD_REQUEST);
    }

    private static AuthException forbidden(String message) {
        return new AuthException(message, HttpStatus.FORBIDDEN);
    }
}
