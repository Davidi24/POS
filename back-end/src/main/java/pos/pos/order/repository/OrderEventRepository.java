package pos.pos.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.order.entity.OrderEvent;

import java.util.List;
import java.util.UUID;

public interface OrderEventRepository extends JpaRepository<OrderEvent, UUID> {

    List<OrderEvent> findAllByOrder_IdOrderByCreatedAtDesc(UUID orderId);
}
