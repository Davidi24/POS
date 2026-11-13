package pos.pos.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.order.entity.OrderItemOption;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderItemOptionRepository extends JpaRepository<OrderItemOption, UUID> {

    List<OrderItemOption> findAllByOrderLineItem_IdOrderByCreatedAtAsc(UUID orderLineItemId);

    Optional<OrderItemOption> findByIdAndOrderLineItem_Id(UUID optionId, UUID orderLineItemId);
}
