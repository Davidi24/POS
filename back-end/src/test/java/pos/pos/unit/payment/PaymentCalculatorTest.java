package pos.pos.unit.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pos.pos.order.entity.Order;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderStatus;
import pos.pos.payment.entity.Payment;
import pos.pos.payment.enums.PaymentMethod;
import pos.pos.payment.enums.PaymentStatus;
import pos.pos.payment.service.PaymentCalculator;
import pos.pos.settings.entity.Settings;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentCalculator")
class PaymentCalculatorTest {

    private final PaymentCalculator calculator = new PaymentCalculator();

    private static Order order(String total) {
        Order order = new Order();
        order.setStatus(OrderStatus.OPEN);
        order.setTotal(new BigDecimal(total));
        order.setPrepaidTotal(BigDecimal.ZERO);
        order.setCurrency("EUR");
        return order;
    }

    private static Payment payment(Order order, PaymentMethod method, String amount, String tip, String refunded, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setMethod(method);
        payment.setAmount(new BigDecimal(amount));
        payment.setTipAmount(new BigDecimal(tip));
        payment.setSurchargeAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(new BigDecimal(refunded));
        payment.setStatus(status);
        payment.setCurrency("EUR");
        order.getPayments().add(payment);
        return payment;
    }

    private static Settings rounding(String increment) {
        Settings settings = new Settings();
        settings.setCashRoundingEnabled(true);
        settings.setCashRoundingIncrement(new BigDecimal(increment));
        return settings;
    }

    @Nested
    @DisplayName("balance")
    class Balance {

        @Test
        @DisplayName("is the total minus prepaid minus what counts towards the bill")
        void totalMinusPrepaidMinusPaid() {
            Order order = order("100.00");
            order.setPrepaidTotal(new BigDecimal("20.00"));
            payment(order, PaymentMethod.CARD, "30.00", "5.00", "0", PaymentStatus.CAPTURED);
            assertThat(calculator.balanceDue(order, new Settings())).isEqualByComparingTo("50.00");
            assertThat(calculator.paidTotal(order)).isEqualByComparingTo("30.00");
            assertThat(calculator.tipTotal(order)).isEqualByComparingTo("5.00");
        }

        @Test
        @DisplayName("ignores voided and failed payments")
        void ignoresVoidedAndFailed() {
            Order order = order("40.00");
            payment(order, PaymentMethod.CARD, "40.00", "0", "0", PaymentStatus.VOIDED);
            payment(order, PaymentMethod.CARD, "40.00", "0", "0", PaymentStatus.FAILED);
            payment(order, PaymentMethod.CARD, "40.00", "0", "0", PaymentStatus.PENDING);
            assertThat(calculator.balanceDue(order, new Settings())).isEqualByComparingTo("40.00");
            assertThat(calculator.paymentStatus(order, new Settings())).isEqualTo(OrderPaymentStatus.UNPAID);
        }

        @Test
        @DisplayName("never goes below zero when more was taken than the total (cash rounded up)")
        void neverNegative() {
            Order order = order("10.03");
            payment(order, PaymentMethod.CASH, "10.05", "0", "0", PaymentStatus.CAPTURED);
            assertThat(calculator.balanceDue(order, rounding("0.05"))).isEqualByComparingTo("0");
            assertThat(calculator.paymentStatus(order, rounding("0.05"))).isEqualTo(OrderPaymentStatus.PAID);
        }

        @Test
        @DisplayName("treats a cash rounding remainder below half the step as settled")
        void roundingRemainderSettles() {
            Order order = order("10.02");
            payment(order, PaymentMethod.CASH, "10.00", "0", "0", PaymentStatus.CAPTURED);
            assertThat(calculator.balanceDue(order, rounding("0.05"))).isEqualByComparingTo("0");
            assertThat(calculator.balanceDue(order, new Settings())).isEqualByComparingTo("0.02");
        }

        @Test
        @DisplayName("keeps a small remainder owed when only cards were used")
        void roundingNeedsCash() {
            Order order = order("10.02");
            payment(order, PaymentMethod.CARD, "10.00", "0", "0", PaymentStatus.CAPTURED);
            assertThat(calculator.balanceDue(order, rounding("0.05"))).isEqualByComparingTo("0.02");
            assertThat(calculator.paymentStatus(order, rounding("0.05"))).isEqualTo(OrderPaymentStatus.PARTIALLY_PAID);
        }

        @ParameterizedTest(name = "{0} rounded to {1} is {2}")
        @CsvSource({
                "10.02, 0.05, 10.00",
                "10.03, 0.05, 10.05",
                "10.025, 0.05, 10.05",
                "9.99, 0.10, 10.00",
                "0.01, 0.05, 0.00",
                "12.34, 1.00, 12.00",
                "12.50, 1.00, 13.00"
        })
        @DisplayName("cash balance is rounded half up to the restaurant's step")
        void cashBalance(String total, String increment, String expected) {
            Order order = order(total);
            assertThat(calculator.cashBalanceDue(order, rounding(increment))).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("cash balance equals the balance when rounding is off or the step is zero")
        void cashBalanceWithoutRounding() {
            Order order = order("10.02");
            assertThat(calculator.cashBalanceDue(order, new Settings())).isEqualByComparingTo("10.02");
            Settings zero = rounding("0.00");
            assertThat(calculator.cashBalanceDue(order, zero)).isEqualByComparingTo("10.02");
            assertThat(calculator.cashBalanceDue(order, null)).isEqualByComparingTo("10.02");
        }
    }

