package pos.pos.unit.preorder.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pos.pos.exception.auth.AuthException;
import pos.pos.order.service.OrderSupport;
import pos.pos.preorder.dto.PreOrderActionRequest;
import pos.pos.preorder.dto.PreOrderItemRequest;
import pos.pos.preorder.dto.PreOrderRequest;
import pos.pos.preorder.dto.PreOrderResponse;
import pos.pos.preorder.entity.PreOrder;
import pos.pos.preorder.enums.PreOrderPaymentStatus;
import pos.pos.preorder.enums.PreOrderSource;
import pos.pos.preorder.enums.PreOrderStatus;
import pos.pos.preorder.mapper.PreOrderMapper;
import pos.pos.preorder.repository.PreOrderRepository;
import pos.pos.preorder.service.PreOrderLifecycleService;
import pos.pos.preorder.service.PreOrderPricing;
import pos.pos.preorder.service.PreOrderService;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.service.ReservationSupport;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.settings.entity.Settings;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pos.pos.unit.preorder.service.PreOrderFixtures.RESERVATION_ID;
import static pos.pos.unit.preorder.service.PreOrderFixtures.RESTAURANT_ID;
import static pos.pos.unit.preorder.service.PreOrderFixtures.inHours;
import static pos.pos.unit.preorder.service.PreOrderFixtures.preOrder;
import static pos.pos.unit.preorder.service.PreOrderFixtures.reservation;

@ExtendWith(MockitoExtension.class)
class PreOrderServiceTest {

    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Mock RestaurantScopeService restaurantScopeService;
    @Mock ReservationSupport reservationSupport;
    @Mock OrderSupport orderSupport;
    @Mock PreOrderRepository preOrderRepository;
    @Mock PreOrderPricing preOrderPricing;
    @Mock PreOrderLifecycleService preOrderLifecycleService;
    @Spy PreOrderMapper preOrderMapper = new PreOrderMapper();
    @Mock Authentication authentication;
    @InjectMocks PreOrderService preOrderService;

    private final PreOrderRequest request = PreOrderRequest.builder()
            .items(List.of(PreOrderItemRequest.builder().menuItemId(UUID.randomUUID()).quantity(2).build()))
            .notes("Birthday")
            .build();

