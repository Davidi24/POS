package pos.pos.reservation.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.reservation.entity.ReservationStatusHistory;

import java.util.List;
import java.util.UUID;

public interface ReservationStatusHistoryRepository extends JpaRepository<ReservationStatusHistory, UUID> {

    @EntityGraph(attributePaths = "changedByUser")
    List<ReservationStatusHistory> findAllByReservation_IdOrderByChangedAtDesc(UUID reservationId);
}
