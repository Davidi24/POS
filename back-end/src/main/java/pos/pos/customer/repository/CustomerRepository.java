package pos.pos.customer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.customer.entity.Customer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findAllByRestaurant_IdAndDeletedAtIsNullOrderByFirstNameAscLastNameAsc(UUID restaurantId);

    Optional<Customer> findByIdAndRestaurant_IdAndDeletedAtIsNull(UUID customerId, UUID restaurantId);

    boolean existsByRestaurant_IdAndCodeAndDeletedAtIsNull(UUID restaurantId, String code);
}