    @Test void placesAPaidScheduledPreOrderWithTheRestaurantsLeadTime() {
        Reservation reservation = staffReservation(ReservationStatus.CONFIRMED, inHours(5));
        enabled(20);
        when(preOrderRepository.lockLiveByReservationId(eq(RESERVATION_ID), any())).thenReturn(Optional.empty());
        doAnswer(call -> {
            ((PreOrder) call.getArgument(0)).setTotal(new BigDecimal("42.50"));
            return null;
        }).when(preOrderPricing).applyItems(any(), eq(reservation), eq(request.getItems()), eq(false));
        when(preOrderRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        PreOrderResponse response = preOrderService.placeForReservation(authentication, RESTAURANT_ID, RESERVATION_ID, request);

        assertThat(response.getStatus()).isEqualTo(PreOrderStatus.SCHEDULED);
        assertThat(response.getPaymentStatus()).isEqualTo(PreOrderPaymentStatus.PAID);
        assertThat(response.getPaidAmount()).isEqualByComparingTo("42.50");
        assertThat(response.getLeadMinutes()).isEqualTo(20);
        assertThat(response.getSource()).isEqualTo(PreOrderSource.STAFF);
        assertThat(response.getSendAt()).isEqualTo(reservation.getReservationStart().minusMinutes(20));
        assertThat(response.getNotes()).isEqualTo("Birthday");
        verify(preOrderLifecycleService).lockFresh(reservation);
    }

    @Test void refusesWhenTheRestaurantHasPreOrdersOff() {
        staffReservation(ReservationStatus.CONFIRMED, inHours(5));
        when(orderSupport.loadSettings(any())).thenReturn(new Settings());

        assertThatThrownBy(() -> preOrderService.placeForReservation(authentication, RESTAURANT_ID, RESERVATION_ID, request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("not available");
        verify(preOrderRepository, never()).save(any());
    }

    @Test void refusesBookingsThatAreNoLongerWaiting() {
        staffReservation(ReservationStatus.CHECKED_IN, inHours(1));
        enabled(15);

        assertThatThrownBy(() -> preOrderService.placeForReservation(authentication, RESTAURANT_ID, RESERVATION_ID, request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("pending or confirmed");
    }

    @Test void refusesOnceTheKitchenCutoffHasPassed() {
        staffReservation(ReservationStatus.CONFIRMED, inHours(0).plusMinutes(10));
        enabled(15);
        when(preOrderRepository.lockLiveByReservationId(eq(RESERVATION_ID), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> preOrderService.placeForReservation(authentication, RESTAURANT_ID, RESERVATION_ID, request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("too late");
        verify(preOrderPricing, never()).applyItems(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test void refusesChangesOnceItIsInTheKitchen() {
        Reservation reservation = staffReservation(ReservationStatus.CONFIRMED, inHours(3));
        enabled(15);
        when(preOrderRepository.lockLiveByReservationId(eq(RESERVATION_ID), any()))
                .thenReturn(Optional.of(preOrder(reservation, PreOrderStatus.SENT, 15)));

        assertThatThrownBy(() -> preOrderService.placeForReservation(authentication, RESTAURANT_ID, RESERVATION_ID, request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("already in the kitchen");
    }

    @Test void guestsCanReplaceTheirPreOrderByCodeBeforeTheCutoff() {
        Reservation reservation = reservation(ReservationStatus.PENDING, inHours(6));
        when(reservationSupport.requirePublicReservation("RSV-1")).thenReturn(reservation);
        enabled(30);
        PreOrder existing = preOrder(reservation, PreOrderStatus.SCHEDULED, 45);
        when(preOrderRepository.lockLiveByReservationId(eq(RESERVATION_ID), any())).thenReturn(Optional.of(existing));
        when(preOrderRepository.save(existing)).thenReturn(existing);

        PreOrderResponse response = preOrderService.placePublic("RSV-1", request);

        // A replaced pre-order keeps its original cutoff even if the restaurant setting changed.
        assertThat(response.getLeadMinutes()).isEqualTo(45);
        verify(preOrderPricing).applyItems(existing, reservation, request.getItems(), true);
    }

    @Test void cancellingBeforeTheCutoffRefunds() {
        Reservation reservation = staffReservation(ReservationStatus.CONFIRMED, inHours(4));
        PreOrder scheduled = preOrder(reservation, PreOrderStatus.SCHEDULED, 15);
        when(preOrderRepository.lockLiveByReservationId(eq(RESERVATION_ID), any())).thenReturn(Optional.of(scheduled));
        when(restaurantScopeService.currentUserId(authentication)).thenReturn(ACTOR_ID);

        preOrderService.cancelForReservation(authentication, RESTAURANT_ID, RESERVATION_ID, new PreOrderActionRequest("Plans changed"));

        verify(preOrderLifecycleService).refund(eq(scheduled), eq("Plans changed"), eq(ACTOR_ID), any());
    }

    @Test void cancellingAfterTheCutoffIsRefused() {
        Reservation reservation = reservation(ReservationStatus.CONFIRMED, inHours(0).plusMinutes(5));
        when(reservationSupport.requirePublicReservation("RSV-1")).thenReturn(reservation);
        PreOrder scheduled = preOrder(reservation, PreOrderStatus.SCHEDULED, 15);
        when(preOrderRepository.lockLiveByReservationId(eq(RESERVATION_ID), any())).thenReturn(Optional.of(scheduled));

        assertThatThrownBy(() -> preOrderService.cancelPublic("RSV-1", null))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("kitchen has started");
        verify(preOrderLifecycleService, never()).refund(any(), anyString(), any(), any());
    }

    @Test void staffCanSendAScheduledPreOrderEarly() {
        Reservation reservation = staffReservation(ReservationStatus.CONFIRMED, inHours(2));
        when(preOrderLifecycleService.lockFresh(reservation)).thenReturn(reservation);
        PreOrder scheduled = preOrder(reservation, PreOrderStatus.SCHEDULED, 15);
        when(preOrderRepository.lockLiveByReservationId(eq(RESERVATION_ID), any())).thenReturn(Optional.of(scheduled));
        when(restaurantScopeService.currentUserId(authentication)).thenReturn(ACTOR_ID);

        preOrderService.sendNow(authentication, RESTAURANT_ID, RESERVATION_ID);

        ArgumentCaptor<PreOrder> sent = ArgumentCaptor.forClass(PreOrder.class);
        verify(preOrderLifecycleService).send(sent.capture(), eq(ACTOR_ID), any());
        assertThat(sent.getValue()).isSameAs(scheduled);
    }

    @Test void theKitchenListCoversAtMostSixtyTwoDays() {
        UUID branchId = UUID.randomUUID();
        java.time.OffsetDateTime from = inHours(0);
        when(preOrderRepository.findForBranch(RESTAURANT_ID, branchId, from, from.plusDays(62), null)).thenReturn(List.of());

        assertThat(preOrderService.getForBranch(authentication, RESTAURANT_ID, branchId, from, from.plusDays(62), null)).isEmpty();
        assertThatThrownBy(() -> preOrderService.getForBranch(authentication, RESTAURANT_ID, branchId, from, from.plusDays(62).plusSeconds(1), null))
                .isInstanceOf(AuthException.class)
                .hasMessage("Choose at most 62 days");
        verify(preOrderRepository, never()).findForBranch(RESTAURANT_ID, branchId, from, from.plusDays(62).plusSeconds(1), null);
    }

    private Reservation staffReservation(ReservationStatus status, java.time.OffsetDateTime start) {
        Reservation reservation = reservation(status, start);
        when(reservationSupport.requireReservation(RESTAURANT_ID, RESERVATION_ID)).thenReturn(reservation);
        return reservation;
    }

    private void enabled(int leadMinutes) {
        Settings settings = new Settings();
        settings.setPreOrdersEnabled(true);
        settings.setPreOrderLeadMinutes(leadMinutes);
        when(orderSupport.loadSettings(any())).thenReturn(settings);
    }
}
