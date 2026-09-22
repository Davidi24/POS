package pos.pos.kds.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.kds.entity.KdsTicketItem;
import pos.pos.kds.enums.KdsTicketStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KdsTicketItemRepository extends JpaRepository<KdsTicketItem, UUID> {

    @EntityGraph(attributePaths = {"kdsTicket", "kdsTicket.station", "orderLineItem"})
    Optional<KdsTicketItem> findByIdAndKdsTicket_Id(UUID ticketItemId, UUID ticketId);

    @EntityGraph(attributePaths = {"kdsTicket", "kdsTicket.station", "orderLineItem"})
    List<KdsTicketItem> findAllByOrderLineItem_IdAndKdsTicket_StatusIn(UUID lineItemId, Collection<KdsTicketStatus> statuses);

    @EntityGraph(attributePaths = {"kdsTicket", "kdsTicket.station", "orderLineItem"})
    Optional<KdsTicketItem> findTopByOrderLineItem_IdAndKdsTicket_StatusInOrderByCreatedAtDesc(
            UUID lineItemId,
            Collection<KdsTicketStatus> statuses
    );

    boolean existsByOrderLineItem_Order_Id(UUID orderId);

    boolean existsByOrderLineItem_Id(UUID lineItemId);
}
