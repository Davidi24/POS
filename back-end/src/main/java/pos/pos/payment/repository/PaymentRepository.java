package pos.pos.payment.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import pos.pos.payment.entity.Payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    @EntityGraph(attributePaths = {"transactions"})
    @Query("select p from Payment p where p.id = :paymentId and p.restaurant.id = :restaurantId")
    Optional<Payment> findDetailed(UUID paymentId, UUID restaurantId);

    @EntityGraph(attributePaths = {"transactions"})
    @Query("select p from Payment p where p.order.id = :orderId order by p.paidAt asc, p.createdAt asc")
    List<Payment> findByOrderIdDetailed(UUID orderId);

    boolean existsByOrder_IdAndReferenceNumber(UUID orderId, String referenceNumber);
}
