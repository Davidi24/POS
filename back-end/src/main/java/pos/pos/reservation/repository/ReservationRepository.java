package pos.pos.reservation.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @EntityGraph(attributePaths = {"branch", "customer"})
    List<Reservation> findAllByRestaurant_IdOrderByReservationStartDesc(UUID restaurantId);

    @EntityGraph(attributePaths = {"branch", "customer", "tableAssignments", "tableAssignments.restaurantTable"})
    List<Reservation> findAllByBranch_IdAndReservationStartBetweenOrderByReservationStartAsc(
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to
    );

    @EntityGraph(attributePaths = {"branch", "customer", "tableAssignments", "tableAssignments.restaurantTable"})
    List<Reservation> findAllByBranch_IdOrderByReservationStartAsc(UUID branchId);

    @EntityGraph(attributePaths = {"branch", "customer", "tableAssignments", "tableAssignments.restaurantTable"})
    List<Reservation> findAllByBranch_IdAndStatusInAndReservationStartLessThanAndReservationEndGreaterThanOrderByReservationStartAsc(
            UUID branchId,
            Collection<ReservationStatus> statuses,
            OffsetDateTime reservationEnd,
            OffsetDateTime reservationStart
    );

    @EntityGraph(attributePaths = {"branch", "customer", "statusHistory", "tableAssignments", "tableAssignments.restaurantTable"})
    Optional<Reservation> findByIdAndRestaurant_Id(UUID reservationId, UUID restaurantId);

    @EntityGraph(attributePaths = {"branch", "customer", "tableAssignments", "tableAssignments.restaurantTable"})
    List<Reservation> findAllByCustomer_IdAndRestaurant_IdOrderByReservationStartDesc(UUID customerId, UUID restaurantId);

    @EntityGraph(attributePaths = {"branch", "customer", "tableAssignments", "tableAssignments.restaurantTable"})
    Optional<Reservation> findTopByReservationCodeOrderByCreatedAtDesc(String reservationCode);

    boolean existsByRestaurant_IdAndReservationCode(UUID restaurantId, String reservationCode);

    long countByBranch_IdAndStatusAndReservationStartBetween(
            UUID branchId,
            ReservationStatus status,
            OffsetDateTime from,
            OffsetDateTime to
    );
}
