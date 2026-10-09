package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.inventory.entity.InventoryLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryLocationRepository extends JpaRepository<InventoryLocation, UUID> {

    Optional<InventoryLocation> findByIdAndRestaurant_Id(UUID id, UUID restaurantId);

    Optional<InventoryLocation> findByRestaurant_IdAndCode(UUID restaurantId, String code);

    List<InventoryLocation> findAllByRestaurant_IdAndActiveTrueOrderByNameAsc(UUID restaurantId);

    List<InventoryLocation> findAllByRestaurant_IdOrderByNameAsc(UUID restaurantId);
}
