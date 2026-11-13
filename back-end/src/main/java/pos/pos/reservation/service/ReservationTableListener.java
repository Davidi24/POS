package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.repository.ReservationTableAssignmentRepository;
import pos.pos.tables.enums.TableStatus;
import pos.pos.tables.event.TableStatusChangedEvent;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

// Keeps bookings in step with the floor plan, in the same transaction as the table change:
//  - staff seat guests at a table that a checked-in booking is waiting for → the booking is Seated;
//  - staff clear a table a seated booking sits at (and none of its other tables are still occupied) → the visit is
//    Completed.
@Component
@RequiredArgsConstructor
public class ReservationTableListener {

    static final String TABLE_CLEARED = "Table cleared";

    private final ReservationTableAssignmentRepository reservationTableAssignmentRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationLifecycleService reservationLifecycleService;
    private final ReservationPolicy reservationPolicy;

    @EventListener
    public void onTableStatusChanged(TableStatusChangedEvent event) {
        ReservationActor actor = ReservationActor.system(event.actorId());
        if (event.newStatus() == TableStatus.OCCUPIED) {
            seatWaitingBooking(event, actor);
        } else if (event.previousStatus() == TableStatus.OCCUPIED) {
            completeSeatedBookings(event, actor);
        }
    }

    private void seatWaitingBooking(TableStatusChangedEvent event, ReservationActor actor) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        reservationTableAssignmentRepository.findByTableAndReservationStatus(event.tableId(), EnumSet.of(ReservationStatus.CHECKED_IN))
                .stream()
                .map(ReservationTableAssignment::getReservation)
                .filter(reservation -> isHappeningNow(reservation, now))
                .findFirst()
                .ifPresent(reservation -> {
                    reservationLifecycleService.seat(reservation, "Seated from the floor plan", actor);
                    reservationRepository.save(reservation);
                });
    }

    private void completeSeatedBookings(TableStatusChangedEvent event, ReservationActor actor) {
        List<Reservation> seated = reservationTableAssignmentRepository
                .findByTableAndReservationStatus(event.tableId(), EnumSet.of(ReservationStatus.SEATED))
                .stream()
                .map(ReservationTableAssignment::getReservation)
                .distinct()
                .toList();
        for (Reservation reservation : seated) {
            boolean stillSitting = reservation.getTableAssignments().stream()
                    .map(ReservationTableAssignment::getRestaurantTable)
                    .filter(table -> table != null && !Objects.equals(table.getId(), event.tableId()))
                    .anyMatch(table -> table.getStatus() == TableStatus.OCCUPIED);
            if (!stillSitting) {
                reservationLifecycleService.complete(reservation, TABLE_CLEARED, actor);
                reservationRepository.save(reservation);
            }
        }
    }

    private boolean isHappeningNow(Reservation reservation, OffsetDateTime now) {
        int opensBefore = reservationPolicy.values(reservation.getRestaurant()).checkInOpensMinutes();
        return !now.isBefore(reservation.getReservationStart().minusMinutes(opensBefore))
                && now.isBefore(reservation.getReservationEnd());
    }
}
