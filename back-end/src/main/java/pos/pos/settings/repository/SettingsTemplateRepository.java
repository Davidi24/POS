package pos.pos.settings.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.settings.entity.SettingsTemplate;

import java.util.UUID;

public interface SettingsTemplateRepository extends JpaRepository<SettingsTemplate, UUID> {
}
