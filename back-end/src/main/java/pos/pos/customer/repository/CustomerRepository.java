package pos.pos.customer.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.customer.entity.Customer;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Page<Customer> findAllByRestaurant_IdAndDeletedAtIsNull(UUID restaurantId, Pageable pageable);

    Optional<Customer> findByIdAndRestaurant_IdAndDeletedAtIsNull(UUID customerId, UUID restaurantId);

    boolean existsByRestaurant_IdAndCode(UUID restaurantId, String code);
}
