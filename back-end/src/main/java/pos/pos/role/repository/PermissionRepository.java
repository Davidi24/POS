package pos.pos.role.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.role.entity.Permission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCode(String code);

    Optional<Permission> findByName(String name);

    @Query("""
            SELECT p.code
            FROM RolePermission rp
            JOIN Permission p ON rp.permissionId = p.id
            WHERE rp.roleId IN :roleIds
            """)
    List<String> findCodesByRoleIds(List<UUID> roleIds);

    // Permissions switched off for this one person (taken away from what their roles grant).
    @Query("select d.permissionCode from UserPermissionDenial d where d.userId = :userId")
    List<String> findDeniedCodesByUserId(@Param("userId") UUID userId);

    // Permissions given to this one person on top of their roles.
    @Query("select g.permissionCode from UserPermissionGrant g where g.userId = :userId")
    List<String> findGrantedCodesByUserId(@Param("userId") UUID userId);

    // Each role's own permissions, as (role id, permission code) pairs.
    @Query("""
            SELECT rp.roleId, p.code
            FROM RolePermission rp
            JOIN Permission p ON rp.permissionId = p.id
            WHERE rp.roleId IN :roleIds
            """)
    List<Object[]> findRoleCodePairs(@Param("roleIds") List<UUID> roleIds);

    // One restaurant's changes to these (built-in) roles, as (role id, permission code, granted) rows.
    @Query("""
            SELECT r.roleId, r.permissionCode, r.granted
            FROM RestaurantRolePermission r
            WHERE r.restaurantId = :restaurantId AND r.roleId IN :roleIds
            """)
    List<Object[]> findRestaurantRoleChanges(@Param("restaurantId") UUID restaurantId, @Param("roleIds") List<UUID> roleIds);

    /** Built-in roles switched off in a restaurant: they give nothing there. */
    @org.springframework.data.jpa.repository.Query("select s.roleId from RolePaySetting s where s.restaurantId = :restaurantId and s.roleId in :roleIds and s.active = false")
    List<UUID> findRoleIdsSwitchedOff(@Param("restaurantId") UUID restaurantId, @Param("roleIds") List<UUID> roleIds);
}
