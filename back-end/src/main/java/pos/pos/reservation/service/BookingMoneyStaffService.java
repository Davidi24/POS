package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.dto.GuestExtraChoice;
import pos.pos.reservation.dto.MoneyLineResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationPayment;
import pos.pos.reservation.enums.PaymentStatus;
import pos.pos.reservation.repository.ReservationPaymentRepository;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

// Staff side of a booking's money: see each paid part, add a paid extra (e.g. a cake ordered on the phone), send the
// guest a payment link, record a payment taken in person, and goodwill refunds (Owner, or whoever the Owner allows).
@Service
@RequiredArgsConstructor
public class BookingMoneyStaffService {

    private final RestaurantScopeService restaurantScopeService;
    private final ReservationSupport reservationSupport;
    private final BookingMoneyService bookingMoneyService;
    private final ReservationPaymentRepository reservationPaymentRepository;
    private final ReservationMailService reservationMailService;
    private final GuestBookingService guestBookingService;

    @Transactional(readOnly = true)
    public List<MoneyLineResponse> lines(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return bookingMoneyService.lines(reservationSupport.requireReservation(restaurantId, reservationId), OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public List<GuestExtraChoice> extraChoices(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        return guestBookingService.extras(reservation.getRestaurant().getSlug(), reservation.getBranch().getCode(), reservation.getOccasionCode());
    }

    @Transactional
    public List<MoneyLineResponse> addExtra(Authentication authentication, UUID restaurantId, UUID reservationId, UUID menuItemId, int quantity) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        bookingMoneyService.addExtra(reservation, menuItemId, quantity, now);
        return bookingMoneyService.lines(reservation, now);
    }

    // Emails the guest a link to pay what's due (phone bookings with paid extras or a deposit).
    @Transactional(readOnly = true)
    public void sendPaymentLink(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        if (reservation.getContactEmail() == null || reservation.getContactEmail().isBlank()) {
            throw new AuthException("Add the guest's email first", HttpStatus.BAD_REQUEST);
        }
        List<MoneyLineResponse> due = bookingMoneyService.lines(reservation, OffsetDateTime.now(ZoneOffset.UTC)).stream()
                .filter(line -> "PENDING".equals(line.getStatus())).toList();
        if (due.isEmpty()) {
            throw new AuthException("Nothing is left to pay", HttpStatus.BAD_REQUEST);
        }
        reservationMailService.paymentLink(reservation, due);
    }

    // The guest paid at the desk (card or cash).
    @Transactional
    public List<MoneyLineResponse> markPaid(Authentication authentication, UUID restaurantId, UUID reservationId, UUID paymentId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        ReservationPayment payment = reservationPaymentRepository.findByIdAndReservation_Id(paymentId, reservationId)
                .orElseThrow(() -> new AuthException("This payment isn't on the booking", HttpStatus.NOT_FOUND));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        bookingMoneyService.markPaid(payment, "IN_PERSON", "staff-" + restaurantScopeService.currentUserId(authentication), now);
        return bookingMoneyService.lines(reservation, now);
    }

    // An unpaid extra the guest no longer wants.
    @Transactional
    public List<MoneyLineResponse> removeUnpaid(Authentication authentication, UUID restaurantId, UUID reservationId, UUID paymentId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        ReservationPayment payment = reservationPaymentRepository.findByIdAndReservation_Id(paymentId, reservationId)
                .orElseThrow(() -> new AuthException("This payment isn't on the booking", HttpStatus.NOT_FOUND));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new AuthException("Only an unpaid extra can be removed", HttpStatus.BAD_REQUEST);
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        reservationPaymentRepository.save(payment);
        return bookingMoneyService.lines(reservation, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public List<MoneyLineResponse> goodwill(Authentication authentication, UUID restaurantId, UUID reservationId, String lineId, BigDecimal amount, String reason) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        bookingMoneyService.goodwill(reservation, lineId, amount, reason, restaurantScopeService.currentUserId(authentication));
        return bookingMoneyService.lines(reservation, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
