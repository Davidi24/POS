package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.menu.entity.MenuSection;

import java.util.List;
import java.util.UUID;

public interface MenuSectionRepository extends JpaRepository<MenuSection, UUID> {

    List<MenuSection> findByMenuIdOrderByDisplayOrderAscNameAsc(UUID menuId);

    boolean existsByMenuId(UUID menuId);
}
