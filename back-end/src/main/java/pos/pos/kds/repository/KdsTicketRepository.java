package pos.pos.kds.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.kds.entity.KdsTicket;
import pos.pos.kds.enums.KdsTicketStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KdsTicketRepository extends JpaRepository<KdsTicket, UUID> {

    @EntityGraph(attributePaths = {
            "station",
            "station.device",
            "order",
            "order.customer",
            "order.restaurantTable",
            "items",
            "items.orderLineItem"
    })
    List<KdsTicket> findAllByOrder_IdOrderByCreatedAtAsc(UUID orderId);

    @EntityGraph(attributePaths = {
            "station",
            "station.device",
            "order",
            "order.customer",
            "order.restaurantTable",
            "items",
            "items.orderLineItem"
    })
    Optional<KdsTicket> findByIdAndBranch_Id(UUID ticketId, UUID branchId);

    @EntityGraph(attributePaths = {
            "station",
            "station.device",
            "order",
            "order.customer",
            "order.restaurantTable",
            "items",
            "items.orderLineItem"
    })
    List<KdsTicket> findAllByBranch_IdOrderByCreatedAtAsc(UUID branchId);

    @EntityGraph(attributePaths = {
            "station",
            "station.device",
            "order",
            "order.customer",
            "order.restaurantTable",
            "items",
            "items.orderLineItem"
    })
    List<KdsTicket> findAllByBranch_IdAndStatusInOrderByCreatedAtAsc(UUID branchId, Collection<KdsTicketStatus> statuses);

    @EntityGraph(attributePaths = {
            "station",
            "station.device",
            "order",
            "order.customer",
            "order.restaurantTable",
            "items",
            "items.orderLineItem"
    })
    Optional<KdsTicket> findTopByOrder_IdAndStation_IdAndStatusInOrderByCreatedAtDesc(
            UUID orderId,
            UUID stationId,
            Collection<KdsTicketStatus> statuses
    );

    boolean existsByRestaurant_IdAndTicketNumber(UUID restaurantId, String ticketNumber);

    boolean existsByOrder_Id(UUID orderId);
}
