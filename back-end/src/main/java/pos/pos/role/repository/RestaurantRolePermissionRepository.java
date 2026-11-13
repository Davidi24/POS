package pos.pos.role.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.role.entity.RestaurantRolePermission;

import java.util.List;
import java.util.UUID;

public interface RestaurantRolePermissionRepository extends JpaRepository<RestaurantRolePermission, UUID> {

    List<RestaurantRolePermission> findAllByRestaurantIdAndRoleId(UUID restaurantId, UUID roleId);

    @Modifying
    @Query("delete from RestaurantRolePermission r where r.restaurantId = :restaurantId and r.roleId = :roleId")
    void deleteAllForRole(@Param("restaurantId") UUID restaurantId, @Param("roleId") UUID roleId);
}
