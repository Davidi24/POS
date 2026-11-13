package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.menu.entity.OptionItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OptionItemRepository extends JpaRepository<OptionItem, UUID> {

    @Override
    @EntityGraph(attributePaths = {"optionGroup", "optionGroup.restaurant", "optionGroup.type"})
    Optional<OptionItem> findById(UUID id);

    List<OptionItem> findByOptionGroupIdOrderByDisplayOrderAscNameAsc(UUID optionGroupId);

    List<OptionItem> findByOptionGroupIdAndAvailableOrderByDisplayOrderAscNameAsc(UUID optionGroupId, boolean available);

    boolean existsByOptionGroupId(UUID optionGroupId);

    boolean existsByOptionGroupIdAndName(UUID optionGroupId, String name);

    boolean existsByOptionGroupIdAndNameAndIdNot(UUID optionGroupId, String name, UUID id);
}
