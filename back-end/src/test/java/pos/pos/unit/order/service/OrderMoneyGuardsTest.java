package pos.pos.unit.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import pos.pos.customer.repository.CustomerRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.inventory.repository.InventoryMovementRepository;
import pos.pos.inventory.service.InventorySaleConsumptionService;
import pos.pos.kds.service.KdsOrderSyncService;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuVariantRepository;
import pos.pos.menu.repository.OptionItemRepository;
import pos.pos.order.dto.CreateOrderDiscountRequest;
import pos.pos.order.dto.OrderActionRequest;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderDiscount;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderDiscountType;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.mapper.OrderMapper;
import pos.pos.order.realtime.OrderChangeNotifier;
import pos.pos.order.repository.OrderRepository;
import pos.pos.order.service.OrderDomainSupport;
import pos.pos.order.service.OrderSupport;
import pos.pos.order.service.OrderWorkflowService;
import pos.pos.payment.entity.Payment;
import pos.pos.payment.enums.PaymentMethod;
import pos.pos.payment.enums.PaymentStatus;
import pos.pos.payment.service.PaymentCalculator;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.entity.SettingsOrderRule;
import pos.pos.settings.repository.SettingsRepository;
import pos.pos.tables.repository.RestaurantTableRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Order money guards, automatic kitchen sending and manager-only discounts")
class OrderMoneyGuardsTest {

    @Mock RestaurantScopeService scope;
    @Mock BranchRepository branches;
    @Mock CustomerRepository customers;
    @Mock ReservationRepository reservations;
    @Mock RestaurantTableRepository tables;
    @Mock MenuItemRepository items;
    @Mock MenuVariantRepository variants;
    @Mock OptionItemRepository options;
    @Mock SettingsRepository settingsRepository;
    @Mock OrderRepository orders;
    @Mock InventoryMovementRepository inventoryMovements;
    @Mock OrderMapper mapper;
    @Mock OrderChangeNotifier notifier;

    private OrderSupport support;
    private OrderWorkflowService workflow;
    private Order order;
    private Settings settings;

    @BeforeEach
    void setUp() {
        support = new OrderSupport(scope, branches, customers, reservations, tables, items, variants, options, settingsRepository,
                orders, mapper, notifier, new PaymentCalculator(), inventoryMovements);
        workflow = new OrderWorkflowService(scope, support, new OrderDomainSupport(orders, support),
                mock(KdsOrderSyncService.class), mock(InventorySaleConsumptionService.class));
        Restaurant restaurant = new Restaurant();
        restaurant.setId(UUID.randomUUID());
        Branch branch = new Branch();
        branch.setId(UUID.randomUUID());
        order = new Order();
        order.setId(UUID.randomUUID());
        order.setRestaurant(restaurant);
        order.setBranch(branch);
        order.setCurrency("EUR");
        order.setStatus(OrderStatus.OPEN);
        order.setTaxRateSnapshot(BigDecimal.ZERO);
        OrderLineItem line = new OrderLineItem();
        line.setItemNameSnapshot("Pasta");
        line.setUnitPriceSnapshot(new BigDecimal("20.00"));
        line.setQuantity(1);
        order.addLineItem(line);
        settings = new Settings();
        settings.setRestaurant(restaurant);
        settings.setOrderRuleSettings(new SettingsOrderRule());
        when(settingsRepository.findByRestaurant_Id(restaurant.getId())).thenReturn(Optional.of(settings));
        when(orders.findByIdAndRestaurant_Id(order.getId(), restaurant.getId())).thenReturn(Optional.of(order));
        when(orders.findForUpdate(order.getId(), restaurant.getId())).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        support.recalculateTotals(order);
        SecurityContextHolder.clearContext();
    }

    private Payment pay(PaymentStatus status, String amount, String refunded) {
        Payment payment = new Payment();
        payment.setMethod(PaymentMethod.CARD);
        payment.setStatus(status);
        payment.setAmount(new BigDecimal(amount));
        payment.setTipAmount(BigDecimal.ZERO);
        payment.setSurchargeAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(new BigDecimal(refunded));
        payment.setCurrency("EUR");
        order.getPayments().add(payment);
        return payment;
    }

    private OrderActionRequest reason() {
        return new OrderActionRequest("Mistake", null);
    }

    @Test
    @DisplayName("an order still holding money can't be voided or cancelled")
    void moneyBlocksVoidAndCancel() {
        pay(PaymentStatus.CAPTURED, "5.00", "0.00");
        assertThatThrownBy(() -> workflow.voidOrder(null, order.getRestaurant().getId(), order.getId(), reason()))
                .isInstanceOf(AuthException.class).hasMessageContaining("Refund or void the payments");
        assertThatThrownBy(() -> workflow.cancelOrder(null, order.getRestaurant().getId(), order.getId(), reason()))
                .isInstanceOf(AuthException.class).hasMessageContaining("Paid orders cannot be cancelled");
    }

