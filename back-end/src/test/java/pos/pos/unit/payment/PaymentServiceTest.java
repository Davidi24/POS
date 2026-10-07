package pos.pos.unit.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import pos.pos.exception.auth.AuthException;
import pos.pos.order.entity.Order;
import pos.pos.order.enums.OrderEventType;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.service.OrderSupport;
import pos.pos.payment.dto.RefundPaymentRequest;
import pos.pos.payment.dto.TakePaymentRequest;
import pos.pos.payment.dto.TakePaymentResponse;
import pos.pos.payment.dto.VoidPaymentRequest;
import pos.pos.payment.entity.Payment;
import pos.pos.payment.enums.PaymentMethod;
import pos.pos.payment.enums.PaymentStatus;
import pos.pos.payment.enums.PaymentTransactionType;
import pos.pos.payment.mapper.PaymentMapper;
import pos.pos.payment.repository.PaymentRepository;
import pos.pos.payment.service.PaymentCalculator;
import pos.pos.payment.service.PaymentCapturePolicy;
import pos.pos.payment.service.PaymentService;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.settings.entity.Settings;
import pos.pos.shift.entity.Shift;
import pos.pos.shift.repository.ShiftRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentService")
class PaymentServiceTest {

    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Mock private RestaurantScopeService scope;
    @Mock private OrderSupport orderSupport;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private PaymentMapper paymentMapper;
    @Mock private PaymentCapturePolicy paymentCapturePolicy;

    private final Authentication authentication = mock(Authentication.class);
    private PaymentService service;
    private Order order;
    private Settings settings;

