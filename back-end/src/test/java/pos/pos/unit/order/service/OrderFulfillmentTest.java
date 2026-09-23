package pos.pos.unit.order.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderFulfillmentStatus;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.enums.OrderType;
import pos.pos.order.service.OrderDomainSupport;
import pos.pos.order.service.OrderSupport;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OrderFulfillmentTest {
    @InjectMocks private OrderSupport support;

    @ParameterizedTest
    @EnumSource(OrderType.class)
    void allServedItemsMeanFulfilledForEveryOrderType(OrderType type) {
        Order order = order(OrderLineItemStatus.FULFILLED, OrderLineItemStatus.FULFILLED);
        order.setOrderType(type);
        support.refreshOrderFulfillment(order);
        assertThat(order.getFulfillmentStatus()).isEqualTo(OrderFulfillmentStatus.FULFILLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING,PENDING,PENDING",
            "FIRED,PENDING,IN_PREPARATION",
            "PREPARING,READY,IN_PREPARATION",
            "READY,READY,READY",
            "FULFILLED,PENDING,PARTIALLY_FULFILLED",
            "FULFILLED,READY,READY",
            "READY,CANCELLED,READY",
            "FULFILLED,VOIDED,FULFILLED",
            "CANCELLED,VOIDED,PENDING"
    })
    void derivesProgressFromOnlyActiveItems(OrderLineItemStatus first, OrderLineItemStatus second,
                                           OrderFulfillmentStatus expected) {
        Order order = order(first, second);
        support.refreshOrderFulfillment(order);
        assertThat(order.getFulfillmentStatus()).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "VOIDED"})
    void cancellationPreservesLastServiceProgress(OrderStatus status) {
        Order order = order(OrderLineItemStatus.CANCELLED, OrderLineItemStatus.VOIDED);
        order.setFulfillmentStatus(OrderFulfillmentStatus.PARTIALLY_FULFILLED);
        order.setStatus(status);
        new OrderDomainSupport(null, support).applyStatusSideEffects(order);
        support.refreshOrderFulfillment(order);
        assertThat(order.getFulfillmentStatus()).isEqualTo(OrderFulfillmentStatus.PARTIALLY_FULFILLED);
        assertThat(order.getStatus()).isEqualTo(status);
    }

    @Test
    void emptyOrderIsPending() {
        Order order = order();
        support.refreshOrderFulfillment(order);
        assertThat(order.getFulfillmentStatus()).isEqualTo(OrderFulfillmentStatus.PENDING);
    }

    private Order order(OrderLineItemStatus... statuses) {
        Order order = new Order();
        for (OrderLineItemStatus status : statuses) {
            OrderLineItem line = new OrderLineItem();
            line.setStatus(status);
            order.addLineItem(line);
        }
        return order;
    }
}
