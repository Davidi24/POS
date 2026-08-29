package pos.pos.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.kds.service.KdsOrderSyncService;
import pos.pos.order.dto.OrderActionRequest;
import pos.pos.order.dto.OrderMergeRequest;
import pos.pos.order.dto.OrderPaymentStatusRequest;
import pos.pos.order.dto.OrderResponse;
import pos.pos.order.dto.OrderSplitRequest;
import pos.pos.order.dto.OrderTransferBranchRequest;
import pos.pos.order.dto.OrderTransferTableRequest;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderDiscount;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderEventType;
import pos.pos.order.enums.OrderFulfillmentStatus;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.enums.OrderType;
import pos.pos.reservation.entity.Reservation;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderWorkflowService {

    private static final EnumSet<OrderPaymentStatus> CLOSED_PAYMENT_STATUSES = EnumSet.of(
            OrderPaymentStatus.PAID,
            OrderPaymentStatus.REFUNDED,
            OrderPaymentStatus.PARTIALLY_REFUNDED
    );

    private final RestaurantScopeService restaurantScopeService;
    private final OrderSupport orderSupport;
    private final OrderDomainSupport orderDomainSupport;
    private final KdsOrderSyncService kdsOrderSyncService;

    @Transactional
    public OrderResponse openOrder(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new AuthException("Only draft orders can be opened", HttpStatus.BAD_REQUEST);
        }

        order.setStatus(OrderStatus.OPEN);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.STATUS_UPDATED, orderDomainSupport.firstNote(request, "Order opened"), order.getUpdatedBy());

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse sendToKitchen(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        orderDomainSupport.assertOrderEditable(order);

        if (order.getStatus() == OrderStatus.DRAFT) {
            order.setStatus(OrderStatus.OPEN);
        }
        order.getLineItems().stream()
                .filter(orderSupport::isFinanciallyActive)
                .filter(lineItem -> lineItem.getStatus() == OrderLineItemStatus.PENDING)
                .forEach(lineItem -> lineItem.setStatus(OrderLineItemStatus.FIRED));
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        order.setFulfillmentStatus(OrderFulfillmentStatus.IN_PREPARATION);
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.SENT_TO_KITCHEN, orderDomainSupport.firstNote(request, "Order sent to kitchen"), order.getUpdatedBy());
        orderSupport.saveOrder(order);
        kdsOrderSyncService.syncFromCurrentOrderState(order, order.getUpdatedBy());
        return orderSupport.toResponse(order);
    }

    @Transactional
    public OrderResponse markOrderReady(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        orderDomainSupport.assertOrderEditable(order);

        order.getLineItems().stream()
                .filter(orderSupport::isFinanciallyActive)
                .filter(lineItem -> lineItem.getStatus() != OrderLineItemStatus.FULFILLED)
                .forEach(lineItem -> lineItem.setStatus(OrderLineItemStatus.READY));
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        order.setFulfillmentStatus(OrderFulfillmentStatus.READY);
        orderSupport.addEvent(order, OrderEventType.READY, orderDomainSupport.firstNote(request, "Order marked ready"), order.getUpdatedBy());
        orderSupport.saveOrder(order);
        kdsOrderSyncService.syncFromCurrentOrderState(order, order.getUpdatedBy());
        return orderSupport.toResponse(order);
    }

    @Transactional
    public OrderResponse fulfillOrder(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        orderDomainSupport.assertOrderEditable(order);

        order.getLineItems().stream()
                .filter(orderSupport::isFinanciallyActive)
                .forEach(lineItem -> lineItem.setStatus(OrderLineItemStatus.FULFILLED));
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.FULFILLED, orderDomainSupport.firstNote(request, "Order fulfilled"), order.getUpdatedBy());
        orderSupport.saveOrder(order);
        kdsOrderSyncService.syncFromCurrentOrderState(order, order.getUpdatedBy());
        return orderSupport.toResponse(order);
    }

    @Transactional
    public OrderResponse closeOrder(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        if (!EnumSet.of(OrderStatus.DRAFT, OrderStatus.OPEN).contains(order.getStatus())) {
            throw new AuthException("Only open orders can be closed", HttpStatus.BAD_REQUEST);
        }

        if (!orderSupport.loadSettings(order.getRestaurant()).isAllowOpenTickets()
                && !CLOSED_PAYMENT_STATUSES.contains(order.getPaymentStatus())) {
            throw new AuthException("This restaurant requires the order to be settled before closing", HttpStatus.BAD_REQUEST);
        }

        order.setStatus(OrderStatus.CLOSED);
        order.setClosedAt(OffsetDateTime.now(ZoneOffset.UTC));
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.CLOSED, orderDomainSupport.firstNote(request, "Order closed"), order.getUpdatedBy());

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse reopenOrder(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        if (order.getStatus() != OrderStatus.CLOSED) {
            throw new AuthException("Only closed orders can be reopened", HttpStatus.BAD_REQUEST);
        }
        if (!orderSupport.loadOrderRules(order.getRestaurant()).isReopenClosedOrdersEnabled()) {
            throw new AuthException("Reopening closed orders is disabled for this restaurant", HttpStatus.BAD_REQUEST);
        }

        order.setStatus(OrderStatus.OPEN);
        order.setClosedAt(null);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.REOPENED, orderDomainSupport.firstNote(request, "Order reopened"), order.getUpdatedBy());

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse cancelOrder(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        if (order.getStatus() == OrderStatus.CLOSED) {
            throw new AuthException("Closed orders must be reopened before they can be cancelled", HttpStatus.BAD_REQUEST);
        }
        if (EnumSet.of(OrderPaymentStatus.PAID, OrderPaymentStatus.PARTIALLY_PAID).contains(order.getPaymentStatus())) {
            throw new AuthException("Paid orders cannot be cancelled", HttpStatus.BAD_REQUEST);
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        order.getLineItems().stream()
                .filter(orderSupport::isFinanciallyActive)
                .forEach(lineItem -> lineItem.setStatus(OrderLineItemStatus.CANCELLED));
        orderSupport.recalculateTotals(order);
        orderDomainSupport.applyStatusSideEffects(order);
        orderSupport.addEvent(order, OrderEventType.CANCELLED, orderDomainSupport.firstNote(request, "Order cancelled"), order.getUpdatedBy());
        orderSupport.saveOrder(order);
        kdsOrderSyncService.syncFromCurrentOrderState(order, order.getUpdatedBy(), request == null ? null : request.getReason());
        return orderSupport.toResponse(order);
    }

    @Transactional
    public OrderResponse voidOrder(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        orderSupport.requireVoidReasonIfNeeded(order, request == null ? null : request.getReason());

        order.setStatus(OrderStatus.VOIDED);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        order.setPaymentStatus(OrderPaymentStatus.VOIDED);
        order.getLineItems().stream()
                .filter(orderSupport::isFinanciallyActive)
                .forEach(lineItem -> {
                    lineItem.setStatus(OrderLineItemStatus.VOIDED);
                    orderSupport.appendReasonToLineItem(lineItem, request == null ? null : request.getReason());
                });
        orderSupport.recalculateTotals(order);
        orderDomainSupport.applyStatusSideEffects(order);
        orderSupport.addEvent(order, OrderEventType.VOIDED, orderDomainSupport.firstNote(request, "Order voided"), order.getUpdatedBy());
        orderSupport.saveOrder(order);
        kdsOrderSyncService.syncFromCurrentOrderState(order, order.getUpdatedBy(), request == null ? null : request.getReason());
        return orderSupport.toResponse(order);
    }

    @Transactional
    public OrderResponse mergeOrders(
            Authentication authentication,
            UUID restaurantId,
            UUID targetOrderId,
            OrderMergeRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order targetOrder = orderSupport.requireOrder(restaurantId, targetOrderId);
        Order sourceOrder = orderSupport.requireOrder(restaurantId, request.getSourceOrderId());
        orderDomainSupport.assertOrderEditable(targetOrder);
        orderDomainSupport.assertOrderEditable(sourceOrder);
        if (Objects.equals(targetOrder.getId(), sourceOrder.getId())) {
            throw new AuthException("sourceOrderId must be different from the target order", HttpStatus.BAD_REQUEST);
        }
        if (!Objects.equals(targetOrder.getBranch().getId(), sourceOrder.getBranch().getId())) {
            throw new AuthException("Orders must belong to the same branch to be merged", HttpStatus.BAD_REQUEST);
        }
        if (!orderSupport.loadOrderRules(targetOrder.getRestaurant()).isMergeOrdersEnabled()) {
            throw new AuthException("Order merging is disabled for this restaurant", HttpStatus.BAD_REQUEST);
        }
        kdsOrderSyncService.assertNoTicketHistory(targetOrder, "Orders with KDS ticket history cannot be merged");
        kdsOrderSyncService.assertNoTicketHistory(sourceOrder, "Orders with KDS ticket history cannot be merged");

        UUID actorId = restaurantScopeService.currentUserId(authentication);
        for (OrderLineItem lineItem : orderSupport.activeLineItems(sourceOrder)) {
            targetOrder.addLineItem(orderSupport.cloneLineItem(lineItem));
        }
        for (OrderDiscount discount : sourceOrder.getDiscounts()) {
            targetOrder.addDiscount(orderSupport.cloneDiscount(discount, discount.getAmountApplied(), actorId));
        }

        new ArrayList<>(sourceOrder.getLineItems()).forEach(sourceOrder::removeLineItem);
        orderSupport.recalculateTotals(targetOrder);
        orderSupport.recalculateTotals(sourceOrder);
        orderSupport.pruneZeroValueDiscounts(sourceOrder);

        sourceOrder.setStatus(OrderStatus.VOIDED);
        sourceOrder.setPaymentStatus(OrderPaymentStatus.VOIDED);
        sourceOrder.setUpdatedBy(actorId);
        orderDomainSupport.applyStatusSideEffects(sourceOrder);
        targetOrder.setUpdatedBy(actorId);

        orderSupport.addEvent(targetOrder, OrderEventType.UPDATED,
                request.getNote() == null ? "Order merged from " + sourceOrder.getOrderNumber() : request.getNote(),
                actorId);
        orderSupport.addEvent(sourceOrder, OrderEventType.VOIDED,
                "Merged into " + targetOrder.getOrderNumber(),
                actorId);

        orderSupport.saveOrder(sourceOrder);
        return orderSupport.toResponse(orderSupport.saveOrder(targetOrder));
    }

    @Transactional
    public OrderResponse transferOrderTable(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            OrderTransferTableRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        orderDomainSupport.assertOrderEditable(order);
        if (!orderSupport.loadOrderRules(order.getRestaurant()).isTransferOrdersEnabled()) {
            throw new AuthException("Order transfers are disabled for this restaurant", HttpStatus.BAD_REQUEST);
        }

        order.setRestaurantTable(orderSupport.resolveTable(order.getBranch().getId(), request.getTableId()));
        order.setOrderType(OrderType.DINE_IN);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.TABLE_CHANGED,
                request.getNote() == null ? "Order transferred to a new table" : request.getNote(),
                order.getUpdatedBy());

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse transferOrderBranch(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            OrderTransferBranchRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        orderDomainSupport.assertOrderEditable(order);
        if (!orderSupport.loadOrderRules(order.getRestaurant()).isTransferOrdersEnabled()) {
            throw new AuthException("Order transfers are disabled for this restaurant", HttpStatus.BAD_REQUEST);
        }
        kdsOrderSyncService.assertNoTicketHistory(
                order,
                "Orders with KDS ticket history cannot be transferred to another branch"
        );

        Branch branch = orderSupport.resolveManagedBranch(authentication, restaurantId, request.getBranchId());
        Reservation reservation = orderSupport.resolveReservation(restaurantId, request.getReservationId());
        orderDomainSupport.requireReservationBranch(reservation, branch);

        order.setBranch(branch);
        order.setRestaurantTable(request.getTableId() == null ? null : orderSupport.resolveTable(branch.getId(), request.getTableId()));
        order.setReservation(reservation);
        if (reservation != null) {
            order.setOrderType(OrderType.DINE_IN);
            if (order.getCustomer() == null && reservation.getCustomer() != null) {
                order.setCustomer(reservation.getCustomer());
            }
        }
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.UPDATED,
                request.getNote() == null ? "Order transferred to a new branch" : request.getNote(),
                order.getUpdatedBy());

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse updatePaymentStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            OrderPaymentStatusRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        order.setPaymentStatus(request.getPaymentStatus());
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.PAYMENT_UPDATED,
                request.getNote() == null ? "Order payment status updated" : request.getNote(),
                order.getUpdatedBy());
        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse markPaid(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        return changePaymentStatus(authentication, restaurantId, orderId, OrderPaymentStatus.PAID,
                orderDomainSupport.firstNote(request, "Order marked paid"));
    }

    @Transactional
    public OrderResponse markPartiallyPaid(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        return changePaymentStatus(authentication, restaurantId, orderId, OrderPaymentStatus.PARTIALLY_PAID,
                orderDomainSupport.firstNote(request, "Order marked partially paid"));
    }

    @Transactional
    public OrderResponse markRefunded(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        return changePaymentStatus(authentication, restaurantId, orderId, OrderPaymentStatus.REFUNDED,
                orderDomainSupport.firstNote(request, "Order marked refunded"));
    }

    @Transactional
    public OrderResponse splitOrder(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            OrderSplitRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order sourceOrder = orderSupport.requireOrder(restaurantId, orderId);
        orderDomainSupport.assertOrderEditable(sourceOrder);
        if (!orderSupport.loadSettings(sourceOrder.getRestaurant()).isAllowSplitBills()) {
            throw new AuthException("Split bills are disabled for this restaurant", HttpStatus.BAD_REQUEST);
        }
        kdsOrderSyncService.assertNoLineItemHistory(sourceOrder, "Orders with KDS ticket history cannot be split");

        List<OrderLineItem> selectedLineItems = orderDomainSupport.resolveSplitLineItems(sourceOrder, request.getLineItemIds());
        if (selectedLineItems.size() >= orderSupport.activeLineItems(sourceOrder).size()) {
            throw new AuthException("At least one active line item must remain on the source order", HttpStatus.BAD_REQUEST);
        }

        UUID actorId = restaurantScopeService.currentUserId(authentication);
        Order newOrder = new Order();
        newOrder.setRestaurant(sourceOrder.getRestaurant());
        newOrder.setBranch(sourceOrder.getBranch());
        newOrder.setRestaurantTable(request.getTargetTableId() == null
                ? sourceOrder.getRestaurantTable()
                : orderSupport.resolveTable(sourceOrder.getBranch().getId(), request.getTargetTableId()));
        newOrder.setReservation(sourceOrder.getReservation());
        newOrder.setCustomer(sourceOrder.getCustomer());
        newOrder.setCreatedBy(actorId);
        newOrder.setUpdatedBy(actorId);
        newOrder.setOrderNumber(request.getNewOrderNumber() == null
                ? orderSupport.nextOrderNumber(sourceOrder.getRestaurant())
                : request.getNewOrderNumber());
        newOrder.setCurrency(sourceOrder.getCurrency());
        newOrder.setOrderType(sourceOrder.getOrderType());
        newOrder.setSource(sourceOrder.getSource());
        newOrder.setStatus(OrderStatus.OPEN);
        newOrder.setPaymentStatus(OrderPaymentStatus.UNPAID);
        newOrder.setGuestCount(request.getGuestCount() == null ? sourceOrder.getGuestCount() : request.getGuestCount());
        newOrder.setNotes(request.getNotes() == null ? sourceOrder.getNotes() : request.getNotes());
        newOrder.setOpenedAt(OffsetDateTime.now(ZoneOffset.UTC));

        for (OrderLineItem lineItem : selectedLineItems) {
            newOrder.addLineItem(orderSupport.cloneLineItem(lineItem));
            sourceOrder.removeLineItem(lineItem);
        }
        for (OrderDiscount discount : sourceOrder.getDiscounts()) {
            newOrder.addDiscount(orderSupport.cloneDiscount(discount, discount.getAmountApplied(), actorId));
        }

        sourceOrder.setUpdatedBy(actorId);
        orderSupport.recalculateTotals(newOrder);
        orderSupport.recalculateTotals(sourceOrder);
        orderSupport.pruneZeroValueDiscounts(sourceOrder);
        orderSupport.addEvent(newOrder, OrderEventType.UPDATED, "Order created from split", actorId);
        orderSupport.addEvent(sourceOrder, OrderEventType.UPDATED, "Order split completed", actorId);

        orderSupport.saveOrder(sourceOrder);
        return orderSupport.toResponse(orderSupport.saveOrder(newOrder));
    }

    private OrderResponse changePaymentStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            OrderPaymentStatus status,
            String note
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        order.setPaymentStatus(status);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.PAYMENT_UPDATED, note, order.getUpdatedBy());
        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }
}
