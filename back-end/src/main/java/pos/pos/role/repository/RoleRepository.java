package pos.pos.role.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.role.entity.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    List<Role> findByIdIn(List<UUID> ids);

    Optional<Role> findByName(String name);

    boolean existsByName(String name);
}