package pos.pos.reservation.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.reservation.entity.ReservationTableAssignment;

import java.util.List;
import java.util.UUID;

public interface ReservationTableAssignmentRepository extends JpaRepository<ReservationTableAssignment, UUID> {

    @EntityGraph(attributePaths = {"restaurantTable", "assignedByUser"})
    List<ReservationTableAssignment> findAllByReservation_IdOrderByPrimaryAssignmentDescAssignedAtAsc(UUID reservationId);

    @EntityGraph(attributePaths = {"reservation", "assignedByUser"})
    List<ReservationTableAssignment> findAllByRestaurantTable_IdOrderByAssignedAtDesc(UUID tableId);

    boolean existsByRestaurantTable_Id(UUID tableId);
}
