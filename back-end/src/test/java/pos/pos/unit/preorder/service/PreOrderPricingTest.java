package pos.pos.unit.preorder.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.exception.auth.AuthException;
import pos.pos.menu.entity.MenuItem;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.service.OrderSupport;
import pos.pos.preorder.dto.PreOrderItemRequest;
import pos.pos.preorder.entity.PreOrder;
import pos.pos.preorder.enums.PreOrderStatus;
import pos.pos.preorder.service.PreOrderPricing;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static pos.pos.unit.preorder.service.PreOrderFixtures.inHours;
import static pos.pos.unit.preorder.service.PreOrderFixtures.menuItem;
import static pos.pos.unit.preorder.service.PreOrderFixtures.preOrder;
import static pos.pos.unit.preorder.service.PreOrderFixtures.reservation;

@ExtendWith(MockitoExtension.class)
class PreOrderPricingTest {

    @Mock OrderSupport orderSupport;
    @InjectMocks PreOrderPricing pricing;

    @Test void copiesTheOrderPricingIntoThePreOrder() {
        Reservation reservation = reservation(ReservationStatus.CONFIRMED, inHours(24));
        PreOrder preOrder = preOrder(reservation, PreOrderStatus.SCHEDULED, 15);
        MenuItem pasta = menuItem("Carbonara", "14.00", true);
        when(orderSupport.buildLineItem(any(), any())).thenReturn(line(pasta, 2, "14.00", "28.00"));
        doAnswer(call -> {
            Order draft = call.getArgument(0);
            draft.setSubtotal(new BigDecimal("28.00"));
            draft.setTaxTotal(new BigDecimal("2.55"));
            draft.setServiceChargeTotal(new BigDecimal("1.40"));
            draft.setTotal(new BigDecimal("29.40"));
            return null;
        }).when(orderSupport).recalculateTotals(any());

        pricing.applyItems(preOrder, reservation, List.of(request(pasta, 2)), false);

        assertThat(preOrder.getItems()).hasSize(1);
        assertThat(preOrder.getItems().get(0).getItemNameSnapshot()).isEqualTo("Carbonara");
        assertThat(preOrder.getItems().get(0).getLineTotal()).isEqualByComparingTo("28.00");
        assertThat(preOrder.getTotal()).isEqualByComparingTo("29.40");
        assertThat(preOrder.getServiceChargeTotal()).isEqualByComparingTo("1.40");
        assertThat(preOrder.getCurrency()).isEqualTo("EUR");
    }

    @Test void refusesDishesFromAMenuThatIsOffOnTheBookingDate() {
        Reservation reservation = reservation(ReservationStatus.CONFIRMED, inHours(24));
        PreOrder preOrder = preOrder(reservation, PreOrderStatus.SCHEDULED, 15);
        MenuItem summerSpecial = menuItem("Summer special", "9.00", true);
        summerSpecial.getSection().getMenu().setAvailableUntilDate(LocalDate.now().minusDays(1));
        when(orderSupport.buildLineItem(any(), any())).thenReturn(line(summerSpecial, 1, "9.00", "9.00"));

        assertThatThrownBy(() -> pricing.applyItems(preOrder, reservation, List.of(request(summerSpecial, 1)), false))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("not on the menu");
    }

    @Test void guestsOnlineCanOnlyPickDishesShownInTheOnlineMenu() {
        Reservation reservation = reservation(ReservationStatus.CONFIRMED, inHours(24));
        PreOrder preOrder = preOrder(reservation, PreOrderStatus.SCHEDULED, 15);
        MenuItem staffOnly = menuItem("Chef's tasting", "60.00", true);
        when(orderSupport.buildLineItem(any(), any())).thenReturn(line(staffOnly, 1, "60.00", "60.00"));

        assertThatThrownBy(() -> pricing.applyItems(preOrder, reservation, List.of(request(staffOnly, 1)), true))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("can't be ordered online");
    }

    private static PreOrderItemRequest request(MenuItem item, int quantity) {
        return PreOrderItemRequest.builder().menuItemId(item.getId()).quantity(quantity).build();
    }

    private static OrderLineItem line(MenuItem item, int quantity, String unitPrice, String lineTotal) {
        OrderLineItem line = new OrderLineItem();
        line.setId(UUID.randomUUID());
        line.setMenuItem(item);
        line.setItemNameSnapshot(item.getName());
        line.setQuantity(quantity);
        line.setUnitPriceSnapshot(new BigDecimal(unitPrice));
        line.setLineTotal(new BigDecimal(lineTotal));
        return line;
    }
}
