package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.inventory.entity.InventoryItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    Optional<InventoryItem> findByIdAndRestaurant_IdAndDeletedAtIsNull(UUID id, UUID restaurantId);

    // How many items each kind has: [itemType, kind id or null, count].
    @Query("""
            select i.itemType, k.id, count(i) from InventoryItem i left join i.kind k
            where i.restaurant.id = :restaurantId and i.deletedAt is null
            group by i.itemType, k.id
            """)
    List<Object[]> countsByKind(@Param("restaurantId") UUID restaurantId);

    // A deleted kind's items go to Uncategorized.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InventoryItem i set i.itemType = pos.pos.inventory.enums.InventoryItemType.UNCATEGORIZED, i.kind = null
            where i.restaurant.id = :restaurantId and i.deletedAt is null and i.itemType = :type and i.itemType <> pos.pos.inventory.enums.InventoryItemType.CUSTOM
            """)
    int uncategorizeType(@Param("restaurantId") UUID restaurantId, @Param("type") pos.pos.inventory.enums.InventoryItemType type);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InventoryItem i set i.itemType = pos.pos.inventory.enums.InventoryItemType.UNCATEGORIZED, i.kind = null
            where i.kind.id = :kindId
            """)
    int uncategorizeKind(@Param("kindId") UUID kindId);

    Optional<InventoryItem> findByRestaurant_IdAndBarcodeAndDeletedAtIsNull(UUID restaurantId, String barcode);

    Optional<InventoryItem> findByRestaurant_IdAndCode(UUID restaurantId, String code);

    List<InventoryItem> findAllByRestaurant_IdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(UUID restaurantId);

    List<InventoryItem> findAllByRestaurant_IdAndActiveFalseAndDeletedAtIsNullOrderByNameAsc(UUID restaurantId);

    List<InventoryItem> findAllByRestaurant_IdAndDeletedAtIsNullOrderByNameAsc(UUID restaurantId);

    List<InventoryItem> findAllByRestaurant_IdAndNameContainingIgnoreCaseAndDeletedAtIsNullOrderByNameAsc(
            UUID restaurantId,
            String keyword
    );
}
