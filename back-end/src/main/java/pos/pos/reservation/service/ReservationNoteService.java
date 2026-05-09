package pos.pos.reservation.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.reservation.ReservationNoteNotFoundException;
import pos.pos.reservation.dto.ReservationNoteRequest;
import pos.pos.reservation.dto.ReservationNoteResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationNote;
import pos.pos.reservation.repository.ReservationNoteRepository;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.UUID;

@Service
@lombok.RequiredArgsConstructor
public class ReservationNoteService {

    private final RestaurantScopeService restaurantScopeService;
    private final ReservationNoteRepository reservationNoteRepository;
    private final ReservationSupport reservationSupport;

    @Transactional
    public ReservationNoteResponse addNote(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationNoteRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        ReservationNote note = new ReservationNote();
        note.setReservation(reservation);
        note.setNote(request.getNote());
        note.setCreatedBy(actorId);
        note.setUpdatedBy(actorId);

        return reservationSupport.toNoteResponse(reservationSupport.saveReservationNote(note));
    }

    @Transactional
    public void deleteNote(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            UUID noteId
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        reservationSupport.requireReservation(restaurantId, reservationId);
        ReservationNote note = reservationNoteRepository.findByIdAndReservation_Id(noteId, reservationId)
                .orElseThrow(ReservationNoteNotFoundException::new);
        reservationNoteRepository.delete(note);
        reservationNoteRepository.flush();
    }
}
