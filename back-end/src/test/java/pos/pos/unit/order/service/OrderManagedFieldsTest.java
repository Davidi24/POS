package pos.pos.unit.order.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pos.pos.order.service.*;
import pos.pos.order.dto.*;
import pos.pos.order.entity.Order;
import pos.pos.restaurant.entity.*;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.kds.service.KdsOrderSyncService;
import pos.pos.exception.auth.AuthException;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OrderManagedFieldsTest {
    @Mock RestaurantScopeService restaurants;
    @Mock OrderSupport support;
    @Mock OrderDomainSupport domain;
    @Mock KdsOrderSyncService kds;
    @Mock Authentication authentication;
    @InjectMocks OrderCommandService service;
    @Test void ignoresCallerNumberCurrencyAndOpeningTimeWhenCreating() {
        var restaurant = new Restaurant(); restaurant.setId(UUID.randomUUID()); restaurant.setCurrency("EUR");
        var branch = new Branch(); branch.setId(UUID.randomUUID()); branch.setRestaurant(restaurant);
        when(restaurants.requireManageableRestaurant(authentication,restaurant.getId())).thenReturn(restaurant);
        when(support.resolveManagedBranch(authentication,restaurant.getId(),branch.getId())).thenReturn(branch);
        when(support.nextOrderNumber(restaurant)).thenReturn("ORD-SERVER");
        when(support.saveOrder(any())).thenAnswer(i -> i.getArgument(0));
        var request = CreateOrderRequest.builder().orderNumber("CALLER").currency("USD").openedAt(OffsetDateTime.parse("2000-01-01T00:00:00Z")).build();
        var before = OffsetDateTime.now().minusSeconds(1);
        service.createBranchOrder(authentication,restaurant.getId(),branch.getId(),request);
        var saved = ArgumentCaptor.forClass(Order.class); verify(support).saveOrder(saved.capture());
        assertThat(saved.getValue().getOrderNumber()).isEqualTo("ORD-SERVER"); assertThat(saved.getValue().getCurrency()).isEqualTo("EUR");
        assertThat(saved.getValue().getOpenedAt()).isAfter(before);
    }
    @Test void updatesCannotChangeHistoricalCurrency() {
        UUID restaurant = UUID.randomUUID(), id = UUID.randomUUID(); var order = new Order(); var branch = new Branch(); branch.setId(UUID.randomUUID()); order.setBranch(branch);
        when(support.requireOrder(restaurant,id)).thenReturn(order);
        assertThatThrownBy(() -> service.updateOrder(authentication,restaurant,id,UpdateOrderRequest.builder().currency("USD").build())).isInstanceOf(AuthException.class).hasMessageContaining("assigned by the server");
        verify(support,never()).saveOrder(any());
    }
}
