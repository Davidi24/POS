package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.menu.entity.MenuItemOptionGroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuItemOptionGroupRepository extends JpaRepository<MenuItemOptionGroup, UUID> {

    @Override
    @EntityGraph(attributePaths = {
            "menuItem",
            "menuItem.section",
            "menuItem.section.menu",
            "menuItem.section.menu.restaurant",
            "optionGroup",
            "optionGroup.restaurant",
            "optionGroup.type"
    })
    Optional<MenuItemOptionGroup> findById(UUID id);

    @Query("""
            SELECT link
            FROM MenuItemOptionGroup link
            JOIN FETCH link.optionGroup group
            WHERE link.menuItem.id = :menuItemId
            ORDER BY link.displayOrder ASC, group.name ASC, link.id ASC
            """)
    List<MenuItemOptionGroup> findByMenuItemIdOrdered(UUID menuItemId);

    @Query("""
            SELECT link
            FROM MenuItemOptionGroup link
            JOIN FETCH link.optionGroup group
            WHERE link.menuItem.id IN :menuItemIds
            ORDER BY link.menuItem.id ASC, link.displayOrder ASC, group.name ASC, link.id ASC
            """)
    List<MenuItemOptionGroup> findByMenuItemIdInOrdered(List<UUID> menuItemIds);

    boolean existsByMenuItemId(UUID menuItemId);

    boolean existsByMenuItemIdAndOptionGroupId(UUID menuItemId, UUID optionGroupId);

    boolean existsByOptionGroupId(UUID optionGroupId);
}
