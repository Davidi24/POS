package pos.pos.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.order.entity.OrderDiscount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderDiscountRepository extends JpaRepository<OrderDiscount, UUID> {

    List<OrderDiscount> findAllByOrder_IdOrderByCreatedAtAsc(UUID orderId);

    Optional<OrderDiscount> findByIdAndOrder_Id(UUID discountId, UUID orderId);
}
