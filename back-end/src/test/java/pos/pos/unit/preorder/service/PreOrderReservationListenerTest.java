package pos.pos.unit.preorder.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.exception.auth.AuthException;
import pos.pos.preorder.entity.PreOrder;
import pos.pos.preorder.enums.PreOrderStatus;
import pos.pos.preorder.repository.PreOrderRepository;
import pos.pos.preorder.service.PreOrderLifecycleService;
import pos.pos.preorder.service.PreOrderReservationListener;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.event.ReservationDeletingEvent;
import pos.pos.reservation.event.ReservationStatusChangedEvent;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static pos.pos.unit.preorder.service.PreOrderFixtures.RESERVATION_ID;
import static pos.pos.unit.preorder.service.PreOrderFixtures.RESTAURANT_ID;
import static pos.pos.unit.preorder.service.PreOrderFixtures.inHours;
import static pos.pos.unit.preorder.service.PreOrderFixtures.preOrder;
import static pos.pos.unit.preorder.service.PreOrderFixtures.reservation;

@ExtendWith(MockitoExtension.class)
class PreOrderReservationListenerTest {

    @Mock PreOrderRepository preOrderRepository;
    @Mock PreOrderLifecycleService lifecycle;
    @Mock EntityManager entityManager;
    @InjectMocks PreOrderReservationListener listener;

    @Test void cancellingTheBookingBeforeTheKitchenRefunds() {
        PreOrder scheduled = live(ReservationStatus.CANCELLED, PreOrderStatus.SCHEDULED);

        listener.onBookingEnded(event(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED));

        verify(lifecycle).refund(eq(scheduled), anyString(), any(), any());
    }

    @Test void aNoShowAfterTheKitchenStartedKeepsThePayment() {
        PreOrder sent = live(ReservationStatus.NO_SHOW, PreOrderStatus.SENT);

        listener.onBookingEnded(event(ReservationStatus.CONFIRMED, ReservationStatus.NO_SHOW));

        verify(lifecycle).forfeit(eq(sent), eq("Guests did not arrive; payment kept"), any(), any());
    }

    @Test void otherStatusChangesLeaveThePreOrderAlone() {
        listener.onBookingEnded(event(ReservationStatus.PENDING, ReservationStatus.CONFIRMED));

        verifyNoInteractions(lifecycle, preOrderRepository, entityManager);
    }

    @Test void arrivalHandsOverToTheKitchenSide() {
        listener.onGuestsArrived(event(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN));

        verify(lifecycle).handleArrival(RESERVATION_ID, null);
    }

    @Test void aKitchenProblemNeverBlocksTheArrival() {
        doThrow(new IllegalStateException("kds down")).when(lifecycle).handleArrival(RESERVATION_ID, null);

        assertThatCode(() -> listener.onGuestsArrived(event(ReservationStatus.CHECKED_IN, ReservationStatus.SEATED)))
                .doesNotThrowAnyException();
    }

    @Test void aBookingWithAPreOrderCannotBeDeleted() {
        when(preOrderRepository.existsByReservation_Id(RESERVATION_ID)).thenReturn(true);

        assertThatThrownBy(() -> listener.onDeleting(new ReservationDeletingEvent(RESERVATION_ID, RESTAURANT_ID)))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Cancel it instead");
    }

    private PreOrder live(ReservationStatus reservationStatus, PreOrderStatus preOrderStatus) {
        Reservation reservation = reservation(reservationStatus, inHours(1));
        when(entityManager.find(Reservation.class, RESERVATION_ID)).thenReturn(reservation);
        PreOrder preOrder = preOrder(reservation, preOrderStatus, 15);
        when(preOrderRepository.lockLiveByReservationId(eq(RESERVATION_ID), any())).thenReturn(Optional.of(preOrder));
        return preOrder;
    }

    private static ReservationStatusChangedEvent event(ReservationStatus from, ReservationStatus to) {
        return new ReservationStatusChangedEvent(RESERVATION_ID, RESTAURANT_ID, from, to, null);
    }
}
