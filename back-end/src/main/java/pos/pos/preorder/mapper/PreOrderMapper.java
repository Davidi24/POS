package pos.pos.preorder.mapper;

import org.springframework.stereotype.Component;
import pos.pos.preorder.dto.PreOrderItemOptionResponse;
import pos.pos.preorder.dto.PreOrderItemResponse;
import pos.pos.preorder.dto.PreOrderResponse;
import pos.pos.preorder.entity.PreOrder;
import pos.pos.preorder.entity.PreOrderItem;
import pos.pos.preorder.entity.PreOrderItemOption;
import pos.pos.reservation.entity.Reservation;

@Component
public class PreOrderMapper {

    public PreOrderResponse toResponse(PreOrder preOrder) {
        Reservation reservation = preOrder.getReservation();
        return PreOrderResponse.builder()
                .id(preOrder.getId())
                .restaurantId(preOrder.getRestaurant() == null ? null : preOrder.getRestaurant().getId())
                .branchId(reservation == null || reservation.getBranch() == null ? null : reservation.getBranch().getId())
                .reservationId(reservation == null ? null : reservation.getId())
                .reservationCode(reservation == null ? null : reservation.getReservationCode())
                .reservationStart(reservation == null ? null : reservation.getReservationStart())
                .partySize(reservation == null ? null : reservation.getPartySize())
                .contactName(reservation == null ? null : reservation.getContactName())
                .status(preOrder.getStatus())
                .paymentStatus(preOrder.getPaymentStatus())
                .source(preOrder.getSource())
                .currency(preOrder.getCurrency())
                .subtotal(preOrder.getSubtotal())
                .taxTotal(preOrder.getTaxTotal())
                .serviceChargeTotal(preOrder.getServiceChargeTotal())
                .total(preOrder.getTotal())
                .paidAmount(preOrder.getPaidAmount())
                .leadMinutes(preOrder.getLeadMinutes())
                .sendAt(preOrder.sendAt())
                .orderId(preOrder.getOrder() == null ? null : preOrder.getOrder().getId())
                .orderNumber(preOrder.getOrder() == null ? null : preOrder.getOrder().getOrderNumber())
                .notes(preOrder.getNotes())
                .cancellationReason(preOrder.getCancellationReason())
                .sentAt(preOrder.getSentAt())
                .cancelledAt(preOrder.getCancelledAt())
                .forfeitedAt(preOrder.getForfeitedAt())
                .createdAt(preOrder.getCreatedAt())
                .updatedAt(preOrder.getUpdatedAt())
                .items(preOrder.getItems().stream().map(this::toItemResponse).toList())
                .build();
    }

    private PreOrderItemResponse toItemResponse(PreOrderItem item) {
        return PreOrderItemResponse.builder()
                .id(item.getId())
                .menuItemId(item.getMenuItem() == null ? null : item.getMenuItem().getId())
                .variantId(item.getVariant() == null ? null : item.getVariant().getId())
                .itemName(item.getItemNameSnapshot())
                .variantName(item.getVariantNameSnapshot())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPriceSnapshot())
                .variantPriceDelta(item.getVariantPriceDeltaSnapshot())
                .priceDeltaTotal(item.getPriceDeltaTotal())
                .lineTotal(item.getLineTotal())
                .notes(item.getNotes())
                .options(item.getOptions().stream().map(this::toOptionResponse).toList())
                .build();
    }

    private PreOrderItemOptionResponse toOptionResponse(PreOrderItemOption option) {
        return PreOrderItemOptionResponse.builder()
                .optionItemId(option.getOptionItem() == null ? null : option.getOptionItem().getId())
                .name(option.getOptionNameSnapshot())
                .priceDelta(option.getPriceDeltaSnapshot())
                .quantity(option.getQuantity())
                .notes(option.getNotes())
                .build();
    }
}
