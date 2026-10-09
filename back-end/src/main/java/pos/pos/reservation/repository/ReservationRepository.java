package pos.pos.reservation.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
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

    @Query(value = """
            select r.id from Reservation r
            where r.branch.id = :branchId and r.reservationStart >= :from and r.reservationStart < :to
            order by r.reservationStart asc, r.id asc
            """,
            countQuery = """
            select count(r.id) from Reservation r
            where r.branch.id = :branchId and r.reservationStart >= :from and r.reservationStart < :to
            """)
    Page<UUID> findReservationIdsForBranchInWindow(
            @Param("branchId") UUID branchId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable
    );

    @Query(value = """
            select r.id from Reservation r
            where r.branch.id = :branchId
              and (:hasFrom = false or r.reservationStart >= :from)
              and (:hasTo = false or r.reservationStart <= :to)
              and (:hasStatus = false or r.status = :status)
              and (:hasCustomer = false or r.customer.id = :customerId)
            order by r.reservationStart asc, r.id asc
            """, countQuery = """
            select count(r.id) from Reservation r
            where r.branch.id = :branchId
              and (:hasFrom = false or r.reservationStart >= :from)
              and (:hasTo = false or r.reservationStart <= :to)
              and (:hasStatus = false or r.status = :status)
              and (:hasCustomer = false or r.customer.id = :customerId)
            """)
    Page<UUID> findReservationIdsForBranch(
            @Param("branchId") UUID branchId,
            @Param("hasFrom") boolean hasFrom,
            @Param("from") OffsetDateTime from,
            @Param("hasTo") boolean hasTo,
            @Param("to") OffsetDateTime to,
            @Param("hasStatus") boolean hasStatus,
            @Param("status") ReservationStatus status,
            @Param("hasCustomer") boolean hasCustomer,
            @Param("customerId") UUID customerId,
            Pageable pageable
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

    @Query(value = """
            select r.id from Reservation r
            where r.customer.id = :customerId and r.restaurant.id = :restaurantId
            order by r.reservationStart desc, r.id desc
            """, countQuery = """
            select count(r.id) from Reservation r
            where r.customer.id = :customerId and r.restaurant.id = :restaurantId
            """)
    Page<UUID> findReservationIdsForCustomer(
            @Param("customerId") UUID customerId,
            @Param("restaurantId") UUID restaurantId,
            Pageable pageable
    );

    // Keep collection loading out of the limited lookup query. Loading tableAssignments here makes Hibernate
    // apply the first-row limit in memory; callers that need assignments load them lazily inside their transaction.
    @EntityGraph(attributePaths = {"branch", "customer"})
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

    // Arrivals feed, one page at a time: guests still to come from a moment on, soonest first (id breaks ties so
    // pages never overlap).
    @Query(value = """
            select r.id from Reservation r
            where r.branch.id = :branchId and r.status in :statuses and r.reservationStart >= :from
            order by r.reservationStart asc, r.id asc
            """, countQuery = """
            select count(r) from Reservation r
            where r.branch.id = :branchId and r.status in :statuses and r.reservationStart >= :from
            """)
    Page<UUID> findUpcomingIdPage(
            @Param("branchId") UUID branchId,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("from") OffsetDateTime from,
            Pageable pageable
    );

    // Same feed for one floor: a booking counts on a floor when one of its tables is there, and bookings without a
    // table show on every floor (same rule as the app's floor filter).
    @Query(value = """
            select r.id from Reservation r
            where r.branch.id = :branchId and r.status in :statuses and r.reservationStart >= :from
              and (not exists (select a.id from ReservationTableAssignment a where a.reservation = r)
                   or exists (select a.id from ReservationTableAssignment a
                              where a.reservation = r and lower(a.restaurantTable.floor) = lower(:floor)))
            order by r.reservationStart asc, r.id asc
            """, countQuery = """
            select count(r) from Reservation r
            where r.branch.id = :branchId and r.status in :statuses and r.reservationStart >= :from
              and (not exists (select a.id from ReservationTableAssignment a where a.reservation = r)
                   or exists (select a.id from ReservationTableAssignment a
                              where a.reservation = r and lower(a.restaurantTable.floor) = lower(:floor)))
            """)
    Page<UUID> findUpcomingIdPageOnFloor(
            @Param("branchId") UUID branchId,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("from") OffsetDateTime from,
            @Param("floor") String floor,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"branch", "customer", "tableAssignments", "tableAssignments.restaurantTable"})
    List<Reservation> findAllByIdInOrderByReservationStartAsc(Collection<UUID> ids);

    @EntityGraph(attributePaths = {"branch", "customer", "tableAssignments", "tableAssignments.restaurantTable"})
    List<Reservation> findAllByIdIn(Collection<UUID> ids);

    long countByBranch_IdAndStatusInAndReservationStartGreaterThanEqual(
            UUID branchId,
            Collection<ReservationStatus> statuses,
            OffsetDateTime from
    );

    // Confirmed bookings whose guests haven't come by the booking time. The job checks each one's hold (it depends on
    // the restaurant's settings). One reopened from no-show before holds existed is skipped, so a late guest isn't
    // flipped back.
    @EntityGraph(attributePaths = {"restaurant", "branch", "tableAssignments", "tableAssignments.restaurantTable"})
    @Query("""
            select r from Reservation r
            where r.status = :confirmed and r.reservationStart < :now
              and (r.holdUntil is not null or not exists (
                  select h.id from ReservationStatusHistory h
                  where h.reservation = r and h.oldStatus = :noShow
              ))
            """)
    List<Reservation> findConfirmedPastStart(
            @Param("confirmed") ReservationStatus confirmed,
            @Param("now") OffsetDateTime now,
            @Param("noShow") ReservationStatus noShow
    );

    // Requests nobody answered before the booking time (or before a reopened request's new hold).
    @EntityGraph(attributePaths = {"restaurant", "branch", "tableAssignments", "tableAssignments.restaurantTable"})
    @Query("""
            select r from Reservation r
            where r.status = :pending and coalesce(r.holdUntil, r.reservationStart) < :now
            """)
    List<Reservation> findUnansweredRequests(
            @Param("pending") ReservationStatus pending,
            @Param("now") OffsetDateTime now
    );

    // Visits still open from before a moment (e.g. left over from an earlier day).
    long countByBranch_IdAndStatusInAndReservationStartLessThan(
            UUID branchId,
            Collection<ReservationStatus> statuses,
            OffsetDateTime before
    );

    // No-shows of these guests (by customer, phone or email) at a restaurant, for the no-show warning.
    @Query("""
            select r from Reservation r left join r.customer c
            where r.restaurant.id = :restaurantId and r.status = :noShow
              and (c.id in :customerIds or r.contactPhone in :phones or r.contactEmail in :emails)
            order by r.reservationStart desc
            """)
    List<Reservation> findGuestNoShows(
            @Param("restaurantId") UUID restaurantId,
            @Param("noShow") ReservationStatus noShow,
            @Param("customerIds") Collection<UUID> customerIds,
            @Param("phones") Collection<String> phones,
            @Param("emails") Collection<String> emails
    );

    // Confirmed bookings whose guests haven't confirmed attendance yet, starting in this window.
    @EntityGraph(attributePaths = {"restaurant", "branch", "tableAssignments", "tableAssignments.restaurantTable"})
    @Query("""
            select r from Reservation r
            where r.status = :confirmed and r.attendanceConfirmedAt is null
              and r.reservationStart > :from and r.reservationStart <= :to
            order by r.reservationStart asc
            """)
    List<Reservation> findAwaitingAttendance(
            @Param("confirmed") ReservationStatus confirmed,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @EntityGraph(attributePaths = {"restaurant", "branch"})
    List<Reservation> findAllByStatusInAndReservationStartBetween(
            Collection<ReservationStatus> statuses,
            OffsetDateTime from,
            OffsetDateTime to
    );

    // Requests (pending) at a branch starting in this window, e.g. around a table that just became free.
    List<Reservation> findAllByBranch_IdAndStatusAndReservationStartBetween(
            UUID branchId,
            ReservationStatus status,
            OffsetDateTime from,
            OffsetDateTime to
    );

    // The guest's links find their booking by its secret token.
    @EntityGraph(attributePaths = {"restaurant", "branch", "customer", "tableAssignments", "tableAssignments.restaurantTable"})
    Optional<Reservation> findByGuestToken(String guestToken);

    boolean existsByRestaurant_IdAndReservationCode(UUID restaurantId, String reservationCode);

    long countByBranch_IdAndStatusAndReservationStartBetween(
            UUID branchId,
            ReservationStatus status,
            OffsetDateTime from,
            OffsetDateTime to
    );
}
