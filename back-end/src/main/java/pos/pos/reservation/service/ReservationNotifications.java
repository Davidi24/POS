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

    // One notification per branch for a batch of automatic no-shows, so a busy evening doesn't flood the list.
    public void noShows(Collection<Reservation> reservations) {
        Map<UUID, List<Reservation>> byBranch = reservations.stream()
                .filter(r -> r.getBranch() != null && r.getRestaurant() != null)
                .collect(Collectors.groupingBy(r -> r.getBranch().getId()));
        byBranch.values().forEach(group -> {
            Reservation first = group.getFirst();
            boolean single = group.size() == 1;
            String body = single
                    ? summary(first)
                    : group.stream().map(this::guestName).limit(4).collect(Collectors.joining(", "))
                        + (group.size() > 4 ? " and " + (group.size() - 4) + " more" : "") + " did not arrive";
            send(first, NO_SHOW, single ? "Marked no-show" : group.size() + " reservations marked no-show",
                    body, single ? first.getId() : null, null);
        });
    }

    private void send(Reservation reservation, String eventCode, String subject, String body, UUID referenceId, UUID actorId) {
        if (reservation.getRestaurant() == null || reservation.getBranch() == null) {
            return;
        }
        UUID restaurantId = reservation.getRestaurant().getId();
        UUID branchId = reservation.getBranch().getId();
        for (UUID recipient : userRepository.findActiveStaffIds(restaurantId, branchId)) {
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
