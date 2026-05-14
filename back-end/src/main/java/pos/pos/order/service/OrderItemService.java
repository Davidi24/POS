package pos.pos.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.kds.service.KdsOrderSyncService;
import pos.pos.order.dto.CreateOrderDiscountRequest;
import pos.pos.order.dto.CreateOrderItemOptionRequest;
import pos.pos.order.dto.CreateOrderLineItemRequest;
import pos.pos.order.dto.OrderActionRequest;
import pos.pos.order.dto.OrderDiscountResponse;
import pos.pos.order.dto.OrderEventResponse;
import pos.pos.order.dto.OrderItemOptionResponse;
import pos.pos.order.dto.OrderLineItemNotesRequest;
import pos.pos.order.dto.OrderLineItemQuantityRequest;
import pos.pos.order.dto.OrderLineItemResponse;
import pos.pos.order.dto.UpdateOrderLineItemStatusRequest;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderDiscount;
import pos.pos.order.entity.OrderItemOption;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderEventType;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderItemService {

    private final RestaurantScopeService restaurantScopeService;
    private final OrderSupport orderSupport;
    private final OrderDomainSupport orderDomainSupport;
    private final KdsOrderSyncService kdsOrderSyncService;

    @Transactional
    public OrderLineItemResponse addItem(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            CreateOrderLineItemRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        orderDomainSupport.assertOrderEditable(order);

        OrderLineItem lineItem = orderSupport.buildLineItem(order, request);
        order.addLineItem(lineItem);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.ITEM_ADDED, "Order item added", order.getUpdatedBy());
        orderSupport.saveOrder(order);

        return orderSupport.toLineItemResponse(lineItem);
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
        orderDomainSupport.assertOrderEditable(order);

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        kdsOrderSyncService.assertLineItemMutable(
                lineItem,
                "Order items with KDS ticket history cannot be structurally replaced"
        );
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
        orderDomainSupport.assertOrderEditable(order);

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        kdsOrderSyncService.assertLineItemMutable(
                lineItem,
                "Order items with KDS ticket history cannot change quantity"
        );
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
        orderDomainSupport.assertOrderEditable(order);

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        lineItem.setNotes(request.getNotes());
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.addEvent(order, OrderEventType.ITEM_UPDATED, "Order item notes updated", order.getUpdatedBy());
        orderSupport.saveOrder(order);
        kdsOrderSyncService.syncLineItemNotes(lineItem, order.getUpdatedBy());

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
        orderDomainSupport.assertOrderEditable(order);
        if (request.getStatus() == OrderLineItemStatus.VOIDED) {
            throw new AuthException("Use the void endpoint for VOIDED item status changes", HttpStatus.BAD_REQUEST);
        }

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        lineItem.setStatus(request.getStatus());
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.ITEM_UPDATED, "Order item status updated", order.getUpdatedBy());
        orderSupport.saveOrder(order);
        kdsOrderSyncService.syncFromCurrentOrderState(order, order.getUpdatedBy());

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
                orderDomainSupport.firstNote(request, "Order item fired"));
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
                orderDomainSupport.firstNote(request, "Order item marked ready"));
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
                orderDomainSupport.firstNote(request, "Order item fulfilled"));
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
        orderDomainSupport.assertOrderEditable(order);
        if (!orderSupport.loadOrderRules(order.getRestaurant()).isAllowItemVoid()) {
            throw new AuthException("Voiding order items is disabled for this restaurant", HttpStatus.BAD_REQUEST);
        }
        orderSupport.requireVoidReasonIfNeeded(order, request == null ? null : request.getReason());

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        lineItem.setStatus(OrderLineItemStatus.VOIDED);
        orderSupport.appendReasonToLineItem(lineItem, request == null ? null : request.getReason());
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.ITEM_VOIDED, orderDomainSupport.firstNote(request, "Order item voided"), order.getUpdatedBy());
        orderSupport.saveOrder(order);
        kdsOrderSyncService.syncFromCurrentOrderState(order, order.getUpdatedBy(), request == null ? null : request.getReason());

        return orderSupport.toLineItemResponse(lineItem);
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
        orderDomainSupport.assertOrderEditable(order);

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
        orderDomainSupport.assertOrderEditable(order);

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
        orderDomainSupport.assertOrderEditable(order);

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        lineItem.removeOption(orderSupport.requireOption(lineItem, optionId));
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.ITEM_UPDATED, "Order item option removed", order.getUpdatedBy());
        orderSupport.saveOrder(order);
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
        orderDomainSupport.assertOrderEditable(order);

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
        orderDomainSupport.assertOrderEditable(order);

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
        orderDomainSupport.assertOrderEditable(order);

        order.removeDiscount(orderSupport.requireDiscount(order, discountId));
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.DISCOUNT_REMOVED, "Order discount removed", order.getUpdatedBy());
        orderSupport.saveOrder(order);
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
        orderSupport.addEvent(order, OrderEventType.NOTE_ADDED, orderDomainSupport.firstNote(request, "Order note added"), order.getUpdatedBy());
        orderSupport.saveOrder(order);
        return orderSupport.toResponse(order).getEvents().get(0);
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
        orderDomainSupport.assertOrderEditable(order);

        OrderLineItem lineItem = orderSupport.requireLineItem(order, lineItemId);
        lineItem.setStatus(status);
        order.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.ITEM_UPDATED, note, order.getUpdatedBy());
        orderSupport.saveOrder(order);
        kdsOrderSyncService.syncFromCurrentOrderState(order, order.getUpdatedBy());

        return orderSupport.toLineItemResponse(lineItem);
    }
}
