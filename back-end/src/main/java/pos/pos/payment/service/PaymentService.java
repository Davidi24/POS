package pos.pos.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.order.entity.Order;
import pos.pos.order.enums.OrderEventType;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.service.OrderSupport;
import pos.pos.payment.dto.OrderPaymentSummaryResponse;
import pos.pos.payment.dto.PaymentResponse;
import pos.pos.payment.dto.RefundPaymentRequest;
import pos.pos.payment.dto.TakePaymentRequest;
import pos.pos.payment.dto.TakePaymentResponse;
import pos.pos.payment.dto.VoidPaymentRequest;
import pos.pos.payment.entity.Payment;
import pos.pos.payment.entity.PaymentTransaction;
import pos.pos.payment.enums.PaymentMethod;
import pos.pos.payment.enums.PaymentStatus;
import pos.pos.payment.enums.PaymentTransactionStatus;
import pos.pos.payment.enums.PaymentTransactionType;
import pos.pos.payment.mapper.PaymentMapper;
import pos.pos.payment.repository.PaymentRepository;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.settings.entity.Settings;
import pos.pos.shift.repository.ShiftRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Taking, refunding and voiding payments on an order. Every write runs under the order row lock (and the restaurant
 * lock of the order write filter), so two tills can't both take the last part of a bill.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    static final Set<PaymentStatus> MONEY_STATUSES = EnumSet.of(
            PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);
    private static final Set<PaymentMethod> CARD_METHODS = EnumSet.of(
            PaymentMethod.CARD, PaymentMethod.CONTACTLESS, PaymentMethod.DIGITAL_WALLET);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final RestaurantScopeService restaurantScopeService;
    private final OrderSupport orderSupport;
    private final PaymentRepository paymentRepository;
    private final ShiftRepository shiftRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentCalculator calculator;
    private final PaymentCapturePolicy paymentCapturePolicy;

    @Transactional(readOnly = true)
    public OrderPaymentSummaryResponse getOrderPayments(Authentication authentication, UUID restaurantId, UUID orderId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        return summary(order);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Authentication authentication, UUID restaurantId, UUID paymentId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        Payment payment = paymentRepository.findDetailed(paymentId, restaurantId)
                .orElseThrow(() -> new AuthException("Payment not found", HttpStatus.NOT_FOUND));
        return paymentMapper.toResponse(payment);
    }

    @Transactional
    public TakePaymentResponse takePayment(Authentication authentication, UUID restaurantId, UUID orderId, TakePaymentRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        Settings settings = orderSupport.loadSettings(order.getRestaurant());

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.VOIDED) {
            throw bad("A " + order.getStatus().name().toLowerCase(Locale.ROOT) + " order can't be paid");
        }

        BigDecimal balanceDue = calculator.balanceDue(order, settings);
        if (balanceDue.signum() <= 0) {
            throw bad("Nothing is left to pay on this order");
        }

        PaymentMethod method = request.method();
        paymentCapturePolicy.requireCaptureAvailable(method);
        BigDecimal amount = money(request.amount());
        BigDecimal tip = money(request.tipAmount() == null ? BigDecimal.ZERO : request.tipAmount());

        // Cash may be rounded to the restaurant's smallest coin: up (the guest pays a little more) or down (the
        // restaurant lets the difference go).
        BigDecimal cashBalanceDue = calculator.cashBalanceDue(order, settings);
        BigDecimal allowed = method == PaymentMethod.CASH ? cashBalanceDue.max(balanceDue) : balanceDue;
        if (amount.compareTo(allowed) > 0) {
            throw bad("The amount is more than what is left to pay (" + allowed.toPlainString() + " " + order.getCurrency() + ")");
        }
        boolean settlesBill = amount.compareTo(balanceDue) >= 0
                || (method == PaymentMethod.CASH && amount.compareTo(cashBalanceDue) == 0);
        if (!settings.isAllowSplitBills() && !settlesBill) {
            throw bad("This restaurant takes the whole bill in one payment (" + balanceDue.toPlainString() + " " + order.getCurrency() + ")");
        }

        if (tip.signum() > 0) {
            if (!settings.isTipsEnabled()) {
                throw bad("Tips are switched off for this restaurant");
            }
            BigDecimal maxTip = amount.multiply(BigDecimal.valueOf(settings.getMaxTipPercent()))
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
            if (tip.compareTo(maxTip) > 0) {
                throw bad("The tip is more than " + settings.getMaxTipPercent() + "% of the payment");
            }
        }

        BigDecimal tendered = null;
        BigDecimal change = null;
        if (request.tenderedAmount() != null) {
            if (method != PaymentMethod.CASH) {
                throw bad("Only cash payments have an amount handed over");
            }
            tendered = money(request.tenderedAmount());
            if (tendered.compareTo(amount.add(tip)) < 0) {
                throw bad("The cash handed over is less than the amount and tip");
            }
            change = tendered.subtract(amount).subtract(tip);
        }
        if (request.cardLast4() != null && !CARD_METHODS.contains(method)) {
            throw bad("Card digits only go with card payments");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Payment payment = new Payment();
        payment.setRestaurant(order.getRestaurant());
        payment.setBranch(order.getBranch());
        payment.setOrder(order);
        payment.setCustomer(order.getCustomer());
        // Linked to the till person's running shift at this branch, so shift and tip reports add up.
        shiftRepository.findActive(actorId)
                .filter(shift -> shift.getBranch() != null && Objects.equals(shift.getBranch().getId(), order.getBranch().getId()))
                .ifPresent(payment::setShift);
        payment.setReferenceNumber(nextReference(order));
        payment.setReceiptNumber(PaymentCodes.receiptNumber(settings.getInvoiceSequencePrefix()));
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setAmount(amount);
        payment.setTipAmount(tip);
        payment.setSurchargeAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setTenderedAmount(tendered);
        payment.setChangeAmount(change);
        payment.setCurrency(order.getCurrency());
        payment.setCardBrand(request.cardBrand());
        payment.setCardLast4(request.cardLast4());
        payment.setExternalReference(request.externalReference());
        payment.setGatewayName(request.gatewayName());
        payment.setNotes(request.notes());
        payment.setPaidAt(now);
        payment.setCreatedBy(actorId);
        payment.setUpdatedBy(actorId);
        payment.addTransaction(transaction(PaymentTransactionType.SALE, amount.add(tip), order.getCurrency(), null, actorId, now));

        Payment saved = paymentRepository.saveAndFlush(payment);
        order.getPayments().add(saved);

        orderSupport.addEvent(order, OrderEventType.PAYMENT_UPDATED, describe("Paid", saved), actorId);
        order.setPaymentStatus(calculator.paymentStatus(order, settings));
        order.setUpdatedBy(actorId);

        boolean closeWhenPaid = request.closeOrderWhenPaid() == null ? settings.isAutoClosePaidOrders() : request.closeOrderWhenPaid();
        boolean closed = false;
        if (closeWhenPaid && calculator.balanceDue(order, settings).signum() <= 0
                && (order.getStatus() == OrderStatus.OPEN || order.getStatus() == OrderStatus.DRAFT)) {
            order.setStatus(OrderStatus.CLOSED);
            order.setClosedAt(now);
            orderSupport.addEvent(order, OrderEventType.CLOSED, "Order closed after payment", actorId);
            closed = true;
        }
        orderSupport.saveOrder(order);
        return new TakePaymentResponse(paymentMapper.toResponse(saved), summary(order), closed);
    }

    @Transactional
    public OrderPaymentSummaryResponse refundPayment(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID paymentId,
            RefundPaymentRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        Payment payment = requirePaymentOfOrder(order, paymentId);
        Settings settings = orderSupport.loadSettings(order.getRestaurant());

        if (payment.getStatus() != PaymentStatus.CAPTURED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw bad("Only taken payments can be refunded");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int windowDays = settings.getRefundWindowDays();
        if (windowDays > 0 && payment.getPaidAt().plusDays(windowDays).isBefore(now)) {
            throw bad("Refunds are only possible within " + windowDays + " days of the payment");
        }

        BigDecimal amount = money(request.amount());
        BigDecimal refundable = calculator.refundable(payment);
        if (amount.compareTo(refundable) > 0) {
            throw bad("At most " + refundable.toPlainString() + " " + payment.getCurrency() + " can still be refunded");
        }

        payment.setRefundedAmount(payment.getRefundedAmount().add(amount));
        payment.setStatus(calculator.refundable(payment).signum() == 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        payment.setUpdatedBy(actorId);
        payment.addTransaction(transaction(PaymentTransactionType.REFUND, amount, payment.getCurrency(), request.reason(), actorId, now));
        paymentRepository.saveAndFlush(payment);

        orderSupport.addEvent(order, OrderEventType.PAYMENT_UPDATED,
                "Refunded " + amount.toPlainString() + " " + payment.getCurrency() + " of " + payment.getReferenceNumber()
                        + ": " + request.reason().trim(),
                actorId);
        order.setPaymentStatus(calculator.paymentStatus(order, settings));
        order.setUpdatedBy(actorId);
        orderSupport.saveOrder(order);
        return summary(order);
    }

    @Transactional
    public OrderPaymentSummaryResponse voidPayment(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId,
            UUID paymentId,
            VoidPaymentRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        Payment payment = requirePaymentOfOrder(order, paymentId);
        Settings settings = orderSupport.loadSettings(order.getRestaurant());

        if (payment.getStatus() != PaymentStatus.CAPTURED || payment.getRefundedAmount().signum() > 0) {
            throw bad("Only a payment with nothing refunded yet can be cancelled; refund it instead");
        }
        // A payment taken by mistake is cancelled the same day; later it's a refund, so the books for a closed day
        // never change.
        ZoneId zone = zoneOf(order);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!payment.getPaidAt().atZoneSameInstant(zone).toLocalDate().equals(now.atZoneSameInstant(zone).toLocalDate())) {
            throw bad("A payment can only be cancelled on the day it was taken; refund it instead");
        }

        payment.setStatus(PaymentStatus.VOIDED);
        payment.setVoidedAt(now);
        payment.setVoidedBy(actorId);
        payment.setVoidReason(request.reason());
        payment.setUpdatedBy(actorId);
        payment.addTransaction(transaction(PaymentTransactionType.VOID,
                payment.getAmount().add(payment.getTipAmount()).add(payment.getSurchargeAmount()),
                payment.getCurrency(), request.reason(), actorId, now));
        paymentRepository.saveAndFlush(payment);

        orderSupport.addEvent(order, OrderEventType.PAYMENT_UPDATED,
                "Cancelled payment " + payment.getReferenceNumber() + ": " + request.reason().trim(), actorId);
        order.setPaymentStatus(calculator.paymentStatus(order, settings));
        order.setUpdatedBy(actorId);
        // The bill is open again, so a closed order goes back to open to be paid properly.
        if (order.getStatus() == OrderStatus.CLOSED && calculator.balanceDue(order, settings).signum() > 0) {
            order.setStatus(OrderStatus.OPEN);
            order.setClosedAt(null);
            orderSupport.addEvent(order, OrderEventType.REOPENED, "Reopened: a payment was cancelled", actorId);
        }
        orderSupport.saveOrder(order);
        return summary(order);
    }

    OrderPaymentSummaryResponse summary(Order order) {
        Settings settings = orderSupport.loadSettings(order.getRestaurant());
        var payments = order.getPayments().stream()
                .sorted(java.util.Comparator.comparing(Payment::getPaidAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(paymentMapper::toResponse)
                .toList();
        return new OrderPaymentSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCurrency(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getTotal(),
                order.getPrepaidTotal(),
                calculator.paidTotal(order),
                calculator.tipTotal(order),
                calculator.refundedTotal(order),
                calculator.balanceDue(order, settings),
                calculator.cashBalanceDue(order, settings),
                settings.isTipsEnabled(),
                pos.pos.settings.mapper.SettingsCodes.parsePercents(settings.getTipSuggestions()),
                settings.getMaxTipPercent(),
                settings.isAllowSplitBills(),
                payments
        );
    }

    private Payment requirePaymentOfOrder(Order order, UUID paymentId) {
        return order.getPayments().stream()
                .filter(payment -> Objects.equals(payment.getId(), paymentId))
                .findFirst()
                .orElseThrow(() -> new AuthException("Payment not found", HttpStatus.NOT_FOUND));
    }

    private String nextReference(Order order) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String reference = PaymentCodes.paymentReference();
            if (order.getPayments().stream().noneMatch(payment -> reference.equals(payment.getReferenceNumber()))
                    && (order.getId() == null || !paymentRepository.existsByOrder_IdAndReferenceNumber(order.getId(), reference))) {
                return reference;
            }
        }
        throw new AuthException("Could not create a payment reference, try again", HttpStatus.CONFLICT);
    }

    private static PaymentTransaction transaction(
            PaymentTransactionType type,
            BigDecimal amount,
            String currency,
            String reason,
            UUID actorId,
            OffsetDateTime at
    ) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setTransactionType(type);
        transaction.setStatus(PaymentTransactionStatus.APPROVED);
        transaction.setAmount(amount);
        transaction.setCurrency(currency);
        transaction.setReason(reason);
        transaction.setCreatedBy(actorId);
        transaction.setProcessedAt(at);
        return transaction;
    }

    private static String describe(String verb, Payment payment) {
        StringBuilder text = new StringBuilder(verb).append(' ')
                .append(payment.getAmount().toPlainString()).append(' ').append(payment.getCurrency())
                .append(" by ").append(payment.getMethod().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        if (payment.getTipAmount().signum() > 0) {
            text.append(" (tip ").append(payment.getTipAmount().toPlainString()).append(')');
        }
        return text.append(" · ").append(payment.getReferenceNumber()).toString();
    }

    private static ZoneId zoneOf(Order order) {
        try {
            String timezone = order.getRestaurant().getTimezone();
            return timezone == null ? ZoneOffset.UTC : ZoneId.of(timezone);
        } catch (Exception ignored) {
            return ZoneOffset.UTC;
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static AuthException bad(String message) {
        return new AuthException(message, HttpStatus.BAD_REQUEST);
    }
}
