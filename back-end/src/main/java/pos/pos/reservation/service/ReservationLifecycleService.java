package pos.pos.reservation.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.dto.ReservationActionRequest;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.UUID;

@Service
@lombok.RequiredArgsConstructor
public class ReservationLifecycleService {

    private final RestaurantScopeService restaurantScopeService;
    private final ReservationSupport reservationSupport;

    @Transactional
    public ReservationResponse confirmReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationActionRequest request
    ) {
        return transitionReservation(authentication, restaurantId, reservationId, ReservationStatus.CONFIRMED, request);
    }

    @Transactional
    public ReservationResponse cancelReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationActionRequest request
    ) {
        return transitionReservation(authentication, restaurantId, reservationId, ReservationStatus.CANCELLED, request);
    }

    @Transactional
    public ReservationResponse checkInReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationActionRequest request
    ) {
        return transitionReservation(authentication, restaurantId, reservationId, ReservationStatus.CHECKED_IN, request);
    }

    @Transactional
    public ReservationResponse seatReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationActionRequest request
    ) {
        return transitionReservation(authentication, restaurantId, reservationId, ReservationStatus.SEATED, request);
    }

    @Transactional
    public ReservationResponse completeReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationActionRequest request
    ) {
        return transitionReservation(authentication, restaurantId, reservationId, ReservationStatus.COMPLETED, request);
    }

    @Transactional
    public ReservationResponse markNoShow(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationActionRequest request
    ) {
        return transitionReservation(authentication, restaurantId, reservationId, ReservationStatus.NO_SHOW, request);
    }

    @Transactional
    public ReservationResponse reopenReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationActionRequest request
    ) {
        return transitionReservation(authentication, restaurantId, reservationId, ReservationStatus.PENDING, request);
    }

    void transitionReservation(Reservation reservation, ReservationStatus targetStatus, String reason, UUID actorId) {
        ReservationStatus currentStatus = reservation.getStatus();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (targetStatus == ReservationStatus.CONFIRMED) {
            assertPendingStatus(currentStatus);
            reservation.setStatus(ReservationStatus.CONFIRMED);
            reservation.setConfirmedAt(now);
        } else if (targetStatus == ReservationStatus.CANCELLED) {
            if (!EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED).contains(currentStatus)) {
                throw new AuthException("Reservation can only be cancelled from PENDING or CONFIRMED", HttpStatus.BAD_REQUEST);
            }
            reservation.setStatus(ReservationStatus.CANCELLED);
            reservation.setCancelledAt(now);
            reservation.setCancellationReason(reason);
        } else if (targetStatus == ReservationStatus.CHECKED_IN) {
            if (!EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED).contains(currentStatus)) {
                throw new AuthException("Reservation can only be checked in from PENDING or CONFIRMED", HttpStatus.BAD_REQUEST);
            }
            reservation.setStatus(ReservationStatus.CHECKED_IN);
            if (reservation.getConfirmedAt() == null) {
                reservation.setConfirmedAt(now);
            }
            reservation.setCheckedInAt(now);
        } else if (targetStatus == ReservationStatus.SEATED) {
            if (!EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN).contains(currentStatus)) {
                throw new AuthException("Reservation can only be seated from PENDING, CONFIRMED, or CHECKED_IN", HttpStatus.BAD_REQUEST);
            }
            reservation.setStatus(ReservationStatus.SEATED);
            if (reservation.getConfirmedAt() == null) {
                reservation.setConfirmedAt(now);
            }
            if (reservation.getCheckedInAt() == null) {
                reservation.setCheckedInAt(now);
            }
            reservation.setSeatedAt(now);
        } else if (targetStatus == ReservationStatus.COMPLETED) {
            if (!EnumSet.of(ReservationStatus.CHECKED_IN, ReservationStatus.SEATED).contains(currentStatus)) {
                throw new AuthException("Reservation can only be completed from CHECKED_IN or SEATED", HttpStatus.BAD_REQUEST);
            }
            reservation.setStatus(ReservationStatus.COMPLETED);
            if (reservation.getConfirmedAt() == null) {
                reservation.setConfirmedAt(now);
            }
            if (reservation.getCheckedInAt() == null) {
                reservation.setCheckedInAt(now);
            }
            reservation.setCompletedAt(now);
        } else if (targetStatus == ReservationStatus.NO_SHOW) {
            if (!EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED).contains(currentStatus)) {
                throw new AuthException("Reservation can only be marked no-show from PENDING or CONFIRMED", HttpStatus.BAD_REQUEST);
            }
            reservation.setStatus(ReservationStatus.NO_SHOW);
            reservation.setNoShowAt(now);
        } else if (targetStatus == ReservationStatus.PENDING) {
            if (!EnumSet.of(ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW).contains(currentStatus)) {
                throw new AuthException("Reservation can only be reopened from CANCELLED or NO_SHOW", HttpStatus.BAD_REQUEST);
            }
            reservation.setStatus(ReservationStatus.PENDING);
            reservation.setConfirmedAt(null);
            reservation.setCancelledAt(null);
            reservation.setCancellationReason(null);
            reservation.setCheckedInAt(null);
            reservation.setSeatedAt(null);
            reservation.setCompletedAt(null);
            reservation.setNoShowAt(null);
        } else {
            throw new AuthException("Unsupported reservation status transition", HttpStatus.BAD_REQUEST);
        }

        reservationSupport.addStatusHistory(reservation, currentStatus, reservation.getStatus(), reason, actorId);
    }

    private ReservationResponse transitionReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationStatus targetStatus,
            ReservationActionRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        transitionReservation(reservation, targetStatus, request == null ? null : request.getReason(), actorId);
        reservation.setUpdatedBy(actorId);
        return reservationSupport.toResponse(reservationSupport.saveReservation(reservation));
    }

    private void assertPendingStatus(ReservationStatus currentStatus) {
        if (currentStatus != ReservationStatus.PENDING) {
            throw new AuthException("Reservation must be PENDING before this transition", HttpStatus.BAD_REQUEST);
        }
    }
}
