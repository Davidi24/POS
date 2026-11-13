package pos.pos.shift.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pos.pos.shift.entity.ShiftPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftPlanRepository extends JpaRepository<ShiftPlan, UUID> {
    Optional<ShiftPlan> findByTemplateIdAndBranchId(UUID templateId, UUID branchId);

    List<ShiftPlan> findByTemplateId(UUID templateId);

    List<ShiftPlan> findByBranchId(UUID branchId);

    @Query("select p.id from ShiftPlan p")
    List<UUID> allIds();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ShiftPlan p where p.id = :id")
    Optional<ShiftPlan> lock(UUID id);
}
