package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.menu.entity.MenuSection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuSectionRepository extends JpaRepository<MenuSection, UUID> {

    @Override
    @EntityGraph(attributePaths = {"menu", "menu.restaurant"})
    Optional<MenuSection> findById(UUID id);

    List<MenuSection> findByMenuIdOrderByDisplayOrderAscNameAsc(UUID menuId);

    List<MenuSection> findByMenuIdAndActiveTrueOrderByDisplayOrderAscNameAsc(UUID menuId);

    List<MenuSection> findByMenuIdAndActiveOrderByDisplayOrderAscNameAsc(UUID menuId, boolean active);

    boolean existsByMenuId(UUID menuId);

    boolean existsByMenuIdAndName(UUID menuId, String name);

    boolean existsByMenuIdAndNameAndIdNot(UUID menuId, String name, UUID id);
}
