package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.menu.entity.OnlineMenuSection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnlineMenuSectionRepository extends JpaRepository<OnlineMenuSection, UUID> {

    List<OnlineMenuSection> findByRestaurant_IdOrderByDisplayOrderAscNameAsc(UUID restaurantId);

    Optional<OnlineMenuSection> findByIdAndRestaurant_Id(UUID id, UUID restaurantId);

    Optional<OnlineMenuSection> findFirstByRestaurant_IdAndNameIgnoreCase(UUID restaurantId, String name);

    @Query("select coalesce(max(s.displayOrder), -1) from OnlineMenuSection s where s.restaurant.id = :restaurantId")
    int findMaxDisplayOrder(UUID restaurantId);
}
