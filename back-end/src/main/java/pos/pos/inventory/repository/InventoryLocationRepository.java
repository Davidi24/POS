package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.inventory.entity.InventoryLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryLocationRepository extends JpaRepository<InventoryLocation, UUID> {

    Optional<InventoryLocation> findByIdAndRestaurant_Id(UUID id, UUID restaurantId);

    Optional<InventoryLocation> findByRestaurant_IdAndCode(UUID restaurantId, String code);

    List<InventoryLocation> findAllByRestaurant_IdAndActiveTrueOrderByNameAsc(UUID restaurantId);

    // The places the app lists: every place that wasn't deleted.
    List<InventoryLocation> findAllByRestaurant_IdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(UUID restaurantId);

    List<InventoryLocation> findAllByRestaurant_IdAndDeletedAtIsNullOrderByNameAsc(UUID restaurantId);

    Optional<InventoryLocation> findByIdAndRestaurant_IdAndDeletedAtIsNull(UUID id, UUID restaurantId);

    // The branch's Uncategorized place, where a deleted place's stock goes when it's kept.
    Optional<InventoryLocation> findFirstByRestaurant_IdAndLocationTypeAndBranchIsNullAndDeletedAtIsNullOrderByCreatedAtAsc(
            UUID restaurantId, pos.pos.inventory.enums.InventoryLocationType locationType);

    Optional<InventoryLocation> findFirstByRestaurant_IdAndLocationTypeAndBranch_IdAndDeletedAtIsNullOrderByCreatedAtAsc(
            UUID restaurantId, pos.pos.inventory.enums.InventoryLocationType locationType, UUID branchId);

    // How many places each kind has: [locationType, kind id or null, count].
    @Query("""
            select l.locationType, k.id, count(l) from InventoryLocation l left join l.kind k
            where l.restaurant.id = :restaurantId and l.deletedAt is null
            group by l.locationType, k.id
            """)
    List<Object[]> countsByKind(@Param("restaurantId") UUID restaurantId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InventoryLocation l set l.locationType = pos.pos.inventory.enums.InventoryLocationType.UNCATEGORIZED, l.kind = null
            where l.restaurant.id = :restaurantId and l.deletedAt is null and l.locationType = :type
              and l.locationType <> pos.pos.inventory.enums.InventoryLocationType.CUSTOM
            """)
    int uncategorizeType(@Param("restaurantId") UUID restaurantId,
                         @Param("type") pos.pos.inventory.enums.InventoryLocationType type);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InventoryLocation l set l.locationType = pos.pos.inventory.enums.InventoryLocationType.UNCATEGORIZED, l.kind = null
            where l.kind.id = :kindId
            """)
    int uncategorizeKind(@Param("kindId") UUID kindId);

    List<InventoryLocation> findAllByRestaurant_IdOrderByNameAsc(UUID restaurantId);
}
