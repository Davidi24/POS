package pos.pos.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.order.dto.OrderActionRequest;
import pos.pos.order.dto.OrderResponse;
import pos.pos.order.dto.PublicOrderCheckoutRequest;
import pos.pos.order.dto.PublicOrderRequest;
import pos.pos.order.entity.Order;
import pos.pos.order.enums.OrderEventType;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderSource;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.enums.OrderType;
import pos.pos.restaurant.entity.Branch;
import pos.pos.tables.entity.RestaurantTable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class OrderPublicService {

    private final OrderSupport orderSupport;

    @Transactional
    public OrderResponse createPublicOrder(
            String restaurantSlug,
            String branchCode,
            String tableCode,
            PublicOrderRequest request
    ) {
        Branch branch = orderSupport.requirePublicBranch(restaurantSlug, branchCode);
        orderSupport.assertQrOrderingEnabled(branch.getRestaurant());
        RestaurantTable table = orderSupport.requirePublicTable(branch, tableCode);

        Order existingOrder = orderSupport.findCurrentOpenOrderForTable(table.getId()).orElse(null);
        boolean created = existingOrder == null;
        Order order = created ? newPublicOrder(branch, table, request) : existingOrder;

        if (!created) {
            assertPublicOrderEditable(order);
        }

        request.getItems().forEach(itemRequest -> order.addLineItem(orderSupport.buildLineItem(order, itemRequest)));
        if (request.getGuestCount() != null && request.getGuestCount() > order.getGuestCount()) {
            order.setGuestCount(request.getGuestCount());
        }
        if (request.getNotes() != null) {
            order.setNotes(request.getNotes());
        }

        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order,
                created ? OrderEventType.CREATED : OrderEventType.ITEM_ADDED,
                created ? "Public QR order created" : "Public QR items added",
                null);

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getPublicOrder(String orderNumber) {
        Order order = orderSupport.requirePublicOrder(orderNumber);
        orderSupport.assertQrOrderingEnabled(order.getRestaurant());
        return orderSupport.toResponse(order);
    }

    @Transactional
    public OrderResponse addPublicItems(String orderNumber, PublicOrderRequest request) {
        Order order = orderSupport.requirePublicOrder(orderNumber);
        orderSupport.assertQrOrderingEnabled(order.getRestaurant());
        assertPublicOrderEditable(order);

        request.getItems().forEach(itemRequest -> order.addLineItem(orderSupport.buildLineItem(order, itemRequest)));
        if (request.getGuestCount() != null && request.getGuestCount() > order.getGuestCount()) {
            order.setGuestCount(request.getGuestCount());
        }
        if (request.getNotes() != null) {
            order.setNotes(request.getNotes());
        }

        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.ITEM_ADDED, "Public QR items added", null);
        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse checkoutPublicOrder(String orderNumber, PublicOrderCheckoutRequest request) {
        Order order = orderSupport.requirePublicOrder(orderNumber);
        orderSupport.assertQrOrderingEnabled(order.getRestaurant());
        assertPublicOrderEditable(order);

        boolean closeOrder = request != null && Boolean.TRUE.equals(request.getCloseOrder());
        if (closeOrder && EnumSet.of(OrderPaymentStatus.PAID, OrderPaymentStatus.REFUNDED).contains(order.getPaymentStatus())) {
            order.setStatus(OrderStatus.CLOSED);
            order.setClosedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }

        orderSupport.addEvent(order,
                closeOrder && order.getStatus() == OrderStatus.CLOSED ? OrderEventType.CLOSED : OrderEventType.NOTE_ADDED,
                request == null || request.getNote() == null ? "Public checkout requested" : request.getNote(),
                null);

        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    @Transactional
    public OrderResponse cancelPublicOrder(String orderNumber, OrderActionRequest request) {
        Order order = orderSupport.requirePublicOrder(orderNumber);
        orderSupport.assertQrOrderingEnabled(order.getRestaurant());
        assertPublicOrderEditable(order);

        boolean canCancelDirectly = order.getPaymentStatus() == OrderPaymentStatus.UNPAID
                && order.getLineItems().stream()
                .filter(orderSupport::isFinanciallyActive)
                .allMatch(lineItem -> lineItem.getStatus() == OrderLineItemStatus.PENDING);

        if (canCancelDirectly) {
            order.setStatus(OrderStatus.CANCELLED);
            order.getLineItems().stream()
                    .filter(orderSupport::isFinanciallyActive)
                    .forEach(lineItem -> lineItem.setStatus(OrderLineItemStatus.CANCELLED));
            orderSupport.recalculateTotals(order);
            orderSupport.addEvent(order, OrderEventType.CANCELLED,
                    request == null || request.getReason() == null ? "Public cancellation confirmed" : request.getReason(),
                    null);
            return orderSupport.toResponse(orderSupport.saveOrder(order));
        }

        orderSupport.addEvent(order, OrderEventType.NOTE_ADDED,
                request == null || request.getReason() == null ? "Public cancellation requested" : request.getReason(),
                null);
        return orderSupport.toResponse(orderSupport.saveOrder(order));
    }

    private Order newPublicOrder(Branch branch, RestaurantTable table, PublicOrderRequest request) {
        Order order = new Order();
        order.setRestaurant(branch.getRestaurant());
        order.setBranch(branch);
        order.setRestaurantTable(table);
        order.setOrderNumber(orderSupport.nextOrderNumber(branch.getRestaurant()));
        order.setCurrency(branch.getRestaurant().getCurrency());
        order.setOrderType(OrderType.DINE_IN);
        order.setSource(OrderSource.QR_TABLE);
        order.setStatus(OrderStatus.OPEN);
        order.setPaymentStatus(OrderPaymentStatus.UNPAID);
        order.setGuestCount(request.getGuestCount() == null ? 1 : request.getGuestCount());
        order.setNotes(request.getNotes());
        order.setOpenedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return order;
    }

    private void assertPublicOrderEditable(Order order) {
        if (EnumSet.of(OrderStatus.CANCELLED, OrderStatus.VOIDED, OrderStatus.CLOSED).contains(order.getStatus())) {
            throw new AuthException("This order can no longer be modified", HttpStatus.BAD_REQUEST);
        }
    }
}
