package pos.pos.settings.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.settings.entity.SettingsOrderRule;

import java.util.Optional;
import java.util.UUID;

public interface SettingsOrderRuleRepository extends JpaRepository<SettingsOrderRule, UUID> {

    Optional<SettingsOrderRule> findBySettings_Restaurant_Id(UUID restaurantId);
}
