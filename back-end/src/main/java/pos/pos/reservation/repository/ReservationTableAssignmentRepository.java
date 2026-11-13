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

    // Bookings whose guests are seated at these tables, the newest first: what they celebrate shows while they're there.
    @EntityGraph(attributePaths = {"reservation"})
    @Query("""
            SELECT assignment
            FROM ReservationTableAssignment assignment
            WHERE assignment.restaurantTable.id IN :tableIds
              AND assignment.reservation.status = pos.pos.reservation.enums.ReservationStatus.SEATED
              AND assignment.reservation.seatedAt >= :since
            ORDER BY assignment.reservation.seatedAt DESC
            """)
    List<ReservationTableAssignment> findSeatedByTableIds(
            @Param("tableIds") Collection<UUID> tableIds,
            @Param("since") OffsetDateTime since
    );

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

    // Bookings whose time has come on a table with guests or an open order; for the special cases.
    @Query("""
            SELECT DISTINCT r.id
            FROM ReservationTableAssignment a JOIN a.reservation r JOIN a.restaurantTable t
            WHERE r.status IN :statuses AND r.reservationStart <= :now AND r.reservationStart >= :since
              AND (t.status = pos.pos.tables.enums.TableStatus.OCCUPIED OR EXISTS (
                  SELECT o.id FROM pos.pos.order.entity.Order o WHERE o.restaurantTable = t
                  AND o.status IN (pos.pos.order.enums.OrderStatus.DRAFT, pos.pos.order.enums.OrderStatus.OPEN)))
            """)
    List<UUID> findDueOnTakenTables(
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("now") OffsetDateTime now,
            @Param("since") OffsetDateTime since
    );

    // Bookings at a table in these statuses, whatever their time (a seated visit can run past its end).
    @EntityGraph(attributePaths = {"reservation", "restaurantTable"})
    @Query("""
            SELECT assignment
            FROM ReservationTableAssignment assignment
            WHERE assignment.restaurantTable.id = :tableId
              AND assignment.reservation.status IN :statuses
            ORDER BY assignment.reservation.reservationStart ASC
            """)
    List<ReservationTableAssignment> findByTableAndReservationStatus(
            @Param("tableId") UUID tableId,
            @Param("statuses") Collection<ReservationStatus> statuses
    );

    boolean existsByRestaurantTable_Id(UUID tableId);
}
