package pos.pos.order.mapper;

import org.springframework.stereotype.Component;
import pos.pos.order.dto.OrderAuditResponse;
import pos.pos.order.dto.OrderDiscountResponse;
import pos.pos.order.dto.OrderEventResponse;
import pos.pos.order.dto.OrderItemOptionResponse;
import pos.pos.order.dto.OrderLineItemResponse;
import pos.pos.order.dto.OrderResponse;
import pos.pos.order.dto.OrderSplitPreviewResponse;
import pos.pos.order.dto.OrderTotalsResponse;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderDiscount;
import pos.pos.order.entity.OrderEvent;
import pos.pos.order.entity.OrderItemOption;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderLineItemStatus;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return toResponse(order, true, true);
    }

    public OrderResponse toResponse(Order order, boolean includeChildren, boolean includeEvents) {
        if (order == null) {
            return null;
        }

        return OrderResponse.builder()
                .id(order.getId())
                .restaurantId(order.getRestaurant() == null ? null : order.getRestaurant().getId())
                .branchId(order.getBranch() == null ? null : order.getBranch().getId())
                .tableId(order.getRestaurantTable() == null ? null : order.getRestaurantTable().getId())
                .tableNumber(order.getRestaurantTable() == null ? null : order.getRestaurantTable().getTableNumber())
                .tableName(order.getRestaurantTable() == null ? null : order.getRestaurantTable().getName())
                .reservationId(order.getReservation() == null ? null : order.getReservation().getId())
                .reservationCode(order.getReservation() == null ? null : order.getReservation().getReservationCode())
                .customerId(order.getCustomer() == null ? null : order.getCustomer().getId())
                .customerCode(order.getCustomer() == null ? null : order.getCustomer().getCode())
                .customerName(order.getCustomer() == null ? null : order.getCustomer().displayName())
                .orderNumber(order.getOrderNumber())
                .currency(order.getCurrency())
                .orderType(order.getOrderType())
                .source(order.getSource())
                .status(order.getStatus())
                .fulfillmentStatus(order.getFulfillmentStatus())
                .paymentStatus(order.getPaymentStatus())
                .guestCount(order.getGuestCount())
                .notes(order.getNotes())
                .subtotal(order.getSubtotal())
                .discountTotal(order.getDiscountTotal())
                .taxTotal(order.getTaxTotal())
                .serviceChargeTotal(order.getServiceChargeTotal())
                .total(order.getTotal())
                .openedAt(order.getOpenedAt())
                .closedAt(order.getClosedAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .createdBy(order.getCreatedBy())
                .updatedBy(order.getUpdatedBy())
                .lineItems(includeChildren ? mapLineItems(order.getLineItems()) : null)
                .discounts(includeChildren ? mapDiscounts(order.getDiscounts()) : null)
                .events(includeEvents ? mapEvents(order.getEvents()) : null)
                .build();
    }

    public OrderAuditResponse toAuditResponse(Order order) {
        if (order == null) {
            return null;
        }

        return OrderAuditResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .fulfillmentStatus(order.getFulfillmentStatus())
                .paymentStatus(order.getPaymentStatus())
                .createdAt(order.getCreatedAt())
                .createdBy(order.getCreatedBy())
                .updatedAt(order.getUpdatedAt())
                .updatedBy(order.getUpdatedBy())
                .lineItems(mapLineItems(order.getLineItems()))
                .discounts(mapDiscounts(order.getDiscounts()))
                .events(mapEvents(order.getEvents()))
                .build();
    }

    public OrderTotalsResponse toTotalsResponse(Order order) {
        if (order == null) {
            return null;
        }

        List<OrderLineItem> activeLineItems = order.getLineItems().stream()
                .filter(lineItem -> lineItem.getStatus() != null)
                .filter(lineItem -> lineItem.getStatus() != OrderLineItemStatus.CANCELLED)
                .filter(lineItem -> lineItem.getStatus() != OrderLineItemStatus.VOIDED)
                .toList();

        return OrderTotalsResponse.builder()
                .orderId(order.getId())
                .currency(order.getCurrency())
                .lineCount(activeLineItems.size())
                .quantityTotal(activeLineItems.stream().mapToInt(OrderLineItem::getQuantity).sum())
                .subtotal(defaultMoney(order.getSubtotal()))
                .discountTotal(defaultMoney(order.getDiscountTotal()))
                .taxTotal(defaultMoney(order.getTaxTotal()))
                .serviceChargeTotal(defaultMoney(order.getServiceChargeTotal()))
                .total(defaultMoney(order.getTotal()))
                .build();
    }

    public OrderSplitPreviewResponse toSplitPreviewResponse(
            UUID sourceOrderId,
            String currency,
            List<UUID> lineItemIds,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal taxTotal,
            BigDecimal serviceChargeTotal,
            BigDecimal total
    ) {
        return OrderSplitPreviewResponse.builder()
                .sourceOrderId(sourceOrderId)
                .currency(currency)
                .lineItemIds(lineItemIds)
                .lineCount(lineItemIds.size())
                .subtotal(defaultMoney(subtotal))
                .discountTotal(defaultMoney(discountTotal))
                .taxTotal(defaultMoney(taxTotal))
                .serviceChargeTotal(defaultMoney(serviceChargeTotal))
                .total(defaultMoney(total))
                .build();
    }

    public OrderLineItemResponse toLineItemResponse(OrderLineItem lineItem) {
        if (lineItem == null) {
            return null;
        }

        return OrderLineItemResponse.builder()
                .id(lineItem.getId())
                .menuItemId(lineItem.getMenuItem() == null ? null : lineItem.getMenuItem().getId())
                .variantId(lineItem.getVariant() == null ? null : lineItem.getVariant().getId())
                .itemNameSnapshot(lineItem.getItemNameSnapshot())
                .variantNameSnapshot(lineItem.getVariantNameSnapshot())
                .skuSnapshot(lineItem.getSkuSnapshot())
                .quantity(lineItem.getQuantity())
                .unitPriceSnapshot(lineItem.getUnitPriceSnapshot())
                .priceDeltaTotal(lineItem.getPriceDeltaTotal())
                .discountTotal(lineItem.getDiscountTotal())
                .taxTotal(lineItem.getTaxTotal())
                .lineTotal(lineItem.getLineTotal())
                .status(lineItem.getStatus())
                .notes(lineItem.getNotes())
                .options(mapOptions(lineItem.getOptions()))
                .createdAt(lineItem.getCreatedAt())
                .updatedAt(lineItem.getUpdatedAt())
                .build();
    }

    public OrderItemOptionResponse toOptionResponse(OrderItemOption option) {
        if (option == null) {
            return null;
        }

        return OrderItemOptionResponse.builder()
                .id(option.getId())
                .optionItemId(option.getOptionItem() == null ? null : option.getOptionItem().getId())
                .optionNameSnapshot(option.getOptionNameSnapshot())
                .priceDeltaSnapshot(option.getPriceDeltaSnapshot())
                .quantity(option.getQuantity())
                .notes(option.getNotes())
                .createdAt(option.getCreatedAt())
                .updatedAt(option.getUpdatedAt())
                .build();
    }

    public OrderDiscountResponse toDiscountResponse(OrderDiscount discount) {
        if (discount == null) {
            return null;
        }

        return OrderDiscountResponse.builder()
                .id(discount.getId())
                .name(discount.getName())
                .discountType(discount.getDiscountType())
                .discountValue(discount.getDiscountValue())
                .amountApplied(discount.getAmountApplied())
                .reason(discount.getReason())
                .appliedBy(discount.getAppliedBy())
                .createdAt(discount.getCreatedAt())
                .updatedAt(discount.getUpdatedAt())
                .build();
    }

    public OrderEventResponse toEventResponse(OrderEvent event) {
        if (event == null) {
            return null;
        }

        return OrderEventResponse.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .note(event.getNote())
                .createdBy(event.getCreatedBy())
                .createdAt(event.getCreatedAt())
                .build();
    }

    public List<OrderLineItemResponse> mapLineItems(List<OrderLineItem> lineItems) {
        return lineItems == null ? List.of() : lineItems.stream()
                .sorted(Comparator.comparing(OrderLineItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toLineItemResponse)
                .toList();
    }

    public List<OrderItemOptionResponse> mapOptions(List<OrderItemOption> options) {
        return options == null ? List.of() : options.stream()
                .sorted(Comparator.comparing(OrderItemOption::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toOptionResponse)
                .toList();
    }

    public List<OrderDiscountResponse> mapDiscounts(List<OrderDiscount> discounts) {
        return discounts == null ? List.of() : discounts.stream()
                .sorted(Comparator.comparing(OrderDiscount::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toDiscountResponse)
                .toList();
    }

    public List<OrderEventResponse> mapEvents(List<OrderEvent> events) {
        return events == null ? List.of() : events.stream()
                .sorted(Comparator.comparing(OrderEvent::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toEventResponse)
                .toList();
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
