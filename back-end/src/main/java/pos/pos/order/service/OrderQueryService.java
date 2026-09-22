package pos.pos.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.order.OrderNotFoundException;
import pos.pos.order.dto.OrderAuditResponse;
import pos.pos.order.dto.OrderDiscountResponse;
import pos.pos.order.dto.OrderEventResponse;
import pos.pos.order.dto.OrderExportResponse;
import pos.pos.order.dto.OrderItemOptionResponse;
import pos.pos.order.dto.OrderLineItemResponse;
import pos.pos.order.dto.OrderNextNumberResponse;
import pos.pos.order.dto.OrderResponse;
import pos.pos.order.dto.OrderSplitPreviewResponse;
import pos.pos.order.dto.OrderSplitRequest;
import pos.pos.order.dto.OrderSummaryResponse;
import pos.pos.order.dto.OrderTotalsResponse;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderDiscount;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.repository.OrderRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.tables.entity.RestaurantTable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private static final EnumSet<OrderStatus> HISTORY_STATUSES = EnumSet.of(
            OrderStatus.CLOSED,
            OrderStatus.CANCELLED,
            OrderStatus.VOIDED
    );

    private final RestaurantScopeService restaurantScopeService;
    private final OrderRepository orderRepository;
    private final OrderSupport orderSupport;
    private final OrderDomainSupport orderDomainSupport;

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(
            Authentication authentication,
            UUID restaurantId,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        List<Order> orders = orderDomainSupport.loadRestaurantOrders(restaurantId, from, to);
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
        return orderDomainSupport.loadBranchOrders(branchId, from, to).stream()
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
        return orderDomainSupport.loadBranchOrders(branchId, from, to).stream()
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

    @Transactional(readOnly = true)
    public OrderNextNumberResponse nextOrderNumber(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return OrderNextNumberResponse.builder()
                .orderNumber(orderSupport.nextOrderNumber(restaurant))
                .build();
    }

    @Transactional(readOnly = true)
    public List<OrderLineItemResponse> getItems(Authentication authentication, UUID restaurantId, UUID orderId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return orderSupport.requireOrder(restaurantId, orderId).getLineItems().stream()
                .sorted(Comparator.comparing(OrderLineItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(orderSupport::toLineItemResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderLineItemResponse getItem(Authentication authentication, UUID restaurantId, UUID orderId, UUID lineItemId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        return orderSupport.toLineItemResponse(orderSupport.requireLineItem(order, lineItemId));
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

    @Transactional(readOnly = true)
    public List<OrderDiscountResponse> getDiscounts(Authentication authentication, UUID restaurantId, UUID orderId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return orderSupport.toResponse(orderSupport.requireOrder(restaurantId, orderId)).getDiscounts();
    }

    @Transactional(readOnly = true)
    public List<OrderEventResponse> getEvents(Authentication authentication, UUID restaurantId, UUID orderId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return orderSupport.toResponse(orderSupport.requireOrder(restaurantId, orderId)).getEvents();
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

    @Transactional(readOnly = true)
    public OrderSplitPreviewResponse splitPreview(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            OrderSplitRequest request
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        List<OrderLineItem> selectedLineItems = orderDomainSupport.resolveSplitLineItems(order, request.getLineItemIds());

        Order previewOrder = getPreviewOrder(request, order);

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

    private static Order getPreviewOrder(OrderSplitRequest request, Order order) {
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
        return previewOrder;
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
                ? orderDomainSupport.loadRestaurantOrders(restaurantId, from, to)
                : orderDomainSupport.loadBranchOrders(orderSupport.resolveAccessibleBranch(authentication, restaurantId, branchId).getId(), from, to);

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
        List<Order> orders = orderDomainSupport.loadBranchOrders(branchId, from, to);
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
}
