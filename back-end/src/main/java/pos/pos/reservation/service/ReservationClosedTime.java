package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.hours.ClosedTimeBookings;
import pos.pos.hours.OpeningHoursService;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Bookings that fall when the branch is closed by its opening hours (user, 2026-11-21): requests, confirmed and checked-in
 * bookings still to come whose start is in closed time. Listed after the hours change, marked in Reservations, and the people
 * told about bookings hear of new ones.
 */
@Component
@RequiredArgsConstructor
public class ReservationClosedTime implements ClosedTimeBookings {

    private static final Set<ReservationStatus> UPCOMING = EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN);
    private static final int DAYS_AHEAD = 400;

    private final ReservationRepository reservations;
    private final OpeningHoursService openingHours;
    private final ReservationNotifications notifications;

    @Override
    @Transactional(readOnly = true)
    public List<Booking> inClosedTime(UUID branchId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Reservation> upcoming = reservations.findAllByBranch_IdAndReservationStartBetweenOrderByReservationStartAsc(branchId, now, now.plusDays(DAYS_AHEAD))
                .stream().filter(r -> UPCOMING.contains(r.getStatus()) && r.getReservationStart() != null).toList();
        if (upcoming.isEmpty()) return List.of();
        Set<OffsetDateTime> closed = openingHours.closedAt(branchId, upcoming.stream().map(Reservation::getReservationStart).toList());
        if (closed.isEmpty()) return List.of();
        return upcoming.stream().filter(r -> closed.contains(r.getReservationStart())).map(ReservationClosedTime::booking).toList();
    }

    @Override
    @Transactional
    public void alert(UUID branchId, List<Booking> bookings, UUID actorId) {
        if (bookings == null || bookings.isEmpty()) return;
        notifications.closedTime(reservations.findAllByIdInOrderByReservationStartAsc(bookings.stream().map(Booking::id).toList()), actorId);
    }

    private static Booking booking(Reservation r) {
        List<String> tables = r.getTableAssignments().stream()
                .map(ReservationTableAssignment::getRestaurantTable)
                .filter(Objects::nonNull)
                .map(table -> table.getTableNumber())
                .filter(Objects::nonNull)
                .toList();
        return new Booking(r.getId(), r.getReservationCode(), r.getReservationStart(), r.getReservationEnd(), r.getPartySize(),
                r.getContactName(), r.getContactPhone(), r.getStatus().name(), tables);
    }
}
