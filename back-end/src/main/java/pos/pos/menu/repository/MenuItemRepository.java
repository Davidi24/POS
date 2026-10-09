package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import pos.pos.menu.entity.MenuItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    @Override
    @EntityGraph(attributePaths = {"section", "section.menu", "section.menu.restaurant"})
    Optional<MenuItem> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from MenuItem i where i.id = :itemId")
    Optional<MenuItem> findByIdForUpdate(@Param("itemId") UUID itemId);

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

    @Query("""
        SELECT DISTINCT i
        FROM MenuItem i
        JOIN FETCH i.section s
        JOIN FETCH s.menu m
        JOIN FETCH m.restaurant r
        LEFT JOIN FETCH i.ingredients ingredient
        WHERE i.id IN :itemIds
        """)
    List<MenuItem> findAllForImportByIdIn(@Param("itemIds") List<UUID> itemIds);

    List<MenuItem> findBySectionIdOrderByDisplayOrderAscNameAsc(UUID sectionId);

    List<MenuItem> findBySectionIdAndAvailableOrderByDisplayOrderAscNameAsc(UUID sectionId, boolean available);

    boolean existsBySectionId(UUID sectionId);

    @Query("""
        SELECT COUNT(i)
        FROM MenuItem i
        JOIN i.section s
        WHERE s.menu.id = :menuId
    """)
    long countByMenuId(UUID menuId);

    @Query("""
        SELECT s.menu.id AS menuId, COUNT(i) AS itemCount
        FROM MenuItem i
        JOIN i.section s
        WHERE s.menu.id IN :menuIds
        GROUP BY s.menu.id
    """)
    List<MenuItemCountRow> countItemsByMenuIds(List<UUID> menuIds);

    interface MenuItemCountRow {
        UUID getMenuId();
        Long getItemCount();
    }

    // Every dish placed in the restaurant's online menu, with its staff menu and section for the visibility checks.
    @Query("""
            select i from MenuItem i
            join fetch i.section s
            join fetch s.menu m
            join fetch i.onlineSection o
            where o.restaurant.id = :restaurantId
            order by i.onlineDisplayOrder asc, i.name asc
            """)
    List<MenuItem> findOnlineByRestaurantId(UUID restaurantId);

    long countByOnlineSection_Id(UUID onlineSectionId);

    List<MenuItem> findByOnlineSection_Id(UUID onlineSectionId);

    @Query("select coalesce(max(i.onlineDisplayOrder), -1) from MenuItem i where i.onlineSection.id = :onlineSectionId")
    int findMaxOnlineDisplayOrder(UUID onlineSectionId);

    // Kitchen routing belongs to the dish: it goes when the dish is deleted (otherwise the delete is refused).
    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from KdsStationRouting r where r.menuItem.id in :itemIds")
    void deleteKdsRoutingsOf(@org.springframework.data.repository.query.Param("itemIds") java.util.Collection<UUID> itemIds);

    // Extras guests can order with a booking: available dishes in the restaurant's active special menus.
    @Query("""
            select i from MenuItem i
            where i.section.menu.restaurant.id = :restaurantId
              and i.section.menu.special = true and i.section.menu.active = true
              and i.section.active = true and i.available = true
            order by i.section.menu.displayOrder asc, i.section.displayOrder asc, i.displayOrder asc
            """)
    List<MenuItem> findSpecialMenuExtras(@org.springframework.data.repository.query.Param("restaurantId") UUID restaurantId);
}
