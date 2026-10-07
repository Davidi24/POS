package pos.pos.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.reservation.entity.RestaurantEvent;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantEventRepository extends JpaRepository<RestaurantEvent, UUID> {

    // Active events touching the days from..to.
    @Query("""
            select e from RestaurantEvent e
            where e.restaurantId = :restaurantId and e.active = true
              and e.startDate <= :to and e.endDate >= :from
            order by e.startDate asc
            """)
    List<RestaurantEvent> findActiveBetween(
            @Param("restaurantId") UUID restaurantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    List<RestaurantEvent> findAllByRestaurantIdAndEndDateGreaterThanEqualOrderByStartDateAsc(UUID restaurantId, LocalDate from);

    Optional<RestaurantEvent> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
