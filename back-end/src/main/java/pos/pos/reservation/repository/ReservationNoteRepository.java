package pos.pos.reservation.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.reservation.entity.ReservationNote;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationNoteRepository extends JpaRepository<ReservationNote, UUID> {

    @EntityGraph(attributePaths = {"createdByUser", "updatedByUser"})
    List<ReservationNote> findAllByReservation_IdOrderByCreatedAtAsc(UUID reservationId);

    @EntityGraph(attributePaths = {"createdByUser", "updatedByUser"})
    Optional<ReservationNote> findByIdAndReservation_Id(UUID noteId, UUID reservationId);
}
