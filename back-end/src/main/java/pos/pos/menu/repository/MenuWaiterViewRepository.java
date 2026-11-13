package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.menu.entity.MenuWaiterView;

import java.util.UUID;

public interface MenuWaiterViewRepository extends JpaRepository<MenuWaiterView, UUID> {
}
