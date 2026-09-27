package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationEvent;
import pos.pos.reservation.entity.ReservationReminder;
import pos.pos.reservation.enums.ReservationEventType;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationEventRepository;
import pos.pos.reservation.repository.ReservationReminderRepository;
import pos.pos.reservation.repository.ReservationRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Every minute, the attendance reminders (Admin Hub → Settings → Reservations):
//  - the day before at the reminder time (15:00), managers get what's left for tomorrow: bookings not confirmed
//    yet, requests waiting, big groups to call;
//  - when a confirmed booking is close (2 h before) and the guest hasn't confirmed, staff are told to call.
// No reply never cancels anything.
@Component
@RequiredArgsConstructor
public class ReservationReminderJob {

    static final String DAY_BEFORE = "DAY_BEFORE";
    private static final Logger logger = LoggerFactory.getLogger(ReservationReminderJob.class);

    private final ReservationRepository reservationRepository;
    private final ReservationReminderRepository reservationReminderRepository;
    private final ReservationEventRepository reservationEventRepository;
    private final ReservationPolicy reservationPolicy;
    private final ReservationSupport reservationSupport;
    private final ReservationNotifications reservationNotifications;

    @Scheduled(fixedDelay = 60_000, initialDelay = 40_000)
    @Transactional
    public void sendReminders() {
        sendReminders(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public void sendReminders(OffsetDateTime now) {
        callReminders(now);
        dayBeforeReminders(now);
    }

    private void callReminders(OffsetDateTime now) {
        for (Reservation booking : reservationRepository.findAwaitingAttendance(ReservationStatus.CONFIRMED, now, now.plusDays(1))) {
            int callBefore = reservationPolicy.values(booking.getRestaurant()).attendanceCallMinutes();
            if (Duration.between(now, booking.getReservationStart()).toMinutes() > callBefore
                    || reservationEventRepository.existsByReservation_IdAndType(booking.getId(), ReservationEventType.CALL_REMINDED)) {
                continue;
            }
            reservationNotifications.callGuest(booking);
            ReservationEvent event = new ReservationEvent();
            event.setReservation(booking);
            event.setType(ReservationEventType.CALL_REMINDED);
            event.setDetail("Staff asked to call: attendance not confirmed");
            reservationEventRepository.save(event);
        }
    }

    private void dayBeforeReminders(OffsetDateTime now) {
        List<Reservation> soon = reservationRepository.findAllByStatusInAndReservationStartBetween(
                EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED), now, now.plusHours(56));
        Map<UUID, List<Reservation>> byRestaurant = soon.stream()
                .filter(booking -> booking.getRestaurant() != null)
                .collect(Collectors.groupingBy(booking -> booking.getRestaurant().getId()));
        byRestaurant.forEach((restaurantId, bookings) -> {
            var restaurant = bookings.getFirst().getRestaurant();
            ZoneId zone = reservationSupport.restaurantZone(restaurant);
            ZonedDateTime local = now.atZoneSameInstant(zone);
            ReservationPolicy.Values values = reservationPolicy.values(restaurant);
            LocalDate today = reservationPolicy.serviceDate(now, zone);
            // Only in the afternoon of the service day itself (not after midnight), once.
            if (!local.toLocalDate().equals(today) || local.toLocalTime().isBefore(values.confirmReminderTime())) {
                return;
            }
            ReservationReminder.Key key = new ReservationReminder.Key(restaurantId, DAY_BEFORE, today);
            if (reservationReminderRepository.existsById(key)) {
                return;
            }
            LocalDate tomorrow = today.plusDays(1);
            bookings.stream()
                    .filter(booking -> reservationPolicy.serviceDate(booking.getReservationStart(), zone).equals(tomorrow))
                    .collect(Collectors.groupingBy(booking -> booking.getBranch().getId()))
                    .values()
                    .forEach(branch -> {
                        int notConfirmed = (int) branch.stream()
                                .filter(b -> b.getStatus() == ReservationStatus.CONFIRMED && b.getAttendanceConfirmedAt() == null).count();
                        int requests = (int) branch.stream().filter(b -> b.getStatus() == ReservationStatus.PENDING).count();
                        int bigGroups = (int) branch.stream()
                                .filter(b -> b.getPartySize() >= values.approvalGroupSize() && b.getAttendanceConfirmedAt() == null).count();
                        reservationNotifications.dayBefore(branch.getFirst(), notConfirmed, requests, bigGroups);
                    });
            reservationReminderRepository.save(new ReservationReminder(key, now));
            logger.info("Sent the day-before reservation reminder for restaurant {}", restaurantId);
        });
    }
}
