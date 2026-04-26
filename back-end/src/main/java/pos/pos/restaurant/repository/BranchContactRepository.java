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

    // Atomically clears the primary flag on all contacts for a branch, optionally keeping one.
    @Modifying
    @Query("""
            UPDATE BranchContact c
            SET c.isPrimary = false, c.updatedBy = :actorId
            WHERE c.branch.id = :branchId
              AND c.isPrimary = true
              AND c.deletedAt IS NULL
              AND (:excludeId IS NULL OR c.id != :excludeId)
            """)
    void clearPrimary(
            @Param("branchId") UUID branchId,
            @Param("excludeId") UUID excludeId,
            @Param("actorId") UUID actorId
    );
}
