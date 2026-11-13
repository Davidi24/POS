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
    private ReservationMailService reservationMailService;

    // Optional so the job runs without email (and in tests).
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setReservationMailService(ReservationMailService reservationMailService) {
        this.reservationMailService = reservationMailService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 40_000)
    @Transactional
    public void sendReminders() {
        sendReminders(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public void sendReminders(OffsetDateTime now) {
        guestReminders(now);
        secondGuestReminders(now);
        callReminders(now);
        dayBeforeReminders(now);
        reviewAlerts(now);
    }

    // The guest's "See you soon? [Confirm] [Cancel]" email, the reminder time (24 h) before, once.
    private boolean alreadyReminded(Reservation booking,ReservationEventType type){
        return booking.getScheduleChangedAt()==null
            ? reservationEventRepository.existsByReservation_IdAndType(booking.getId(),type)
            : reservationEventRepository.existsByReservation_IdAndTypeAndCreatedAtGreaterThanEqual(booking.getId(),type,booking.getScheduleChangedAt());
    }

    private void guestReminders(OffsetDateTime now) {
        if (reservationMailService == null) {
            return;
        }
        for (Reservation booking : reservationRepository.findAwaitingAttendance(ReservationStatus.CONFIRMED, now, now.plusDays(8))) {
            if (booking.getContactEmail() == null || booking.getContactEmail().isBlank()) {
                continue;
            }
            int hours = reservationPolicy.values(booking.getRestaurant()).guestReminderHours();
            if (now.isBefore(booking.getReservationStart().minusHours(hours))
                    || alreadyReminded(booking,ReservationEventType.GUEST_REMINDED)) {
                continue;
            }
            reservationMailService.reminder(booking);
        }
    }

    // No answer to the first email: asked again the second reminder time (6 h) before, once. Only when the first
    // went out before that, so a booking made late doesn't get two emails in a row.
    private void secondGuestReminders(OffsetDateTime now) {
        if (reservationMailService == null) {
            return;
        }
        for (Reservation booking : reservationRepository.findAwaitingAttendance(ReservationStatus.CONFIRMED, now, now.plusDays(3))) {
            if (booking.getContactEmail() == null || booking.getContactEmail().isBlank()) {
                continue;
            }
            int hours = reservationPolicy.values(booking.getRestaurant()).guestSecondReminderHours();
            OffsetDateTime askAgainAt = booking.getReservationStart().minusHours(hours);
            if (hours <= 0 || now.isBefore(askAgainAt)
                    || !alreadyReminded(booking, ReservationEventType.GUEST_REMINDED)
                    || !reservationEventRepository.existsByReservation_IdAndTypeAndCreatedAtBefore(booking.getId(), ReservationEventType.GUEST_REMINDED, askAgainAt)
                    || alreadyReminded(booking, ReservationEventType.GUEST_REMINDED_AGAIN)) {
                continue;
            }
            reservationMailService.reminderAgain(booking);
        }
    }

    // Visits staff must look at (checked in and never seated after their time, still open from an earlier day):
    // the chosen roles are told once per booking (user, 2026-11-02).
    private void reviewAlerts(OffsetDateTime now) {
        for (Reservation booking : reservationRepository.findAllByStatusInAndReservationStartBetween(
                EnumSet.of(ReservationStatus.CHECKED_IN, ReservationStatus.SEATED), now.minusDays(2), now)) {
            String reason = reservationSupport.reviewReason(booking, now);
            if (reason == null || alreadyReminded(booking, ReservationEventType.REVIEW_NOTIFIED)) {
                continue;
            }
            reservationNotifications.needsReview(booking, reason);
            ReservationEvent event = new ReservationEvent();
            event.setReservation(booking);
            event.setType(ReservationEventType.REVIEW_NOTIFIED);
            event.setDetail("Told: " + reason);
            reservationEventRepository.save(event);
        }
    }

    private void callReminders(OffsetDateTime now) {
        for (Reservation booking : reservationRepository.findAwaitingAttendance(ReservationStatus.CONFIRMED, now, now.plusDays(1))) {
            int callBefore = reservationPolicy.values(booking.getRestaurant()).attendanceCallMinutes();
            if (Duration.between(now, booking.getReservationStart()).toMinutes() > callBefore
                    || alreadyReminded(booking,ReservationEventType.CALL_REMINDED)) {
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
