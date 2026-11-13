package pos.pos.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.inventory.entity.InventoryCount;
import pos.pos.inventory.enums.InventoryCountStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryCountRepository extends JpaRepository<InventoryCount, UUID> {

    Optional<InventoryCount> findByIdAndRestaurant_Id(UUID id, UUID restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select count from InventoryCount count where count.id = :countId and count.restaurant.id = :restaurantId")
    Optional<InventoryCount> findByIdAndRestaurantIdForUpdate(
            @Param("countId") UUID countId,
            @Param("restaurantId") UUID restaurantId
    );

    List<InventoryCount> findAllByRestaurant_IdOrderByCreatedAtDesc(UUID restaurantId);

    List<InventoryCount> findAllByRestaurant_IdAndStatusOrderByCreatedAtDesc(
            UUID restaurantId,
            InventoryCountStatus status
    );

    @Query(value = """
            select count.id from InventoryCount count
            where count.restaurant.id = :restaurantId
              and (:hasStatus = false or count.status = :status)
            order by count.createdAt desc, count.id desc
            """, countQuery = """
            select count(count.id) from InventoryCount count
            where count.restaurant.id = :restaurantId
              and (:hasStatus = false or count.status = :status)
            """)
    Page<UUID> findCountIds(
            @Param("restaurantId") UUID restaurantId,
            @Param("hasStatus") boolean hasStatus,
            @Param("status") InventoryCountStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"location", "approvedByUser", "createdByUser", "updatedByUser", "lines", "lines.inventoryItem"})
    List<InventoryCount> findAllByIdIn(Iterable<UUID> ids);

    // A count not approved or cancelled yet still holds a line for the item (approving it would change its stock).
    @Query("""
            select case when count(line) > 0 then true else false end from InventoryCountLine line
            where line.inventoryItem.id = :itemId
              and line.inventoryCount.status in (pos.pos.inventory.enums.InventoryCountStatus.DRAFT,
                  pos.pos.inventory.enums.InventoryCountStatus.IN_PROGRESS, pos.pos.inventory.enums.InventoryCountStatus.COMPLETED)
            """)
    boolean itemInOpenCount(@Param("itemId") UUID itemId);

    boolean existsByLocation_IdAndStatusIn(UUID locationId, java.util.Collection<pos.pos.inventory.enums.InventoryCountStatus> statuses);
}
