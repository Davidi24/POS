package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.event.ReservationStatusChangedEvent;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.repository.ReservationTableAssignmentRepository;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.enums.TableStatus;
import pos.pos.tables.realtime.TableLayoutChangeNotifier;
import pos.pos.tables.repository.RestaurantTableRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.UUID;

// A booking that ended (cancelled, a no-show, expired) lets go of its reserved tables, unless another booking on one is near.
@Component
@RequiredArgsConstructor
public class ReservationTableRelease {

    private static final EnumSet<ReservationStatus> ENDED = EnumSet.of(ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW, ReservationStatus.EXPIRED);
    private static final EnumSet<ReservationStatus> EXPECTED = EnumSet.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN);

    private final ReservationRepository reservationRepository;
    private final ReservationTableAssignmentRepository assignmentRepository;
    private final RestaurantTableRepository tableRepository;
    private final ReservationPolicy reservationPolicy;
    private final TableLayoutChangeNotifier layoutNotifier;

    @EventListener
    public void onStatusChanged(ReservationStatusChangedEvent event) {
        if (!ENDED.contains(event.newStatus())) {
            return;
        }
        Reservation booking = reservationRepository.findById(event.reservationId()).orElse(null);
        if (booking == null) {
            return;
        }
        boolean released = false;
        for (ReservationTableAssignment assignment : booking.getTableAssignments()) {
            RestaurantTable table = assignment.getRestaurantTable();
            if (table != null && table.getStatus() == TableStatus.RESERVED && !anotherBookingNear(table.getId(), booking)) {
                table.setStatus(TableStatus.AVAILABLE);
                table.setUpdatedBy(event.actorId());
                tableRepository.save(table);
                released = true;
            }
        }
        if (released) {
            layoutNotifier.notifyBranchChanged(booking.getRestaurant().getId(), booking.getBranch().getId());
        }
    }

    // A booking moved away: the tables it left wait for nobody any more.
    public void afterMove(java.util.Collection<RestaurantTable> left, Reservation booking, UUID actorId) {
        left.stream()
                .filter(table -> table.getStatus() == TableStatus.RESERVED && !anotherBookingNear(table.getId(), booking))
                .forEach(table -> {
                    table.setStatus(TableStatus.AVAILABLE);
                    table.setUpdatedBy(actorId);
                    tableRepository.save(table);
                });
    }

    private boolean anotherBookingNear(UUID tableId, Reservation booking) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime horizon = now.plusMinutes(reservationPolicy.values(booking.getRestaurant()).checkInOpensMinutes());
        return assignmentRepository.findByTableAndReservationStatus(tableId, EXPECTED).stream()
                .map(ReservationTableAssignment::getReservation)
                .anyMatch(other -> !other.getId().equals(booking.getId())
                        && other.getReservationStart().isBefore(horizon)
                        && other.getReservationEnd().isAfter(now));
    }
}
