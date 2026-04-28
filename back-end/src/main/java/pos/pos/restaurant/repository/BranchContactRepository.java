package pos.pos.restaurant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.restaurant.entity.BranchContact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchContactRepository extends JpaRepository<BranchContact, UUID> {

    List<BranchContact> findAllByBranchIdAndDeletedAtIsNullOrderByIsPrimaryDescCreatedAtAsc(UUID branchId);

    Optional<BranchContact> findByIdAndBranchIdAndDeletedAtIsNull(UUID id, UUID branchId);

    Optional<BranchContact> findByBranchIdAndIsPrimaryTrueAndDeletedAtIsNull(UUID branchId);

    // Clears all primary flags for a branch in one UPDATE to avoid the select→update race condition.
    @Modifying
    @Query("""
            UPDATE BranchContact c
            SET c.isPrimary = false, c.updatedBy = :actorId
            WHERE c.branch.id = :branchId
              AND c.isPrimary = true
              AND c.deletedAt IS NULL
            """)
    void clearAllPrimary(
            @Param("branchId") UUID branchId,
            @Param("actorId") UUID actorId
    );
}
