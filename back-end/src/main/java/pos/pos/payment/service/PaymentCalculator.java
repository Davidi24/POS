package pos.pos.payment.service;

import org.springframework.stereotype.Component;
import pos.pos.order.entity.Order;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderStatus;
import pos.pos.payment.entity.Payment;
import pos.pos.payment.enums.PaymentMethod;
import pos.pos.payment.enums.PaymentStatus;
import pos.pos.settings.entity.Settings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Bill arithmetic for an order and its payments. A refund gives back the bill part of a payment first and the tip
 * after, so {@link #paidTotal} is what still counts towards the bill.
 */
@Component
public class PaymentCalculator {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    public List<Payment> moneyPayments(Order order) {
        return order.getPayments().stream()
                .filter(payment -> PaymentService.MONEY_STATUSES.contains(payment.getStatus()))
                .toList();
    }

    public BigDecimal paidTotal(Order order) {
        return moneyPayments(order).stream()
                .map(this::billPartKept)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal tipTotal(Order order) {
        return moneyPayments(order).stream()
                .map(this::tipKept)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal refundedTotal(Order order) {
        return moneyPayments(order).stream()
                .map(Payment::getRefundedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** What is left to pay. A remainder below half the cash rounding step after a cash payment counts as settled. */
    public BigDecimal balanceDue(Order order, Settings settings) {
        BigDecimal raw = rawBalance(order);
        if (raw.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal increment = roundingIncrement(settings);
        boolean paidSomeCash = moneyPayments(order).stream().anyMatch(payment -> payment.getMethod() == PaymentMethod.CASH);
        if (increment != null && paidSomeCash && raw.multiply(TWO).compareTo(increment) < 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return raw;
    }

    /** The balance rounded to the restaurant's cash step (half up), or the plain balance when rounding is off. */
    public BigDecimal cashBalanceDue(Order order, Settings settings) {
        BigDecimal balance = balanceDue(order, settings);
        BigDecimal increment = roundingIncrement(settings);
        if (increment == null || balance.signum() <= 0) {
            return balance;
        }
        return balance.divide(increment, 0, RoundingMode.HALF_UP).multiply(increment).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal refundable(Payment payment) {
        return payment.getAmount()
                .add(payment.getTipAmount())
                .add(payment.getSurchargeAmount())
                .subtract(payment.getRefundedAmount())
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public OrderPaymentStatus paymentStatus(Order order, Settings settings) {
        if (order.getStatus() == OrderStatus.VOIDED) {
            return OrderPaymentStatus.VOIDED;
        }
        List<Payment> money = moneyPayments(order);
        BigDecimal total = order.getTotal() == null ? BigDecimal.ZERO : order.getTotal();
        BigDecimal prepaid = order.getPrepaidTotal() == null ? BigDecimal.ZERO : order.getPrepaidTotal();
        if (money.isEmpty()) {
            return total.signum() > 0 && prepaid.compareTo(total) >= 0 ? OrderPaymentStatus.PAID : OrderPaymentStatus.UNPAID;
        }
        boolean anyRefund = money.stream().anyMatch(payment -> payment.getRefundedAmount().signum() > 0);
        if (anyRefund) {
            boolean allReturned = money.stream().allMatch(payment -> payment.getStatus() == PaymentStatus.REFUNDED);
            return allReturned ? OrderPaymentStatus.REFUNDED : OrderPaymentStatus.PARTIALLY_REFUNDED;
        }
        if (balanceDue(order, settings).signum() <= 0) {
            return OrderPaymentStatus.PAID;
        }
        return paidTotal(order).signum() > 0 ? OrderPaymentStatus.PARTIALLY_PAID : OrderPaymentStatus.UNPAID;
    }

    private BigDecimal rawBalance(Order order) {
        BigDecimal total = order.getTotal() == null ? BigDecimal.ZERO : order.getTotal();
        BigDecimal prepaid = order.getPrepaidTotal() == null ? BigDecimal.ZERO : order.getPrepaidTotal();
        return total.subtract(prepaid).subtract(paidTotal(order)).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal billPartKept(Payment payment) {
        BigDecimal refundedFromBill = payment.getRefundedAmount().min(payment.getAmount());
        return payment.getAmount().subtract(refundedFromBill);
    }

    private BigDecimal tipKept(Payment payment) {
        BigDecimal refundedFromTip = payment.getRefundedAmount().subtract(payment.getAmount()).max(BigDecimal.ZERO);
        return payment.getTipAmount().subtract(refundedFromTip).max(BigDecimal.ZERO);
    }

    private static BigDecimal roundingIncrement(Settings settings) {
        if (settings == null || !settings.isCashRoundingEnabled() || settings.getCashRoundingIncrement() == null
                || settings.getCashRoundingIncrement().signum() <= 0) {
            return null;
        }
        return settings.getCashRoundingIncrement();
    }
}
