package pos.pos.settings.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.settings.entity.SettingsReceipt;

import java.util.Optional;
import java.util.UUID;

public interface SettingsReceiptRepository extends JpaRepository<SettingsReceipt, UUID> {

    Optional<SettingsReceipt> findBySettings_Restaurant_Id(UUID restaurantId);
}
