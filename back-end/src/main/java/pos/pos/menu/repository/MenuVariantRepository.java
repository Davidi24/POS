package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.menu.entity.MenuVariant;

import java.util.UUID;

public interface MenuVariantRepository extends JpaRepository<MenuVariant, UUID> {
}
