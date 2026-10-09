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
}
