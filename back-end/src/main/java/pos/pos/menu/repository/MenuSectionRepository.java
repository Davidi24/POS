package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.menu.entity.MenuSection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuSectionRepository extends JpaRepository<MenuSection, UUID> {

    @Override
    @EntityGraph(attributePaths = {"menu", "menu.restaurant"})
    Optional<MenuSection> findById(UUID id);

    @Query("select s from MenuSection s where s.menu.id = :menuId order by case when lower(trim(s.name)) = 'uncategorized' then 1 else 0 end, s.displayOrder, s.name, s.id")
    List<MenuSection> findByMenuIdOrderByDisplayOrderAscNameAsc(UUID menuId);

    @Query("select s from MenuSection s where s.menu.id = :menuId and s.active = true order by case when lower(trim(s.name)) = 'uncategorized' then 1 else 0 end, s.displayOrder, s.name, s.id")
    List<MenuSection> findByMenuIdAndActiveTrueOrderByDisplayOrderAscNameAsc(UUID menuId);

    @Query("select s from MenuSection s where s.menu.id = :menuId and s.active = :active order by case when lower(trim(s.name)) = 'uncategorized' then 1 else 0 end, s.displayOrder, s.name, s.id")
    List<MenuSection> findByMenuIdAndActiveOrderByDisplayOrderAscNameAsc(UUID menuId, boolean active);

    Optional<MenuSection> findByMenuIdAndName(UUID menuId, String name);

    boolean existsByMenuIdAndName(UUID menuId, String name);

    boolean existsByMenuIdAndNameAndIdNot(UUID menuId, String name, UUID id);
}
