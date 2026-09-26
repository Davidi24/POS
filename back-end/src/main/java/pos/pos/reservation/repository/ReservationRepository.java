package pos.pos.reservation.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @EntityGraph(attributePaths = {"branch", "customer", "tableAssignments", "tableAssignments.restaurantTable"})
    Optional<Reservation> findByIdAndRestaurant_Id(UUID reservationId, UUID restaurantId);

    @EntityGraph(attributePaths = {"branch", "customer", "tableAssignments", "tableAssignments.restaurantTable"})
    List<Reservation> findAllByCustomer_IdAndRestaurant_IdOrderByReservationStartDesc(UUID customerId, UUID restaurantId);

    @EntityGraph(attributePaths = {"branch", "customer", "tableAssignments", "tableAssignments.restaurantTable"})
    Optional<Reservation> findTopByReservationCodeOrderByCreatedAtDesc(String reservationCode);

    // Ids first, then the full rows, so the limit runs in the database and not after fetching every table assignment.
    @Query("""
            select r.id from Reservation r
            where r.branch.id = :branchId and r.status in :statuses and r.reservationStart >= :from
            order by r.reservationStart asc
            """)
    List<UUID> findUpcomingIds(
            @Param("branchId") UUID branchId,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("from") OffsetDateTime from,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"branch", "customer", "tableAssignments", "tableAssignments.restaurantTable"})
    List<Reservation> findAllByIdInOrderByReservationStartAsc(Collection<UUID> ids);

    long countByBranch_IdAndStatusInAndReservationStartGreaterThanEqual(
            UUID branchId,
            Collection<ReservationStatus> statuses,
            OffsetDateTime from
    );

    // Bookings still waiting for their guest after the grace time. One that staff already
    // reopened from no-show is skipped, so a late guest isn't flipped back automatically.
    @Query("""
            select r from Reservation r
            where r.status in :statuses and r.reservationStart < :cutoff
              and not exists (
                  select h.id from ReservationStatusHistory h
                  where h.reservation = r and h.oldStatus = :noShow
              )
            """)
    List<Reservation> findOverdueForNoShow(
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("cutoff") OffsetDateTime cutoff,
            @Param("noShow") ReservationStatus noShow
    );

    // Checked in at the door but never seated, and the booking has already ended.
    @Query("""
            select r from Reservation r
            where r.status = :checkedIn and r.reservationEnd < :now
              and not exists (
                  select h.id from ReservationStatusHistory h
                  where h.reservation = r and h.oldStatus = :noShow
              )
            """)
    List<Reservation> findCheckedInPastEnd(
            @Param("checkedIn") ReservationStatus checkedIn,
            @Param("now") OffsetDateTime now,
            @Param("noShow") ReservationStatus noShow
    );

    boolean existsByRestaurant_IdAndReservationCode(UUID restaurantId, String reservationCode);

    long countByBranch_IdAndStatusAndReservationStartBetween(
            UUID branchId,
            ReservationStatus status,
            OffsetDateTime from,
            OffsetDateTime to
    );
}
