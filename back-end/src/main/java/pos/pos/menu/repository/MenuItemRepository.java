package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.menu.entity.MenuItem;

import java.util.List;
import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    @Query("""
        SELECT i
        FROM MenuItem i
        JOIN FETCH i.section s
        WHERE s.menu.id = :menuId
        ORDER BY s.displayOrder ASC, s.name ASC, i.displayOrder ASC, i.name ASC
    """)
    List<MenuItem> findByMenuIdOrdered(UUID menuId);
}
