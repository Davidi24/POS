package pos.pos.unit.reservation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.event.ReservationStatusChangedEvent;
import pos.pos.reservation.service.ReservationLifecycleService;
import pos.pos.reservation.service.ReservationNotifications;
import pos.pos.reservation.service.ReservationSupport;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Other modules (pre-orders) follow bookings through this event, so every status change must publish it.
@ExtendWith(MockitoExtension.class)
class ReservationStatusEventTest {

    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final UUID RESERVATION_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Mock RestaurantScopeService restaurantScopeService;
    @Mock ReservationSupport reservationSupport;
    @Mock ReservationNotifications reservationNotifications;
    @Mock ApplicationEventPublisher events;
    @Mock Authentication authentication;

    @Test void cancellingPublishesTheStatusChange() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        Reservation reservation = new Reservation();
        reservation.setId(RESERVATION_ID);
        reservation.setRestaurant(restaurant);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setReservationStart(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        when(reservationSupport.requireReservation(RESTAURANT_ID, RESERVATION_ID)).thenReturn(reservation);
        when(reservationSupport.saveReservation(reservation)).thenReturn(reservation);
        when(restaurantScopeService.currentUserId(authentication)).thenReturn(ACTOR_ID);

        new ReservationLifecycleService(restaurantScopeService, reservationSupport, reservationNotifications, events)
                .cancelReservation(authentication, RESTAURANT_ID, RESERVATION_ID, null);

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue()).isEqualTo(new ReservationStatusChangedEvent(
                RESERVATION_ID, RESTAURANT_ID, ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED, ACTOR_ID));
        verify(reservationNotifications).cancelled(any(), any());
    }
}
