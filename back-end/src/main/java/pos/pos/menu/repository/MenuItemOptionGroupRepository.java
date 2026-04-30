package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.menu.entity.MenuItemOptionGroup;

import java.util.List;
import java.util.UUID;

public interface MenuItemOptionGroupRepository extends JpaRepository<MenuItemOptionGroup, UUID> {

    @Query("""
            SELECT link
            FROM MenuItemOptionGroup link
            JOIN FETCH link.optionGroup group
            WHERE link.menuItem.id = :menuItemId
            ORDER BY link.displayOrder ASC, group.name ASC, link.id ASC
            """)
    List<MenuItemOptionGroup> findByMenuItemIdOrdered(UUID menuItemId);

    boolean existsByMenuItemId(UUID menuItemId);

    boolean existsByOptionGroupId(UUID optionGroupId);
}
