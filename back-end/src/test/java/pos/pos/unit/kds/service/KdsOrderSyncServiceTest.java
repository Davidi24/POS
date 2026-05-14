package pos.pos.unit.kds.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.kds.entity.KdsStation;
import pos.pos.kds.entity.KdsStationRouting;
import pos.pos.kds.entity.KdsTicket;
import pos.pos.kds.enums.KdsPriority;
import pos.pos.kds.enums.KdsStationType;
import pos.pos.kds.service.KdsOrderSyncService;
import pos.pos.kds.service.KdsSupport;
import pos.pos.menu.entity.MenuItem;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.service.OrderSupport;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KdsOrderSyncService")
class KdsOrderSyncServiceTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000001021");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000001022");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000001023");
    private static final UUID LINE_ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000001024");
    private static final UUID MENU_ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000001025");
    private static final UUID STATION_ID = UUID.fromString("00000000-0000-0000-0000-000000001026");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000001027");

    @Mock
    private KdsSupport kdsSupport;

    @Mock
    private OrderSupport orderSupport;

    private KdsOrderSyncService kdsOrderSyncService;

    @BeforeEach
    void setUp() {
        kdsOrderSyncService = new KdsOrderSyncService(kdsSupport, orderSupport);
    }

    @Test
    @DisplayName("Should create a KDS ticket when a routed line item is fired")
    void shouldCreateTicketForRoutedFiredItem() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setCode("MAIN");

        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        branch.setRestaurant(restaurant);

        MenuItem menuItem = new MenuItem();
        menuItem.setId(MENU_ITEM_ID);
        menuItem.setName("Burger");

        OrderLineItem lineItem = new OrderLineItem();
        lineItem.setId(LINE_ITEM_ID);
        lineItem.setMenuItem(menuItem);
        lineItem.setItemNameSnapshot("Burger");
        lineItem.setQuantity(2);
        lineItem.setStatus(OrderLineItemStatus.FIRED);

        Order order = new Order();
        order.setId(ORDER_ID);
        order.setRestaurant(restaurant);
        order.setBranch(branch);
        order.setOpenedAt(OffsetDateTime.parse("2026-05-14T12:00:00Z"));
        order.addLineItem(lineItem);

        KdsStation station = new KdsStation();
        station.setId(STATION_ID);
        station.setRestaurant(restaurant);
        station.setBranch(branch);
        station.setCode("HOT_LINE");
        station.setName("Hot Line");
        station.setStationType(KdsStationType.GRILL);

        KdsStationRouting routing = new KdsStationRouting();
        routing.setStation(station);
        routing.setMenuItem(menuItem);
        routing.setPriority(KdsPriority.RUSH);
        routing.setCourseLabel("Mains");

        when(kdsSupport.loadActiveBranchRoutings(BRANCH_ID)).thenReturn(List.of(routing));
        when(kdsSupport.findActiveTicketItem(LINE_ITEM_ID)).thenReturn(Optional.empty());
        when(kdsSupport.findActiveTicket(ORDER_ID, STATION_ID)).thenReturn(Optional.empty());
        when(kdsSupport.nextTicketNumber(restaurant)).thenReturn("MAIN_KDS-0001");
        when(kdsSupport.maxPriority(any())).thenReturn(KdsPriority.RUSH);
        when(kdsSupport.loadOrderTickets(ORDER_ID)).thenReturn(List.of());

        kdsOrderSyncService.syncFromCurrentOrderState(order, ACTOR_ID);

        ArgumentCaptor<Collection<KdsTicket>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(kdsSupport).saveTickets(captor.capture());

        KdsTicket savedTicket = captor.getValue().iterator().next();
        assertThat(savedTicket.getOrder()).isEqualTo(order);
        assertThat(savedTicket.getStation()).isEqualTo(station);
        assertThat(savedTicket.getTicketNumber()).isEqualTo("MAIN_KDS-0001");
        assertThat(savedTicket.getItems()).hasSize(1);
        assertThat(savedTicket.getItems().get(0).getOrderLineItem()).isEqualTo(lineItem);
        assertThat(savedTicket.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(savedTicket.getItems().get(0).getPriority()).isEqualTo(KdsPriority.RUSH);
    }
}
