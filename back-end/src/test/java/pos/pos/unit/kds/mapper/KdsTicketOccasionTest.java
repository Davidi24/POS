package pos.pos.unit.kds.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.kds.entity.KdsTicket;
import pos.pos.kds.mapper.KdsMapper;
import pos.pos.order.entity.Order;
import pos.pos.reservation.entity.Reservation;

import static org.assertj.core.api.Assertions.assertThat;

// The kitchen sees a booking's occasion on its tickets, e.g. the cake and candles for a birthday.
@DisplayName("Occasion on kitchen tickets")
class KdsTicketOccasionTest {

    @Test
    @DisplayName("shows the occasion, the picked options and the note")
    void occasionOnTicket() {
        Reservation booking = new Reservation();
        booking.setOccasionIcon("🎂");
        booking.setOccasionName("Birthday");
        booking.setOccasionOptions("Cake from us\nCandles");
        booking.setOccasionNote("30 candles at dessert");
        Order order = new Order();
        order.setReservation(booking);
        KdsTicket ticket = new KdsTicket();
        ticket.setOrder(order);

        assertThat(new KdsMapper().toTicketResponse(ticket).getOccasion())
                .isEqualTo("🎂 Birthday · Cake from us, Candles · 30 candles at dessert");
    }

    @Test
    @DisplayName("nothing for orders without an occasion")
    void noOccasion() {
        KdsTicket ticket = new KdsTicket();
        ticket.setOrder(new Order());

        assertThat(new KdsMapper().toTicketResponse(ticket).getOccasion()).isNull();
    }
}
