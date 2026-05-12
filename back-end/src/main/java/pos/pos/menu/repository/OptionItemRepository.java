package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.menu.entity.OptionItem;

import java.util.UUID;

public interface OptionItemRepository extends JpaRepository<OptionItem, UUID> {
}
