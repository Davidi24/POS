package pos.pos.unit.reservation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationMutationType;
import pos.pos.notification.enums.NotificationPriority;
import pos.pos.notification.enums.NotificationTopic;
import pos.pos.notification.service.NotificationOperationalPublisher;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.service.ReservationNotifications;
import pos.pos.reservation.service.ReservationSupport;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Reservation notifications")
class ReservationNotificationsTest {

    private static final UUID RESTAURANT = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID WAITER = UUID.randomUUID();
    private static final UUID MANAGER = UUID.randomUUID();

    @Mock private NotificationOperationalPublisher publisher;
    @Mock private UserRepository userRepository;

    private ReservationNotifications notifications() {
        ReservationSupport support = new ReservationSupport(null, null, null, null, null, null, null, null);
        return new ReservationNotifications(publisher, userRepository, support);
    }

    private Reservation reservation() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT);
        restaurant.setTimezone("Europe/Tirane");
        Branch branch = new Branch();
        branch.setId(BRANCH);
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setRestaurant(restaurant);
        reservation.setBranch(branch);
        reservation.setContactName("Emma Wilson");
        reservation.setPartySize(4);
        reservation.setReservationStart(OffsetDateTime.of(2026, 9, 25, 17, 0, 0, 0, ZoneOffset.UTC));
        return reservation;
    }

    @Test
    @DisplayName("every staff member of the branch gets a personal notification, except whoever made the booking")
    void newReservationGoesToOtherStaff() {
        when(userRepository.findActiveStaffIds(RESTAURANT, BRANCH)).thenReturn(List.of(ACTOR, WAITER, MANAGER));
        Reservation reservation = reservation();

        notifications().created(reservation, ACTOR, false);

        for (UUID recipient : List.of(WAITER, MANAGER)) {
            verify(publisher).publishCustom(
                    eq(NotificationTopic.RESERVATION), eq(NotificationMutationType.UPSERT), eq(NotificationChannel.IN_APP),
                    eq(NotificationPriority.HIGH), eq(ReservationNotifications.CREATED), eq(RESTAURANT), eq(BRANCH),
                    eq(recipient), eq("RESERVATION"), eq(reservation.getId()), eq(ACTOR), eq("New reservation"),
                    eq("Emma Wilson · 4 guests · Fri 25 Sep, 19:00 · no table yet"));
        }
        verify(publisher, never()).publishCustom(any(), any(), any(), any(), anyString(), any(), any(), eq(ACTOR), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an online booking notifies everyone")
    void onlineBookingNotifiesAll() {
        when(userRepository.findActiveStaffIds(RESTAURANT, BRANCH)).thenReturn(List.of(WAITER, MANAGER));

        notifications().created(reservation(), null, true);

        verify(publisher, times(2)).publishCustom(any(), any(), any(), any(), eq(ReservationNotifications.CREATED), any(), any(),
                any(), any(), any(), any(), eq("New online reservation"), anyString());
    }
}
