package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.menu.entity.MenuVariant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuVariantRepository extends JpaRepository<MenuVariant, UUID> {

    @Override
    @EntityGraph(attributePaths = {"menuItem", "menuItem.section", "menuItem.section.menu", "menuItem.section.menu.restaurant"})
    Optional<MenuVariant> findById(UUID id);

    List<MenuVariant> findByMenuItemIdOrderByDisplayOrderAscNameAsc(UUID menuItemId);

    @Query("""
            SELECT variant
            FROM MenuVariant variant
            WHERE variant.menuItem.id IN :menuItemIds
            ORDER BY variant.menuItem.id ASC, variant.displayOrder ASC, variant.name ASC, variant.id ASC
            """)
    List<MenuVariant> findByMenuItemIdInOrdered(List<UUID> menuItemIds);

    boolean existsByMenuItemId(UUID menuItemId);

    boolean existsByMenuItemIdAndName(UUID menuItemId, String name);

    boolean existsByMenuItemIdAndNameAndIdNot(UUID menuItemId, String name, UUID id);
}
