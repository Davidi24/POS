package pos.pos.unit.preorder.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.kds.entity.KdsTicket;
import pos.pos.kds.service.KdsOrderSyncService;
import pos.pos.kds.service.KdsSupport;
import pos.pos.menu.entity.MenuItem;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.enums.OrderSource;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.enums.OrderType;
import pos.pos.order.service.OrderDomainSupport;
import pos.pos.order.service.OrderSupport;
import pos.pos.preorder.entity.PreOrder;
import pos.pos.preorder.enums.PreOrderPaymentStatus;
import pos.pos.preorder.enums.PreOrderSource;
import pos.pos.preorder.enums.PreOrderStatus;
import pos.pos.preorder.repository.PreOrderRepository;
import pos.pos.preorder.repository.ScheduledPreOrderRef;
import pos.pos.preorder.service.PreOrderLifecycleService;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pos.pos.unit.preorder.service.PreOrderFixtures.RESERVATION_ID;
import static pos.pos.unit.preorder.service.PreOrderFixtures.item;
import static pos.pos.unit.preorder.service.PreOrderFixtures.menuItem;
import static pos.pos.unit.preorder.service.PreOrderFixtures.preOrder;
import static pos.pos.unit.preorder.service.PreOrderFixtures.reservation;

@ExtendWith(MockitoExtension.class)
class PreOrderLifecycleServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 26, 18, 45, 0, 0, ZoneOffset.UTC);

    @Mock PreOrderRepository preOrderRepository;
    @Mock OrderSupport orderSupport;
    @Mock OrderDomainSupport orderDomainSupport;
    @Mock KdsOrderSyncService kdsOrderSyncService;
    @Mock KdsSupport kdsSupport;
    @Mock EntityManager entityManager;
    @InjectMocks PreOrderLifecycleService lifecycle;

    @Test void sendingBuildsAPrepaidOrderFromTheSavedPricesAndFiresOnlyKitchenItems() {
        Reservation reservation = reservation(ReservationStatus.CONFIRMED, NOW.plusMinutes(15));
        PreOrder preOrder = preOrder(reservation, PreOrderStatus.SCHEDULED, 15);
        preOrder.setSource(PreOrderSource.ONLINE);
        MenuItem pasta = menuItem("Carbonara", "14.00", true);
        MenuItem cola = menuItem("Cola", "3.00", false);
        preOrder.addItem(item(pasta, 2, "12.00"));
        preOrder.addItem(item(cola, 1, "2.50"));
        KdsTicket ticket = new KdsTicket();
        when(orderSupport.nextOrderNumber(any())).thenReturn("ORD-7");
        when(orderSupport.saveOrder(any())).thenAnswer(call -> call.getArgument(0));
        when(kdsOrderSyncService.syncFromCurrentOrderState(any(), any())).thenReturn(List.of(ticket));

        Order order = lifecycle.send(preOrder, null, NOW);

        assertThat(order.getOrderType()).isEqualTo(OrderType.DINE_IN);
        assertThat(order.getSource()).isEqualTo(OrderSource.WEB);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(order.getReservation()).isSameAs(reservation);
        assertThat(order.getPrepaidTotal()).isEqualByComparingTo("30.00");
        assertThat(order.getGuestCount()).isEqualTo(4);
        assertThat(order.getNotes()).contains("Pre-order for Anna Rossi").contains("RSV-1");
        // Prices come from the pre-order, not today's menu.
        assertThat(order.getLineItems()).extracting(OrderLineItem::getUnitPriceSnapshot)
                .usingElementComparator(java.math.BigDecimal::compareTo)
                .containsExactly(new java.math.BigDecimal("12.00"), new java.math.BigDecimal("2.50"));
        assertThat(order.getLineItems()).extracting(OrderLineItem::getStatus)
                .containsExactly(OrderLineItemStatus.FIRED, OrderLineItemStatus.PENDING);
        assertThat(ticket.getDueAt()).isEqualTo(reservation.getReservationStart());
        verify(kdsSupport).saveTickets(List.of(ticket));
        verify(orderSupport).recalculateTotals(order);
        assertThat(preOrder.getStatus()).isEqualTo(PreOrderStatus.SENT);
        assertThat(preOrder.getOrder()).isSameAs(order);
        assertThat(preOrder.getSentAt()).isEqualTo(NOW);
    }

    @Test void refundingCancelsAndMarksTheMoneyReturned() {
        PreOrder preOrder = preOrder(reservation(ReservationStatus.CONFIRMED, NOW.plusHours(3)), PreOrderStatus.SCHEDULED, 15);

        lifecycle.refund(preOrder, "Plans changed", null, NOW);

        assertThat(preOrder.getStatus()).isEqualTo(PreOrderStatus.CANCELLED);
        assertThat(preOrder.getPaymentStatus()).isEqualTo(PreOrderPaymentStatus.REFUNDED);
        assertThat(preOrder.getCancelledAt()).isEqualTo(NOW);
        verify(preOrderRepository).save(preOrder);
    }

    @Test void forfeitingKeepsTheMoneyAndTakesTheOrderOffTheFloor() {
        PreOrder preOrder = preOrder(reservation(ReservationStatus.NO_SHOW, NOW.minusMinutes(40)), PreOrderStatus.SENT, 15);
        Order order = new Order();
        order.setStatus(OrderStatus.OPEN);
        OrderLineItem line = new OrderLineItem();
        line.setStatus(OrderLineItemStatus.FIRED);
        order.addLineItem(line);
        preOrder.setOrder(order);
        preOrder.setSentAt(NOW.minusMinutes(55));
        when(orderSupport.isFinanciallyActive(line)).thenReturn(true);

        lifecycle.forfeit(preOrder, "Guests did not arrive; payment kept", null, NOW);

        assertThat(preOrder.getStatus()).isEqualTo(PreOrderStatus.FORFEITED);
        assertThat(preOrder.getPaymentStatus()).isEqualTo(PreOrderPaymentStatus.RETAINED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(line.getStatus()).isEqualTo(OrderLineItemStatus.CANCELLED);
        verify(orderSupport).saveOrder(order);
        verify(kdsOrderSyncService).syncFromCurrentOrderState(order, null, "Guests did not arrive; payment kept");
    }

    @Test void theJobWaitsUntilThePreOrderIsDue() {
        Reservation reservation = reservation(ReservationStatus.CONFIRMED, NOW.plusMinutes(40));
        PreOrder preOrder = preOrder(reservation, PreOrderStatus.SCHEDULED, 15);
        stubLocks(reservation, preOrder);

        assertThat(lifecycle.processScheduled(new ScheduledPreOrderRef(preOrder.getId(), RESERVATION_ID), NOW)).isFalse();

        assertThat(preOrder.getStatus()).isEqualTo(PreOrderStatus.SCHEDULED);
        verify(orderSupport, never()).saveOrder(any());
    }

    @Test void theJobSendsOnceTheLeadTimeIsReached() {
        Reservation reservation = reservation(ReservationStatus.CONFIRMED, NOW.plusMinutes(10));
        PreOrder preOrder = preOrder(reservation, PreOrderStatus.SCHEDULED, 15);
        preOrder.addItem(item(menuItem("Carbonara", "14.00", true), 1, "14.00"));
        stubLocks(reservation, preOrder);
        when(orderSupport.nextOrderNumber(any())).thenReturn("ORD-8");
        when(orderSupport.saveOrder(any())).thenAnswer(call -> call.getArgument(0));
        when(kdsOrderSyncService.syncFromCurrentOrderState(any(), any())).thenReturn(List.of());

        assertThat(lifecycle.processScheduled(new ScheduledPreOrderRef(preOrder.getId(), RESERVATION_ID), NOW)).isTrue();

        assertThat(preOrder.getStatus()).isEqualTo(PreOrderStatus.SENT);
    }

    @Test void theJobRefundsWhenTheBookingIsGone() {
        Reservation reservation = reservation(ReservationStatus.CANCELLED, NOW.plusMinutes(10));
        PreOrder preOrder = preOrder(reservation, PreOrderStatus.SCHEDULED, 15);
        stubLocks(reservation, preOrder);

        assertThat(lifecycle.processScheduled(new ScheduledPreOrderRef(preOrder.getId(), RESERVATION_ID), NOW)).isTrue();

        assertThat(preOrder.getStatus()).isEqualTo(PreOrderStatus.CANCELLED);
        assertThat(preOrder.getPaymentStatus()).isEqualTo(PreOrderPaymentStatus.REFUNDED);
    }

    @Test void earlyGuestsGetTheirPreOrderFiredOnArrival() {
        Reservation reservation = reservation(ReservationStatus.CHECKED_IN, NOW.plusHours(1));
        PreOrder preOrder = preOrder(reservation, PreOrderStatus.SCHEDULED, 15);
        when(entityManager.find(Reservation.class, RESERVATION_ID, LockModeType.PESSIMISTIC_WRITE)).thenReturn(reservation);
        when(preOrderRepository.lockLiveByReservationId(eq(RESERVATION_ID), any())).thenReturn(Optional.of(preOrder));
        when(orderSupport.nextOrderNumber(any())).thenReturn("ORD-9");
        when(orderSupport.saveOrder(any())).thenAnswer(call -> call.getArgument(0));
        when(kdsOrderSyncService.syncFromCurrentOrderState(any(), any())).thenReturn(List.of());

        lifecycle.handleArrival(RESERVATION_ID, null);

        assertThat(preOrder.getStatus()).isEqualTo(PreOrderStatus.SENT);
    }

    @Test void seatingPutsTheSentOrderOnTheReservedTable() {
        Reservation reservation = reservation(ReservationStatus.SEATED, NOW);
        pos.pos.tables.entity.RestaurantTable table = new pos.pos.tables.entity.RestaurantTable();
        table.setId(UUID.randomUUID());
        table.setTableNumber("T4");
        pos.pos.reservation.entity.ReservationTableAssignment assignment = new pos.pos.reservation.entity.ReservationTableAssignment();
        assignment.setRestaurantTable(table);
        assignment.setPrimaryAssignment(true);
        reservation.getTableAssignments().add(assignment);
        PreOrder preOrder = preOrder(reservation, PreOrderStatus.SENT, 15);
        Order order = new Order();
        order.setStatus(OrderStatus.OPEN);
        preOrder.setOrder(order);
        when(orderSupport.findCurrentOpenOrderForTable(table.getId())).thenReturn(Optional.empty());

        lifecycle.seatAtReservationTable(preOrder, reservation, null);

        assertThat(order.getRestaurantTable()).isSameAs(table);
        verify(orderSupport).saveOrder(order);
    }

    private void stubLocks(Reservation reservation, PreOrder preOrder) {
        when(entityManager.find(Reservation.class, RESERVATION_ID, LockModeType.PESSIMISTIC_WRITE)).thenReturn(reservation);
        when(preOrderRepository.lockById(preOrder.getId())).thenReturn(Optional.of(preOrder));
    }
}
