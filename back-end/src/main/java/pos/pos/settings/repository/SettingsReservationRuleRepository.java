package pos.pos.settings.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.settings.entity.SettingsReservationRule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettingsReservationRuleRepository extends JpaRepository<SettingsReservationRule, UUID> {

    List<SettingsReservationRule> findAllBySettings_Restaurant_IdOrderByPriorityAscCreatedAtAsc(UUID restaurantId);

    Optional<SettingsReservationRule> findByIdAndSettings_Restaurant_Id(UUID ruleId, UUID restaurantId);
}
