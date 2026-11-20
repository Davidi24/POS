package pos.pos.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderLineItemStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderLineItemRepository extends JpaRepository<OrderLineItem, UUID> {

    List<OrderLineItem> findAllByOrder_IdOrderByCreatedAtAsc(UUID orderId);

    List<OrderLineItem> findAllByOrder_IdAndStatusInOrderByCreatedAtAsc(
            UUID orderId,
            Collection<OrderLineItemStatus> statuses
    );

    Optional<OrderLineItem> findByIdAndOrder_Id(UUID lineItemId, UUID orderId);

    @Query("select item.order.id, sum(item.quantity) from OrderLineItem item " +
            "where item.order.id in :orderIds and item.status not in :excludedStatuses group by item.order.id")
    List<Object[]> sumActiveQuantitiesByOrderIds(@Param("orderIds") Collection<UUID> orderIds,
            @Param("excludedStatuses") Collection<OrderLineItemStatus> excludedStatuses);
}
