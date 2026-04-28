package pos.pos.restaurant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.restaurant.entity.Branch;

import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    Optional<Branch> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Branch> findByIdAndRestaurant_IdAndDeletedAtIsNull(UUID id, UUID restaurantId);
}
