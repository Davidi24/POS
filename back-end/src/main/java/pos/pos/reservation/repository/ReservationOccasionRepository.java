package pos.pos.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.reservation.entity.ReservationOccasion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationOccasionRepository extends JpaRepository<ReservationOccasion, UUID> {

    List<ReservationOccasion> findAllByRestaurantIdOrderByDisplayOrderAscNameAsc(UUID restaurantId);

    Optional<ReservationOccasion> findByRestaurantIdAndCode(UUID restaurantId, String code);
}
