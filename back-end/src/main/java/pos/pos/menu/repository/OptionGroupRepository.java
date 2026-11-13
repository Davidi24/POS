package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.menu.entity.OptionGroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OptionGroupRepository extends JpaRepository<OptionGroup, UUID> {

    @Override
    @EntityGraph(attributePaths = {"restaurant", "type"})
    Optional<OptionGroup> findById(UUID id);

    @Query("""
            SELECT optionGroup
            FROM OptionGroup optionGroup
            JOIN FETCH optionGroup.restaurant restaurant
            JOIN FETCH optionGroup.type optionGroupType
            WHERE restaurant.id = :restaurantId
              AND (:typeId IS NULL OR optionGroupType.id = :typeId)
              AND (:active IS NULL OR optionGroup.active = :active)
              AND (:searchLike IS NULL OR lower(optionGroup.name) LIKE :searchLike)
            ORDER BY optionGroup.displayOrder ASC, optionGroup.name ASC, optionGroup.id ASC
            """)
    List<OptionGroup> searchByRestaurant(UUID restaurantId, UUID typeId, Boolean active, String searchLike);

    boolean existsByRestaurantIdAndName(UUID restaurantId, String name);

    boolean existsByRestaurantIdAndNameAndIdNot(UUID restaurantId, String name, UUID id);

    boolean existsByTypeId(UUID typeId);
}
