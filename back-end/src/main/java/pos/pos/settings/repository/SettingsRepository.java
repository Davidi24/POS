package pos.pos.settings.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.settings.entity.Settings;

import java.util.Optional;
import java.util.UUID;

public interface SettingsRepository extends JpaRepository<Settings, UUID> {

    Optional<Settings> findByRestaurant_Id(UUID restaurantId);
}
