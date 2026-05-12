package pos.pos.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.customer.entity.Customer;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.order.OrderNotFoundException;
import pos.pos.order.dto.CreateOrderDiscountRequest;
import pos.pos.order.dto.CreateOrderItemOptionRequest;
import pos.pos.order.dto.CreateOrderLineItemRequest;
import pos.pos.order.dto.CreateOrderRequest;
import pos.pos.order.dto.OrderActionRequest;
import pos.pos.order.dto.OrderAuditResponse;
import pos.pos.order.dto.OrderCustomerRequest;
import pos.pos.order.dto.OrderDiscountResponse;
import pos.pos.order.dto.OrderEventResponse;
import pos.pos.order.dto.OrderExportResponse;
import pos.pos.order.dto.OrderItemOptionResponse;
import pos.pos.order.dto.OrderLineItemNotesRequest;
import pos.pos.order.dto.OrderLineItemQuantityRequest;
import pos.pos.order.dto.OrderLineItemResponse;
import pos.pos.order.dto.OrderMergeRequest;
import pos.pos.order.dto.OrderNextNumberResponse;
import pos.pos.order.dto.OrderPaymentStatusRequest;
import pos.pos.order.dto.OrderReservationRequest;
import pos.pos.order.dto.OrderResponse;
import pos.pos.order.dto.OrderSplitPreviewResponse;
import pos.pos.order.dto.OrderSplitRequest;
import pos.pos.order.dto.OrderSummaryResponse;
import pos.pos.order.dto.OrderTableRequest;
import pos.pos.order.dto.OrderTotalsResponse;
import pos.pos.order.dto.OrderTransferBranchRequest;
import pos.pos.order.dto.OrderTransferTableRequest;
import pos.pos.order.dto.OrderValidationResponse;
import pos.pos.order.dto.UpdateOrderLineItemStatusRequest;
import pos.pos.order.dto.UpdateOrderRequest;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderDiscount;
import pos.pos.order.entity.OrderItemOption;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderEventType;
import pos.pos.order.enums.OrderFulfillmentStatus;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderSource;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.enums.OrderType;
import pos.pos.order.repository.OrderRepository;
import pos.pos.reservation.entity.Reservation;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.tables.entity.RestaurantTable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final EnumSet<OrderStatus> HISTORY_STATUSES = EnumSet.of(
            OrderStatus.CLOSED,
            OrderStatus.CANCELLED,
            OrderStatus.VOIDED
    );
    private static final EnumSet<OrderPaymentStatus> CLOSED_PAYMENT_STATUSES = EnumSet.of(
            OrderPaymentStatus.PAID,
            OrderPaymentStatus.REFUNDED,
            OrderPaymentStatus.PARTIALLY_REFUNDED
    );

    private final RestaurantScopeService restaurantScopeService;
    private final OrderRepository orderRepository;
    private final OrderSupport orderSupport;

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(
            Authentication authentication,
            UUID restaurantId,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        List<Order> orders = loadRestaurantOrders(restaurantId, from, to);
        return orders.stream()
                .map(order -> orderSupport.toResponse(order, false, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Authentication authentication, UUID restaurantId, UUID orderId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return orderSupport.toResponse(orderSupport.requireOrder(restaurantId, orderId));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(Authentication authentication, UUID restaurantId, String orderNumber) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return orderSupport.toResponse(
                orderRepository.findByRestaurant_IdAndOrderNumber(restaurantId, orderSupport.normalizeOrderNumber(orderNumber))
                        .orElseThrow(OrderNotFoundException::new)
        );
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getCustomerOrders(Authentication authentication, UUID restaurantId, UUID customerId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        orderSupport.resolveCustomer(restaurantId, customerId);
        return orderRepository.findAllByCustomer_IdAndRestaurant_IdOrderByOpenedAtDesc(customerId, restaurantId).stream()
                .map(order -> orderSupport.toResponse(order, false, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getBranchOrders(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to,
            OrderStatus status,
            UUID customerId
    ) {
        orderSupport.resolveAccessibleBranch(authentication, restaurantId, branchId);
        return loadBranchOrders(branchId, from, to).stream()
                .filter(order -> status == null || order.getStatus() == status)
                .filter(order -> customerId == null || Objects.equals(
                        order.getCustomer() == null ? null : order.getCustomer().getId(),
                        customerId
                ))
                .map(order -> orderSupport.toResponse(order, false, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getBranchOpenOrders(Authentication authentication, UUID restaurantId, UUID branchId) {
        orderSupport.resolveAccessibleBranch(authentication, restaurantId, branchId);
        return orderRepository.findAllByBranch_IdAndStatusInOrderByOpenedAtDesc(branchId, EnumSet.of(OrderStatus.DRAFT, OrderStatus.OPEN))
                .stream()
                .map(order -> orderSupport.toResponse(order, false, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getBranchHistory(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        orderSupport.resolveAccessibleBranch(authentication, restaurantId, branchId);
        return loadBranchOrders(branchId, from, to).stream()
                .filter(order -> HISTORY_STATUSES.contains(order.getStatus()))
                .map(order -> orderSupport.toResponse(order, false, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getCurrentTableOrder(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID tableId
    ) {
        orderSupport.resolveAccessibleBranch(authentication, restaurantId, branchId);
        RestaurantTable table = orderSupport.resolveTable(branchId, tableId);
        Order order = orderSupport.findCurrentOpenOrderForTable(table.getId())
                .orElseThrow(OrderNotFoundException::new);
        return orderSupport.toResponse(order);
    }

    @Transactional
    public OrderResponse createBranchOrder(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            CreateOrderRequest request
    ) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Branch branch = orderSupport.resolveManagedBranch(authentication, restaurantId, branchId);
        validateBranchPath(branchId, request.getBranchId());
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        Order order = new Order();
        order.setRestaurant(restaurant);
        order.setBranch(branch);
        order.setCreatedBy(actorId);
        order.setUpdatedBy(actorId);

        applyCreateAssociations(order, restaurantId, branch, request, request.getTableId());
        applyCreateRequest(order, request);
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.CREATED, "Order created", actorId);

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse createTableOrder(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID tableId,
            CreateOrderRequest request
    ) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Branch branch = orderSupport.resolveManagedBranch(authentication, restaurantId, branchId);
        validateBranchPath(branchId, request.getBranchId());
        if (request.getTableId() != null && !Objects.equals(request.getTableId(), tableId)) {
            throw new AuthException("tableId in the request must match the path tableId", HttpStatus.BAD_REQUEST);
        }

        UUID actorId = restaurantScopeService.currentUserId(authentication);
        Order order = new Order();
        order.setRestaurant(restaurant);
        order.setBranch(branch);
        order.setCreatedBy(actorId);
        order.setUpdatedBy(actorId);

        applyCreateAssociations(order, restaurantId, branch, request, tableId);
        applyCreateRequest(order, request);
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.CREATED, "Order created", actorId);

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse updateOrder(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UpdateOrderRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        UUID actorId = restaurantScopeService.currentUserId(authentication);
        Branch branch = order.getBranch();
        if (request.getBranchId() != null && !Objects.equals(request.getBranchId(), order.getBranch().getId())) {
            branch = orderSupport.resolveManagedBranch(authentication, restaurantId, request.getBranchId());
            order.setBranch(branch);
        }

        if (request.getCustomerId() != null) {
            order.setCustomer(orderSupport.resolveCustomer(restaurantId, request.getCustomerId()));
        }

        if (request.getReservationId() != null) {
            Reservation reservation = orderSupport.resolveReservation(restaurantId, request.getReservationId());
            requireReservationBranch(reservation, branch);
            order.setReservation(reservation);
            if (order.getCustomer() == null && reservation.getCustomer() != null) {
                order.setCustomer(reservation.getCustomer());
            }
        } else if (!Objects.equals(order.getBranch().getId(), branch.getId())
                && order.getReservation() != null
                && !Objects.equals(order.getReservation().getBranch().getId(), branch.getId())) {
            order.setReservation(null);
        }

        if (request.getTableId() != null) {
            order.setRestaurantTable(orderSupport.resolveTable(branch.getId(), request.getTableId()));
        } else if (!Objects.equals(order.getBranch().getId(), branch.getId())
                && order.getRestaurantTable() != null
                && !Objects.equals(order.getRestaurantTable().getBranch().getId(), branch.getId())) {
            order.setRestaurantTable(null);
        }

        applyUpdateRequest(order, request);
        order.setUpdatedBy(actorId);
        orderSupport.recalculateTotals(order);
        applyStatusSideEffects(order);
        orderSupport.addEvent(order, OrderEventType.UPDATED, "Order updated", actorId);

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse updateOrderCustomer(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            OrderCustomerRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        order.setCustomer(orderSupport.resolveCustomer(restaurantId, request.getCustomerId()));
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.UPDATED, "Order customer updated", order.getUpdatedBy());

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse updateOrderTable(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            OrderTableRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        order.setRestaurantTable(request.getTableId() == null ? null : orderSupport.resolveTable(order.getBranch().getId(), request.getTableId()));
        if (order.getRestaurantTable() != null) {
            order.setOrderType(OrderType.DINE_IN);
        }
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.TABLE_CHANGED, "Order table updated", order.getUpdatedBy());

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse updateOrderReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            OrderReservationRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        Reservation reservation = orderSupport.resolveReservation(restaurantId, request.getReservationId());
        requireReservationBranch(reservation, order.getBranch());
        order.setReservation(reservation);
        if (reservation != null) {
            order.setOrderType(OrderType.DINE_IN);
            if (order.getCustomer() == null && reservation.getCustomer() != null) {
                order.setCustomer(reservation.getCustomer());
            }
        }
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.RESERVATION_LINKED,
                reservation == null ? "Order reservation removed" : "Order reservation updated",
                order.getUpdatedBy());

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional(readOnly = true)
    public OrderValidationResponse validateOrder(Authentication authentication, UUID restaurantId, CreateOrderRequest request) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Order preview = new Order();

        try {
            Restaurant restaurant = restaurantScopeService.requireExistingRestaurant(restaurantId);
            Branch branch = request.getBranchId() == null ? null : restaurantScopeService.requireExistingBranch(restaurantId, request.getBranchId());
            if (branch == null) {
                errors.add("branchId is required");
            } else {
                preview.setRestaurant(restaurant);
                preview.setBranch(branch);
                preview.setRestaurantTable(orderSupport.resolveTable(branch.getId(), request.getTableId()));
                Reservation reservation = orderSupport.resolveReservation(restaurantId, request.getReservationId());
                requireReservationBranch(reservation, branch);
                preview.setReservation(reservation);
                preview.setCustomer(request.getCustomerId() == null
                        ? reservation == null ? null : reservation.getCustomer()
                        : orderSupport.resolveCustomer(restaurantId, request.getCustomerId()));
                applyCreateRequest(preview, request);
                orderSupport.recalculateTotals(preview);
            }
        } catch (AuthException ex) {
            errors.add(ex.getMessage());
        } catch (IllegalStateException ex) {
            errors.add(ex.getMessage());
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            warnings.add("The order does not contain any line items yet");
        }

        return OrderValidationResponse.builder()
                .valid(errors.isEmpty())
                .suggestedOrderNumber(preview.getRestaurant() == null ? null : orderSupport.nextOrderNumber(preview.getRestaurant()))
                .errors(List.copyOf(errors))
                .warnings(List.copyOf(warnings))
                .totals(errors.isEmpty() ? orderSupport.toTotalsResponse(preview) : null)
                .build();
    }

    @Transactional(readOnly = true)
    public OrderNextNumberResponse nextOrderNumber(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return OrderNextNumberResponse.builder()
                .orderNumber(orderSupport.nextOrderNumber(restaurant))
                .build();
    }

    @Transactional
    public OrderResponse openOrder(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new AuthException("Only draft orders can be opened", HttpStatus.BAD_REQUEST);
        }

        order.setStatus(OrderStatus.OPEN);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.STATUS_UPDATED, firstNote(request, "Order opened"), order.getUpdatedBy());

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse sendToKitchen(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

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
        orderSupport.addEvent(order, OrderEventType.SENT_TO_KITCHEN, firstNote(request, "Order sent to kitchen"), order.getUpdatedBy());

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse markOrderReady(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        order.getLineItems().stream()
                .filter(orderSupport::isFinanciallyActive)
                .filter(lineItem -> lineItem.getStatus() != OrderLineItemStatus.FULFILLED)
                .forEach(lineItem -> lineItem.setStatus(OrderLineItemStatus.READY));
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        order.setFulfillmentStatus(OrderFulfillmentStatus.READY);
        orderSupport.addEvent(order, OrderEventType.READY, firstNote(request, "Order marked ready"), order.getUpdatedBy());

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse fulfillOrder(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        order.getLineItems().stream()
                .filter(orderSupport::isFinanciallyActive)
                .forEach(lineItem -> lineItem.setStatus(OrderLineItemStatus.FULFILLED));
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.FULFILLED, firstNote(request, "Order fulfilled"), order.getUpdatedBy());

        return orderSupport.toResponse(orderSupport.saveOrder(order));
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
        orderSupport.addEvent(order, OrderEventType.CLOSED, firstNote(request, "Order closed"), order.getUpdatedBy());

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
        orderSupport.addEvent(order, OrderEventType.REOPENED, firstNote(request, "Order reopened"), order.getUpdatedBy());

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
        applyStatusSideEffects(order);
        orderSupport.addEvent(order, OrderEventType.CANCELLED, firstNote(request, "Order cancelled"), order.getUpdatedBy());

        return orderSupport.toResponse(orderSupport.saveOrder(order));
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
        applyStatusSideEffects(order);
        orderSupport.addEvent(order, OrderEventType.VOIDED, firstNote(request, "Order voided"), order.getUpdatedBy());

        return orderSupport.toResponse(orderSupport.saveOrder(order));
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
        assertOrderEditable(targetOrder);
        assertOrderEditable(sourceOrder);
        if (Objects.equals(targetOrder.getId(), sourceOrder.getId())) {
            throw new AuthException("sourceOrderId must be different from the target order", HttpStatus.BAD_REQUEST);
        }
        if (!Objects.equals(targetOrder.getBranch().getId(), sourceOrder.getBranch().getId())) {
            throw new AuthException("Orders must belong to the same branch to be merged", HttpStatus.BAD_REQUEST);
        }
        if (!orderSupport.loadOrderRules(targetOrder.getRestaurant()).isMergeOrdersEnabled()) {
            throw new AuthException("Order merging is disabled for this restaurant", HttpStatus.BAD_REQUEST);
        }

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
        applyStatusSideEffects(sourceOrder);
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
        assertOrderEditable(order);
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
        assertOrderEditable(order);
        if (!orderSupport.loadOrderRules(order.getRestaurant()).isTransferOrdersEnabled()) {
            throw new AuthException("Order transfers are disabled for this restaurant", HttpStatus.BAD_REQUEST);
        }

        Branch branch = orderSupport.resolveManagedBranch(authentication, restaurantId, request.getBranchId());
        Reservation reservation = orderSupport.resolveReservation(restaurantId, request.getReservationId());
        requireReservationBranch(reservation, branch);

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

    @Transactional(readOnly = true)
    public List<OrderLineItemResponse> getItems(Authentication authentication, UUID restaurantId, UUID orderId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return orderSupport.requireOrder(restaurantId, orderId).getLineItems().stream()
                .sorted(Comparator.comparing(OrderLineItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(orderSupport::toLineItemResponse)
                .toList();
    }

    @Transactional
    public OrderLineItemResponse addItem(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            CreateOrderLineItemRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        OrderLineItem lineItem = orderSupport.buildLineItem(order, request);
        order.addLineItem(lineItem);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.ITEM_ADDED, "Order item added", order.getUpdatedBy());
        orderSupport.saveOrder(order);

        return orderSupport.toLineItemResponse(lineItem);
    }

    @Transactional(readOnly = true)
    public OrderLineItemResponse getItem(Authentication authentication, UUID restaurantId, UUID orderId, UUID lineItemId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        return orderSupport.toLineItemResponse(orderSupport.requireLineItem(order, lineItemId));
    }

    @Transactional
    public OrderLineItemResponse updateItem(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID lineItemId,
            CreateOrderLineItemRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        orderSupport.applyLineItemRequest(order, lineItem, request, true);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.ITEM_UPDATED, "Order item updated", order.getUpdatedBy());
        orderSupport.saveOrder(order);

        return orderSupport.toLineItemResponse(lineItem);
    }

    @Transactional
    public OrderLineItemResponse updateItemQuantity(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID lineItemId,
            OrderLineItemQuantityRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        lineItem.setQuantity(request.getQuantity());
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.ITEM_UPDATED, "Order item quantity updated", order.getUpdatedBy());
        orderSupport.saveOrder(order);

        return orderSupport.toLineItemResponse(lineItem);
    }

    @Transactional
    public OrderLineItemResponse updateItemNotes(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID lineItemId,
            OrderLineItemNotesRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        lineItem.setNotes(request.getNotes());
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.ITEM_UPDATED, "Order item notes updated", order.getUpdatedBy());
        orderSupport.saveOrder(order);

        return orderSupport.toLineItemResponse(lineItem);
    }

    @Transactional
    public OrderLineItemResponse updateItemStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID lineItemId,
            UpdateOrderLineItemStatusRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);
        if (request.getStatus() == OrderLineItemStatus.VOIDED) {
            throw new AuthException("Use the void endpoint for VOIDED item status changes", HttpStatus.BAD_REQUEST);
        }

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        lineItem.setStatus(request.getStatus());
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.ITEM_UPDATED, "Order item status updated", order.getUpdatedBy());
        orderSupport.saveOrder(order);

        return orderSupport.toLineItemResponse(lineItem);
    }

    @Transactional
    public OrderLineItemResponse fireItem(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID lineItemId,
            OrderActionRequest request
    ) {
        return changeItemStatus(authentication, restaurantId, orderId, lineItemId, OrderLineItemStatus.FIRED,
                firstNote(request, "Order item fired"));
    }

    @Transactional
    public OrderLineItemResponse readyItem(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID lineItemId,
            OrderActionRequest request
    ) {
        return changeItemStatus(authentication, restaurantId, orderId, lineItemId, OrderLineItemStatus.READY,
                firstNote(request, "Order item marked ready"));
    }

    @Transactional
    public OrderLineItemResponse fulfillItem(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID lineItemId,
            OrderActionRequest request
    ) {
        return changeItemStatus(authentication, restaurantId, orderId, lineItemId, OrderLineItemStatus.FULFILLED,
                firstNote(request, "Order item fulfilled"));
    }

    @Transactional
    public OrderLineItemResponse voidItem(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID lineItemId,
            OrderActionRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);
        if (!orderSupport.loadOrderRules(order.getRestaurant()).isAllowItemVoid()) {
            throw new AuthException("Voiding order items is disabled for this restaurant", HttpStatus.BAD_REQUEST);
        }
        orderSupport.requireVoidReasonIfNeeded(order, request == null ? null : request.getReason());

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        lineItem.setStatus(OrderLineItemStatus.VOIDED);
        orderSupport.appendReasonToLineItem(lineItem, request == null ? null : request.getReason());
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.ITEM_VOIDED, firstNote(request, "Order item voided"), order.getUpdatedBy());
        orderSupport.saveOrder(order);

        return orderSupport.toLineItemResponse(lineItem);
    }

    @Transactional(readOnly = true)
    public List<OrderItemOptionResponse> getItemOptions(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID lineItemId
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        OrderLineItem lineItem = orderSupport.requireLineItem(orderSupport.requireOrder(restaurantId, orderId), lineItemId);
        return orderSupport.toLineItemResponse(lineItem).getOptions();
    }

    @Transactional
    public OrderItemOptionResponse addItemOption(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID lineItemId,
            CreateOrderItemOptionRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        OrderItemOption option = orderSupport.buildOption(order, lineItem, request);
        lineItem.addOption(option);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.ITEM_UPDATED, "Order item option added", order.getUpdatedBy());
        orderSupport.saveOrder(order);

        return orderSupport.toLineItemResponse(lineItem).getOptions().stream()
                .filter(response -> Objects.equals(response.getId(), option.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public OrderItemOptionResponse updateItemOption(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID lineItemId,
            UUID optionId,
            CreateOrderItemOptionRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        OrderItemOption option = orderSupport.requireOption(lineItem, optionId);
        OrderItemOption replacement = orderSupport.buildOption(order, lineItem, request);
        option.setOptionItem(replacement.getOptionItem());
        option.setOptionNameSnapshot(replacement.getOptionNameSnapshot());
        option.setPriceDeltaSnapshot(replacement.getPriceDeltaSnapshot());
        option.setQuantity(replacement.getQuantity());
        option.setNotes(replacement.getNotes());

        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.ITEM_UPDATED, "Order item option updated", order.getUpdatedBy());
        orderSupport.saveOrder(order);

        return orderSupport.toLineItemResponse(lineItem).getOptions().stream()
                .filter(response -> Objects.equals(response.getId(), option.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void deleteItemOption(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID lineItemId,
            UUID optionId
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        lineItem.removeOption(orderSupport.requireOption(lineItem, optionId));
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.ITEM_UPDATED, "Order item option removed", order.getUpdatedBy());
        orderSupport.saveOrder(order);
    }

    @Transactional(readOnly = true)
    public List<OrderDiscountResponse> getDiscounts(Authentication authentication, UUID restaurantId, UUID orderId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return orderSupport.toResponse(orderSupport.requireOrder(restaurantId, orderId)).getDiscounts();
    }

    @Transactional
    public OrderDiscountResponse addDiscount(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            CreateOrderDiscountRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        OrderDiscount discount = orderSupport.buildDiscount(order, request, restaurantScopeService.currentUserId(authentication));
        order.addDiscount(discount);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.DISCOUNT_APPLIED, "Order discount applied", order.getUpdatedBy());
        orderSupport.saveOrder(order);

        return orderSupport.toResponse(order).getDiscounts().stream()
                .filter(response -> Objects.equals(response.getId(), discount.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public OrderDiscountResponse updateDiscount(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID discountId,
            CreateOrderDiscountRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        OrderDiscount discount = orderSupport.requireDiscount(order, discountId);
        orderSupport.applyDiscountRequest(order, discount, request, restaurantScopeService.currentUserId(authentication));
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.DISCOUNT_APPLIED, "Order discount updated", order.getUpdatedBy());
        orderSupport.saveOrder(order);

        return orderSupport.toResponse(order).getDiscounts().stream()
                .filter(response -> Objects.equals(response.getId(), discount.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void deleteDiscount(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID discountId
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        order.removeDiscount(orderSupport.requireDiscount(order, discountId));
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.DISCOUNT_REMOVED, "Order discount removed", order.getUpdatedBy());
        orderSupport.saveOrder(order);
    }

    @Transactional(readOnly = true)
    public List<OrderEventResponse> getEvents(Authentication authentication, UUID restaurantId, UUID orderId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return orderSupport.toResponse(orderSupport.requireOrder(restaurantId, orderId)).getEvents();
    }

    @Transactional
    public OrderEventResponse addNoteEvent(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            OrderActionRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.NOTE_ADDED, firstNote(request, "Order note added"), order.getUpdatedBy());
        orderSupport.saveOrder(order);
        return orderSupport.toResponse(order).getEvents().get(0);
    }

    @Transactional(readOnly = true)
    public List<OrderEventResponse> getTimeline(Authentication authentication, UUID restaurantId, UUID orderId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return getEvents(authentication, restaurantId, orderId);
    }

    @Transactional(readOnly = true)
    public OrderAuditResponse getAudit(Authentication authentication, UUID restaurantId, UUID orderId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return orderSupport.toAuditResponse(orderSupport.requireOrder(restaurantId, orderId));
    }

    @Transactional(readOnly = true)
    public OrderTotalsResponse getTotals(Authentication authentication, UUID restaurantId, UUID orderId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return orderSupport.toTotalsResponse(orderSupport.requireOrder(restaurantId, orderId));
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
                firstNote(request, "Order marked paid"));
    }

    @Transactional
    public OrderResponse markPartiallyPaid(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        return changePaymentStatus(authentication, restaurantId, orderId, OrderPaymentStatus.PARTIALLY_PAID,
                firstNote(request, "Order marked partially paid"));
    }

    @Transactional
    public OrderResponse markRefunded(Authentication authentication, UUID restaurantId, UUID orderId, OrderActionRequest request) {
        return changePaymentStatus(authentication, restaurantId, orderId, OrderPaymentStatus.REFUNDED,
                firstNote(request, "Order marked refunded"));
    }

    @Transactional(readOnly = true)
    public OrderSplitPreviewResponse splitPreview(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            OrderSplitRequest request
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        List<OrderLineItem> selectedLineItems = resolveSplitLineItems(order, request.getLineItemIds());

        Order previewOrder = new Order();
        previewOrder.setRestaurant(order.getRestaurant());
        previewOrder.setBranch(order.getBranch());
        previewOrder.setCurrency(order.getCurrency());
        previewOrder.setOrderType(order.getOrderType());
        previewOrder.setSource(order.getSource());
        previewOrder.setStatus(OrderStatus.OPEN);
        previewOrder.setPaymentStatus(order.getPaymentStatus());
        previewOrder.setGuestCount(request.getGuestCount() == null ? order.getGuestCount() : request.getGuestCount());
        previewOrder.setNotes(request.getNotes() == null ? order.getNotes() : request.getNotes());

        for (OrderLineItem lineItem : selectedLineItems) {
            previewOrder.addLineItem(orderSupport.cloneLineItem(lineItem));
        }
        for (OrderDiscount discount : order.getDiscounts()) {
            previewOrder.addDiscount(orderSupport.cloneDiscount(discount, discount.getAmountApplied(), order.getUpdatedBy()));
        }
        orderSupport.recalculateTotals(previewOrder);

        return OrderSplitPreviewResponse.builder()
                .sourceOrderId(order.getId())
                .currency(previewOrder.getCurrency())
                .lineItemIds(request.getLineItemIds())
                .lineCount(request.getLineItemIds().size())
                .subtotal(previewOrder.getSubtotal())
                .discountTotal(previewOrder.getDiscountTotal())
                .taxTotal(previewOrder.getTaxTotal())
                .serviceChargeTotal(previewOrder.getServiceChargeTotal())
                .total(previewOrder.getTotal())
                .build();
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
        assertOrderEditable(sourceOrder);
        if (!orderSupport.loadSettings(sourceOrder.getRestaurant()).isAllowSplitBills()) {
            throw new AuthException("Split bills are disabled for this restaurant", HttpStatus.BAD_REQUEST);
        }

        List<OrderLineItem> selectedLineItems = resolveSplitLineItems(sourceOrder, request.getLineItemIds());
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

    @Transactional(readOnly = true)
    public OrderSummaryResponse getSummary(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        List<Order> orders = branchId == null
                ? loadRestaurantOrders(restaurantId, from, to)
                : loadBranchOrders(orderSupport.resolveAccessibleBranch(authentication, restaurantId, branchId).getId(), from, to);

        BigDecimal totalRevenue = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.CLOSED)
                .map(order -> order.getTotal() == null ? BigDecimal.ZERO : order.getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal openTicketTotal = orders.stream()
                .filter(order -> EnumSet.of(OrderStatus.DRAFT, OrderStatus.OPEN).contains(order.getStatus()))
                .map(order -> order.getTotal() == null ? BigDecimal.ZERO : order.getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long revenueOrderCount = orders.stream().filter(order -> order.getStatus() == OrderStatus.CLOSED).count();
        BigDecimal averageTicket = revenueOrderCount == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : totalRevenue.divide(BigDecimal.valueOf(revenueOrderCount), 2, RoundingMode.HALF_UP);

        return OrderSummaryResponse.builder()
                .branchId(branchId)
                .from(from)
                .to(to)
                .totalOrders(orders.size())
                .openCount((int) orders.stream().filter(order -> order.getStatus() == OrderStatus.OPEN).count())
                .closedCount((int) orders.stream().filter(order -> order.getStatus() == OrderStatus.CLOSED).count())
                .cancelledCount((int) orders.stream().filter(order -> order.getStatus() == OrderStatus.CANCELLED).count())
                .voidedCount((int) orders.stream().filter(order -> order.getStatus() == OrderStatus.VOIDED).count())
                .paidCount((int) orders.stream().filter(order -> order.getPaymentStatus() == OrderPaymentStatus.PAID).count())
                .unpaidCount((int) orders.stream().filter(order -> order.getPaymentStatus() == OrderPaymentStatus.UNPAID).count())
                .totalRevenue(totalRevenue.setScale(2, RoundingMode.HALF_UP))
                .averageTicket(averageTicket)
                .openTicketTotal(openTicketTotal.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getKitchenBoard(Authentication authentication, UUID restaurantId, UUID branchId) {
        orderSupport.resolveAccessibleBranch(authentication, restaurantId, branchId);
        return orderRepository.findAllByBranch_IdAndStatusInOrderByOpenedAtDesc(branchId, EnumSet.of(OrderStatus.DRAFT, OrderStatus.OPEN))
                .stream()
                .filter(order -> order.getLineItems().stream().anyMatch(lineItem ->
                        orderSupport.isFinanciallyActive(lineItem) && lineItem.getStatus() != OrderLineItemStatus.FULFILLED
                ))
                .map(order -> orderSupport.toResponse(order, true, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderExportResponse exportOrders(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        orderSupport.resolveAccessibleBranch(authentication, restaurantId, branchId);
        List<Order> orders = loadBranchOrders(branchId, from, to);
        return OrderExportResponse.builder()
                .restaurantId(restaurantId)
                .branchId(branchId)
                .from(from)
                .to(to)
                .exportedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .orderCount(orders.size())
                .orders(orders.stream().map(orderSupport::toResponse).toList())
                .build();
    }

    private OrderLineItemResponse changeItemStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID lineItemId,
            OrderLineItemStatus status,
            String note
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        assertOrderEditable(order);

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        lineItem.setStatus(status);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.ITEM_UPDATED, note, order.getUpdatedBy());
        orderSupport.saveOrder(order);

        return orderSupport.toLineItemResponse(lineItem);
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

    private void applyCreateAssociations(
            Order order,
            UUID restaurantId,
            Branch branch,
            CreateOrderRequest request,
            UUID tableIdOverride
    ) {
        Reservation reservation = orderSupport.resolveReservation(restaurantId, request.getReservationId());
        requireReservationBranch(reservation, branch);
        RestaurantTable table = tableIdOverride == null
                ? orderSupport.resolveTable(branch.getId(), request.getTableId())
                : orderSupport.resolveTable(branch.getId(), tableIdOverride);
        Customer customer = request.getCustomerId() == null
                ? reservation == null ? null : reservation.getCustomer()
                : orderSupport.resolveCustomer(restaurantId, request.getCustomerId());

        order.setReservation(reservation);
        order.setRestaurantTable(table);
        order.setCustomer(customer);

        if (table != null) {
            orderSupport.assertTableCanAcceptNewOrder(table);
        }
    }

    private void applyCreateRequest(Order order, CreateOrderRequest request) {
        OrderType orderType = resolveOrderType(order.getRestaurantTable(), order.getReservation(), request.getOrderType());
        orderSupport.validateOrderMode(order.getRestaurant(), orderType);
        orderSupport.validateOpenedAt(order.getRestaurant(), request.getOpenedAt());

        order.setOrderNumber(request.getOrderNumber() == null
                ? orderSupport.nextOrderNumber(order.getRestaurant())
                : request.getOrderNumber());
        order.setCurrency(request.getCurrency() == null ? order.getRestaurant().getCurrency() : request.getCurrency());
        order.setOrderType(orderType);
        order.setSource(request.getSource() == null
                ? order.getRestaurantTable() == null ? OrderSource.POS : OrderSource.POS
                : request.getSource());
        order.setStatus(request.getStatus() == null ? OrderStatus.OPEN : request.getStatus());
        order.setPaymentStatus(OrderPaymentStatus.UNPAID);
        order.setGuestCount(request.getGuestCount() == null ? 1 : request.getGuestCount());
        order.setNotes(request.getNotes());
        order.setOpenedAt(request.getOpenedAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : request.getOpenedAt());
        orderSupport.replaceItems(order, request.getItems());
        orderSupport.replaceDiscounts(order, request.getDiscounts(), order.getUpdatedBy());
        applyStatusSideEffects(order);
    }

    private void applyUpdateRequest(Order order, UpdateOrderRequest request) {
        if (request.getOrderNumber() != null) {
            order.setOrderNumber(request.getOrderNumber());
        }
        if (request.getCurrency() != null) {
            order.setCurrency(request.getCurrency());
        }
        if (request.getOrderType() != null) {
            orderSupport.validateOrderMode(order.getRestaurant(), request.getOrderType());
            order.setOrderType(request.getOrderType());
        }
        if (request.getSource() != null) {
            order.setSource(request.getSource());
        }
        if (request.getStatus() != null) {
            order.setStatus(request.getStatus());
        }
        if (request.getGuestCount() != null) {
            order.setGuestCount(request.getGuestCount());
        }
        if (request.getNotes() != null) {
            order.setNotes(request.getNotes());
        }
        if (request.getOpenedAt() != null) {
            orderSupport.validateOpenedAt(order.getRestaurant(), request.getOpenedAt());
            order.setOpenedAt(request.getOpenedAt());
        }
        if (request.getItems() != null) {
            orderSupport.replaceItems(order, request.getItems());
        }
        if (request.getDiscounts() != null) {
            orderSupport.replaceDiscounts(order, request.getDiscounts(), order.getUpdatedBy());
        }
    }

    private OrderType resolveOrderType(RestaurantTable table, Reservation reservation, OrderType requestedOrderType) {
        if (table != null || reservation != null) {
            return OrderType.DINE_IN;
        }
        return requestedOrderType == null ? OrderType.DINE_IN : requestedOrderType;
    }

    private void applyStatusSideEffects(Order order) {
        if (order.getStatus() == OrderStatus.CLOSED && order.getClosedAt() == null) {
            order.setClosedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }
        if (order.getStatus() != OrderStatus.CLOSED) {
            order.setClosedAt(null);
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.VOIDED) {
            order.setFulfillmentStatus(OrderFulfillmentStatus.CANCELLED);
        }
        if (order.getStatus() == OrderStatus.VOIDED) {
            order.setPaymentStatus(OrderPaymentStatus.VOIDED);
        }
    }

    private void requireReservationBranch(Reservation reservation, Branch branch) {
        if (reservation != null && !Objects.equals(reservation.getBranch().getId(), branch.getId())) {
            throw new AuthException("reservationId must belong to the selected branch", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateBranchPath(UUID branchId, UUID requestBranchId) {
        if (requestBranchId != null && !Objects.equals(requestBranchId, branchId)) {
            throw new AuthException("branchId in the request must match the path branchId", HttpStatus.BAD_REQUEST);
        }
    }

    private void assertOrderEditable(Order order) {
        if (EnumSet.of(OrderStatus.CANCELLED, OrderStatus.VOIDED, OrderStatus.CLOSED).contains(order.getStatus())) {
            throw new AuthException("This order can no longer be modified in its current state", HttpStatus.BAD_REQUEST);
        }
    }

    private String firstNote(OrderActionRequest request, String fallback) {
        if (request == null) {
            return fallback;
        }
        if (request.getNote() != null && !request.getNote().isBlank()) {
            return request.getNote();
        }
        if (request.getReason() != null && !request.getReason().isBlank()) {
            return request.getReason();
        }
        return fallback;
    }

    private List<Order> loadRestaurantOrders(UUID restaurantId, OffsetDateTime from, OffsetDateTime to) {
        if (from == null && to == null) {
            return orderRepository.findAllByRestaurant_IdOrderByOpenedAtDesc(restaurantId);
        }
        orderSupport.requireCompleteWindow(from, to);
        return orderRepository.findAllByRestaurant_IdAndOpenedAtBetweenOrderByOpenedAtDesc(restaurantId, from, to);
    }

    private List<Order> loadBranchOrders(UUID branchId, OffsetDateTime from, OffsetDateTime to) {
        if (from == null && to == null) {
            return orderRepository.findAllByBranch_IdOrderByOpenedAtDesc(branchId);
        }
        orderSupport.requireCompleteWindow(from, to);
        return orderRepository.findAllByBranch_IdAndOpenedAtBetweenOrderByOpenedAtDesc(branchId, from, to);
    }

    private List<OrderLineItem> resolveSplitLineItems(Order order, List<UUID> lineItemIds) {
        orderSupport.requireDistinctIds(lineItemIds, "lineItemIds must not contain duplicate values");
        List<OrderLineItem> selectedLineItems = lineItemIds.stream()
                .map(lineItemId -> orderSupport.requireLineItem(order, lineItemId))
                .filter(orderSupport::isFinanciallyActive)
                .toList();
        if (selectedLineItems.size() != lineItemIds.size()) {
            throw new AuthException("lineItemIds must reference active order items", HttpStatus.BAD_REQUEST);
        }
        return selectedLineItems;
    }
}
