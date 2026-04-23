package pos.pos.restaurant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.restaurant.entity.BranchAddress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchAddressRepository extends JpaRepository<BranchAddress, UUID> {

    List<BranchAddress> findAllByBranchIdAndDeletedAtIsNullOrderByIsPrimaryDescCreatedAtAsc(UUID branchId);

    Optional<BranchAddress> findByIdAndBranchIdAndDeletedAtIsNull(UUID id, UUID branchId);

    Optional<BranchAddress> findByBranchIdAndIsPrimaryTrueAndDeletedAtIsNull(UUID branchId);
}
