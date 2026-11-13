package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.inventory.entity.InventoryKind;
import pos.pos.inventory.enums.InventoryKindScope;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryKindRepository extends JpaRepository<InventoryKind, UUID> {

    // The restaurant's own kinds that are still there, by name.
    List<InventoryKind> findAllByRestaurant_IdAndAppliesToAndBuiltinCodeIsNullAndDeletedAtIsNullOrderByNameAsc(UUID restaurantId, InventoryKindScope appliesTo);

    // The built-in kinds the restaurant removed.
    List<InventoryKind> findAllByRestaurant_IdAndAppliesToAndBuiltinCodeIsNotNull(UUID restaurantId, InventoryKindScope appliesTo);

    Optional<InventoryKind> findByIdAndRestaurant_IdAndAppliesToAndBuiltinCodeIsNullAndDeletedAtIsNull(UUID id, UUID restaurantId, InventoryKindScope appliesTo);

    boolean existsByRestaurant_IdAndAppliesToAndBuiltinCodeIsNullAndDeletedAtIsNullAndNameIgnoreCase(UUID restaurantId, InventoryKindScope appliesTo, String name);
}
