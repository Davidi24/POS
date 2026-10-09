package pos.pos.reservation.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.dto.ReservationDepositResponse;
import pos.pos.reservation.dto.UpdateReservationDepositRequest;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationDepositStatus;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.EnumSet;
import java.util.UUID;

@Service
@lombok.RequiredArgsConstructor
public class ReservationDepositService {

    private final RestaurantScopeService restaurantScopeService;
    private final ReservationSupport reservationSupport;

    @Transactional(readOnly = true)
    public ReservationDepositResponse getDeposit(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return reservationSupport.toDepositResponse(reservationSupport.requireReservation(restaurantId, reservationId));
    }

    @Transactional
    public ReservationDepositResponse updateDeposit(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            UpdateReservationDepositRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        reservation.setUpdatedBy(restaurantScopeService.currentUserId(authentication));

        reservationSupport.applyDepositFields(
                reservation,
                request.getDepositRequired() == null ? reservation.isDepositRequired() : request.getDepositRequired(),
                request.getDepositAmount() == null ? reservation.getDepositAmount() : request.getDepositAmount(),
                false
        );

        reservationSupport.saveReservation(reservation);
        return reservationSupport.toDepositResponse(reservation);
    }

    @Transactional
    public ReservationDepositResponse payDeposit(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        reservationSupport.requireDepositConfigured(reservation);
        reservation.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        reservation.setDepositStatus(ReservationDepositStatus.PAID);
        reservationSupport.saveReservation(reservation);
        return reservationSupport.toDepositResponse(reservation);
    }

    @Transactional
    public ReservationDepositResponse refundDeposit(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        if (!EnumSet.of(ReservationDepositStatus.PAID, ReservationDepositStatus.PARTIALLY_PAID).contains(reservation.getDepositStatus())) {
            throw new AuthException("Deposit can only be refunded after payment", HttpStatus.BAD_REQUEST);
        }
        reservation.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        reservation.setDepositStatus(ReservationDepositStatus.REFUNDED);
        reservationSupport.saveReservation(reservation);
        return reservationSupport.toDepositResponse(reservation);
    }

    @Transactional
    public ReservationDepositResponse waiveDeposit(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        reservationSupport.requireDepositConfigured(reservation);
        reservation.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        reservation.setDepositStatus(ReservationDepositStatus.WAIVED);
        reservationSupport.saveReservation(reservation);
        return reservationSupport.toDepositResponse(reservation);
    }

    @Transactional
    public ReservationDepositResponse forfeitDeposit(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        reservationSupport.requireDepositConfigured(reservation);
        reservation.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        reservation.setDepositStatus(ReservationDepositStatus.FORFEITED);
        reservationSupport.saveReservation(reservation);
        return reservationSupport.toDepositResponse(reservation);
    }
}
