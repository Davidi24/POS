package pos.pos.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.kds.service.KdsOrderSyncService;
import pos.pos.inventory.service.InventorySaleConsumptionService;
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
import pos.pos.tables.entity.RestaurantTable;

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
    private final InventorySaleConsumptionService inventorySaleConsumptionService;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final pos.pos.tables.repository.RestaurantTableRepository restaurantTableRepository;

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

    /**
     * The guests left without paying (user, 2026-11-01): the order is voided as unpaid. What was paid stays, the rest is
     * written down as owed, the table is freed and the chosen people are told. It can be reopened later for payment.
     */
    @Transactional
    public OrderResponse closeUnpaid(Authentication authentication, UUID restaurantId, UUID orderId, pos.pos.order.dto.OrderCloseUnpaidRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        if (order.getStatus() != OrderStatus.OPEN && order.getStatus() != OrderStatus.DRAFT) {
            throw new AuthException("Only an open order can be closed as unpaid", HttpStatus.BAD_REQUEST);
        }
        java.math.BigDecimal owed = orderSupport.balanceDue(order);
        if (owed.signum() <= 0) {
            throw new AuthException("Nothing is owed on this order. Close it instead", HttpStatus.BAD_REQUEST);
        }
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        String reason = request.getReason().trim();
        RestaurantTable table = order.getRestaurantTable();
        order.setUnpaidReason(pos.pos.order.enums.OrderUnpaidReason.OTHER);
        order.setUnpaidNote(reason);
        order.setUnpaidAmount(owed);
        order.setUnpaidAt(OffsetDateTime.now(ZoneOffset.UTC));
        order.setUnpaidBy(actorId);
        order.setUnpaidTableNumber(table == null ? null : table.getTableNumber());
        order.setRestaurantTable(null);
        order.setBillRequestedAt(null);
        // Its dishes and what was paid stay as they are, so reopening it shows the same bill.
        order.setStatus(OrderStatus.VOIDED);
        order.setUpdatedBy(actorId);
        orderSupport.addEvent(order, OrderEventType.VOIDED, "Closed as unpaid: " + reason, actorId);
        orderSupport.saveOrder(order);
        kdsOrderSyncService.stopOrder(order, actorId, "Closed as unpaid");
        if (table != null) {
            freeTable(table, actorId);
        }
        events.publishEvent(new pos.pos.order.event.OrderClosedUnpaidEvent(order.getRestaurant().getId(), order.getBranch().getId(), order.getBranch().getName(),
                order.getId(), order.getOrderNumber(), order.getUnpaidTableNumber(), owed, order.getCurrency(), reason, actorId));
        return orderSupport.toResponse(order);
    }

    // An order closed as unpaid comes back to take the payment; it's no longer on a table.
    @Transactional
    public OrderResponse reopenForPayment(Authentication authentication, UUID restaurantId, UUID orderId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        if (order.getStatus() != OrderStatus.VOIDED || order.getUnpaidAt() == null) {
            throw new AuthException("Only an order closed as unpaid can be reopened for payment", HttpStatus.BAD_REQUEST);
        }
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        order.setStatus(OrderStatus.OPEN);
        order.setClosedAt(null);
        // What was paid and its dishes are as they were, so the payment picks up where it stopped. The unpaid details
        // stay: they say why it's open, and that it's open only for the payment.
        order.setUpdatedBy(actorId);
        orderSupport.addEvent(order, OrderEventType.REOPENED, "Reopened for payment", actorId);
        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    // The guests are gone: the table and the tables merged into it are free for the next guests.
    private void freeTable(RestaurantTable table, UUID actorId) {
        pos.pos.tables.enums.TableStatus previous = table.getStatus();
        List<RestaurantTable> merged = restaurantTableRepository.findAllByMergedInto_IdOrderByTableNumberAsc(table.getId());
        merged.stream().filter(child -> child.getStatus() == pos.pos.tables.enums.TableStatus.OCCUPIED).forEach(child -> {
            child.setStatus(pos.pos.tables.enums.TableStatus.AVAILABLE);
            child.setGuestCount(null);
            child.setSeatedAt(null);
            child.setUpdatedBy(actorId);
        });
        if (previous == pos.pos.tables.enums.TableStatus.OCCUPIED) {
            table.setStatus(pos.pos.tables.enums.TableStatus.AVAILABLE);
            table.setGuestCount(null);
            table.setSeatedAt(null);
            table.setMoreComing(false);
            table.setUpdatedBy(actorId);
        }
        restaurantTableRepository.save(table);
        restaurantTableRepository.saveAll(merged);
        if (previous != table.getStatus()) {
            events.publishEvent(new pos.pos.tables.event.TableStatusChangedEvent(table.getBranch().getRestaurant().getId(), table.getBranch().getId(),
                    table.getId(), previous, table.getStatus(), actorId));
        }
    }

    private static void clearUnpaid(Order order) {
        order.setUnpaidReason(null);
        order.setUnpaidNote(null);
        order.setUnpaidAmount(null);
        order.setUnpaidAt(null);
        order.setUnpaidBy(null);
    }

    // Open only for its payment: it either gets paid or is closed as unpaid again.
    private static void assertNotOpenForPaymentOnly(Order order) {
        if (order.getStatus() == OrderStatus.OPEN && order.getUnpaidAt() != null) {
            throw new AuthException("This order is open only to take its payment", HttpStatus.BAD_REQUEST);
        }
    }

    // Nobody acted on it for too long (Settings → Tables → Special cases): the same as closing it as unpaid, by the system.
    // The caller frees the table. Return puts it all back.
    @Transactional
    public void closeNoAction(Order order) {
        RestaurantTable table = order.getRestaurantTable();
        order.setUnpaidReason(pos.pos.order.enums.OrderUnpaidReason.NO_ACTION);
        order.setUnpaidNote(null);
        order.setUnpaidAmount(orderSupport.balanceDue(order));
        order.setUnpaidAt(OffsetDateTime.now(ZoneOffset.UTC));
        order.setUnpaidBy(null);
        order.setUnpaidTableNumber(table == null ? null : table.getTableNumber());
        order.setRestaurantTable(null);
        order.setBillRequestedAt(null);
        order.setStatus(OrderStatus.VOIDED);
        order.setUpdatedBy(null);
        orderSupport.addEvent(order, OrderEventType.VOIDED, "Closed as unpaid: No action taken", null);
        orderSupport.saveOrder(order);
        kdsOrderSyncService.stopOrder(order, null, "Closed as unpaid: No action taken");
    }

    // Everything was paid and it was left open, so it just closes.
    @Transactional
    public void closePaidNoAction(Order order) {
        order.setStatus(OrderStatus.CLOSED);
        order.setClosedAt(OffsetDateTime.now(ZoneOffset.UTC));
        order.setUpdatedBy(null);
        orderSupport.addEvent(order, OrderEventType.CLOSED, "Closed automatically: paid and no action taken", null);
        orderSupport.saveOrder(order);
    }

    // Nobody acted and it has no dishes: nothing to pay, so it just ends.
    @Transactional
    public void cancelEmptyNoAction(Order order) {
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedBy(null);
        orderSupport.addEvent(order, OrderEventType.CANCELLED, "Cancelled automatically: no dishes and no action taken", null);
        orderSupport.saveOrder(order);
    }

    // Reopened for its payment and left there: back to unpaid, as it was.
    @Transactional
    public void backToUnpaid(Order order) {
        order.setUnpaidAmount(orderSupport.balanceDue(order));
        order.setStatus(OrderStatus.VOIDED);
        order.setUpdatedBy(null);
        orderSupport.addEvent(order, OrderEventType.VOIDED, "Closed as unpaid again: it wasn't paid after being reopened", null);
        orderSupport.saveOrder(order);
    }

    @Transactional
    public OrderResponse setBillRequested(Authentication authentication, UUID restaurantId, UUID orderId, boolean requested) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        orderDomainSupport.assertOrderEditable(order);

        order.setBillRequestedAt(requested ? OffsetDateTime.now(ZoneOffset.UTC) : null);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.UPDATED, requested ? "Bill requested" : "Bill request taken back", order.getUpdatedBy());
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
                .filter(OrderLineItem::goesToKitchen)
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

        UUID actorId = restaurantScopeService.currentUserId(authentication);
        order.getLineItems().stream()
                .filter(orderSupport::isFinanciallyActive)
                .filter(lineItem -> lineItem.getStatus() != OrderLineItemStatus.FULFILLED)
                .forEach(lineItem -> {
                    inventorySaleConsumptionService.consumeFulfilledLine(order, lineItem, actorId);
                    lineItem.setStatus(OrderLineItemStatus.FULFILLED);
                });
        order.setUpdatedBy(actorId);
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

        // One open order per table (user, 2026-11-01): if its table has a new order by now, it's reopened without a table.
        String reopened = orderDomainSupport.firstNote(request, "Order reopened");
        if (order.getRestaurantTable() != null && orderSupport.findCurrentOpenOrderForTable(order.getRestaurantTable().getId()).isPresent()) {
            reopened += "; " + order.getRestaurantTable().getTableNumber() + " has another order now, so this one has no table";
            order.setRestaurantTable(null);
        }
        order.setStatus(OrderStatus.OPEN);
        order.setClosedAt(null);
        order.setBillRequestedAt(null);
        clearUnpaid(order);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.REOPENED, reopened, order.getUpdatedBy());

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse cancelOrder(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        if (order.getStatus() == OrderStatus.VOIDED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new AuthException("This order was already " + order.getStatus().name().toLowerCase(), HttpStatus.BAD_REQUEST);
        }
        if (order.getStatus() == OrderStatus.CLOSED) {
            throw new AuthException("Closed orders must be reopened before they can be cancelled", HttpStatus.BAD_REQUEST);
        }
        assertNotOpenForPaymentOnly(order);
        if (EnumSet.of(OrderPaymentStatus.PAID, OrderPaymentStatus.PARTIALLY_PAID).contains(order.getPaymentStatus())
                || orderSupport.hasMoneyTaken(order)) {
            throw new AuthException("Paid orders cannot be cancelled", HttpStatus.BAD_REQUEST);
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderDomainSupport.applyStatusSideEffects(order);
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
        if (order.getStatus() == OrderStatus.VOIDED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new AuthException("This order was already " + order.getStatus().name().toLowerCase(), HttpStatus.BAD_REQUEST);
        }
        if (order.getStatus() == OrderStatus.CLOSED) {
            throw new AuthException("Closed orders must be reopened before they can be voided", HttpStatus.BAD_REQUEST);
        }
        assertNotOpenForPaymentOnly(order);
        // Money taken must be given back (refund) or cancelled (void the payment) first, so it is never lost track of.
        if (orderSupport.hasMoneyTaken(order)) {
            throw new AuthException("Refund or void the payments on this order first", HttpStatus.BAD_REQUEST);
        }
        orderSupport.requireVoidReasonIfNeeded(order, request == null ? null : request.getReason());

        order.setStatus(OrderStatus.VOIDED);
        orderDomainSupport.applyStatusSideEffects(order);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        order.setPaymentStatus(OrderPaymentStatus.VOIDED);
        order.getLineItems().stream()
                .filter(orderSupport::isFinanciallyActive)
                .forEach(lineItem -> {
                    // Voiding/refunding the bill does not mean prepared ingredients returned to stock. Any genuine
                    // physical stock correction must be an explicit inventory movement, never an automatic reversal.
                    lineItem.setStatus(OrderLineItemStatus.VOIDED);
                    lineItem.setVoidedBy(order.getUpdatedBy());
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
        orderSupport.assertNoSaleConsumedItems(targetOrder, "merged");
        orderSupport.assertNoSaleConsumedItems(sourceOrder, "merged");
        if (Objects.equals(targetOrder.getId(), sourceOrder.getId())) {
            throw new AuthException("sourceOrderId must be different from the target order", HttpStatus.BAD_REQUEST);
        }
        targetOrder.setBillRequestedAt(null);
        if (!Objects.equals(targetOrder.getBranch().getId(), sourceOrder.getBranch().getId())) {
            throw new AuthException("Orders must belong to the same branch to be merged", HttpStatus.BAD_REQUEST);
        }
        if (!Objects.equals(sourceOrder.getCurrency(), targetOrder.getCurrency())
                || sourceOrder.getTaxRateSnapshot().compareTo(targetOrder.getTaxRateSnapshot()) != 0
                || sourceOrder.isTaxInclusiveSnapshot() != targetOrder.isTaxInclusiveSnapshot()) {
            throw new AuthException("Orders with different currencies or tax policies cannot be merged", HttpStatus.BAD_REQUEST);
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
        // A merge preserves each order's applied discount; percentages must not spread
        // onto the other order's items when their subtotals are combined.
        for (OrderDiscount discount : targetOrder.getDiscounts()) {
            discount.setDiscountType(pos.pos.order.enums.OrderDiscountType.FIXED_AMOUNT);
            discount.setDiscountValue(discount.getAmountApplied());
        }
        for (OrderDiscount discount : sourceOrder.getDiscounts()) {
            OrderDiscount allocated = orderSupport.cloneDiscount(discount, discount.getAmountApplied(), actorId);
            allocated.setDiscountType(pos.pos.order.enums.OrderDiscountType.FIXED_AMOUNT);
            allocated.setDiscountValue(discount.getAmountApplied());
            targetOrder.addDiscount(allocated);
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

        RestaurantTable table = orderSupport.resolveTableForUpdate(order.getBranch().getId(), request.getTableId());
        if (!Objects.equals(order.getRestaurantTable() == null ? null : order.getRestaurantTable().getId(),
                table == null ? null : table.getId())) {
            orderSupport.assertTableCanReceiveOrder(table);
        }
        order.setRestaurantTable(table);
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
        orderSupport.assertNoSaleConsumedItems(order, "transferred to another branch");
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

        // Refresh the old branch as well once the transfer transaction commits.
        orderSupport.notifyOrderBranchChanged(order);
        order.setBranch(branch);
        RestaurantTable table = orderSupport.resolveTableForUpdate(branch.getId(), request.getTableId());
        if (!Objects.equals(order.getRestaurantTable() == null ? null : order.getRestaurantTable().getId(),
                table == null ? null : table.getId())) {
            orderSupport.assertTableCanReceiveOrder(table);
        }
        order.setRestaurantTable(table);
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
        orderSupport.assertNoSaleConsumedItems(sourceOrder, "split");
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
        // One open order per table (user, 2026-11-01): split off with no table named, the new order has no table.
        UUID targetTableId = request.getTargetTableId();
        RestaurantTable targetTable = orderSupport.resolveTableForUpdate(sourceOrder.getBranch().getId(), targetTableId);
        orderSupport.assertTableCanReceiveOrder(targetTable);
        newOrder.setRestaurantTable(targetTable);
        newOrder.setReservation(sourceOrder.getReservation());
        newOrder.setCustomer(sourceOrder.getCustomer());
        newOrder.setCreatedBy(actorId);
        newOrder.setUpdatedBy(actorId);
        newOrder.setOrderNumber(orderSupport.nextOrderNumber(sourceOrder.getRestaurant()));
        newOrder.setCurrency(sourceOrder.getCurrency());
        newOrder.setTaxRateSnapshot(sourceOrder.getTaxRateSnapshot());
        newOrder.setTaxInclusiveSnapshot(sourceOrder.isTaxInclusiveSnapshot());
        newOrder.setOrderType(sourceOrder.getOrderType());
        newOrder.setSource(sourceOrder.getSource());
        newOrder.setStatus(OrderStatus.OPEN);
        newOrder.setPaymentStatus(OrderPaymentStatus.UNPAID);
        newOrder.setGuestCount(request.getGuestCount() == null ? sourceOrder.getGuestCount() : request.getGuestCount());
        newOrder.setNotes(request.getNotes() == null ? sourceOrder.getNotes() : request.getNotes());
        newOrder.setOpenedAt(OffsetDateTime.now(ZoneOffset.UTC));

        for (OrderDiscount discount : sourceOrder.getDiscounts()) {
            var amount = orderSupport.splitDiscountAmount(sourceOrder, selectedLineItems, discount);
            OrderDiscount allocated = orderSupport.cloneDiscount(discount, amount, actorId);
            allocated.setDiscountType(pos.pos.order.enums.OrderDiscountType.FIXED_AMOUNT);
            allocated.setDiscountValue(amount);
            newOrder.addDiscount(allocated);
            discount.setDiscountType(pos.pos.order.enums.OrderDiscountType.FIXED_AMOUNT);
            discount.setDiscountValue(discount.getAmountApplied().subtract(amount));
        }
        for (OrderLineItem lineItem : selectedLineItems) {
            newOrder.addLineItem(orderSupport.cloneLineItem(lineItem));
            sourceOrder.removeLineItem(lineItem);
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
