package pos.pos.unit.order.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pos.pos.exception.auth.AuthException;
import pos.pos.inventory.service.InventorySaleConsumptionService;
import pos.pos.kds.service.KdsOrderSyncService;
import pos.pos.menu.entity.MenuItem;
import pos.pos.order.dto.UpdateOrderLineItemStatusRequest;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.service.OrderDomainSupport;
import pos.pos.order.service.OrderItemService;
import pos.pos.order.service.OrderSupport;
import pos.pos.order.service.OrderWorkflowService;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Counter items (menu item "send to kitchen" off, e.g. a cola) are served directly and never fired to the kitchen.
@ExtendWith(MockitoExtension.class)
class OrderCounterItemTest {
    @Mock RestaurantScopeService restaurants;
    @Mock OrderSupport support;
    @Mock OrderDomainSupport domain;
    @Mock KdsOrderSyncService kds;
    @Mock InventorySaleConsumptionService inventorySaleConsumptionService;
    @Mock Authentication authentication;
    @InjectMocks OrderWorkflowService workflow;
    @InjectMocks OrderItemService items;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final Order order = new Order();
    private final OrderLineItem burger = line("Burger", true);
    private final OrderLineItem cola = line("Cola", false);

    @Test void sendAllToKitchenFiresOnlyKitchenItems() {
        order.setStatus(OrderStatus.OPEN);
        order.addLineItem(burger);
        order.addLineItem(cola);
        when(support.requireOrder(restaurantId, orderId)).thenReturn(order);
        when(support.isFinanciallyActive(any(OrderLineItem.class))).thenReturn(true);

        workflow.sendToKitchen(authentication, restaurantId, orderId, null);

        assertThat(burger.getStatus()).isEqualTo(OrderLineItemStatus.FIRED);
        assertThat(cola.getStatus()).isEqualTo(OrderLineItemStatus.PENDING);
    }

    @Test void sendingACounterItemToTheKitchenIsRefused() {
        order.addLineItem(cola);
        when(support.requireOrder(restaurantId, orderId)).thenReturn(order);
        when(support.requireLineItem(order, cola.getId())).thenReturn(cola);

        assertThatThrownBy(() -> items.fireItem(authentication, restaurantId, orderId, cola.getId(), null))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("served directly");
        assertThatThrownBy(() -> items.updateItemStatus(authentication, restaurantId, orderId, cola.getId(),
                UpdateOrderLineItemStatusRequest.builder().status(OrderLineItemStatus.PREPARING).build()))
                .isInstanceOf(AuthException.class);

        assertThat(cola.getStatus()).isEqualTo(OrderLineItemStatus.PENDING);
        verify(support, never()).saveOrder(any());
    }

    @Test void counterItemCanBeServedStraightAway() {
        order.addLineItem(cola);
        when(support.requireOrder(restaurantId, orderId)).thenReturn(order);
        when(support.requireLineItem(order, cola.getId())).thenReturn(cola);

        items.fulfillItem(authentication, restaurantId, orderId, cola.getId(), null);

        assertThat(cola.getStatus()).isEqualTo(OrderLineItemStatus.FULFILLED);
    }

    private static OrderLineItem line(String name, boolean sendToKitchen) {
        MenuItem menuItem = new MenuItem();
        menuItem.setId(UUID.randomUUID());
        menuItem.setName(name);
        menuItem.setSendToKitchen(sendToKitchen);

        OrderLineItem line = new OrderLineItem();
        line.setId(UUID.randomUUID());
        line.setMenuItem(menuItem);
        line.setItemNameSnapshot(name);
        line.setQuantity(1);
        line.setStatus(OrderLineItemStatus.PENDING);
        return line;
    }
}