    @Nested
    @DisplayName("refunds")
    class Refunds {

        @Test
        @DisplayName("take back the bill part first, then the tip")
        void billPartFirst() {
            Order order = order("25.00");
            Payment payment = payment(order, PaymentMethod.CARD, "25.00", "5.00", "10.00", PaymentStatus.PARTIALLY_REFUNDED);
            assertThat(calculator.paidTotal(order)).isEqualByComparingTo("15.00");
            assertThat(calculator.tipTotal(order)).isEqualByComparingTo("5.00");
            assertThat(calculator.refundable(payment)).isEqualByComparingTo("20.00");

            payment.setRefundedAmount(new BigDecimal("27.00"));
            assertThat(calculator.paidTotal(order)).isEqualByComparingTo("0");
            assertThat(calculator.tipTotal(order)).isEqualByComparingTo("3.00");
            assertThat(calculator.refundable(payment)).isEqualByComparingTo("3.00");
        }

        @Test
        @DisplayName("make the order PARTIALLY_REFUNDED, or REFUNDED once every payment is given back")
        void statuses() {
            Order order = order("20.00");
            Payment first = payment(order, PaymentMethod.CARD, "10.00", "0", "4.00", PaymentStatus.PARTIALLY_REFUNDED);
            Payment second = payment(order, PaymentMethod.CASH, "10.00", "0", "0", PaymentStatus.CAPTURED);
            assertThat(calculator.paymentStatus(order, new Settings())).isEqualTo(OrderPaymentStatus.PARTIALLY_REFUNDED);
            first.setRefundedAmount(new BigDecimal("10.00"));
            first.setStatus(PaymentStatus.REFUNDED);
            second.setRefundedAmount(new BigDecimal("10.00"));
            second.setStatus(PaymentStatus.REFUNDED);
            assertThat(calculator.paymentStatus(order, new Settings())).isEqualTo(OrderPaymentStatus.REFUNDED);
            assertThat(calculator.refundedTotal(order)).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("never report a negative refundable amount")
        void refundableNeverNegative() {
            Payment payment = payment(order("1.00"), PaymentMethod.CARD, "1.00", "0", "5.00", PaymentStatus.REFUNDED);
            assertThat(calculator.refundable(payment)).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("order payment status")
    class Status {

        @Test
        @DisplayName("follows what the payments cover")
        void followsPayments() {
            Order order = order("30.00");
            Settings settings = new Settings();
            assertThat(calculator.paymentStatus(order, settings)).isEqualTo(OrderPaymentStatus.UNPAID);
            payment(order, PaymentMethod.CARD, "10.00", "0", "0", PaymentStatus.CAPTURED);
            assertThat(calculator.paymentStatus(order, settings)).isEqualTo(OrderPaymentStatus.PARTIALLY_PAID);
            payment(order, PaymentMethod.CASH, "20.00", "0", "0", PaymentStatus.CAPTURED);
            assertThat(calculator.paymentStatus(order, settings)).isEqualTo(OrderPaymentStatus.PAID);
        }

        @Test
        @DisplayName("is PAID when a prepayment covers the bill and VOIDED for a voided order")
        void prepaidAndVoided() {
            Order order = order("30.00");
            order.setPrepaidTotal(new BigDecimal("30.00"));
            assertThat(calculator.paymentStatus(order, new Settings())).isEqualTo(OrderPaymentStatus.PAID);
            order.setStatus(OrderStatus.VOIDED);
            assertThat(calculator.paymentStatus(order, new Settings())).isEqualTo(OrderPaymentStatus.VOIDED);
        }

        @Test
        @DisplayName("is UNPAID for an empty order with nothing paid")
        void emptyOrder() {
            assertThat(calculator.paymentStatus(order("0.00"), new Settings())).isEqualTo(OrderPaymentStatus.UNPAID);
        }

        @Test
        @DisplayName("goes back to PARTIALLY_PAID when the bill grows after it was paid")
        void billGrows() {
            Order order = order("30.00");
            payment(order, PaymentMethod.CARD, "30.00", "0", "0", PaymentStatus.CAPTURED);
            assertThat(calculator.paymentStatus(order, new Settings())).isEqualTo(OrderPaymentStatus.PAID);
            order.setTotal(new BigDecimal("42.00"));
            assertThat(calculator.paymentStatus(order, new Settings())).isEqualTo(OrderPaymentStatus.PARTIALLY_PAID);
            assertThat(calculator.balanceDue(order, new Settings())).isEqualByComparingTo("12.00");
        }
    }
}
