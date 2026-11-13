package pos.pos.unit.reservation.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.controller.GuestBookingPageController;
import pos.pos.reservation.service.GuestBookingService;
import pos.pos.reservation.service.ReservationOccasionService;
import pos.pos.reservation.service.ReservationSupport;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuestBookingPageControllerTest {
    private final ReservationSupport support = mock(ReservationSupport.class);
    private final GuestBookingPageController controller = new GuestBookingPageController(
            mock(GuestBookingService.class), mock(ReservationOccasionService.class), support);

    @Test void missingOrClosedRestaurantReturns404InsteadOfSuccessfulNotFoundPage() {
        when(support.requirePublicBranch("closed", "MAIN"))
                .thenThrow(new AuthException("Unavailable", HttpStatus.NOT_FOUND));
        var result = controller.bookingForm("closed", "MAIN");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(org.springframework.web.util.HtmlUtils.htmlUnescape(result.getBody())).contains("This restaurant doesn't take bookings online.");
    }

    @Test void lookupFailureIsNotDisguisedAsMissingRestaurant() {
        var failure = new AuthException("Temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        when(support.requirePublicBranch("restaurant", "MAIN")).thenThrow(failure);
        assertThatThrownBy(() -> controller.bookingForm("restaurant", "MAIN")).isSameAs(failure);
    }
}
