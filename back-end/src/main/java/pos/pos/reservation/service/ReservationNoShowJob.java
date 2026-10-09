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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Every minute:
//  - a confirmed booking whose table hold has run out (booking time + the hold setting, or later if staff held it
//    longer) becomes a no-show;
//  - a request nobody answered before the booking time expires. That's not the guest's fault, so it's not a no-show.
// Checked-in and seated guests are never touched: a visit only ends when staff end it.
@Component
@RequiredArgsConstructor
public class ReservationNoShowJob {

    static final String EXPIRED_REASON = "Request expired: nobody answered it before the booking time";
    private static final DateTimeFormatter CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Logger logger = LoggerFactory.getLogger(ReservationNoShowJob.class);

    private final ReservationRepository reservationRepository;
    private final ReservationLifecycleService reservationLifecycleService;
    private final ReservationPolicy reservationPolicy;
    private final ReservationSupport reservationSupport;
    private final ReservationNotifications reservationNotifications;

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    @Transactional
    public void markOverdueReservations() {
        markOverdueReservations(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public int markOverdueReservations(OffsetDateTime now) {
        ReservationActor system = ReservationActor.system(null);
        List<Reservation> noShows = new ArrayList<>();
        for (Reservation reservation : reservationRepository.findConfirmedPastStart(ReservationStatus.CONFIRMED, now, ReservationStatus.NO_SHOW)) {
            OffsetDateTime holdUntil = reservationPolicy.holdUntil(reservation);
            if (holdUntil == null || holdUntil.isAfter(now)) {
                continue;
            }
            String heldUntil = CLOCK_TIME.format(holdUntil.atZoneSameInstant(reservationSupport.restaurantZone(reservation.getRestaurant())));
            reservationLifecycleService.noShow(reservation, "Automatically marked no-show: the table was held until " + heldUntil, system);
            noShows.add(reservation);
        }
        List<Reservation> expired = new ArrayList<>(reservationRepository.findUnansweredRequests(ReservationStatus.PENDING, now));
        for (Reservation reservation : expired) {
            reservationLifecycleService.expire(reservation, EXPIRED_REASON, system);
        }
        List<Reservation> changed = new ArrayList<>(noShows);
        changed.addAll(expired);
        if (!changed.isEmpty()) {
            reservationRepository.saveAll(changed);
            if (!noShows.isEmpty()) {
                reservationNotifications.noShows(noShows);
            }
            if (!expired.isEmpty()) {
                reservationNotifications.expired(expired);
            }
            logger.info("Marked {} no-show(s) and expired {} request(s)", noShows.size(), expired.size());
        }
        return changed.size();
    }
}
