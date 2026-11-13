package pos.pos.tables.specialcase;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationMutationType;
import pos.pos.notification.enums.NotificationPriority;
import pos.pos.notification.enums.NotificationTopic;
import pos.pos.notification.service.NotificationOperationalPublisher;
import pos.pos.order.entity.Order;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.repository.OrderRepository;
import pos.pos.order.service.OrderSupport;
import pos.pos.order.service.OrderWorkflowService;
import pos.pos.reservation.dto.ReservationAvailabilityOptionResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.repository.ReservationTableAssignmentRepository;
import pos.pos.reservation.service.ReservationActor;
import pos.pos.reservation.service.ReservationLifecycleService;
import pos.pos.reservation.service.ReservationPolicy;
import pos.pos.reservation.service.ReservationTableAssignmentService;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.repository.SettingsRepository;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.enums.TableStatus;
import pos.pos.tables.event.TableStatusChangedEvent;
import pos.pos.tables.realtime.TableLayoutChangeNotifier;
import pos.pos.tables.repository.RestaurantTableRepository;
import pos.pos.user.repository.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// The special cases of Settings → Tables (user, 2026-11-01): guests in the way of the next booking, and what the system
// does when nobody acts at all. Everything it does by itself is recorded here and can be returned.
@Service
@RequiredArgsConstructor
public class SpecialCaseService {

    public static final String EVENT_CODE = "SPECIAL_CASE_ACTION";

