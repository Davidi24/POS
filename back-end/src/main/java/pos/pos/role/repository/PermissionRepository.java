package pos.pos.role.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}