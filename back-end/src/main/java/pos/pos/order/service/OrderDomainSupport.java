package pos.pos.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pos.pos.exception.auth.AuthException;
import pos.pos.order.dto.OrderActionRequest;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderFulfillmentStatus;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.enums.OrderType;
import pos.pos.order.repository.OrderRepository;
import pos.pos.reservation.entity.Reservation;
import pos.pos.restaurant.entity.Branch;
import pos.pos.tables.entity.RestaurantTable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderDomainSupport {

    private final OrderRepository orderRepository;
    private final OrderSupport orderSupport;

    public OrderType resolveOrderType(RestaurantTable table, Reservation reservation, OrderType requestedOrderType) {
        if (table != null || reservation != null) {
            return OrderType.DINE_IN;
        }
        return requestedOrderType == null ? OrderType.DINE_IN : requestedOrderType;
    }

    public void applyStatusSideEffects(Order order) {
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

    public void requireReservationBranch(Reservation reservation, Branch branch) {
        if (reservation != null && !Objects.equals(reservation.getBranch().getId(), branch.getId())) {
            throw new AuthException("reservationId must belong to the selected branch", HttpStatus.BAD_REQUEST);
        }
    }

    public void validateBranchPath(UUID branchId, UUID requestBranchId) {
        if (requestBranchId != null && !Objects.equals(requestBranchId, branchId)) {
            throw new AuthException("branchId in the request must match the path branchId", HttpStatus.BAD_REQUEST);
        }
    }

    public void assertOrderEditable(Order order) {
        if (EnumSet.of(OrderStatus.CANCELLED, OrderStatus.VOIDED, OrderStatus.CLOSED).contains(order.getStatus())) {
            throw new AuthException("This order can no longer be modified in its current state", HttpStatus.BAD_REQUEST);
        }
    }

    public String firstNote(OrderActionRequest request, String fallback) {
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

    public List<Order> loadRestaurantOrders(UUID restaurantId, OffsetDateTime from, OffsetDateTime to) {
        if (from == null && to == null) {
            return orderRepository.findAllByRestaurant_IdOrderByOpenedAtDesc(restaurantId);
        }
        orderSupport.requireCompleteWindow(from, to);
        return orderRepository.findAllByRestaurant_IdAndOpenedAtBetweenOrderByOpenedAtDesc(restaurantId, from, to);
    }

    public List<Order> loadBranchOrders(UUID branchId, OffsetDateTime from, OffsetDateTime to) {
        if (from == null && to == null) {
            return orderRepository.findAllByBranch_IdOrderByOpenedAtDesc(branchId);
        }
        orderSupport.requireCompleteWindow(from, to);
        return orderRepository.findAllByBranch_IdAndOpenedAtBetweenOrderByOpenedAtDesc(branchId, from, to);
    }

    public List<OrderLineItem> resolveSplitLineItems(Order order, List<UUID> lineItemIds) {
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
