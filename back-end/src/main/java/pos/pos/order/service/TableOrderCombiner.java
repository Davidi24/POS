package pos.pos.order.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderDiscount;
import pos.pos.order.enums.OrderDiscountType;
import pos.pos.order.enums.OrderEventType;
import pos.pos.order.enums.OrderStatus;
import pos.pos.tables.entity.RestaurantTable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A table has one open order (user, 2026-11-01). When tables are joined or taken apart, the orders that end up on one table
 * become one: an order with no dishes is cancelled, the others' dishes (and their kitchen tickets) move into the order that
 * stays, so the kitchen keeps working on them.
 */
@Component
@RequiredArgsConstructor
public class TableOrderCombiner {

    private final EntityManager entityManager;
    private final OrderSupport orderSupport;

    /**
     * Makes [orders] one order on [table]. The first order with dishes stays (put the one that should stay first); with no
     * dishes anywhere, the first stays. Returns the order that stays, or null when there was none.
     */
    public Order combineOnto(RestaurantTable table, List<Order> orders, String note, UUID actorId) {
        if (orders.isEmpty()) {
            return null;
        }
        Order keep = orders.stream().filter(this::hasDishes).findFirst().orElse(orders.get(0));
        for (Order order : orders) {
            if (order == keep) {
                continue;
            }
            if (!hasDishes(order) && order.getPayments().isEmpty()) {
                cancelEmpty(order, note, actorId);
            } else {
                moveInto(keep, order, note, actorId);
            }
        }
        // The others are closed before the one that stays may sit on the table (one open order per table).
        entityManager.flush();
        if (keep.getRestaurantTable() == null || !keep.getRestaurantTable().getId().equals(table.getId())) {
            keep.setRestaurantTable(table);
            keep.setUpdatedBy(actorId);
            orderSupport.saveOrder(keep);
        }
        return keep;
    }

    public boolean hasDishes(Order order) {
        return !orderSupport.activeLineItems(order).isEmpty();
    }

    /** Cancels an order that has no dishes and no money taken; answers whether it did. */
    public boolean cancelIfEmpty(Order order, String note, UUID actorId) {
        if (hasDishes(order) || !order.getPayments().isEmpty()) {
            return false;
        }
        cancelEmpty(order, note, actorId);
        return true;
    }

    private void cancelEmpty(Order order, String note, UUID actorId) {
        order.setStatus(OrderStatus.CANCELLED);
        order.setClosedAt(null);
        order.setUpdatedBy(actorId);
        orderSupport.addEvent(order, OrderEventType.CANCELLED, note + "; it had no dishes", actorId);
        orderSupport.saveOrder(order);
    }

    private void moveInto(Order target, Order source, String note, UUID actorId) {
        // Each order keeps the discount it had: percentages must not spread onto the other order's dishes.
        for (OrderDiscount discount : target.getDiscounts()) {
            discount.setDiscountType(OrderDiscountType.FIXED_AMOUNT);
            discount.setDiscountValue(discount.getAmountApplied());
        }
        for (OrderDiscount discount : new ArrayList<>(source.getDiscounts())) {
            OrderDiscount moved = orderSupport.cloneDiscount(discount, discount.getAmountApplied(), actorId);
            moved.setDiscountType(OrderDiscountType.FIXED_AMOUNT);
            moved.setDiscountValue(discount.getAmountApplied());
            target.addDiscount(moved);
        }
        entityManager.flush();
        // The dishes themselves move (not copies), so their kitchen tickets and stock records stay with them.
        entityManager.createNativeQuery("update {h-schema}order_line_items set order_id = :target where order_id = :source")
                .setParameter("target", target.getId()).setParameter("source", source.getId()).executeUpdate();
        entityManager.createNativeQuery("update {h-schema}kds_tickets set order_id = :target where order_id = :source")
                .setParameter("target", target.getId()).setParameter("source", source.getId()).executeUpdate();
        entityManager.refresh(target);
        entityManager.refresh(source);

        orderSupport.recalculateTotals(target);
        orderSupport.recalculateTotals(source);
        orderSupport.pruneZeroValueDiscounts(source);
        target.setBillRequestedAt(null);
        target.setUpdatedBy(actorId);
        source.setStatus(OrderStatus.CANCELLED);
        source.setClosedAt(null);
        source.setUpdatedBy(actorId);
        orderSupport.addEvent(target, OrderEventType.UPDATED, note + "; the dishes of " + source.getOrderNumber() + " joined this order", actorId);
        orderSupport.addEvent(source, OrderEventType.CANCELLED, note + "; its dishes moved to " + target.getOrderNumber(), actorId);
        orderSupport.saveOrder(source);
        orderSupport.saveOrder(target);
    }
}
