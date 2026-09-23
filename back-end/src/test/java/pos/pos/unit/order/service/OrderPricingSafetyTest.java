package pos.pos.unit.order.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.order.service.OrderSupport;
import pos.pos.order.entity.*;
import pos.pos.menu.entity.*;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.customer.repository.CustomerRepository;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.tables.repository.RestaurantTableRepository;
import pos.pos.menu.repository.*;
import pos.pos.settings.repository.SettingsRepository;
import pos.pos.order.repository.OrderRepository;
import pos.pos.order.mapper.OrderMapper;
import pos.pos.exception.auth.AuthException;
import pos.pos.order.enums.OrderDiscountType;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderPricingSafetyTest {
    @Mock RestaurantScopeService restaurantScopeService;
    @Mock BranchRepository branches;
    @Mock CustomerRepository customers;
    @Mock ReservationRepository reservations;
    @Mock RestaurantTableRepository tables;
    @Mock MenuItemRepository items;
    @Mock MenuVariantRepository variants;
    @Mock OptionItemRepository options;
    @Mock SettingsRepository settings;
    @Mock OrderRepository orders;
    @Mock OrderMapper mapper;
    @InjectMocks OrderSupport support;
    private OrderLineItem line(String price, int quantity) {
        var line = new OrderLineItem(); line.setUnitPriceSnapshot(new BigDecimal(price)); line.setQuantity(quantity); return line;
    }
    private Order order(OrderLineItem... lines) {
        var order = new Order(); var restaurant = new Restaurant(); restaurant.setId(UUID.randomUUID());
        order.setRestaurant(restaurant); order.setCurrency("EUR"); order.setTaxRateSnapshot(BigDecimal.ZERO);
        when(settings.findByRestaurant_Id(restaurant.getId())).thenReturn(Optional.empty());
        for(var line:lines) order.addLineItem(line); return order;
    }
    @Test void pricesUseVariantSnapshotAndOptionsPerUnit() {
        var line = line("10",2); var variant = new MenuVariant(); variant.setPriceDelta(new BigDecimal("100")); line.setVariant(variant);
        line.setVariantPriceDeltaSnapshot(new BigDecimal("2")); var option = new OrderItemOption(); option.setQuantity(1); option.setPriceDeltaSnapshot(new BigDecimal("1.50")); line.addOption(option);
        support.refreshLineItemPricing(line);
        assertThat(line.getLineTotal()).isEqualByComparingTo("27");
        variant.setPriceDelta(new BigDecimal("999")); line.setQuantity(3); support.refreshLineItemPricing(line);
        assertThat(line.getLineTotal()).isEqualByComparingTo("40.50");
        assertThat(support.cloneLineItem(line).getVariantPriceDeltaSnapshot()).isEqualByComparingTo("2");
    }
    @Test void calculatesExclusiveTaxAfterDiscount() {
        var line = line("100",1); var order = order(line); order.setTaxRateSnapshot(new BigDecimal("20"));
        var discount = new OrderDiscount(); discount.setDiscountType(OrderDiscountType.FIXED_AMOUNT); discount.setDiscountValue(new BigDecimal("10")); order.addDiscount(discount);
        support.recalculateTotals(order);
        assertThat(order.getTaxTotal()).isEqualByComparingTo("18"); assertThat(order.getTotal()).isEqualByComparingTo("108");
        assertThat(line.getLineTotal()).isEqualByComparingTo("108");
    }
    @Test void includedTaxIsReportedWithoutAddingItAgain() {
        var line = line("120",1); var order = order(line); order.setTaxRateSnapshot(new BigDecimal("20")); order.setTaxInclusiveSnapshot(true);
        support.recalculateTotals(order); assertThat(order.getTaxTotal()).isEqualByComparingTo("20"); assertThat(order.getTotal()).isEqualByComparingTo("120");
        assertThat(line.getLineTotal()).isEqualByComparingTo("120");
    }
    @Test void existingTaxSnapshotSurvivesSettingsChanges() {
        var line = line("100",1); var order = order(line); order.setTaxRateSnapshot(new BigDecimal("5"));
        var changed = new pos.pos.settings.entity.Settings(); changed.setOrderTaxRate(new BigDecimal("25"));
        when(settings.findByRestaurant_Id(order.getRestaurant().getId())).thenReturn(Optional.of(changed));
        support.recalculateTotals(order); assertThat(order.getTotal()).isEqualByComparingTo("105");
    }
    @Test void validatesRequiredMinimumMaximumAndDuplicates() {
        var item = new MenuItem(); var group = new OptionGroup(); group.setId(UUID.randomUUID()); group.setName("Sauce"); group.setActive(true); group.setRequired(true); group.setMaxSelect(1);
        var link = new MenuItemOptionGroup(); link.setOptionGroup(group); link.setMenuItem(item); item.getOptionGroups().add(link);
        var line = line("10",1); line.setMenuItem(item);
        assertThatThrownBy(() -> support.validateOptionSelection(line)).isInstanceOf(AuthException.class);
        var choice = new OptionItem(); choice.setId(UUID.randomUUID()); choice.setOptionGroup(group); var first = new OrderItemOption(); first.setId(UUID.randomUUID()); first.setOptionItem(choice); line.addOption(first);
        assertThatCode(() -> support.validateOptionSelection(line)).doesNotThrowAnyException();
        var duplicate = new OrderItemOption(); duplicate.setId(UUID.randomUUID()); duplicate.setOptionItem(choice); line.addOption(duplicate);
        assertThatThrownBy(() -> support.validateOptionSelection(line)).isInstanceOf(AuthException.class);
        line.removeOption(duplicate); var secondChoice = new OptionItem(); secondChoice.setId(UUID.randomUUID()); secondChoice.setOptionGroup(group); duplicate.setOptionItem(secondChoice); line.addOption(duplicate);
        assertThatThrownBy(() -> support.validateOptionSelection(line)).isInstanceOf(AuthException.class);
        link.setMaxSelectOverride(2); assertThatCode(() -> support.validateOptionSelection(line)).doesNotThrowAnyException();
    }
    @Test void splitAllocatesOnlyTheSelectedShareOfDiscount() {
        var first = line("30",1); var second = line("70",1); var order = order(first,second);
        var discount = new OrderDiscount(); discount.setDiscountType(OrderDiscountType.FIXED_AMOUNT); discount.setDiscountValue(new BigDecimal("20")); order.addDiscount(discount);
        support.recalculateTotals(order); assertThat(support.splitDiscountAmount(order,List.of(first),discount)).isEqualByComparingTo("6");
    }
    @Test void roundingAllocatesTheEntireDiscountAcrossSmallLines() {
        var lines = new ArrayList<OrderLineItem>();
        for(int i = 0; i < 100; i++) lines.add(line("0.01",1));
        var order = order(lines.toArray(OrderLineItem[]::new));
        var discount = new OrderDiscount(); discount.setDiscountType(OrderDiscountType.FIXED_AMOUNT);
        discount.setDiscountValue(new BigDecimal("0.20")); order.addDiscount(discount);
        support.recalculateTotals(order);
        assertThat(lines.stream().map(OrderLineItem::getDiscountTotal).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("0.20");
        assertThat(lines.stream().map(OrderLineItem::getLineTotal).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(order.getTotal());
    }
    @Test void mergingKeepsTheCombinedDiscountWithoutExtendingPercentages() {
        var target = order(line("100",1)); var source = new Order(); source.addLineItem(line("100",1));
        source.setRestaurant(target.getRestaurant()); source.setCurrency("EUR"); source.setTaxRateSnapshot(BigDecimal.ZERO);
        var branch = new pos.pos.restaurant.entity.Branch(); branch.setId(UUID.randomUUID());
        target.setBranch(branch); source.setBranch(branch); target.setId(UUID.randomUUID()); source.setId(UUID.randomUUID());
        for(var current:List.of(target,source)) {
            var discount = new OrderDiscount(); discount.setDiscountType(OrderDiscountType.PERCENTAGE);
            discount.setDiscountValue(new BigDecimal("10")); current.addDiscount(discount); support.recalculateTotals(current);
            when(orders.findByIdAndRestaurant_Id(current.getId(),current.getRestaurant().getId())).thenReturn(Optional.of(current));
            when(orders.saveAndFlush(current)).thenReturn(current);
        }
        var domain = new pos.pos.order.service.OrderDomainSupport(orders,support);
        var workflow = new pos.pos.order.service.OrderWorkflowService(restaurantScopeService,support,domain,mock(pos.pos.kds.service.KdsOrderSyncService.class));
        workflow.mergeOrders(null,target.getRestaurant().getId(),target.getId(),new pos.pos.order.dto.OrderMergeRequest(source.getId(),null));
        assertThat(target.getDiscountTotal()).isEqualByComparingTo("20"); assertThat(target.getTotal()).isEqualByComparingTo("180");
        assertThat(source.getTotal()).isEqualByComparingTo("0");
    }
    @Test void markingServedOrderReadyKeepsFulfilledProgress() {
        var line = line("10", 1);
        line.setStatus(pos.pos.order.enums.OrderLineItemStatus.FULFILLED);
        var order = order(line); order.setId(UUID.randomUUID());
        when(orders.findByIdAndRestaurant_Id(order.getId(), order.getRestaurant().getId())).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);
        var domain = new pos.pos.order.service.OrderDomainSupport(orders, support);
        var workflow = new pos.pos.order.service.OrderWorkflowService(restaurantScopeService, support, domain,
                mock(pos.pos.kds.service.KdsOrderSyncService.class));
        workflow.markOrderReady(null, order.getRestaurant().getId(), order.getId(), new pos.pos.order.dto.OrderActionRequest());
        assertThat(order.getFulfillmentStatus()).isEqualTo(pos.pos.order.enums.OrderFulfillmentStatus.FULFILLED);
    }
    @Test void rapidOrdersReceiveDistinctNumbers() {
        var restaurant = new Restaurant(); restaurant.setId(UUID.randomUUID());
        when(settings.findByRestaurant_Id(restaurant.getId())).thenReturn(Optional.empty());
        var used = new HashSet<String>();
        when(orders.existsByRestaurant_IdAndOrderNumber(eq(restaurant.getId()),anyString()))
                .thenAnswer(invocation -> used.contains(invocation.getArgument(1)));
        for(int count = 0; count < 100; count++) assertThat(used.add(support.nextOrderNumber(restaurant))).isTrue();
        assertThat(used).hasSize(100);
    }
    @Test void cancellationIsConsistentBeforeSettingsQueryCanFlush() {
        var order = new Order(); var restaurant = new Restaurant(); restaurant.setId(UUID.randomUUID()); order.setRestaurant(restaurant); order.setId(UUID.randomUUID()); order.setStatus(pos.pos.order.enums.OrderStatus.OPEN); order.setTaxRateSnapshot(BigDecimal.ZERO);
        when(orders.findByIdAndRestaurant_Id(order.getId(),restaurant.getId())).thenReturn(Optional.of(order));
        order.setFulfillmentStatus(pos.pos.order.enums.OrderFulfillmentStatus.IN_PREPARATION);
        when(settings.findByRestaurant_Id(restaurant.getId())).thenAnswer(i -> {
            assertThat(order.getStatus()).isEqualTo(pos.pos.order.enums.OrderStatus.CANCELLED);
            assertThat(order.getFulfillmentStatus()).isEqualTo(pos.pos.order.enums.OrderFulfillmentStatus.IN_PREPARATION);
            return Optional.empty();
        });
        when(orders.saveAndFlush(order)).thenReturn(order);
        var domain = new pos.pos.order.service.OrderDomainSupport(orders,support);
        var workflow = new pos.pos.order.service.OrderWorkflowService(restaurantScopeService,support,domain,mock(pos.pos.kds.service.KdsOrderSyncService.class));
        workflow.cancelOrder(null,restaurant.getId(),order.getId(),new pos.pos.order.dto.OrderActionRequest());
        assertThat(order.getStatus()).isEqualTo(pos.pos.order.enums.OrderStatus.CANCELLED);
    }
}
