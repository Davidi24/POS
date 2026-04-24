package pos.pos.menu.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.menu.entity.Menu;

import java.util.Optional;
import java.util.UUID;

public interface MenuRepository extends JpaRepository<Menu, UUID> {

    @EntityGraph(attributePaths = "restaurant")
    @Query(
            value = """
            SELECT m
            FROM Menu m
            JOIN m.restaurant r
            WHERE r.deletedAt IS NULL
              AND (
                    :superAdmin = true
                    OR (:actorRestaurantId IS NOT NULL AND r.id = :actorRestaurantId)
                    OR (:ownerId IS NOT NULL AND r.ownerId = :ownerId)
              )
              AND (:restaurantId IS NULL OR r.id = :restaurantId)
              AND (:active IS NULL OR m.active = :active)
              AND (
                    :searchLike IS NULL
                    OR lower(m.code) LIKE :searchLike
                    OR lower(m.name) LIKE :searchLike
              )
            """,
            countQuery = """
            SELECT COUNT(m)
            FROM Menu m
            JOIN m.restaurant r
            WHERE r.deletedAt IS NULL
              AND (
                    :superAdmin = true
                    OR (:actorRestaurantId IS NOT NULL AND r.id = :actorRestaurantId)
                    OR (:ownerId IS NOT NULL AND r.ownerId = :ownerId)
              )
              AND (:restaurantId IS NULL OR r.id = :restaurantId)
              AND (:active IS NULL OR m.active = :active)
              AND (
                    :searchLike IS NULL
                    OR lower(m.code) LIKE :searchLike
                    OR lower(m.name) LIKE :searchLike
              )
            """
    )
    Page<Menu> searchVisibleMenus(
            UUID restaurantId,
            Boolean active,
            String searchLike,
            boolean superAdmin,
            UUID actorRestaurantId,
            UUID ownerId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "restaurant")
    Optional<Menu> findByIdAndRestaurantDeletedAtIsNull(UUID id);

    boolean existsByRestaurantIdAndCode(UUID restaurantId, String code);

    boolean existsByRestaurantIdAndCodeAndIdNot(UUID restaurantId, String code, UUID id);
}
