package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.dto.GuestHistoryResponse;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.entity.GuestNoShowClear;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationEvent;
import pos.pos.reservation.enums.ReservationEventType;
import pos.pos.reservation.repository.GuestNoShowClearRepository;
import pos.pos.reservation.repository.ReservationEventRepository;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// No-show history on the guest: from the first no-show (Admin Hub setting) staff see a warning on each new booking.
// A manager can clear the warning with a reason; the no-show bookings themselves stay.
@Service
@RequiredArgsConstructor
public class GuestHistoryService {

    private final RestaurantScopeService restaurantScopeService;
    private final ReservationSupport reservationSupport;
    private final GuestNoShowCounter guestNoShowCounter;
    private final GuestNoShowClearRepository guestNoShowClearRepository;
    private final ReservationEventRepository reservationEventRepository;
    private final ReservationPolicy reservationPolicy;

    @Transactional(readOnly = true)
    public GuestHistoryResponse getGuestHistory(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        Reservation booking = reservationSupport.requireReservation(restaurantId, reservationId);
        List<Reservation> noShows = guestNoShowCounter.noShowsOf(booking);
        return GuestHistoryResponse.builder()
                .noShowCount(noShows.size())
                .warningFrom(reservationPolicy.values(booking.getRestaurant()).noShowWarningFrom())
                .noShows(noShows.stream()
                        .map(noShow -> GuestHistoryResponse.NoShow.builder()
                                .reservationId(noShow.getId())
                                .reservationCode(noShow.getReservationCode())
                                .reservationStart(noShow.getReservationStart())
                                .partySize(noShow.getPartySize())
                                .build())
                        .toList())
                .clears(guestNoShowCounter.clearsOf(booking).stream()
                        .sorted(Comparator.comparing(GuestNoShowClear::getClearedAt).reversed())
                        .map(clear -> GuestHistoryResponse.Clear.builder()
                                .clearedAt(clear.getClearedAt())
                                .clearedBy(clear.getClearedBy())
                                .reason(clear.getReason())
                                .build())
                        .toList())
                .build();
    }

    @Transactional
    public ReservationResponse clearNoShowWarning(Authentication authentication, UUID restaurantId, UUID reservationId, String reason) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation booking = reservationSupport.requireReservation(restaurantId, reservationId);
        if (reason == null || reason.isBlank()) {
            throw new AuthException("Give a reason for clearing the warning", HttpStatus.BAD_REQUEST);
        }
        if (booking.getCustomer() == null && booking.getContactPhone() == null && booking.getContactEmail() == null) {
            throw new AuthException("This booking has no guest details to clear a warning for", HttpStatus.BAD_REQUEST);
        }
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        GuestNoShowClear clear = new GuestNoShowClear();
        clear.setRestaurantId(restaurantId);
        clear.setCustomerId(booking.getCustomer() == null ? null : booking.getCustomer().getId());
        clear.setContactPhone(booking.getContactPhone());
        clear.setContactEmail(booking.getContactEmail());
        clear.setReason(reason.trim());
        clear.setClearedBy(actorId);
        guestNoShowClearRepository.save(clear);

        ReservationEvent event = new ReservationEvent();
        event.setReservation(booking);
        event.setType(ReservationEventType.NO_SHOW_WARNING_CLEARED);
        event.setDetail("No-show warning cleared");
        event.setReason(reason.trim());
        event.setActorId(actorId);
        reservationEventRepository.save(event);
        guestNoShowClearRepository.flush();
        return reservationSupport.toResponse(booking);
    }
}
