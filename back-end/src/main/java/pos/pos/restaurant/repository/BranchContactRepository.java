package pos.pos.restaurant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.restaurant.entity.BranchContact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchContactRepository extends JpaRepository<BranchContact, UUID> {

    List<BranchContact> findAllByBranchIdAndDeletedAtIsNullOrderByIsPrimaryDescCreatedAtAsc(UUID branchId);

    Optional<BranchContact> findByIdAndBranchIdAndDeletedAtIsNull(UUID id, UUID branchId);

    Optional<BranchContact> findByBranchIdAndIsPrimaryTrueAndDeletedAtIsNull(UUID branchId);
}
