package pos.pos.reservation.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.reservation.enums.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationTableAssignmentRepository extends JpaRepository<ReservationTableAssignment, UUID> {

    @EntityGraph(attributePaths = {"restaurantTable", "assignedByUser"})
    List<ReservationTableAssignment> findAllByReservation_IdOrderByPrimaryAssignmentDescAssignedAtAsc(UUID reservationId);

    @EntityGraph(attributePaths = {"reservation", "assignedByUser"})
    List<ReservationTableAssignment> findAllByRestaurantTable_IdOrderByAssignedAtDesc(UUID tableId);

    @EntityGraph(attributePaths = {"restaurantTable", "assignedByUser"})
    Optional<ReservationTableAssignment> findByReservation_IdAndRestaurantTable_Id(UUID reservationId, UUID tableId);

    @EntityGraph(attributePaths = {"reservation"})
    @Query("""
            SELECT assignment
            FROM ReservationTableAssignment assignment
            WHERE assignment.restaurantTable.id IN :tableIds
              AND assignment.reservation.status IN :statuses
              AND assignment.reservation.reservationEnd >= :from
            ORDER BY assignment.reservation.reservationStart ASC
            """)
    List<ReservationTableAssignment> findUpcomingByTableIds(
            @Param("tableIds") Collection<UUID> tableIds,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("from") OffsetDateTime from
    );

    boolean existsByRestaurantTable_Id(UUID tableId);
}
