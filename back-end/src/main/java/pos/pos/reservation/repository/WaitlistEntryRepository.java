package pos.pos.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.reservation.entity.WaitlistEntry;
import pos.pos.reservation.enums.WaitlistStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {

    List<WaitlistEntry> findAllByBranchIdAndStatusOrderByCreatedAtAsc(UUID branchId, WaitlistStatus status);

    Optional<WaitlistEntry> findByIdAndRestaurantIdAndBranchId(UUID id, UUID restaurantId, UUID branchId);
}