    private static final EnumSet<OrderStatus> OPEN = EnumSet.of(OrderStatus.DRAFT, OrderStatus.OPEN);
    private static final EnumSet<ReservationStatus> EXPECTED = EnumSet.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN);
    private static final EnumSet<SpecialCaseAction.Kind> RETURNABLE = EnumSet.of(SpecialCaseAction.Kind.TABLE_FREED,
            SpecialCaseAction.Kind.ORDER_CLOSED, SpecialCaseAction.Kind.BOOKING_MOVED, SpecialCaseAction.Kind.BOOKING_NO_ACTION);

    private final RestaurantTableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final OrderSupport orderSupport;
    private final OrderWorkflowService orderWorkflowService;
    private final ReservationRepository reservationRepository;
    private final ReservationTableAssignmentRepository assignmentRepository;
    private final ReservationTableAssignmentService assignmentService;
    private final ReservationLifecycleService lifecycle;
    private final ReservationPolicy reservationPolicy;
    private final SettingsRepository settingsRepository;
    private final SpecialCaseActionRepository actionRepository;
    private final RestaurantScopeService scope;
    private final TableLayoutChangeNotifier layoutNotifier;
    private final NotificationOperationalPublisher notifications;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final pos.pos.reservation.service.ReservationTableRelease tableRelease;

    // ---- What staff do ----

    @Transactional
    public void giveMoreTime(Authentication authentication, UUID restaurantId, UUID branchId, UUID tableId, int minutes) {
        scope.requireManageableBranch(authentication, restaurantId, branchId);
        RestaurantTable table = lockTable(branchId, tableId);
        UUID actorId = scope.currentUserId(authentication);
        table.setMoreTimeUntil(now().plusMinutes(minutes));
        table.setUpdatedBy(actorId);
        tableRepository.save(table);
        nextBooking(table).ifPresent(booking -> lifecycle.note(booking, minutes + " minutes more for the guests at " + table.getTableNumber(), actorId));
        layoutNotifier.notifyBranchChanged(restaurantId, branchId);
    }

    @Transactional
    public void stillHere(Authentication authentication, UUID restaurantId, UUID branchId, UUID tableId) {
        scope.requireManageableBranch(authentication, restaurantId, branchId);
        RestaurantTable table = lockTable(branchId, tableId);
        table.setStillHereAt(now());
        table.setUpdatedBy(scope.currentUserId(authentication));
        tableRepository.save(table);
        layoutNotifier.notifyBranchChanged(restaurantId, branchId);
    }

    @Transactional(readOnly = true)
    public List<ReservationAvailabilityOptionResponse> bookingOptions(Authentication authentication, UUID restaurantId, UUID branchId, UUID tableId) {
        scope.requireAccessibleBranch(authentication, restaurantId, branchId);
        RestaurantTable table = tableRepository.findByIdAndBranch_Id(tableId, branchId).orElseThrow(SpecialCaseService::noTable);
        // The same floor first.
        return nextBooking(table).map(assignmentService::freeOptions).orElse(List.of()).stream()
                .sorted(java.util.Comparator.comparing((ReservationAvailabilityOptionResponse option) -> !option.getTableIds().stream().allMatch(id -> sameFloor(id, table))))
                .toList();
    }

    @Transactional
    public void moveBooking(Authentication authentication, UUID restaurantId, UUID branchId, UUID tableId, List<UUID> tableIds, UUID primaryTableId) {
        scope.requireManageableBranch(authentication, restaurantId, branchId);
        RestaurantTable from = tableRepository.findByIdAndBranch_Id(tableId, branchId).orElseThrow(SpecialCaseService::noTable);
        Reservation booking = nextBooking(from).orElseThrow(() -> new AuthException("This table has no booking coming", HttpStatus.CONFLICT));
        UUID actorId = scope.currentUserId(authentication);
        List<RestaurantTable> left = booking.getTableAssignments().stream().map(ReservationTableAssignment::getRestaurantTable)
                .filter(table -> !tableIds.contains(table.getId())).toList();
        assignmentService.moveTo(booking, tableIds, primaryTableId, actorId);
        reservationRepository.save(booking);
        tableRelease.afterMove(left, booking, actorId);
        String to = booking.getTableAssignments().stream().map(a -> a.getRestaurantTable().getTableNumber()).collect(Collectors.joining("-"));
        lifecycle.note(booking, "Moved from " + from.getTableNumber() + " to " + to, actorId);
        reserve(booking, actorId);
        layoutNotifier.notifyBranchChanged(restaurantId, branchId);
    }

    @Transactional(readOnly = true)
    public List<SpecialCaseDtos.ActionResponse> actions(Authentication authentication, UUID restaurantId, UUID branchId) {
        scope.requireAccessibleBranch(authentication, restaurantId, branchId);
        return actionRepository.findTop50ByBranchIdAndHappenedAtAfterAndReturnedAtIsNullAndKindInOrderByHappenedAtDesc(branchId, now().minusDays(2), RETURNABLE)
                .stream()
                .filter(this::stillToHandle)
                .map(action -> new SpecialCaseDtos.ActionResponse(action.getId(), action.getKind(), action.getSummary(), action.getDetail(), action.getTableNumber(),
                        action.getGuests(), firstOrder(action), action.getHappenedAt(), tableFree(action)))
                .toList();
    }

    // An order reopened from History is dealt with, so it leaves the list.
    private boolean stillToHandle(SpecialCaseAction action) {
        if (action.getKind() != SpecialCaseAction.Kind.ORDER_CLOSED) {
            return true;
        }
        UUID orderId = firstOrder(action);
        return orderId != null && orderRepository.findById(orderId).map(order -> order.getStatus() == OrderStatus.VOIDED).orElse(false);
    }

    private static UUID firstOrder(SpecialCaseAction action) {
        return action.getOrderIds() == null || action.getOrderIds().isBlank() ? null : UUID.fromString(action.getOrderIds().split(",")[0]);
    }

    private boolean tableFree(SpecialCaseAction action) {
        // An order is reopened from History and needs no table. A moved booking goes back whatever is on its table: staff ask for it.
        if (action.getTableId() == null || action.getKind() == SpecialCaseAction.Kind.ORDER_CLOSED || action.getKind() == SpecialCaseAction.Kind.BOOKING_MOVED) {
            return true;
        }
        return tableRepository.findById(action.getTableId()).map(table -> table.getStatus() == TableStatus.AVAILABLE).orElse(false);
    }

    @Transactional
    public void returnAction(Authentication authentication, UUID restaurantId, UUID branchId, UUID actionId, UUID otherTableId) {
        scope.requireManageableBranch(authentication, restaurantId, branchId);
        SpecialCaseAction action = actionRepository.findByIdAndBranchId(actionId, branchId)
                .orElseThrow(() -> new AuthException("This action no longer exists", HttpStatus.NOT_FOUND));
        if (action.getReturnedAt() != null) {
            throw new AuthException("This was already returned", HttpStatus.CONFLICT);
        }
        UUID actorId = scope.currentUserId(authentication);
        switch (action.getKind()) {
            case TABLE_FREED -> returnGuests(action, branchId, otherTableId, actorId);
            // Orders are reopened only from History (user, 2026-11-01).
            case ORDER_CLOSED -> throw new AuthException("Reopen this order from History", HttpStatus.BAD_REQUEST);
            case BOOKING_MOVED -> returnMovedBooking(action, branchId, actorId);
            case BOOKING_NO_ACTION -> returnBooking(action, branchId, otherTableId, actorId);
            default -> throw new AuthException("There's nothing to return here", HttpStatus.BAD_REQUEST);
        }
        action.setReturnedAt(now());
        action.setReturnedBy(actorId);
        actionRepository.save(action);
        layoutNotifier.notifyBranchChanged(restaurantId, branchId);
    }

    // Never through a status change event: this is the guests who were here, not a waiting booking being seated.
    private void returnGuests(SpecialCaseAction action, UUID branchId, UUID otherTableId, UUID actorId) {
        UUID targetId = otherTableId == null ? action.getTableId() : otherTableId;
        RestaurantTable table = lockTable(branchId, targetId);
        if (table.getStatus() != TableStatus.AVAILABLE || table.getMergedInto() != null) {
            throw new AuthException(table.getTableNumber() + " is taken now. Pick another table.", HttpStatus.CONFLICT);
        }
        Map<UUID, Integer> guests = guestsOf(action);
        OffsetDateTime seatedAt = action.getSeatedAt() == null ? now() : action.getSeatedAt();
        if (targetId.equals(action.getTableId())) {
            for (Map.Entry<UUID, Integer> entry : guests.entrySet()) {
                tableRepository.findByIdAndBranch_Id(entry.getKey(), branchId)
                        .filter(other -> other.getStatus() == TableStatus.AVAILABLE)
                        .ifPresent(other -> seat(other, entry.getValue(), seatedAt, actorId));
            }
        } else {
            seat(table, guests.values().stream().mapToInt(Integer::intValue).sum(), seatedAt, actorId);
        }
    }

    private void seat(RestaurantTable table, int guests, OffsetDateTime seatedAt, UUID actorId) {
        table.setStatus(TableStatus.OCCUPIED);
        table.setGuestCount(Math.max(guests, 1));
        table.setSeatedAt(seatedAt);
        table.setStillHereAt(now());
        table.setUpdatedBy(actorId);
        tableRepository.save(table);
    }

    private void returnMovedBooking(SpecialCaseAction action, UUID branchId, UUID actorId) {
        Reservation booking = reservationRepository.findById(action.getReservationId())
                .orElseThrow(() -> new AuthException("This booking no longer exists", HttpStatus.NOT_FOUND));
        release(booking, actorId);
        RestaurantTable table = tableRepository.findByIdAndBranch_Id(action.getTableId(), branchId).orElseThrow(SpecialCaseService::noTable);
        assignmentService.putBack(booking, table, actorId);
        reservationRepository.save(booking);
        lifecycle.note(booking, "Back on " + table.getTableNumber(), actorId);
    }

    // Its table, or the one chosen when that's taken now (user, 2026-11-01). Its time starts again from now if it had run out.
    private void returnBooking(SpecialCaseAction action, UUID branchId, UUID otherTableId, UUID actorId) {
        Reservation booking = reservationRepository.findById(action.getReservationId())
                .orElseThrow(() -> new AuthException("This booking no longer exists", HttpStatus.NOT_FOUND));
        if (otherTableId == null && !tableFree(action)) {
            throw new AuthException(action.getTableNumber() + " isn't free now. Pick another table.", HttpStatus.CONFLICT);
        }
        if (!booking.getReservationEnd().isAfter(now())) {
            booking.setReservationEnd(now().plus(java.time.Duration.between(booking.getReservationStart(), booking.getReservationEnd())));
        }
        lifecycle.reopen(booking, "Put back from No action taken", ReservationActor.system(actorId));
        if (otherTableId != null) {
            RestaurantTable table = lockTable(branchId, otherTableId);
            if (table.getStatus() != TableStatus.AVAILABLE) {
                throw new AuthException(table.getTableNumber() + " is taken now. Pick another table.", HttpStatus.CONFLICT);
            }
            assignmentService.putBack(booking, table, actorId);
            lifecycle.note(booking, "Put back on " + table.getTableNumber(), actorId);
        }
        booking.setUpdatedBy(actorId);
        reservationRepository.save(booking);
        reserve(booking, actorId);
    }

    // ---- What the system does when nobody acts ----

    @Transactional(readOnly = true)
    public List<UUID> tablesWithGuests() {
        return tableRepository.findRootIdsWithGuests();
    }

    @Transactional
    public void handleTable(UUID tableId, OffsetDateTime now) {
        RestaurantTable table = tableRepository.findById(tableId).orElse(null);
        if (table == null || table.getMergedInto() != null) {
            return;
        }
        Settings settings = settingsOf(table.getRestaurant().getId());
        List<RestaurantTable> children = tableRepository.findAllByMergedInto_IdOrderByTableNumberAsc(tableId);
        List<UUID> ids = new ArrayList<>(List.of(tableId));
        children.forEach(child -> ids.add(child.getId()));
        List<Order> orders = orderRepository.findAllByRestaurantTable_IdInAndStatusInOrderByOpenedAtAsc(ids, OPEN);
        if (table.getStatus() != TableStatus.OCCUPIED && orders.isEmpty()) {
            return;
        }
        // An order with no dishes is just guests sitting; it owes nothing and hasn't been paid.
        List<Order> empty = orders.stream().filter(order -> order.getTotal() == null || order.getTotal().signum() == 0).toList();
        List<Order> withDishes = orders.stream().filter(order -> !empty.contains(order)).toList();
        List<Order> unpaid = withDishes.stream().filter(order -> orderSupport.balanceDue(order).signum() > 0).toList();
        boolean paid = unpaid.isEmpty() && (!withDishes.isEmpty() || isPaidSinceSeated(table));
        int limit = SpecialCaseLimits.minutes(settings, !unpaid.isEmpty(), paid);
        OffsetDateTime lastAction = lastAction(table, children, orders);
        if (limit <= 0 || lastAction == null || lastAction.plusMinutes(limit).isAfter(now)) {
            return;
        }
        String guestsByTable = guestsByTable(table, children);
        OffsetDateTime seatedAt = table.getSeatedAt();
        int guests = totalGuests(table, children);
        BigDecimal owed = unpaid.stream().map(orderSupport::balanceDue).reduce(BigDecimal.ZERO, BigDecimal::add);
        String orderIds = unpaid.stream().map(order -> order.getId().toString()).collect(Collectors.joining(","));
        String firstOrder = unpaid.isEmpty() ? null : unpaid.get(0).getOrderNumber();
        String currency = unpaid.isEmpty() ? "" : unpaid.get(0).getCurrency();
        unpaid.forEach(order -> orderWorkflowService.closeNoAction(order));
        withDishes.stream().filter(order -> !unpaid.contains(order)).forEach(order -> orderWorkflowService.closePaidNoAction(order));
        empty.forEach(order -> orderWorkflowService.cancelEmptyNoAction(order));
        free(table, children);

        SpecialCaseAction action = newAction(table.getRestaurant().getId(), table.getBranch().getId(),
                unpaid.isEmpty() ? SpecialCaseAction.Kind.TABLE_FREED : SpecialCaseAction.Kind.ORDER_CLOSED);
        action.setTableId(tableId);
        action.setTableNumber(table.getTableNumber());
        action.setGuestsByTable(guestsByTable);
        action.setSeatedAt(seatedAt);
        action.setOrderIds(orderIds.isEmpty() ? null : orderIds);
        action.setGuests(guests > 0 ? guests : null);
        if (unpaid.isEmpty()) {
            action.setSummary(table.getTableNumber() + " set free");
            action.setDetail(guests > 0 ? guests + (guests == 1 ? " guest" : " guests") + " were seated" : "Guests were seated");
        } else {
            action.setSummary(firstOrder + " closed as unpaid");
            action.setDetail(table.getTableNumber() + " set free · " + money(owed, currency) + " to pay");
        }
        actionRepository.save(action);
        tell(settings, table.getBranch().getName(), "No action taken at " + table.getTableNumber(), action.getSummary() + " · " + action.getDetail() + ".", "TABLE", tableId);
        layoutNotifier.notifyBranchChanged(table.getRestaurant().getId(), table.getBranch().getId());
    }

    private static String money(BigDecimal amount, String currency) {
        String symbol = switch (currency) {
            case "EUR" -> "€";
            case "USD" -> "$";
            case "GBP" -> "£";
            default -> currency + " ";
        };
        return symbol + amount.toPlainString();
    }

    private boolean isPaidSinceSeated(RestaurantTable table) {
        if (table.getSeatedAt() == null) {
            return false;
        }
        return orderRepository.lastPaidCloseByTable(List.of(table.getId())).stream()
                .map(row -> (OffsetDateTime) row[1])
                .anyMatch(closedAt -> closedAt != null && !closedAt.isBefore(table.getSeatedAt()));
    }

    private OffsetDateTime lastAction(RestaurantTable table, List<RestaurantTable> children, List<Order> orders) {
        OffsetDateTime last = SpecialCaseLimits.latest(table.getUpdatedAt(), table.getSeatedAt(), table.getStillHereAt());
        for (RestaurantTable child : children) {
            last = SpecialCaseLimits.latest(last, child.getUpdatedAt(), child.getStillHereAt());
        }
        if (!orders.isEmpty()) {
            for (Order order : orders) {
                last = SpecialCaseLimits.latest(last, order.getUpdatedAt());
            }
            List<UUID> orderIds = orders.stream().map(Order::getId).toList();
            for (Object[] row : orderRepository.lastLineChange(orderIds)) {
                last = SpecialCaseLimits.latest(last, (OffsetDateTime) row[1]);
            }
        }
        return last;
    }

    private void free(RestaurantTable table, List<RestaurantTable> children) {
        TableStatus previous = table.getStatus();
        children.stream().filter(child -> child.getStatus() == TableStatus.OCCUPIED).forEach(this::clear);
        tableRepository.saveAll(children);
        if (previous == TableStatus.OCCUPIED) {
            clear(table);
            tableRepository.save(table);
            events.publishEvent(new TableStatusChangedEvent(table.getRestaurant().getId(), table.getBranch().getId(), table.getId(), previous, TableStatus.AVAILABLE, null));
        }
    }

    private void clear(RestaurantTable table) {
        table.setStatus(TableStatus.AVAILABLE);
        table.setGuestCount(null);
        table.setSeatedAt(null);
        table.setMoreComing(false);
        table.setMoreTimeUntil(null);
        table.setUpdatedBy(null);
    }

    private static String guestsByTable(RestaurantTable table, List<RestaurantTable> children) {
        List<RestaurantTable> all = new ArrayList<>(List.of(table));
        all.addAll(children);
        return all.stream().filter(one -> one.getGuestCount() != null)
                .map(one -> one.getId() + "=" + one.getGuestCount())
                .collect(Collectors.joining(";"));
    }

    private static int totalGuests(RestaurantTable table, List<RestaurantTable> children) {
        int guests = table.getGuestCount() == null ? 0 : table.getGuestCount();
        for (RestaurantTable child : children) {
            guests += child.getGuestCount() == null ? 0 : child.getGuestCount();
        }
        return guests;
    }

    private static Map<UUID, Integer> guestsOf(SpecialCaseAction action) {
        Map<UUID, Integer> guests = new java.util.LinkedHashMap<>();
        if (action.getGuestsByTable() != null && !action.getGuestsByTable().isBlank()) {
            for (String part : action.getGuestsByTable().split(";")) {
                String[] pair = part.split("=");
                guests.put(UUID.fromString(pair[0]), Integer.parseInt(pair[1]));
            }
        }
        if (guests.isEmpty()) {
            guests.put(action.getTableId(), 1);
        }
        return guests;
    }

    @Transactional(readOnly = true)
    public List<UUID> bookingsDueOnTakenTables(OffsetDateTime now) {
        return assignmentRepository.findDueOnTakenTables(EXPECTED, now, now.minusDays(1));
    }

    // At the booking's time, if nobody acted: on to a free table when one fits, and the people chosen are told either way.
    @Transactional
    public void handleBookingDue(UUID reservationId, OffsetDateTime now) {
        Reservation booking = reservationRepository.findById(reservationId).orElse(null);
        if (booking == null || !EXPECTED.contains(booking.getStatus())) {
            return;
        }
        Settings settings = settingsOf(booking.getRestaurant().getId());
        RestaurantTable takenTable = takenTable(booking, settings, now);
        if (takenTable == null || moreTimeRunning(takenTable, now)) {
            return;
        }
        // Once per booking: staff who put it back meant it.
        if (actionRepository.existsByReservationIdAndKindIn(reservationId,
                EnumSet.of(SpecialCaseAction.Kind.BOOKING_MOVED, SpecialCaseAction.Kind.BOOKING_NOT_HANDLED))) {
            return;
        }
        String who = bookingName(booking);
        String to = settings.isSpecialCaseAutoMove() ? assignmentService.moveToFreeTable(booking, id -> sameFloor(id, takenTable)) : null;
        SpecialCaseAction action = newAction(booking.getRestaurant().getId(), booking.getBranch().getId(),
                to == null ? SpecialCaseAction.Kind.BOOKING_NOT_HANDLED : SpecialCaseAction.Kind.BOOKING_MOVED);
        action.setTableId(takenTable.getId());
        action.setTableNumber(takenTable.getTableNumber());
        action.setReservationId(reservationId);
        action.setMovedTo(to);
        action.setGuests(booking.getPartySize());
        if (to == null) {
            action.setSummary(who + " can't sit at " + takenTable.getTableNumber());
            action.setDetail("Nobody handled it and no free table fits");
            lifecycle.note(booking, "Nobody handled it: the guests at " + takenTable.getTableNumber() + " are still there and no free table fits", null);
        } else {
            action.setSummary(who + " moved");
            action.setDetail(takenTable.getTableNumber() + " → " + to + " · nobody handled it");
            reservationRepository.save(booking);
            lifecycle.note(booking, "Nobody handled it, moved automatically from " + takenTable.getTableNumber() + " to " + to, null);
            reserve(booking, null);
        }
        actionRepository.save(action);
        tell(settings, booking.getBranch().getName(), "Next booking at " + takenTable.getTableNumber(), action.getSummary() + " · " + action.getDetail() + ".", "RESERVATION", reservationId);
        layoutNotifier.notifyBranchChanged(booking.getRestaurant().getId(), booking.getBranch().getId());
    }

    // A table with guests who sat down before the warning began, in the way of this booking; null when there's none.
    private RestaurantTable takenTable(Reservation booking, Settings settings, OffsetDateTime now) {
        OffsetDateTime warningFrom = booking.getReservationStart().minusMinutes(settings.getNextBookingWarningMinutes());
        for (ReservationTableAssignment assignment : booking.getTableAssignments()) {
            RestaurantTable table = assignment.getRestaurantTable();
            if (table == null) {
                continue;
            }
            boolean guestsHere = table.getStatus() == TableStatus.OCCUPIED
                    || !orderRepository.findAllByRestaurantTable_IdInAndStatusInOrderByOpenedAtAsc(List.of(table.getId()), OPEN).isEmpty();
            if (guestsHere && (table.getSeatedAt() == null || table.getSeatedAt().isBefore(warningFrom))) {
                return table;
            }
        }
        return null;
    }

    private boolean sameFloor(UUID tableId, RestaurantTable table) {
        return tableRepository.findById(tableId).map(other -> Objects.equals(other.getFloor(), table.getFloor())).orElse(false);
    }

    private static boolean moreTimeRunning(RestaurantTable table, OffsetDateTime now) {
        return table.getMoreTimeUntil() != null && table.getMoreTimeUntil().isAfter(now);
    }

    // The no-show job leaves these to the special cases: nobody can say the guests didn't come.
    @Transactional(readOnly = true)
    public boolean leftToSpecialCases(Reservation booking, OffsetDateTime now) {
        Settings settings = settingsOf(booking.getRestaurant().getId());
        return settings.isSpecialCaseBookingNoAction()
                && (wasMovedAutomatically(booking) || takenTable(booking, settings, now) != null);
    }

    private boolean wasMovedAutomatically(Reservation booking) {
        return actionRepository.existsByReservationIdAndReturnedAtIsNullAndKindIn(booking.getId(), EnumSet.of(SpecialCaseAction.Kind.BOOKING_MOVED));
    }

    @Transactional(readOnly = true)
    public List<UUID> bookingsToCheck(OffsetDateTime now) {
        return reservationRepository.findIdsWithStatusesStartedBetween(EXPECTED, now.minusDays(2), now);
    }

    // Confirmed, once its hold is over (moved or on a taken table); guests who came and were never seated, when its time ends.
    @Transactional
    public void handleBookingNeverSeated(UUID reservationId, OffsetDateTime now) {
        Reservation booking = reservationRepository.findById(reservationId).orElse(null);
        if (booking == null || !EXPECTED.contains(booking.getStatus())) {
            return;
        }
        Settings settings = settingsOf(booking.getRestaurant().getId());
        if (!settings.isSpecialCaseBookingNoAction()) {
            return;
        }
        boolean over;
        RestaurantTable takenTable = takenTable(booking, settings, now);
        if (booking.getStatus() == ReservationStatus.CHECKED_IN) {
            over = !booking.getReservationEnd().isAfter(now);
        } else {
            OffsetDateTime holdUntil = reservationPolicy.holdUntil(booking);
            over = holdUntil != null && !holdUntil.isAfter(now) && (takenTable != null || wasMovedAutomatically(booking));
        }
        if (!over || (takenTable != null && moreTimeRunning(takenTable, now))) {
            return;
        }
        String where = booking.getTableAssignments().stream().map(a -> a.getRestaurantTable().getTableNumber()).collect(Collectors.joining("-"));
        UUID firstTable = booking.getTableAssignments().stream().map(a -> a.getRestaurantTable().getId()).findFirst().orElse(null);
        lifecycle.noAction(booking, "No action taken: nobody seated the guests", ReservationActor.system(null));
        reservationRepository.save(booking);
        release(booking, null);
        SpecialCaseAction action = newAction(booking.getRestaurant().getId(), booking.getBranch().getId(), SpecialCaseAction.Kind.BOOKING_NO_ACTION);
        action.setReservationId(reservationId);
        action.setTableId(firstTable);
        action.setTableNumber(where.isEmpty() ? null : where);
        action.setGuests(booking.getPartySize());
        action.setSummary(bookingName(booking) + " closed");
        action.setDetail("Nobody seated them" + (where.isEmpty() ? "" : " · " + where));
        actionRepository.save(action);
        tell(settings, booking.getBranch().getName(), "No action taken on a booking", action.getSummary() + " · " + action.getDetail() + ".", "RESERVATION", reservationId);
        layoutNotifier.notifyBranchChanged(booking.getRestaurant().getId(), booking.getBranch().getId());
    }

    @Transactional(readOnly = true)
    public List<UUID> reopenedOrders() {
        return orderRepository.findIdsOpenForPaymentOnly();
    }

    // Reopened for its payment and left: it goes back to unpaid, nothing stays open waiting.
    @Transactional
    public void handleReopenedOrder(UUID orderId, OffsetDateTime now) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.OPEN || order.getUnpaidAt() == null) {
            return;
        }
        int limit = settingsOf(order.getRestaurant().getId()).getSpecialCaseOrderMinutes();
        if (limit > 0 && !order.getUpdatedAt().plusMinutes(limit).isAfter(now) && order.getPaymentStatus() != OrderPaymentStatus.PAID) {
            orderWorkflowService.backToUnpaid(order);
        }
    }

    // ---- Helpers ----

    private java.util.Optional<Reservation> nextBooking(RestaurantTable table) {
        return assignmentRepository.findUpcomingByTableIds(List.of(table.getId()), EXPECTED, now()).stream()
                .map(ReservationTableAssignment::getReservation)
                .findFirst();
    }

    // The tables a moved booking is on wait for it.
    private void reserve(Reservation booking, UUID actorId) {
        booking.getTableAssignments().stream().map(ReservationTableAssignment::getRestaurantTable)
                .filter(table -> table.getStatus() == TableStatus.AVAILABLE)
                .forEach(table -> {
                    table.setStatus(TableStatus.RESERVED);
                    table.setUpdatedBy(actorId);
                    tableRepository.save(table);
                });
    }

    private void release(Reservation booking, UUID actorId) {
        booking.getTableAssignments().stream().map(ReservationTableAssignment::getRestaurantTable)
                .filter(table -> table.getStatus() == TableStatus.RESERVED)
                .forEach(table -> {
                    table.setStatus(TableStatus.AVAILABLE);
                    table.setUpdatedBy(actorId);
                    tableRepository.save(table);
                });
    }

    private static String bookingName(Reservation booking) {
        String name = booking.getContactName();
        return name == null || name.isBlank() ? "A booking" : name + "'s booking";
    }

    private SpecialCaseAction newAction(UUID restaurantId, UUID branchId, SpecialCaseAction.Kind kind) {
        SpecialCaseAction action = new SpecialCaseAction();
        action.setRestaurantId(restaurantId);
        action.setBranchId(branchId);
        action.setKind(kind);
        return action;
    }

    private void tell(Settings settings, String branchName, String subject, String body, String referenceType, UUID referenceId) {
        Set<String> roles = Arrays.stream(settings.getSpecialCaseAlertRoles().split(","))
                .map(String::trim).filter(code -> !code.isEmpty()).collect(Collectors.toSet());
        if (roles.isEmpty()) {
            return;
        }
        UUID restaurantId = settings.getRestaurant().getId();
        // No branch on the notification: an owner working in another branch must see it too, so the body names it.
        for (UUID recipient : userRepository.findActiveStaffIdsWithRoles(restaurantId, null, roles)) {
            notifications.publishCustom(NotificationTopic.TABLE, NotificationMutationType.STATE_CHANGE, NotificationChannel.IN_APP,
                    NotificationPriority.HIGH, EVENT_CODE, restaurantId, null, recipient, referenceType, referenceId, null, subject,
                    body + " (" + branchName + ")");
        }
    }

    private Settings settingsOf(UUID restaurantId) {
        return settingsRepository.findByRestaurant_Id(restaurantId).orElseGet(() -> {
            Settings defaults = new Settings();
            defaults.setSpecialCasePaidMinutes(0);
            defaults.setSpecialCaseSeatedMinutes(0);
            defaults.setSpecialCaseOrderMinutes(0);
            defaults.setSpecialCaseAutoMove(false);
            defaults.setSpecialCaseBookingNoAction(false);
            defaults.setSpecialCaseAlertRoles("");
            return defaults;
        });
    }

    private RestaurantTable lockTable(UUID branchId, UUID tableId) {
        return tableRepository.findByIdAndBranchIdForUpdate(tableId, branchId).orElseThrow(SpecialCaseService::noTable);
    }

    private static AuthException noTable() {
        return new AuthException("Table not found", HttpStatus.NOT_FOUND);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
