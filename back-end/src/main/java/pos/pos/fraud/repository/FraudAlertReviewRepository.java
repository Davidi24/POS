package pos.pos.fraud.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.fraud.entity.FraudAlertReview;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FraudAlertReviewRepository extends JpaRepository<FraudAlertReview, UUID> {

    List<FraudAlertReview> findAllByRestaurantIdAndAlertKeyIn(UUID restaurantId, Collection<String> alertKeys);

    Optional<FraudAlertReview> findByRestaurantIdAndAlertKey(UUID restaurantId, String alertKey);
}
