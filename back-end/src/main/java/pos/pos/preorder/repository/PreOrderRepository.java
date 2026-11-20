package pos.pos.preorder.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pos.pos.preorder.entity.PreOrder;
import pos.pos.preorder.enums.PreOrderStatus;
import pos.pos.reservation.enums.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PreOrderRepository extends JpaRepository<PreOrder, UUID> {

    // The live (scheduled or sent) pre-order, locked so placing, sending and cancelling never overlap.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PreOrder p where p.reservation.id = :reservationId and p.status in :statuses")
    Optional<PreOrder> lockLiveByReservationId(UUID reservationId, Collection<PreOrderStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PreOrder p where p.id = :id")
    Optional<PreOrder> lockById(UUID id);

    // The live one if there is one, otherwise the most recent (e.g. cancelled) for history.
    @Query("""
            select p from PreOrder p
            where p.reservation.id = :reservationId
            order by case when p.status in :liveStatuses then 0 else 1 end, p.createdAt desc
            """)
    List<PreOrder> findForReservation(UUID reservationId, Collection<PreOrderStatus> liveStatuses);

    boolean existsByReservation_Id(UUID reservationId);

    // Scheduled pre-orders whose booking starts before the horizon; the job then checks each one's own lead time.
    @Query("""
            select new pos.pos.preorder.repository.ScheduledPreOrderRef(p.id, p.reservation.id) from PreOrder p
            where p.status = pos.pos.preorder.enums.PreOrderStatus.SCHEDULED
              and p.reservation.reservationStart <= :horizon
            order by p.reservation.reservationStart asc
            """)
    List<ScheduledPreOrderRef> findScheduledStartingBefore(OffsetDateTime horizon);

    // Safety net: scheduled pre-orders whose booking already ended some other way (e.g. a missed event).
    @Query("""
            select new pos.pos.preorder.repository.ScheduledPreOrderRef(p.id, p.reservation.id) from PreOrder p
            where p.status = pos.pos.preorder.enums.PreOrderStatus.SCHEDULED
              and p.reservation.status in :reservationStatuses
            """)
    List<ScheduledPreOrderRef> findScheduledWithReservationStatusIn(Collection<ReservationStatus> reservationStatuses);

    @Query("""
            select p from PreOrder p
            where p.restaurant.id = :restaurantId
              and p.reservation.branch.id = :branchId
              and p.reservation.reservationStart >= :from
              and p.reservation.reservationStart < :to
              and (:status is null or p.status = :status)
            order by p.reservation.reservationStart asc, p.createdAt asc
            """)
    List<PreOrder> findForBranch(UUID restaurantId, UUID branchId, OffsetDateTime from, OffsetDateTime to, PreOrderStatus status);
}
