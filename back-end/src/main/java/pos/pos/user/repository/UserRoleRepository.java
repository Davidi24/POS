package pos.pos.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.user.entity.UserRole;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserId(UUID userId);

    List<UserRole> findByUserIdIn(Collection<UUID> userIds);

    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);
}
