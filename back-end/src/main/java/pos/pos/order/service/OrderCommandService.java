package pos.pos.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.customer.entity.Customer;
import pos.pos.exception.auth.AuthException;
import pos.pos.kds.service.KdsOrderSyncService;
import pos.pos.order.dto.CreateOrderRequest;
import pos.pos.order.dto.OrderCustomerRequest;
import pos.pos.order.dto.OrderReservationRequest;
import pos.pos.order.dto.OrderResponse;
import pos.pos.order.dto.OrderTableRequest;
import pos.pos.order.dto.OrderValidationResponse;
import pos.pos.order.dto.UpdateOrderRequest;
import pos.pos.order.entity.Order;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderSource;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.enums.OrderType;
import pos.pos.reservation.entity.Reservation;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.tables.entity.RestaurantTable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private final RestaurantScopeService restaurantScopeService;
    private final OrderSupport orderSupport;
    private final OrderDomainSupport orderDomainSupport;
    private final KdsOrderSyncService kdsOrderSyncService;

    @Transactional
    public OrderResponse createBranchOrder(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            CreateOrderRequest request
    ) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Branch branch = orderSupport.resolveManagedBranch(authentication, restaurantId, branchId);
        orderDomainSupport.validateBranchPath(branchId, request.getBranchId());
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        Order order = new Order();
        order.setRestaurant(restaurant);
        order.setBranch(branch);
        order.setCreatedBy(actorId);
        order.setUpdatedBy(actorId);

        applyCreateAssociations(order, restaurantId, branch, request, request.getTableId());
        applyCreateRequest(order, request);
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, pos.pos.order.enums.OrderEventType.CREATED, "Order created", actorId);

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
        orderDomainSupport.validateBranchPath(branchId, request.getBranchId());
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
        orderSupport.addEvent(order, pos.pos.order.enums.OrderEventType.CREATED, "Order created", actorId);

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
        orderDomainSupport.assertOrderEditable(order);

        UUID actorId = restaurantScopeService.currentUserId(authentication);
        Branch branch = order.getBranch();
        if (request.getBranchId() != null && !Objects.equals(request.getBranchId(), order.getBranch().getId())) {
            kdsOrderSyncService.assertNoTicketHistory(
                    order,
                    "Orders with KDS ticket history cannot be moved to another branch"
            );
            branch = orderSupport.resolveManagedBranch(authentication, restaurantId, request.getBranchId());
            order.setBranch(branch);
        }

        if (request.getItems() != null) {
            kdsOrderSyncService.assertNoLineItemHistory(
                    order,
                    "Orders with KDS ticket history cannot replace their line items"
            );
        }

        if (request.getCustomerId() != null) {
            order.setCustomer(orderSupport.resolveCustomer(restaurantId, request.getCustomerId()));
        }

        if (request.getReservationId() != null) {
            Reservation reservation = orderSupport.resolveReservation(restaurantId, request.getReservationId());
            orderDomainSupport.requireReservationBranch(reservation, branch);
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
        orderDomainSupport.applyStatusSideEffects(order);
        orderSupport.addEvent(order, pos.pos.order.enums.OrderEventType.UPDATED, "Order updated", actorId);

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
        orderDomainSupport.assertOrderEditable(order);

        order.setCustomer(orderSupport.resolveCustomer(restaurantId, request.getCustomerId()));
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, pos.pos.order.enums.OrderEventType.UPDATED, "Order customer updated", order.getUpdatedBy());

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
        orderDomainSupport.assertOrderEditable(order);

        order.setRestaurantTable(request.getTableId() == null ? null : orderSupport.resolveTable(order.getBranch().getId(), request.getTableId()));
        if (order.getRestaurantTable() != null) {
            order.setOrderType(OrderType.DINE_IN);
        }
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, pos.pos.order.enums.OrderEventType.TABLE_CHANGED, "Order table updated", order.getUpdatedBy());

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
        orderDomainSupport.assertOrderEditable(order);

        Reservation reservation = orderSupport.resolveReservation(restaurantId, request.getReservationId());
        orderDomainSupport.requireReservationBranch(reservation, order.getBranch());
        order.setReservation(reservation);
        if (reservation != null) {
            order.setOrderType(OrderType.DINE_IN);
            if (order.getCustomer() == null && reservation.getCustomer() != null) {
                order.setCustomer(reservation.getCustomer());
            }
        }
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, pos.pos.order.enums.OrderEventType.RESERVATION_LINKED,
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
                orderDomainSupport.requireReservationBranch(reservation, branch);
                preview.setReservation(reservation);
                preview.setCustomer(request.getCustomerId() == null
                        ? reservation == null ? null : reservation.getCustomer()
                        : orderSupport.resolveCustomer(restaurantId, request.getCustomerId()));
                applyCreateRequest(preview, request);
                orderSupport.recalculateTotals(preview);
            }
        } catch (AuthException | IllegalStateException ex) {
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

    private void applyCreateAssociations(
            Order order,
            UUID restaurantId,
            Branch branch,
            CreateOrderRequest request,
            UUID tableIdOverride
    ) {
        Reservation reservation = orderSupport.resolveReservation(restaurantId, request.getReservationId());
        orderDomainSupport.requireReservationBranch(reservation, branch);
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
        OrderType orderType = orderDomainSupport.resolveOrderType(order.getRestaurantTable(), order.getReservation(), request.getOrderType());
        orderSupport.validateOrderMode(order.getRestaurant(), orderType);
        orderSupport.validateOpenedAt(order.getRestaurant(), request.getOpenedAt());

        order.setOrderNumber(request.getOrderNumber() == null
                ? orderSupport.nextOrderNumber(order.getRestaurant())
                : request.getOrderNumber());
        order.setCurrency(request.getCurrency() == null ? order.getRestaurant().getCurrency() : request.getCurrency());
        order.setOrderType(orderType);
        order.setSource(request.getSource() == null
                ? OrderSource.POS
                : request.getSource());
        order.setStatus(request.getStatus() == null ? OrderStatus.OPEN : request.getStatus());
        order.setPaymentStatus(OrderPaymentStatus.UNPAID);
        order.setGuestCount(request.getGuestCount() == null ? 1 : request.getGuestCount());
        order.setNotes(request.getNotes());
        order.setOpenedAt(request.getOpenedAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : request.getOpenedAt());
        orderSupport.replaceItems(order, request.getItems());
        orderSupport.replaceDiscounts(order, request.getDiscounts(), order.getUpdatedBy());
        orderDomainSupport.applyStatusSideEffects(order);
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
}
