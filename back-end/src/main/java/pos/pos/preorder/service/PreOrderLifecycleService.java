package pos.pos.preorder.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.kds.entity.KdsTicket;
import pos.pos.kds.service.KdsOrderSyncService;
import pos.pos.kds.service.KdsSupport;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderItemOption;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderEventType;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderSource;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.enums.OrderType;
import pos.pos.order.service.OrderDomainSupport;
import pos.pos.order.service.OrderSupport;
import pos.pos.preorder.entity.PreOrder;
import pos.pos.preorder.entity.PreOrderItem;
import pos.pos.preorder.entity.PreOrderItemOption;
import pos.pos.preorder.enums.PreOrderPaymentStatus;
import pos.pos.preorder.enums.PreOrderSource;
import pos.pos.preorder.enums.PreOrderStatus;
import pos.pos.preorder.repository.PreOrderRepository;
import pos.pos.preorder.repository.ScheduledPreOrderRef;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.tables.entity.RestaurantTable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Moves a pre-order through its life: sent to the kitchen as a real order, refunded, or forfeited.
// Callers lock the reservation row first and the pre-order row second, everywhere, so these never race or deadlock.
@Service
@RequiredArgsConstructor
public class PreOrderLifecycleService {

    public static final Set<PreOrderStatus> LIVE = EnumSet.of(PreOrderStatus.SCHEDULED, PreOrderStatus.SENT);
    // Bookings whose pre-order may still go to the kitchen.
    public static final Set<ReservationStatus> SENDABLE = EnumSet.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN,
            ReservationStatus.SEATED
    );
    private static final Set<OrderStatus> EDITABLE_ORDER = EnumSet.of(OrderStatus.DRAFT, OrderStatus.OPEN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final PreOrderRepository preOrderRepository;
    private final OrderSupport orderSupport;
    private final OrderDomainSupport orderDomainSupport;
    private final KdsOrderSyncService kdsOrderSyncService;
    private final KdsSupport kdsSupport;
    private final EntityManager entityManager;

    // Reloads and locks a reservation this transaction hasn't changed.
    public Reservation lockFresh(Reservation reservation) {
        entityManager.refresh(reservation, LockModeType.PESSIMISTIC_WRITE);
        return reservation;
    }

    // Locks a reservation this transaction is changing, keeping the change.
    public void lockHeld(Reservation reservation) {
        entityManager.lock(reservation, LockModeType.PESSIMISTIC_WRITE);
    }

    // Turns a scheduled pre-order into a real order the guests have already paid for and fires it to the kitchen.
    // Counter items (not sent to the kitchen) stay pending for the waiter; kitchen tickets are due at the booking time.
    public Order send(PreOrder preOrder, UUID actorId, OffsetDateTime now) {
        if (preOrder.getStatus() != PreOrderStatus.SCHEDULED) {
            throw new IllegalStateException("only scheduled pre-orders can be sent");
        }
        Reservation reservation = preOrder.getReservation();

        Order order = new Order();
        order.setRestaurant(preOrder.getRestaurant());
        order.setBranch(reservation.getBranch());
        order.setReservation(reservation);
        order.setCustomer(reservation.getCustomer());
        order.setOrderNumber(orderSupport.nextOrderNumber(preOrder.getRestaurant()));
        order.setCurrency(preOrder.getCurrency());
        order.setOrderType(OrderType.DINE_IN);
        order.setSource(preOrder.getSource() == PreOrderSource.ONLINE ? OrderSource.WEB : OrderSource.POS);
        order.setStatus(OrderStatus.OPEN);
        order.setPaymentStatus(OrderPaymentStatus.UNPAID);
        order.setPrepaidTotal(preOrder.getPaidAmount());
        order.setGuestCount(Math.max(1, reservation.getPartySize()));
        order.setNotes(kitchenNote(preOrder, reservation));
        order.setOpenedAt(now);
        order.setCreatedBy(actorId);
        order.setUpdatedBy(actorId);
        if (reservation.getStatus() == ReservationStatus.SEATED) {
            freeReservationTable(reservation).ifPresent(order::setRestaurantTable);
        }

        for (PreOrderItem item : preOrder.getItems()) {
            order.addLineItem(toLineItem(item));
        }
        order.getLineItems().stream()
                .filter(OrderLineItem::goesToKitchen)
                .forEach(line -> line.setStatus(OrderLineItemStatus.FIRED));
        orderSupport.recalculateTotals(order);
        orderSupport.addEvent(order, OrderEventType.CREATED, "Created from the pre-order for reservation " + reservation.getReservationCode(), actorId);
        orderSupport.addEvent(order, OrderEventType.SENT_TO_KITCHEN, "Pre-order sent to the kitchen", actorId);
        Order saved = orderSupport.saveOrder(order);

        List<KdsTicket> tickets = kdsOrderSyncService.syncFromCurrentOrderState(saved, actorId);
        // The kitchen plans by when the guests sit down, not by when the ticket arrived.
        tickets.stream()
                .filter(ticket -> ticket.getDueAt() == null)
                .forEach(ticket -> ticket.setDueAt(reservation.getReservationStart()));
        if (!tickets.isEmpty()) {
            kdsSupport.saveTickets(tickets);
        }

        preOrder.setOrder(saved);
        preOrder.setStatus(PreOrderStatus.SENT);
        preOrder.setSentAt(now);
        preOrder.setUpdatedBy(actorId);
        preOrderRepository.save(preOrder);
        return saved;
    }

    // Cancelled before the kitchen started: the guest gets their money back.
    public void refund(PreOrder preOrder, String reason, UUID actorId, OffsetDateTime now) {
        if (preOrder.getStatus() != PreOrderStatus.SCHEDULED) {
            throw new IllegalStateException("only scheduled pre-orders can be refunded");
        }
        preOrder.setStatus(PreOrderStatus.CANCELLED);
        preOrder.setPaymentStatus(PreOrderPaymentStatus.REFUNDED);
        preOrder.setCancelledAt(now);
        preOrder.setCancellationReason(reason);
        preOrder.setUpdatedBy(actorId);
        preOrderRepository.save(preOrder);
    }

    // The kitchen already cooked and nobody came (or cancelled too late): the restaurant keeps the payment and
    // the order leaves the floor and the kitchen screens.
    public void forfeit(PreOrder preOrder, String reason, UUID actorId, OffsetDateTime now) {
        if (preOrder.getStatus() != PreOrderStatus.SENT) {
            throw new IllegalStateException("only sent pre-orders can be forfeited");
        }
        preOrder.setStatus(PreOrderStatus.FORFEITED);
        preOrder.setPaymentStatus(PreOrderPaymentStatus.RETAINED);
        preOrder.setForfeitedAt(now);
        preOrder.setCancellationReason(reason);
        preOrder.setUpdatedBy(actorId);
        preOrderRepository.save(preOrder);

        Order order = preOrder.getOrder();
        if (order == null || !EDITABLE_ORDER.contains(order.getStatus())) {
            return;
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedBy(actorId);
        order.getLineItems().stream()
                .filter(orderSupport::isFinanciallyActive)
                .forEach(line -> line.setStatus(OrderLineItemStatus.CANCELLED));
        orderSupport.recalculateTotals(order);
        orderDomainSupport.applyStatusSideEffects(order);
        orderSupport.addEvent(order, OrderEventType.CANCELLED, reason, actorId);
        orderSupport.saveOrder(order);
        kdsOrderSyncService.syncFromCurrentOrderState(order, actorId, reason);
    }

    // Guests sat down: put the already-sent order on their table so the waiter continues from it.
    public void seatAtReservationTable(PreOrder preOrder, Reservation reservation, UUID actorId) {
        Order order = preOrder.getOrder();
        if (preOrder.getStatus() != PreOrderStatus.SENT || order == null
                || !EDITABLE_ORDER.contains(order.getStatus()) || order.getRestaurantTable() != null) {
            return;
        }
        freeReservationTable(reservation).ifPresent(table -> {
            order.setRestaurantTable(table);
            order.setUpdatedBy(actorId);
            orderSupport.addEvent(order, OrderEventType.TABLE_CHANGED, "Guests seated at table " + table.getTableNumber(), actorId);
            orderSupport.saveOrder(order);
        });
    }

    // One scheduled pre-order for the dispatch job, in its own transaction: sent when due, refunded if its booking is gone.
    @Transactional
    public boolean processScheduled(ScheduledPreOrderRef ref, OffsetDateTime now) {
        Reservation reservation = entityManager.find(Reservation.class, ref.reservationId(), LockModeType.PESSIMISTIC_WRITE);
        PreOrder preOrder = preOrderRepository.lockById(ref.preOrderId()).orElse(null);
        if (reservation == null || preOrder == null || preOrder.getStatus() != PreOrderStatus.SCHEDULED) {
            return false;
        }
        if (!SENDABLE.contains(reservation.getStatus())) {
            refund(preOrder, "Reservation " + reservation.getStatus().name().toLowerCase().replace('_', ' ') + " before the kitchen started", null, now);
            return true;
        }
        if (now.isBefore(preOrder.sendAt())) {
            return false;
        }
        send(preOrder, null, now);
        return true;
    }

    // Guests checked in or sat down. Runs after their arrival is saved, in its own transaction, so a kitchen
    // problem never blocks the host stand. Early guests get their pre-order fired right away.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleArrival(UUID reservationId, UUID actorId) {
        Reservation reservation = entityManager.find(Reservation.class, reservationId, LockModeType.PESSIMISTIC_WRITE);
        if (reservation == null) {
            return;
        }
        preOrderRepository.lockLiveByReservationId(reservationId, LIVE).ifPresent(preOrder -> {
            if (preOrder.getStatus() == PreOrderStatus.SCHEDULED) {
                send(preOrder, actorId, OffsetDateTime.now(ZoneOffset.UTC));
            } else if (reservation.getStatus() == ReservationStatus.SEATED) {
                seatAtReservationTable(preOrder, reservation, actorId);
            }
        });
    }

    private OrderLineItem toLineItem(PreOrderItem item) {
        OrderLineItem line = new OrderLineItem();
        line.setMenuItem(item.getMenuItem());
        line.setVariant(item.getVariant());
        line.setItemNameSnapshot(item.getItemNameSnapshot());
        line.setVariantNameSnapshot(item.getVariantNameSnapshot());
        line.setSkuSnapshot(item.getSkuSnapshot());
        line.setUnitPriceSnapshot(item.getUnitPriceSnapshot());
        line.setVariantPriceDeltaSnapshot(item.getVariantPriceDeltaSnapshot());
        line.setQuantity(item.getQuantity());
        line.setNotes(item.getNotes());
        line.setStatus(OrderLineItemStatus.PENDING);
        for (PreOrderItemOption option : item.getOptions()) {
            OrderItemOption copy = new OrderItemOption();
            copy.setOptionItem(option.getOptionItem());
            copy.setOptionNameSnapshot(option.getOptionNameSnapshot());
            copy.setPriceDeltaSnapshot(option.getPriceDeltaSnapshot());
            copy.setQuantity(option.getQuantity());
            copy.setNotes(option.getNotes());
            line.addOption(copy);
        }
        return line;
    }

    // The reservation's main table, if no other open order is sitting on it.
    private Optional<RestaurantTable> freeReservationTable(Reservation reservation) {
        return reservation.getTableAssignments().stream()
                .sorted(Comparator.comparing(ReservationTableAssignment::isPrimaryAssignment).reversed())
                .map(ReservationTableAssignment::getRestaurantTable)
                .filter(table -> table != null && orderSupport.findCurrentOpenOrderForTable(table.getId()).isEmpty())
                .findFirst();
    }

    // Shown on the order and copied onto the kitchen tickets.
    private String kitchenNote(PreOrder preOrder, Reservation reservation) {
        String time = reservation.getReservationStart()
                .atZoneSameInstant(PreOrderPricing.restaurantZone(preOrder.getRestaurant()))
                .format(TIME);
        String guest = reservation.getContactName() == null ? "" : " for " + reservation.getContactName();
        String note = "Pre-order" + guest + " · reservation " + reservation.getReservationCode()
                + " at " + time + " (" + reservation.getPartySize() + " guests)";
        return preOrder.getNotes() == null ? note : note + " · " + preOrder.getNotes();
    }
}
