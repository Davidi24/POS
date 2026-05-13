package pos.pos.settings.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.settings.entity.SettingsBusinessHour;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettingsBusinessHourRepository extends JpaRepository<SettingsBusinessHour, UUID> {

    List<SettingsBusinessHour> findAllByBranch_IdOrderByDayOfWeekAsc(UUID branchId);

    Optional<SettingsBusinessHour> findByBranch_IdAndDayOfWeek(UUID branchId, Integer dayOfWeek);

    void deleteAllByBranch_Id(UUID branchId);
}
