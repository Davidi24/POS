package pos.pos.shift.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.shift.entity.ShiftTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftTemplateRepository extends JpaRepository<ShiftTemplate, UUID> {

    List<ShiftTemplate> findByRestaurantIdOrderBySortOrderAscNameAsc(UUID restaurantId);

    Optional<ShiftTemplate> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
