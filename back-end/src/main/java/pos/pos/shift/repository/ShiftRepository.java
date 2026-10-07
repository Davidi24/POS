package pos.pos.shift.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import pos.pos.shift.entity.Shift;
import pos.pos.user.entity.UserRole;
import java.time.OffsetDateTime;
import java.util.*;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {
    @Query("select s from Shift s where s.user.id = :userId and s.status in (pos.pos.shift.enums.ShiftStatus.OPEN, pos.pos.shift.enums.ShiftStatus.ON_BREAK)")
    Optional<Shift> findActive(UUID userId);

    @Query("""
        select s from Shift s where s.restaurant.id = :restaurantId and s.branch.id = :branchId
        and (:userId is null or s.user.id = :userId)
        and ((coalesce(s.scheduledStart, s.startedAt) < :to
        and coalesce(s.endedAt, s.scheduledEnd, s.startedAt) > :from)
        or s.status in (pos.pos.shift.enums.ShiftStatus.OPEN, pos.pos.shift.enums.ShiftStatus.ON_BREAK))
        order by coalesce(s.scheduledStart, s.startedAt), s.id
        """)
    List<Shift> inWindow(UUID restaurantId, UUID branchId, UUID userId, OffsetDateTime from, OffsetDateTime to, Pageable page);

    @Query("""
        select count(s) from Shift s where s.user.id = :userId and s.id <> :exclude
        and s.status not in (pos.pos.shift.enums.ShiftStatus.CANCELLED, pos.pos.shift.enums.ShiftStatus.MISSED)
        and (
            (coalesce(s.scheduledStart, s.startedAt) < :end
            and coalesce(s.scheduledEnd, s.endedAt, :end) > :start)
            or (s.startedAt is not null and s.startedAt < :end
            and coalesce(s.endedAt, :end) > :start)
        )
        """)
    long overlaps(UUID userId, UUID exclude, OffsetDateTime start, OffsetDateTime end);

    @Query("select s from Shift s where s.user.id = :userId and s.branch.id = :branchId and s.status = pos.pos.shift.enums.ShiftStatus.SCHEDULED and s.scheduledStart <= :latestStart and s.scheduledEnd >= :now order by s.scheduledStart")
    List<Shift> eligible(UUID userId, UUID branchId, OffsetDateTime now, OffsetDateTime latestStart);

    @Query("select count(s) from Shift s where s.user.id = :userId and s.id <> :exclude and s.startedAt is not null and s.startedAt < :end and coalesce(s.endedAt, :end) > :start")
    long attendanceOverlaps(UUID userId, UUID exclude, OffsetDateTime start, OffsetDateTime end);

    @Query("select ur.userId, r.name from UserRole ur, Role r where ur.roleId = r.id and ur.userId in :userIds and r.isActive = true and r.deletedAt is null order by r.rank, r.name")
    List<Object[]> roleNames(Collection<UUID> userIds);

    @Query("select u from User u where u.restaurantId = :restaurantId and u.deletedAt is null and u.isActive = true and (u.defaultBranchId is null or u.defaultBranchId = :branchId) order by u.firstName, u.lastName")
    List<pos.pos.user.entity.User> staff(UUID restaurantId, UUID branchId);
}
