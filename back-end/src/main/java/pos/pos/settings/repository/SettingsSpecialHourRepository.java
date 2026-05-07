package pos.pos.settings.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.settings.entity.SettingsSpecialHour;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettingsSpecialHourRepository extends JpaRepository<SettingsSpecialHour, UUID> {

    List<SettingsSpecialHour> findAllByBranch_IdOrderBySpecialDateAsc(UUID branchId);

    List<SettingsSpecialHour> findAllByBranch_IdAndSpecialDateBetweenOrderBySpecialDateAsc(
            UUID branchId,
            LocalDate startDate,
            LocalDate endDate
    );

    Optional<SettingsSpecialHour> findByIdAndBranch_Id(UUID specialHourId, UUID branchId);
}
