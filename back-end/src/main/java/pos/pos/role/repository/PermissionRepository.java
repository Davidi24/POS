package pos.pos.role.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.role.entity.Permission;

import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByName(String name);

}