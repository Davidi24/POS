package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;

// Marks bookings as no-show when the guest hasn't checked in within the grace time after the start.
@Component
@RequiredArgsConstructor
public class ReservationNoShowJob {

    public static final Duration GRACE = Duration.ofMinutes(30);
    static final String REASON = "Automatically marked no-show: guest did not arrive within 30 minutes";
    static final String NEVER_SEATED_REASON = "Automatically marked no-show: checked in but never seated before the reservation ended";

    private static final Logger logger = LoggerFactory.getLogger(ReservationNoShowJob.class);
    private static final EnumSet<ReservationStatus> WAITING = EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

    private final ReservationRepository reservationRepository;
    private final ReservationLifecycleService reservationLifecycleService;
    private final ReservationSupport reservationSupport;
    private final ReservationNotifications reservationNotifications;

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    @Transactional
    public void markOverdueReservations() {
        markOverdueReservations(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public int markOverdueReservations(OffsetDateTime now) {
        List<Reservation> overdue = new java.util.ArrayList<>(
                reservationRepository.findOverdueForNoShow(WAITING, now.minus(GRACE), ReservationStatus.NO_SHOW));
        for (Reservation reservation : overdue) {
            reservationLifecycleService.transitionReservation(reservation, ReservationStatus.NO_SHOW, REASON, null);
        }
        // Checked in but the table never got them before the booking ended: they left, so it doesn't stay open forever.
        List<Reservation> neverSeated = reservationRepository.findCheckedInPastEnd(ReservationStatus.CHECKED_IN, now, ReservationStatus.NO_SHOW);
        for (Reservation reservation : neverSeated) {
            reservation.setStatus(ReservationStatus.NO_SHOW);
            // A no-show can't carry a check-in time; the check-in stays visible in the status history.
            reservation.setCheckedInAt(null);
            reservation.setNoShowAt(now);
            reservationSupport.addStatusHistory(reservation, ReservationStatus.CHECKED_IN, ReservationStatus.NO_SHOW, NEVER_SEATED_REASON, null);
            reservationLifecycleService.announceStatusChange(reservation, ReservationStatus.CHECKED_IN, null);
        }
        overdue.addAll(neverSeated);
        if (!overdue.isEmpty()) {
            reservationRepository.saveAll(overdue);
            reservationNotifications.noShows(overdue);
            logger.info("Marked {} overdue reservation(s) as no-show", overdue.size());
        }
        return overdue.size();
    }
}
