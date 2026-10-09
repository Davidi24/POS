package pos.pos.preorder.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pos.pos.exception.auth.AuthException;
import pos.pos.menu.entity.Menu;
import pos.pos.order.dto.CreateOrderItemOptionRequest;
import pos.pos.order.dto.CreateOrderLineItemRequest;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderItemOption;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.service.OrderSupport;
import pos.pos.preorder.dto.PreOrderItemOptionRequest;
import pos.pos.preorder.dto.PreOrderItemRequest;
import pos.pos.preorder.entity.PreOrder;
import pos.pos.preorder.entity.PreOrderItem;
import pos.pos.preorder.entity.PreOrderItemOption;
import pos.pos.reservation.entity.Reservation;
import pos.pos.restaurant.entity.Restaurant;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

// Prices a pre-order with exactly the rules of a real order (menu checks, options, tax, service charge) by building
// a throwaway order, so what the guest pays up front matches the order the kitchen gets later.
@Component
@RequiredArgsConstructor
public class PreOrderPricing {

    private final OrderSupport orderSupport;

    // onlineMenuOnly: guests ordering online may only pick dishes shown in the online menu; staff can take any dish.
    public void applyItems(PreOrder preOrder, Reservation reservation, List<PreOrderItemRequest> items, boolean onlineMenuOnly) {
        Restaurant restaurant = reservation.getRestaurant();
        Order draft = new Order();
        draft.setRestaurant(restaurant);
        draft.setBranch(reservation.getBranch());

        LocalDate serviceDate = serviceDate(restaurant, reservation);
        for (PreOrderItemRequest item : items) {
            OrderLineItem line = orderSupport.buildLineItem(draft, toOrderLine(item));
            if (onlineMenuOnly && !line.getMenuItem().isShowOnline()) {
                throw new AuthException(line.getItemNameSnapshot() + " can't be ordered online", HttpStatus.BAD_REQUEST);
            }
            Menu menu = line.getMenuItem().getSection().getMenu();
            if (!menu.isAvailableOn(serviceDate)) {
                throw new AuthException(line.getItemNameSnapshot() + " is not on the menu on " + serviceDate, HttpStatus.BAD_REQUEST);
            }
            draft.addLineItem(line);
        }
        orderSupport.recalculateTotals(draft);

        preOrder.clearItems();
        int position = 0;
        for (OrderLineItem line : draft.getLineItems()) {
            preOrder.addItem(toPreOrderItem(line, position++));
        }
        preOrder.setCurrency(restaurant.getCurrency());
        preOrder.setSubtotal(draft.getSubtotal());
        preOrder.setTaxTotal(draft.getTaxTotal());
        preOrder.setServiceChargeTotal(draft.getServiceChargeTotal());
        preOrder.setTotal(draft.getTotal());
    }

    private CreateOrderLineItemRequest toOrderLine(PreOrderItemRequest item) {
        return CreateOrderLineItemRequest.builder()
                .menuItemId(item.getMenuItemId())
                .variantId(item.getVariantId())
                .quantity(item.getQuantity())
                .notes(item.getNotes())
                .options(item.getOptions() == null ? List.of() : item.getOptions().stream().map(this::toOrderOption).toList())
                .build();
    }

    private CreateOrderItemOptionRequest toOrderOption(PreOrderItemOptionRequest option) {
        return CreateOrderItemOptionRequest.builder()
                .optionItemId(option.getOptionItemId())
                .quantity(option.getQuantity())
                .notes(option.getNotes())
                .build();
    }

    private PreOrderItem toPreOrderItem(OrderLineItem line, int position) {
        PreOrderItem item = new PreOrderItem();
        item.setMenuItem(line.getMenuItem());
        item.setVariant(line.getVariant());
        item.setItemNameSnapshot(line.getItemNameSnapshot());
        item.setVariantNameSnapshot(line.getVariantNameSnapshot());
        item.setSkuSnapshot(line.getSkuSnapshot());
        item.setQuantity(line.getQuantity());
        item.setUnitPriceSnapshot(line.getUnitPriceSnapshot());
        item.setVariantPriceDeltaSnapshot(line.getVariantPriceDeltaSnapshot());
        item.setPriceDeltaTotal(line.getPriceDeltaTotal());
        item.setLineTotal(line.getLineTotal());
        item.setNotes(line.getNotes());
        item.setDisplayOrder(position);
        for (OrderItemOption option : line.getOptions()) {
            PreOrderItemOption copy = new PreOrderItemOption();
            copy.setOptionItem(option.getOptionItem());
            copy.setOptionNameSnapshot(option.getOptionNameSnapshot());
            copy.setPriceDeltaSnapshot(option.getPriceDeltaSnapshot());
            copy.setQuantity(option.getQuantity());
            copy.setNotes(option.getNotes());
            item.addOption(copy);
        }
        return item;
    }

    // The booking's date in the restaurant's own time zone, which is what menu date windows mean.
    private LocalDate serviceDate(Restaurant restaurant, Reservation reservation) {
        return reservation.getReservationStart().atZoneSameInstant(restaurantZone(restaurant)).toLocalDate();
    }

    static ZoneId restaurantZone(Restaurant restaurant) {
        try {
            return restaurant == null || restaurant.getTimezone() == null ? ZoneOffset.UTC : ZoneId.of(restaurant.getTimezone());
        } catch (RuntimeException invalidZone) {
            return ZoneOffset.UTC;
        }
    }
}
