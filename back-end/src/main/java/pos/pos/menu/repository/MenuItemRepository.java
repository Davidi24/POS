package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.menu.entity.MenuItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    @Override
    @EntityGraph(attributePaths = {"section", "section.menu", "section.menu.restaurant"})
    Optional<MenuItem> findById(UUID id);

    @Query("""
        SELECT i
        FROM MenuItem i
        JOIN FETCH i.section s
        WHERE s.menu.id = :menuId
        ORDER BY s.displayOrder ASC, s.name ASC, i.displayOrder ASC, i.name ASC
    """)
    List<MenuItem> findByMenuIdOrdered(UUID menuId);

    @Query("""
        SELECT i
        FROM MenuItem i
        JOIN FETCH i.section s
        WHERE s.menu.id = :menuId
          AND s.active = true
          AND i.available = true
        ORDER BY s.displayOrder ASC, s.name ASC, i.displayOrder ASC, i.name ASC
    """)
    List<MenuItem> findByMenuIdAndAvailableTrueOrdered(UUID menuId);

    List<MenuItem> findBySectionIdOrderByDisplayOrderAscNameAsc(UUID sectionId);

    List<MenuItem> findBySectionIdAndAvailableOrderByDisplayOrderAscNameAsc(UUID sectionId, boolean available);

    boolean existsBySectionId(UUID sectionId);
}
