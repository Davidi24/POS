package pos.pos.unit.reservation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.reservation.dto.ReservationRequest;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.service.ReservationSupport;
import pos.pos.restaurant.entity.Restaurant;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ReservationCodeTest {

    @Mock ReservationRepository reservationRepository;
    @InjectMocks ReservationSupport reservationSupport;

    // Regression: codes came from a time-ordered UUID's first 8 characters, which repeat for ~27 seconds,
    // so a second booking made within that time couldn't be created.
    @Test void bookingsMadeBackToBackGetDifferentCodes() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(UUID.randomUUID());
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        ReservationRequest request = ReservationRequest.builder()
                .partySize(2)
                .contactName("Maria")
                .reservationStart(start)
                .reservationEnd(start.plusHours(2))
                .build();

        Reservation first = booking(restaurant);
        Reservation second = booking(restaurant);
        reservationSupport.applyReservationRequest(first, request, null, true);
        reservationSupport.applyReservationRequest(second, request, null, true);

        assertThat(first.getReservationCode()).startsWith("RES_");
        assertThat(second.getReservationCode()).isNotEqualTo(first.getReservationCode());
    }

    private static Reservation booking(Restaurant restaurant) {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        Reservation reservation = new Reservation();
        reservation.setRestaurant(restaurant);
        reservation.setReservationStart(start);
        reservation.setReservationEnd(start.plusHours(2));
        return reservation;
    }
}