    @Test
    @DisplayName("money fully given back (refunded or cancelled payment) no longer blocks voiding")
    void refundedMoneyDoesNotBlock() {
        pay(PaymentStatus.REFUNDED, "5.00", "5.00");
        pay(PaymentStatus.VOIDED, "15.00", "0.00");
        assertThat(support.hasMoneyTaken(order)).isFalse();
        assertThatCode(() -> workflow.voidOrder(null, order.getRestaurant().getId(), order.getId(), reason())).doesNotThrowAnyException();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.VOIDED);
        assertThat(order.getLineItems()).allMatch(line -> line.getStatus() == OrderLineItemStatus.VOIDED);
    }

    @Test
    @DisplayName("voided, cancelled and closed orders can't be voided or cancelled again")
    void endedOrders() {
        order.setStatus(OrderStatus.VOIDED);
        assertThatThrownBy(() -> workflow.voidOrder(null, order.getRestaurant().getId(), order.getId(), reason()))
                .hasMessageContaining("already voided");
        assertThatThrownBy(() -> workflow.cancelOrder(null, order.getRestaurant().getId(), order.getId(), reason()))
                .hasMessageContaining("already voided");
        order.setStatus(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> workflow.cancelOrder(null, order.getRestaurant().getId(), order.getId(), reason()))
                .hasMessageContaining("already cancelled");
        order.setStatus(OrderStatus.CLOSED);
        assertThatThrownBy(() -> workflow.voidOrder(null, order.getRestaurant().getId(), order.getId(), reason()))
                .hasMessageContaining("reopened before they can be voided");
    }

    @Test
    @DisplayName("the payment status follows a bill that grows after it was paid")
    void paymentStatusFollowsTotals() {
        pay(PaymentStatus.CAPTURED, "20.00", "0.00");
        support.recalculateTotals(order);
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);
        OrderLineItem more = new OrderLineItem();
        more.setItemNameSnapshot("Wine");
        more.setUnitPriceSnapshot(new BigDecimal("8.00"));
        more.setQuantity(1);
        order.addLineItem(more);
        support.recalculateTotals(order);
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PARTIALLY_PAID);
    }

    @Test
    @DisplayName("with automatic sending on, pending kitchen items of an open order are sent at once")
    void autoFire() {
        assertThat(support.autoFireIfEnabled(order, UUID.randomUUID())).isFalse();
        settings.getOrderRuleSettings().setAutoFireToKitchen(true);
        order.setStatus(OrderStatus.DRAFT);
        assertThat(support.autoFireIfEnabled(order, UUID.randomUUID())).isFalse();
        order.setStatus(OrderStatus.OPEN);
        assertThat(support.autoFireIfEnabled(order, UUID.randomUUID())).isTrue();
        assertThat(order.getLineItems().getFirst().getStatus()).isEqualTo(OrderLineItemStatus.FIRED);
        assertThat(support.autoFireIfEnabled(order, UUID.randomUUID())).as("nothing new to send").isFalse();
    }

    @Test
    @DisplayName("discounts: at most 100%, a reason when asked, and only a manager unless the restaurant allows staff")
    void discounts() {
        CreateOrderDiscountRequest request = new CreateOrderDiscountRequest("Friend", OrderDiscountType.PERCENTAGE, new BigDecimal("10"), "Regular");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("waiter", null,
                List.of(new SimpleGrantedAuthority("ORDER_DISCOUNT_APPLY"))));
        assertThatThrownBy(() -> support.buildDiscount(order, request, UUID.randomUUID()))
                .isInstanceOf(AuthException.class).hasMessageContaining("A manager has to give discounts");

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("manager", null,
                List.of(new SimpleGrantedAuthority("ORDER_DISCOUNT_APPLY"), new SimpleGrantedAuthority("ORDER_VOID"))));
        OrderDiscount discount = support.buildDiscount(order, request, UUID.randomUUID());
        assertThat(discount.getDiscountValue()).isEqualByComparingTo("10");

        CreateOrderDiscountRequest tooMuch = new CreateOrderDiscountRequest("All", OrderDiscountType.PERCENTAGE, new BigDecimal("100.01"), "Oops");
        assertThatThrownBy(() -> support.buildDiscount(order, tooMuch, UUID.randomUUID())).hasMessageContaining("at most 100%");
        CreateOrderDiscountRequest noReason = new CreateOrderDiscountRequest("Friend", OrderDiscountType.FIXED_AMOUNT, BigDecimal.ONE, " ");
        assertThatThrownBy(() -> support.buildDiscount(order, noReason, UUID.randomUUID())).hasMessageContaining("reason is required");

        settings.getOrderRuleSettings().setAllowDiscountWithoutManager(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("waiter", null,
                List.of(new SimpleGrantedAuthority("ORDER_DISCOUNT_APPLY"))));
        assertThatCode(() -> support.buildDiscount(order, request, UUID.randomUUID())).doesNotThrowAnyException();
        SecurityContextHolder.clearContext();
    }
}
