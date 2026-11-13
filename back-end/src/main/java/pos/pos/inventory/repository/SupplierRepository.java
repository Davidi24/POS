package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.inventory.entity.Supplier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    List<Supplier> findAllByRestaurantIdAndDeletedAtIsNullOrderByNameAsc(UUID restaurantId);

    Optional<Supplier> findByIdAndRestaurantIdAndDeletedAtIsNull(UUID id, UUID restaurantId);

    @Query("select s from Supplier s where s.restaurantId = :restaurantId and s.deletedAt is null and lower(s.name) = lower(:name)")
    Optional<Supplier> findLiveByName(@Param("restaurantId") UUID restaurantId, @Param("name") String name);

    // A renamed supplier: the items they're the supplier of carry the new name too.
    @Modifying
    @Query("update InventoryItem i set i.supplierName = :name where i.supplierId = :supplierId")
    int renameOnItems(@Param("supplierId") UUID supplierId, @Param("name") String name);
}
