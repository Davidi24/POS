package pos.pos.role.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.role.entity.RolePermission;

import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByRoleId(UUID roleId);

}