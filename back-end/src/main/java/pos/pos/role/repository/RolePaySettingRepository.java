package pos.pos.role.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.role.entity.RolePaySetting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RolePaySettingRepository extends JpaRepository<RolePaySetting, UUID> {

    List<RolePaySetting> findByRestaurantId(UUID restaurantId);

    Optional<RolePaySetting> findByRestaurantIdAndRoleId(UUID restaurantId, UUID roleId);
}
