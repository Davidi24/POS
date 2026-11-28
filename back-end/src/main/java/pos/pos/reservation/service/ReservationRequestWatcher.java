package pos.pos.reservation.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.event.ReservationStatusChangedEvent;
import pos.pos.reservation.repository.ReservationRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;

// A booking with a table was cancelled or didn't show: if requests wait around that time, staff are told a table
// is free so they can decide. Requests are never confirmed automatically.
@Component
@RequiredArgsConstructor
public class ReservationRequestWatcher {

    private static final Logger logger = LoggerFactory.getLogger(ReservationRequestWatcher.class);
    private static final EnumSet<ReservationStatus> HELD_A_TABLE = EnumSet.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN);

    private final EntityManager entityManager;
    private final ReservationRepository reservationRepository;
    private final ReservationNotifications reservationNotifications;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingEnded(ReservationStatusChangedEvent event) {
        boolean freed = (event.newStatus() == ReservationStatus.CANCELLED || event.newStatus() == ReservationStatus.NO_SHOW)
                && event.previousStatus() != null && HELD_A_TABLE.contains(event.previousStatus());
        if (!freed) {
            return;
        }
        try {
            Reservation booking = entityManager.find(Reservation.class, event.reservationId());
            if (booking == null || booking.getTableAssignments().isEmpty() || booking.getReservationEnd() == null) {
                return;
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            if (booking.getReservationEnd().isBefore(now)) {
                return;
            }
            // Requests starting within the freed booking's time.
            OffsetDateTime from = booking.getReservationStart().isAfter(now) ? booking.getReservationStart().minusHours(1) : now;
            int waiting = reservationRepository.findAllByBranch_IdAndStatusAndReservationStartBetween(
                    booking.getBranch().getId(), ReservationStatus.PENDING, from, booking.getReservationEnd()).size();
            if (waiting > 0) {
                reservationNotifications.tableFreed(booking, waiting);
            }
        } catch (RuntimeException error) {
            logger.warn("Could not check waiting requests after booking {} ended", event.reservationId(), error);
        }
    }
}
