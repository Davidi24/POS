package pos.pos.kds.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.kds.dto.KdsActionRequest;
import pos.pos.kds.dto.KdsTicketResponse;
import pos.pos.kds.entity.KdsTicket;
import pos.pos.kds.entity.KdsTicketItem;
import pos.pos.inventory.service.InventorySaleConsumptionService;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderEventType;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.service.OrderDomainSupport;
import pos.pos.order.service.OrderSupport;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KdsTicketWorkflowService {

    private final RestaurantScopeService restaurantScopeService;
    private final OrderSupport orderSupport;
    private final OrderDomainSupport orderDomainSupport;
    private final KdsSupport kdsSupport;
    private final KdsOrderSyncService kdsOrderSyncService;
    private final InventorySaleConsumptionService inventorySaleConsumptionService;

    @Transactional
    public KdsTicketResponse fireTicket(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID ticketId,
            KdsActionRequest request
    ) {
        return changeTicketStatus(authentication, restaurantId, branchId, ticketId, request, OrderLineItemStatus.FIRED, "KDS ticket fired");
    }

    @Transactional
    public KdsTicketResponse startTicket(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID ticketId,
            KdsActionRequest request
    ) {
        return changeTicketStatus(authentication, restaurantId, branchId, ticketId, request, OrderLineItemStatus.PREPARING, "KDS ticket started");
    }

    @Transactional
    public KdsTicketResponse readyTicket(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID ticketId,
            KdsActionRequest request
    ) {
        return changeTicketStatus(authentication, restaurantId, branchId, ticketId, request, OrderLineItemStatus.READY, "KDS ticket marked ready");
    }

    @Transactional
    public KdsTicketResponse completeTicket(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID ticketId,
            KdsActionRequest request
    ) {
        return changeTicketStatus(authentication, restaurantId, branchId, ticketId, request, OrderLineItemStatus.FULFILLED, "KDS ticket completed");
    }

    /**
     * A waiter took the ready food out: the ticket's ready items are served. Only ready food can be picked up; items
     * still cooking stay on the ticket for the kitchen.
     */
    @Transactional
    public KdsTicketResponse pickUpTicket(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID ticketId,
            KdsActionRequest request
    ) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        KdsTicket ticket = kdsSupport.requireTicketInBranch(branchId, ticketId);
        Order order = lockAndValidateOrder(ticket);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        orderDomainSupport.assertOrderEditable(order);

        boolean changed = false;
        for (KdsTicketItem ticketItem : ticket.getItems()) {
            OrderLineItem lineItem = ticketItem.getOrderLineItem();
            if (lineItem != null && lineItem.getStatus() == OrderLineItemStatus.READY) {
                changed |= applyLineItemStatus(order, lineItem, OrderLineItemStatus.FULFILLED, actorId);
            }
        }
        if (!changed) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Nothing on this ticket is ready yet. Refresh to see the latest.");
        }
        return saveAndSync(order, authentication, branchId, ticketId, request, "Picked up by the waiter", true);
    }

    @Transactional
    public KdsTicketResponse fireTicketItem(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID ticketId,
            UUID ticketItemId,
            KdsActionRequest request
    ) {
        return changeTicketItemStatus(authentication, restaurantId, branchId, ticketId, ticketItemId, request, OrderLineItemStatus.FIRED, "KDS item fired");
    }

    @Transactional
    public KdsTicketResponse startTicketItem(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID ticketId,
            UUID ticketItemId,
            KdsActionRequest request
    ) {
        return changeTicketItemStatus(authentication, restaurantId, branchId, ticketId, ticketItemId, request, OrderLineItemStatus.PREPARING, "KDS item started");
    }

    @Transactional
    public KdsTicketResponse readyTicketItem(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID ticketId,
            UUID ticketItemId,
            KdsActionRequest request
    ) {
        return changeTicketItemStatus(authentication, restaurantId, branchId, ticketId, ticketItemId, request, OrderLineItemStatus.READY, "KDS item marked ready");
    }

    @Transactional
    public KdsTicketResponse completeTicketItem(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID ticketId,
            UUID ticketItemId,
            KdsActionRequest request
    ) {
        return changeTicketItemStatus(authentication, restaurantId, branchId, ticketId, ticketItemId, request, OrderLineItemStatus.FULFILLED, "KDS item completed");
    }

    @Transactional
    public List<KdsTicketResponse> syncOrderTickets(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        orderDomainSupport.assertOrderEditable(order);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        return kdsSupport.mapper().mapTicketResponses(kdsOrderSyncService.syncFromCurrentOrderState(order, actorId));
    }

    private KdsTicketResponse changeTicketStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID ticketId,
            KdsActionRequest request,
            OrderLineItemStatus targetStatus,
            String fallbackNote
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        KdsTicket ticket = kdsSupport.requireTicketInBranch(branchId, ticketId);
        Order order = lockAndValidateOrder(ticket);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        orderDomainSupport.assertOrderEditable(order);

        boolean changed = false;
        for (KdsTicketItem ticketItem : ticket.getItems()) {
            changed |= applyLineItemStatus(order, ticketItem.getOrderLineItem(), targetStatus, actorId);
        }

        return saveAndSync(order, authentication, branchId, ticketId, request, fallbackNote, changed);
    }

    private KdsTicketResponse changeTicketItemStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID ticketId,
            UUID ticketItemId,
            KdsActionRequest request,
            OrderLineItemStatus targetStatus,
            String fallbackNote
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        KdsTicket ticket = kdsSupport.requireTicketInBranch(branchId, ticketId);
        Order order = lockAndValidateOrder(ticket);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        orderDomainSupport.assertOrderEditable(order);

        KdsTicketItem ticketItem = kdsSupport.requireTicketItem(ticket, ticketItemId);
        boolean changed = applyLineItemStatus(order, ticketItem.getOrderLineItem(), targetStatus, actorId);
        return saveAndSync(order, authentication, branchId, ticketId, request, fallbackNote, changed);
    }

    private KdsTicketResponse saveAndSync(
            Order order,
            Authentication authentication,
            UUID branchId,
            UUID ticketId,
            KdsActionRequest request,
            String fallbackNote,
            boolean changed
    ) {
        if (changed) {
            UUID actorId = restaurantScopeService.currentUserId(authentication);
            order.setUpdatedBy(actorId);
            orderSupport.recalculateTotals(order);
            orderSupport.addEvent(order, OrderEventType.ITEM_UPDATED, actionNote(request, fallbackNote), actorId);
            orderSupport.saveOrder(order);
            kdsOrderSyncService.syncFromCurrentOrderState(order, actorId, request == null ? null : request.getReason());
        }

        return kdsSupport.mapper().toTicketResponse(kdsSupport.requireTicketInBranch(branchId, ticketId));
    }

    private Order lockAndValidateOrder(KdsTicket ticket) {
        Order ticketOrder = ticket.getOrder();
        return orderSupport.requireOrder(ticketOrder.getRestaurant().getId(), ticketOrder.getId());
    }

    private boolean applyLineItemStatus(
            Order order,
            OrderLineItem lineItem,
            OrderLineItemStatus targetStatus,
            UUID actorId
    ) {
        if (lineItem == null || lineItem.getStatus() == null) {
            return false;
        }
        if (lineItem.getStatus() == OrderLineItemStatus.CANCELLED || lineItem.getStatus() == OrderLineItemStatus.VOIDED) {
            return false;
        }

        return switch (targetStatus) {
            case FIRED -> {
                if (lineItem.getStatus() == OrderLineItemStatus.PENDING) {
                    lineItem.setStatus(OrderLineItemStatus.FIRED);
                    yield true;
                }
                yield false;
            }
            case PREPARING -> {
                if (lineItem.getStatus() == OrderLineItemStatus.PENDING || lineItem.getStatus() == OrderLineItemStatus.FIRED) {
                    lineItem.setStatus(OrderLineItemStatus.PREPARING);
                    yield true;
                }
                yield false;
            }
            case READY -> {
                if (lineItem.getStatus() == OrderLineItemStatus.FULFILLED || lineItem.getStatus() == OrderLineItemStatus.READY) {
                    yield false;
                }
                lineItem.setStatus(OrderLineItemStatus.READY);
                yield true;
            }
            case FULFILLED -> {
                if (lineItem.getStatus() == OrderLineItemStatus.FULFILLED) {
                    yield false;
                }
                inventorySaleConsumptionService.consumeFulfilledLine(order, lineItem, actorId);
                lineItem.setStatus(OrderLineItemStatus.FULFILLED);
                yield true;
            }
            default -> false;
        };
    }

    private String actionNote(KdsActionRequest request, String fallbackNote) {
        if (request == null) {
            return fallbackNote;
        }
        if (request.getNote() != null && !request.getNote().isBlank()) {
            return request.getNote();
        }
        if (request.getReason() != null && !request.getReason().isBlank()) {
            return request.getReason();
        }
        return fallbackNote;
    }
}
