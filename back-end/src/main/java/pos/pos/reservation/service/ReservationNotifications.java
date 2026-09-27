package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationMutationType;
import pos.pos.notification.enums.NotificationPriority;
import pos.pos.notification.enums.NotificationTopic;
import pos.pos.notification.service.NotificationOperationalPublisher;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.user.repository.UserRepository;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

// Sends staff a personal in-app notification for reservation events, e.g.
// "New reservation" / "Emma Wilson · 4 guests · Fri 25 Sep, 19:00 · T03". Whoever made the change doesn't get one.
@Component
@RequiredArgsConstructor
public class ReservationNotifications {

    public static final String CREATED = "RESERVATION_CREATED";
    public static final String CANCELLED = "RESERVATION_CANCELLED";
    public static final String NO_SHOW = "RESERVATION_NO_SHOW";
    public static final String EXPIRED = "RESERVATION_EXPIRED";
    public static final String NO_SHOW_WARNING = "RESERVATION_NO_SHOW_WARNING";
    public static final String CALL_GUEST = "RESERVATION_CALL_GUEST";
    public static final String DAY_BEFORE = "RESERVATION_DAY_BEFORE";
    public static final String TABLE_FREED = "RESERVATION_TABLE_FREED";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    private final NotificationOperationalPublisher notificationOperationalPublisher;
    private final UserRepository userRepository;
    private final ReservationSupport reservationSupport;

    public void created(Reservation reservation, UUID actorId, boolean online) {
        send(reservation, CREATED, online ? "New online reservation" : "New reservation", summary(reservation), reservation.getId(), actorId);
    }

    public void cancelled(Reservation reservation, UUID actorId) {
        send(reservation, CANCELLED, "Reservation cancelled", summary(reservation), reservation.getId(), actorId);
    }

    // A guest who didn't come before has booked again. Staff decide; nothing is restricted automatically.
    public void noShowWarning(Reservation reservation, int noShows, UUID actorId) {
        send(reservation, NO_SHOW_WARNING, "Booked by a guest with " + noShows + (noShows == 1 ? " no-show" : " no-shows"),
                summary(reservation), reservation.getId(), actorId);
    }

    // Attendance isn't confirmed and the booking is close: someone should call. Never cancelled for no reply.
    public void callGuest(Reservation reservation) {
        String phone = reservation.getContactPhone() == null ? "" : " · " + reservation.getContactPhone();
        send(reservation, CALL_GUEST, "Call to confirm attendance", summary(reservation) + phone, reservation.getId(), null);
    }

