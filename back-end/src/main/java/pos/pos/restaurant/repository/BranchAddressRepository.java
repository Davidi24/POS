package pos.pos.restaurant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.restaurant.entity.BranchAddress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchAddressRepository extends JpaRepository<BranchAddress, UUID> {

    List<BranchAddress> findAllByBranchIdAndDeletedAtIsNullOrderByIsPrimaryDescCreatedAtAsc(UUID branchId);

    Optional<BranchAddress> findByIdAndBranchIdAndDeletedAtIsNull(UUID id, UUID branchId);

    Optional<BranchAddress> findByBranchIdAndIsPrimaryTrueAndDeletedAtIsNull(UUID branchId);

    // Atomically clears the primary flag on all addresses for a branch, optionally keeping one.
    @Modifying
    @Query("""
            UPDATE BranchAddress a
            SET a.isPrimary = false, a.updatedBy = :actorId
            WHERE a.branch.id = :branchId
              AND a.isPrimary = true
              AND a.deletedAt IS NULL
              AND (:excludeId IS NULL OR a.id != :excludeId)
            """)
    void clearPrimary(
            @Param("branchId") UUID branchId,
            @Param("excludeId") UUID excludeId,
            @Param("actorId") UUID actorId
    );
}
