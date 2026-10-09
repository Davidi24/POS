package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.menu.entity.OnlineMenuSection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnlineMenuSectionRepository extends JpaRepository<OnlineMenuSection, UUID> {

    List<OnlineMenuSection> findByRestaurant_IdOrderByDisplayOrderAscNameAsc(UUID restaurantId);

    @Query("""
            select s.id as id, s.name as name, s.displayOrder as displayOrder, count(i.id) as itemCount
            from OnlineMenuSection s
            left join MenuItem i on i.onlineSection.id = s.id
            where s.restaurant.id = :restaurantId
            group by s.id, s.name, s.displayOrder
            order by s.displayOrder asc, s.name asc
            """)
    List<SectionSummaryRow> findSummariesByRestaurantId(@Param("restaurantId") UUID restaurantId);

    Optional<OnlineMenuSection> findByIdAndRestaurant_Id(UUID id, UUID restaurantId);

    Optional<OnlineMenuSection> findFirstByRestaurant_IdAndNameIgnoreCase(UUID restaurantId, String name);

    @Query("select coalesce(max(s.displayOrder), -1) from OnlineMenuSection s where s.restaurant.id = :restaurantId")
    int findMaxDisplayOrder(UUID restaurantId);

    interface SectionSummaryRow {
        UUID getId();
        String getName();
        int getDisplayOrder();
        long getItemCount();
    }
}