    // The day before, to managers: what still needs doing for tomorrow's bookings.
    public void dayBefore(Reservation anyTomorrow, int notConfirmed, int requests, int bigGroups) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (notConfirmed > 0) parts.add(notConfirmed + (notConfirmed == 1 ? " booking isn't" : " bookings aren't") + " confirmed yet");
        if (requests > 0) parts.add(requests + (requests == 1 ? " request is" : " requests are") + " waiting for an answer");
        if (bigGroups > 0) parts.add(bigGroups + (bigGroups == 1 ? " big group" : " big groups") + " to call");
        if (parts.isEmpty()) {
            return;
        }
        sendToPermission(anyTomorrow, DAY_BEFORE, "Tomorrow's bookings", String.join(" · ", parts), "RESERVATION_APPROVE");
    }

    // A booking ended early (cancelled or no-show) while requests wait around that time: staff decide who gets it.
    public void tableFreed(Reservation freed, int requestsWaiting) {
        ZoneId zone = reservationSupport.restaurantZone(freed.getRestaurant());
        String at = freed.getReservationStart() == null ? "" : " at " + WHEN.format(freed.getReservationStart().atZoneSameInstant(zone));
        send(freed, TABLE_FREED, "A table is free" + at,
                requestsWaiting + (requestsWaiting == 1 ? " request is" : " requests are") + " waiting around that time", null, null);
    }

    // One notification per branch for a batch of automatic no-shows, so a busy evening doesn't flood the list.
    public void noShows(Collection<Reservation> reservations) {
        batch(reservations, NO_SHOW, "Marked no-show", " reservations marked no-show", " did not arrive");
    }

    // Requests nobody answered before their time.
    public void expired(Collection<Reservation> reservations) {
        batch(reservations, EXPIRED, "Request expired", " requests expired", " were never answered");
    }

    private void batch(Collection<Reservation> reservations, String eventCode, String singleSubject, String manySubject, String manyBody) {
        Map<UUID, List<Reservation>> byBranch = reservations.stream()
                .filter(r -> r.getBranch() != null && r.getRestaurant() != null)
                .collect(Collectors.groupingBy(r -> r.getBranch().getId()));
        byBranch.values().forEach(group -> {
            Reservation first = group.getFirst();
            boolean single = group.size() == 1;
            String body = single
                    ? summary(first)
                    : group.stream().map(this::guestName).limit(4).collect(Collectors.joining(", "))
                        + (group.size() > 4 ? " and " + (group.size() - 4) + " more" : "") + manyBody;
            send(first, eventCode, single ? singleSubject : group.size() + manySubject,
                    body, single ? first.getId() : null, null);
        });
    }

    private void sendToPermission(Reservation reservation, String eventCode, String subject, String body, String permission) {
        if (reservation.getRestaurant() == null || reservation.getBranch() == null) {
            return;
        }
        UUID restaurantId = reservation.getRestaurant().getId();
        UUID branchId = reservation.getBranch().getId();
        deliver(userRepository.findActiveStaffIdsWithPermission(restaurantId, branchId, permission),
                restaurantId, branchId, eventCode, subject, body, null, null);
    }

    private void send(Reservation reservation, String eventCode, String subject, String body, UUID referenceId, UUID actorId) {
        if (reservation.getRestaurant() == null || reservation.getBranch() == null) {
            return;
        }
        UUID restaurantId = reservation.getRestaurant().getId();
        UUID branchId = reservation.getBranch().getId();
        deliver(userRepository.findActiveStaffIds(restaurantId, branchId), restaurantId, branchId, eventCode, subject, body, referenceId, actorId);
    }

    private void deliver(
            Collection<UUID> recipients,
            UUID restaurantId,
            UUID branchId,
            String eventCode,
            String subject,
            String body,
            UUID referenceId,
            UUID actorId
    ) {
        for (UUID recipient : recipients) {
            if (Objects.equals(recipient, actorId)) {
                continue;
            }
            notificationOperationalPublisher.publishCustom(
                    NotificationTopic.RESERVATION,
                    NotificationMutationType.UPSERT,
                    NotificationChannel.IN_APP,
                    NotificationPriority.HIGH,
                    eventCode,
                    restaurantId,
                    branchId,
                    recipient,
                    "RESERVATION",
                    referenceId,
                    actorId,
                    subject,
                    body
            );
        }
    }

    private String summary(Reservation reservation) {
        ZoneId zone = reservationSupport.restaurantZone(reservation.getRestaurant());
        String tables = reservation.getTableAssignments().stream()
                .map(ReservationTableAssignment::getRestaurantTable)
                .filter(table -> table != null && table.getTableNumber() != null)
                .map(table -> table.getTableNumber())
                .collect(Collectors.joining(" + "));
        StringBuilder text = new StringBuilder(guestName(reservation))
                .append(" · ").append(reservation.getPartySize()).append(reservation.getPartySize() == 1 ? " guest" : " guests");
        if (reservation.getReservationStart() != null) {
            text.append(" · ").append(WHEN.format(reservation.getReservationStart().atZoneSameInstant(zone)));
        }
        text.append(" · ").append(tables.isBlank() ? "no table yet" : tables);
        return text.toString();
    }

    private String guestName(Reservation reservation) {
        String name = reservation.getContactName();
        return name == null || name.isBlank() ? "Guest" : name.trim();
    }
}
