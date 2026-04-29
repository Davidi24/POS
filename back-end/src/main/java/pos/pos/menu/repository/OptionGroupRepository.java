package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.menu.entity.OptionGroup;

import java.util.UUID;

public interface OptionGroupRepository extends JpaRepository<OptionGroup, UUID> {

    boolean existsByTypeId(UUID typeId);
}