    @BeforeEach
    void setUp() {
        service = new PaymentService(scope, orderSupport, paymentRepository, shiftRepository, paymentMapper,
                new PaymentCalculator(), paymentCapturePolicy);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setTimezone("Europe/Rome");
        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        branch.setRestaurant(restaurant);
        order = new Order();
        order.setId(ORDER_ID);
        order.setRestaurant(restaurant);
        order.setBranch(branch);
        order.setCurrency("EUR");
        order.setStatus(OrderStatus.OPEN);
        order.setTotal(new BigDecimal("50.00"));
        order.setPrepaidTotal(BigDecimal.ZERO);
        order.setOrderNumber("ORD-TEST1");
        settings = new Settings();

        when(scope.currentUserId(authentication)).thenReturn(ACTOR_ID);
        when(orderSupport.requireOrder(RESTAURANT_ID, ORDER_ID)).thenReturn(order);
        when(orderSupport.loadSettings(restaurant)).thenReturn(settings);
        when(orderSupport.saveOrder(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(UUID.randomUUID());
            }
            return payment;
        });
        when(shiftRepository.findActive(ACTOR_ID)).thenReturn(Optional.empty());
    }

    private TakePaymentRequest request(PaymentMethod method, String amount) {
        return new TakePaymentRequest(method, new BigDecimal(amount), null, null, null, null, null, null, null, null);
    }

    private TakePaymentRequest request(PaymentMethod method, String amount, String tip, String tendered, String last4, Boolean close) {
        return new TakePaymentRequest(method, new BigDecimal(amount), tip == null ? null : new BigDecimal(tip),
                tendered == null ? null : new BigDecimal(tendered), null, last4, null, null, null, close);
    }

    private void assertBadRequest(Runnable action, String messagePart) {
        assertThatThrownBy(action::run)
                .isInstanceOf(AuthException.class)
                .satisfies(error -> assertThat(((AuthException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining(messagePart);
    }

    private Payment existingPayment(PaymentMethod method, String amount, String tip, OffsetDateTime paidAt) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        payment.setRestaurant(order.getRestaurant());
        payment.setBranch(order.getBranch());
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setAmount(new BigDecimal(amount));
        payment.setTipAmount(new BigDecimal(tip));
        payment.setSurchargeAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setCurrency("EUR");
        payment.setReferenceNumber("PAY-EXISTING");
        payment.setPaidAt(paidAt);
        order.getPayments().add(payment);
        return payment;
    }

    @Nested
    @DisplayName("taking a payment")
    class Take {

        @Test
        @DisplayName("records a captured card payment with a sale transaction and a partial status")
        void partialCard() {
            TakePaymentResponse response = service.takePayment(authentication, RESTAURANT_ID, ORDER_ID,
                    request(PaymentMethod.CARD, "20.00", "2.00", null, "4242", null));

            ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).saveAndFlush(saved.capture());
            Payment payment = saved.getValue();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(payment.getAmount()).isEqualByComparingTo("20.00");
            assertThat(payment.getTipAmount()).isEqualByComparingTo("2.00");
            assertThat(payment.getCurrency()).isEqualTo("EUR");
            assertThat(payment.getCardLast4()).isEqualTo("4242");
            assertThat(payment.getReferenceNumber()).startsWith("PAY-").hasSize(14);
            assertThat(payment.getReceiptNumber()).startsWith("INV-");
            assertThat(payment.getCreatedBy()).isEqualTo(ACTOR_ID);
            assertThat(payment.getTransactions()).singleElement()
                    .satisfies(transaction -> {
                        assertThat(transaction.getTransactionType()).isEqualTo(PaymentTransactionType.SALE);
                        assertThat(transaction.getAmount()).isEqualByComparingTo("22.00");
                    });
            assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PARTIALLY_PAID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
            assertThat(response.orderClosed()).isFalse();
            assertThat(response.summary().balanceDue()).isEqualByComparingTo("30.00");
            verify(orderSupport).addEvent(eq(order), eq(OrderEventType.PAYMENT_UPDATED), anyString(), eq(ACTOR_ID));
        }

        @Test
        @DisplayName("closes the order once nothing is left to pay")
        void closesWhenPaid() {
            TakePaymentResponse response = service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CARD, "50.00"));
            assertThat(response.orderClosed()).isTrue();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CLOSED);
            assertThat(order.getClosedAt()).isNotNull();
            assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);
            verify(orderSupport).addEvent(eq(order), eq(OrderEventType.CLOSED), anyString(), eq(ACTOR_ID));
        }

        @Test
        @DisplayName("keeps the order open when asked or when the setting is off")
        void staysOpen() {
            service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CARD, "10.00", null, null, null, false));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
            settings.setAutoClosePaidOrders(false);
            TakePaymentResponse response = service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CARD, "40.00"));
            assertThat(response.orderClosed()).isFalse();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
            assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);
        }

        @Test
        @DisplayName("works out the change from the cash handed over")
        void cashChange() {
            service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CASH, "50.00", "3.00", "60.00", null, null));
            ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getTenderedAmount()).isEqualByComparingTo("60.00");
            assertThat(saved.getValue().getChangeAmount()).isEqualByComparingTo("7.00");
        }

        @Test
        @DisplayName("links the payment to the running shift at the same branch only")
        void shiftLink() {
            Shift sameBranch = new Shift();
            sameBranch.setId(UUID.randomUUID());
            sameBranch.setBranch(order.getBranch());
            when(shiftRepository.findActive(ACTOR_ID)).thenReturn(Optional.of(sameBranch));
            service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CARD, "10.00"));
            ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getShift()).isSameAs(sameBranch);

            Branch other = new Branch();
            other.setId(UUID.randomUUID());
            Shift otherBranch = new Shift();
            otherBranch.setBranch(other);
            when(shiftRepository.findActive(ACTOR_ID)).thenReturn(Optional.of(otherBranch));
            service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CARD, "10.00"));
            verify(paymentRepository, org.mockito.Mockito.times(2)).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getShift()).isNull();
        }

        @Test
        @DisplayName("refuses more than what is left, nothing left, and cancelled or voided orders")
        void refusesAmounts() {
            assertBadRequest(() -> service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CARD, "50.01")),
                    "more than what is left");
            order.setStatus(OrderStatus.CANCELLED);
            assertBadRequest(() -> service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CARD, "1.00")),
                    "cancelled order");
            order.setStatus(OrderStatus.VOIDED);
            assertBadRequest(() -> service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CARD, "1.00")),
                    "voided order");
            order.setStatus(OrderStatus.OPEN);
            order.setTotal(BigDecimal.ZERO);
            assertBadRequest(() -> service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CARD, "1.00")),
                    "Nothing is left");
            verify(paymentRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("lets a closed open-tab order be paid later")
        void paysClosedTab() {
            order.setStatus(OrderStatus.CLOSED);
            TakePaymentResponse response = service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CARD, "50.00"));
            assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);
            assertThat(response.orderClosed()).isFalse();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CLOSED);
        }

        @Test
        @DisplayName("refuses tips over the limit or when tips are off")
        void tips() {
            assertBadRequest(() -> service.takePayment(authentication, RESTAURANT_ID, ORDER_ID,
                    request(PaymentMethod.CARD, "10.00", "5.01", null, null, null)), "tip");
            service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CARD, "10.00", "5.00", null, null, null));
            settings.setTipsEnabled(false);
            assertBadRequest(() -> service.takePayment(authentication, RESTAURANT_ID, ORDER_ID,
                    request(PaymentMethod.CARD, "10.00", "0.01", null, null, null)), "Tips are switched off");
            service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CARD, "10.00", "0", null, null, null));
        }

        @Test
        @DisplayName("refuses short cash, cash details on cards and card digits on cash")
        void tenderRules() {
            assertBadRequest(() -> service.takePayment(authentication, RESTAURANT_ID, ORDER_ID,
                    request(PaymentMethod.CASH, "10.00", "1.00", "10.99", null, null)), "less than");
            assertBadRequest(() -> service.takePayment(authentication, RESTAURANT_ID, ORDER_ID,
                    request(PaymentMethod.CARD, "10.00", null, "20.00", null, null)), "Only cash");
            assertBadRequest(() -> service.takePayment(authentication, RESTAURANT_ID, ORDER_ID,
                    request(PaymentMethod.CASH, "10.00", null, null, "1234", null)), "Card digits");
        }

        @Test
        @DisplayName("with split bills off takes only the whole bill (or the rounded cash bill)")
        void splitOff() {
            settings.setAllowSplitBills(false);
            assertBadRequest(() -> service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CARD, "49.99")),
                    "whole bill");
            order.setTotal(new BigDecimal("50.02"));
            settings.setCashRoundingEnabled(true);
            settings.setCashRoundingIncrement(new BigDecimal("0.05"));
            TakePaymentResponse response = service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CASH, "50.00"));
            assertThat(response.orderClosed()).isTrue();
            assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);
        }

        @Test
        @DisplayName("lets cash round up to the step")
        void cashRoundsUp() {
            order.setTotal(new BigDecimal("50.03"));
            settings.setCashRoundingEnabled(true);
            settings.setCashRoundingIncrement(new BigDecimal("0.05"));
            service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CASH, "50.05"));
            assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);
            assertBadRequest(() -> service.takePayment(authentication, RESTAURANT_ID, ORDER_ID, request(PaymentMethod.CASH, "0.05")),
                    "Nothing is left");
        }
    }

    @Nested
    @DisplayName("refunding")
    class Refund {

        @Test
        @DisplayName("partly then fully, with a transaction per refund and the order status following")
        void partialThenFull() {
            Payment payment = existingPayment(PaymentMethod.CARD, "50.00", "5.00", OffsetDateTime.now(ZoneOffset.UTC));
            order.setPaymentStatus(OrderPaymentStatus.PAID);
            service.refundPayment(authentication, RESTAURANT_ID, ORDER_ID, payment.getId(), new RefundPaymentRequest(new BigDecimal("20.00"), "Cold"));
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
            assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PARTIALLY_REFUNDED);
            service.refundPayment(authentication, RESTAURANT_ID, ORDER_ID, payment.getId(), new RefundPaymentRequest(new BigDecimal("35.00"), "All"));
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(payment.getRefundedAmount()).isEqualByComparingTo("55.00");
            assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.REFUNDED);
            assertThat(payment.getTransactions()).extracting(transaction -> transaction.getTransactionType())
                    .containsExactly(PaymentTransactionType.REFUND, PaymentTransactionType.REFUND);
            assertThat(payment.getTransactions().getFirst().getReason()).isEqualTo("Cold");
            assertThat(payment.getTransactions().getFirst().getCreatedBy()).isEqualTo(ACTOR_ID);
        }

        @Test
        @DisplayName("refuses more than is left, refunded or voided payments, and refunds after the window")
        void refusals() {
            Payment payment = existingPayment(PaymentMethod.CARD, "10.00", "0", OffsetDateTime.now(ZoneOffset.UTC));
            assertBadRequest(() -> service.refundPayment(authentication, RESTAURANT_ID, ORDER_ID, payment.getId(),
                    new RefundPaymentRequest(new BigDecimal("10.01"), "Too much")), "can still be refunded");
            payment.setStatus(PaymentStatus.VOIDED);
            assertBadRequest(() -> service.refundPayment(authentication, RESTAURANT_ID, ORDER_ID, payment.getId(),
                    new RefundPaymentRequest(BigDecimal.ONE, "Voided")), "Only taken payments");
            payment.setStatus(PaymentStatus.CAPTURED);
            payment.setPaidAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(31));
            assertBadRequest(() -> service.refundPayment(authentication, RESTAURANT_ID, ORDER_ID, payment.getId(),
                    new RefundPaymentRequest(BigDecimal.ONE, "Late")), "within 30 days");
            settings.setRefundWindowDays(0);
            service.refundPayment(authentication, RESTAURANT_ID, ORDER_ID, payment.getId(), new RefundPaymentRequest(BigDecimal.ONE, "No limit"));
            assertThat(payment.getRefundedAmount()).isEqualByComparingTo("1.00");
        }

        @Test
        @DisplayName("answers 404 for a payment of another order")
        void unknownPayment() {
            assertThatThrownBy(() -> service.refundPayment(authentication, RESTAURANT_ID, ORDER_ID, UUID.randomUUID(),
                    new RefundPaymentRequest(BigDecimal.ONE, "Who")))
                    .isInstanceOf(AuthException.class)
                    .satisfies(error -> assertThat(((AuthException) error).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("cancelling a payment")
    class Void {

        @Test
        @DisplayName("on the same day voids it and reopens a closed order that is owed again")
        void sameDay() {
            Payment payment = existingPayment(PaymentMethod.CASH, "50.00", "0", OffsetDateTime.now(ZoneOffset.UTC));
            order.setStatus(OrderStatus.CLOSED);
            order.setClosedAt(OffsetDateTime.now(ZoneOffset.UTC));
            service.voidPayment(authentication, RESTAURANT_ID, ORDER_ID, payment.getId(), new VoidPaymentRequest("Wrong method"));
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VOIDED);
            assertThat(payment.getVoidedAt()).isNotNull();
            assertThat(payment.getVoidedBy()).isEqualTo(ACTOR_ID);
            assertThat(payment.getVoidReason()).isEqualTo("Wrong method");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
            assertThat(order.getClosedAt()).isNull();
            assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.UNPAID);
            verify(orderSupport).addEvent(eq(order), eq(OrderEventType.REOPENED), anyString(), eq(ACTOR_ID));
        }

        @Test
        @DisplayName("refuses payments from an earlier day or with refunds")
        void refusals() {
            Payment old = existingPayment(PaymentMethod.CARD, "10.00", "0", OffsetDateTime.now(ZoneOffset.UTC).minusDays(2));
            assertBadRequest(() -> service.voidPayment(authentication, RESTAURANT_ID, ORDER_ID, old.getId(), new VoidPaymentRequest("Old")),
                    "on the day");
            Payment refunded = existingPayment(PaymentMethod.CARD, "10.00", "0", OffsetDateTime.now(ZoneOffset.UTC));
            refunded.setRefundedAmount(BigDecimal.ONE);
            refunded.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
            assertBadRequest(() -> service.voidPayment(authentication, RESTAURANT_ID, ORDER_ID, refunded.getId(), new VoidPaymentRequest("Refunded")),
                    "refund it instead");
        }
    }
}
