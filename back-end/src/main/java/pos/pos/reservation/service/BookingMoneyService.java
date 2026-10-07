package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pos.pos.exception.auth.AuthException;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.preorder.entity.PreOrder;
import pos.pos.preorder.enums.PreOrderPaymentStatus;
import pos.pos.preorder.enums.PreOrderStatus;
import pos.pos.preorder.repository.PreOrderRepository;
import pos.pos.reservation.dto.MoneyLineResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationEvent;
import pos.pos.reservation.entity.ReservationPayment;
import pos.pos.reservation.enums.PaymentKind;
import pos.pos.reservation.enums.PaymentStatus;
import pos.pos.reservation.enums.ReservationEventType;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationEventRepository;
import pos.pos.reservation.repository.ReservationPaymentRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.settings.entity.SettingsReservationRule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

// Money for bookings, under the rules agreed with the owner (2026-09-27):
//  - each paid part has its own deadline: a pre-order until it goes to the kitchen, an extra until its "order before"
//    time, a deposit until its refund window;
//  - the guest cancels before the deadline → refund minus the card fee; after it, or a no-show → the restaurant keeps it;
//  - the restaurant declines a request, or a request expires → everything back;
//  - only the Owner (or whoever the Owner allows) may give part of kept money back, as goodwill, never all of it.
@Service
@RequiredArgsConstructor
public class BookingMoneyService {

    public enum Outcome { GUEST_CANCELLED, RESTAURANT_DECLINED, NO_SHOW }

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);
    private static final String PRE_ORDER_LINE_PREFIX = "pre-order-";

    private final ReservationPaymentRepository reservationPaymentRepository;
    private final PreOrderRepository preOrderRepository;
    private final MenuItemRepository menuItemRepository;
    private final ReservationEventRepository reservationEventRepository;
    private final ReservationPolicy reservationPolicy;
    private final ReservationRuleResolver reservationRuleResolver;
    private final ReservationSupport reservationSupport;

    // The payment provider's fee on an amount (Admin Hub → Settings → Reservations), never more than the amount.
    public BigDecimal cardFee(BigDecimal amount, Restaurant restaurant) {
        ReservationPolicy.Values values = reservationPolicy.values(restaurant);
        BigDecimal fee = amount.multiply(values.cardFeePercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .add(values.cardFeeFixed())
                .setScale(2, RoundingMode.HALF_UP);
        return fee.min(amount);
    }

    // Every paid (or to-pay) part of a booking, and what a cancel right now would do with it.
    public List<MoneyLineResponse> lines(Reservation reservation, OffsetDateTime now) {
        List<MoneyLineResponse> lines = new ArrayList<>();
        for (ReservationPayment payment : reservationPaymentRepository.findAllByReservation_IdOrderByCreatedAtAsc(reservation.getId())) {
            lines.add(line(reservation, payment, now));
        }
        preOrderOf(reservation).ifPresent(preOrder -> lines.add(preOrderLine(reservation, preOrder, now)));
        return lines;
    }

    // Big groups (from the Admin Hub size) pay a deposit when the restaurant turned deposits on.
    public void createDepositIfRequired(Reservation reservation) {
        SettingsReservationRule rule = reservationRuleResolver.activeRule(reservation.getBranch(), reservation.getReservationStart()).orElse(null);
        if (rule == null || !rule.isRequireDeposit() || rule.getDepositValue() == null || rule.getDepositValue().signum() <= 0) {
            return;
        }
        if (reservation.getPartySize() < reservationPolicy.values(reservation.getRestaurant()).depositFromGuests()) {
            return;
        }
        ReservationPayment deposit = new ReservationPayment();
        deposit.setReservation(reservation);
        deposit.setKind(PaymentKind.DEPOSIT);
        deposit.setDescription("Deposit for " + reservation.getPartySize() + " guests");
        deposit.setAmount(rule.getDepositValue().setScale(2, RoundingMode.HALF_UP));
        deposit.setCurrency(currency(reservation));
        deposit.setRefundDeadline(reservation.getReservationStart().minusHours(Math.max(0, rule.getCancellationWindowHours())));
        reservationPaymentRepository.save(deposit);
    }

    // A paid extra from a special menu (e.g. a cake), ordered in time and offered for the booking's occasion.
    public ReservationPayment addExtra(Reservation reservation, UUID menuItemId, int quantity, OffsetDateTime now) {
        if (quantity < 1 || quantity > 50) {
            throw bad("Choose between 1 and 50");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw bad("Extras can only be added before the guests arrive");
        }
        MenuItem item = menuItemRepository.findById(menuItemId).orElseThrow(() -> bad("This extra no longer exists"));
        if (!item.getSection().getMenu().getRestaurant().getId().equals(reservation.getRestaurant().getId())
                || !item.getSection().getMenu().isSpecial() || !item.isAvailable()) {
            throw bad("Pick an extra from a special menu");
        }
        String codes = item.getOccasionCodes();
        if (codes != null && !codes.isBlank()) {
            List<String> offeredFor = Arrays.stream(codes.split(",")).map(String::trim).toList();
            if (reservation.getOccasionCode() == null || !offeredFor.contains(reservation.getOccasionCode())) {
                throw bad(item.getName() + " is only offered for some occasions");
            }
        }
        OffsetDateTime deadline = item.getOrderBeforeHours() == null
                ? reservation.getReservationStart()
                : reservation.getReservationStart().minusHours(item.getOrderBeforeHours());
        if (now.isAfter(deadline)) {
            throw bad("Too late to order " + item.getName() + (item.getOrderBeforeHours() == null ? "" : ": order it at least " + item.getOrderBeforeHours() + " h ahead"));
        }
        ReservationPayment extra = new ReservationPayment();
        extra.setReservation(reservation);
        extra.setKind(PaymentKind.EXTRA);
        extra.setDescription(quantity == 1 ? item.getName() : quantity + " × " + item.getName());
        extra.setAmount(item.getBasePrice().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP));
        extra.setCurrency(currency(reservation));
        extra.setRefundDeadline(deadline);
        extra.setMenuItemId(item.getId());
        extra.setQuantity(quantity);
        return reservationPaymentRepository.save(extra);
    }

    public List<ReservationPayment> pending(Reservation reservation) {
        return reservationPaymentRepository.findAllByReservation_IdOrderByCreatedAtAsc(reservation.getId()).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .toList();
    }

    public void markPaid(ReservationPayment payment, String provider, String reference, OffsetDateTime now) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw bad("This is already " + payment.getStatus().name().toLowerCase(Locale.ROOT));
        }
        payment.setStatus(PaymentStatus.PAID);
        payment.setProvider(provider);
        payment.setProviderRef(reference);
        payment.setPaidAt(now);
        reservationPaymentRepository.save(payment);
    }

    // The booking ended early: unpaid parts are dropped; paid ones are refunded or kept by the rules above.
    public void settle(Reservation reservation, Outcome outcome, OffsetDateTime now) {
        for (ReservationPayment payment : reservationPaymentRepository.findAllByReservationIdForUpdate(reservation.getId())) {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.CANCELLED);
            } else if (payment.getStatus() == PaymentStatus.PAID) {
                BigDecimal back = refundFor(reservation, payment.getAmount(), payment.getRefundDeadline(), outcome, now);
                if (back.signum() > 0) {
                    payment.setStatus(PaymentStatus.REFUNDED);
                    payment.setRefundedAmount(back);
                    payment.setCardFee(payment.getAmount().subtract(back));
                    record(reservation, ReservationEventType.REFUNDED, money(back, reservation) + " back: " + payment.getDescription(), null, null);
                } else {
                    payment.setStatus(PaymentStatus.KEPT);
                }
            } else {
                continue;
            }
            reservationPaymentRepository.save(payment);
        }
    }

    // How much comes back for a paid part.
    public BigDecimal refundFor(Reservation reservation, BigDecimal paid, OffsetDateTime deadline, Outcome outcome, OffsetDateTime now) {
        return switch (outcome) {
            case RESTAURANT_DECLINED -> paid;
            case NO_SHOW -> BigDecimal.ZERO;
            case GUEST_CANCELLED -> deadline == null || !now.isAfter(deadline)
                    ? paid.subtract(cardFee(paid, reservation.getRestaurant())).max(BigDecimal.ZERO)
                    : BigDecimal.ZERO;
        };
    }

    // The Owner (or whoever the Owner allows) gives part of kept money back, with a reason. Never all of it.
    public void goodwill(Reservation reservation, String lineId, BigDecimal amount, String reason, UUID actorId) {
        if (lineId == null || lineId.isBlank()) {
            throw bad("This payment isn't on the booking");
        }
        if (reason == null || reason.isBlank()) {
            throw bad("Give a reason for the goodwill refund");
        }
        if (amount == null || amount.signum() <= 0) {
            throw bad("Enter an amount to give back");
        }
        if (lineId.startsWith(PRE_ORDER_LINE_PREFIX)) {
            UUID preOrderId = parseMoneyLineId(lineId.substring(PRE_ORDER_LINE_PREFIX.length()));
            PreOrder preOrder = preOrderRepository.lockById(preOrderId)
                    .filter(p -> Objects.equals(p.getReservation().getId(), reservation.getId()))
                    .filter(p -> p.getStatus() == PreOrderStatus.SCHEDULED || p.getStatus() == PreOrderStatus.SENT)
                    .filter(p -> p.getPaidAmount() != null && p.getPaidAmount().signum() > 0)
                    .orElseThrow(() -> bad("This payment isn't on the booking"));
            if (preOrder.getPaymentStatus() != PreOrderPaymentStatus.RETAINED) {
                throw bad("Only money the restaurant kept can be given back as goodwill");
            }
            BigDecimal left = preOrder.getPaidAmount().subtract(preOrder.getRefundedAmount());
            if (amount.compareTo(left) >= 0) {
                throw bad("A goodwill refund is part of the money, never all of it (less than " + money(left, reservation) + ")");
            }
            preOrder.setRefundedAmount(preOrder.getRefundedAmount().add(amount));
            preOrderRepository.save(preOrder);
            record(reservation, ReservationEventType.REFUNDED, "Goodwill: " + money(amount, reservation) + " back of the pre-order", reason, actorId);
            return;
        }
        UUID paymentId = parseMoneyLineId(lineId);
        ReservationPayment payment = reservationPaymentRepository.findByIdAndReservation_Id(paymentId, reservation.getId())
                .orElseThrow(() -> bad("This payment isn't on the booking"));
        if (payment.getStatus() != PaymentStatus.KEPT) {
            throw bad("Only money the restaurant kept can be given back as goodwill");
        }
        BigDecimal left = payment.getAmount().subtract(payment.getRefundedAmount());
        if (amount.compareTo(left) >= 0) {
            throw bad("A goodwill refund is part of the money, never all of it (less than " + money(left, reservation) + ")");
        }
        payment.setRefundedAmount(payment.getRefundedAmount().add(amount));
        reservationPaymentRepository.save(payment);
        record(reservation, ReservationEventType.REFUNDED, "Goodwill: " + money(amount, reservation) + " back of " + payment.getDescription(), reason, actorId);
    }

    private UUID parseMoneyLineId(String lineId) {
        try {
            return UUID.fromString(lineId);
        } catch (IllegalArgumentException invalidPaymentId) {
            throw bad("This payment isn't on the booking");
        }
    }

    public String money(BigDecimal amount, Reservation reservation) {
        String currency = currency(reservation);
        String symbol = switch (currency) {
            case "EUR" -> "€";
            case "USD" -> "$";
            case "GBP" -> "£";
            default -> currency + " ";
        };
        return symbol + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // ---- Lines ----

    private MoneyLineResponse line(Reservation reservation, ReservationPayment payment, OffsetDateTime now) {
        BigDecimal ifCancelled = payment.getStatus() == PaymentStatus.PAID
                ? refundFor(reservation, payment.getAmount(), payment.getRefundDeadline(), Outcome.GUEST_CANCELLED, now)
                : BigDecimal.ZERO;
        return MoneyLineResponse.builder()
                .id(payment.getId().toString())
                .kind(payment.getKind().name())
                .description(payment.getDescription())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus().name())
                .refundDeadline(payment.getRefundDeadline())
                .refundedAmount(payment.getRefundedAmount())
                .refundIfCancelledNow(ifCancelled)
                .explanation(explain(reservation, payment.getDescription(), payment.getAmount(), payment.getStatus().name(),
                        payment.getRefundDeadline(), payment.getRefundedAmount(), ifCancelled))
                .build();
    }

    private MoneyLineResponse preOrderLine(Reservation reservation, PreOrder preOrder, OffsetDateTime now) {
        OffsetDateTime deadline = reservation.getReservationStart().minusMinutes(preOrder.getLeadMinutes());
        String status = switch (preOrder.getPaymentStatus()) {
            case PAID -> "PAID";
            case REFUNDED -> "REFUNDED";
            case RETAINED -> "KEPT";
        };
        BigDecimal ifCancelled = preOrder.getStatus() == PreOrderStatus.SCHEDULED && preOrder.getPaymentStatus() == PreOrderPaymentStatus.PAID
                ? refundFor(reservation, preOrder.getPaidAmount(), deadline, Outcome.GUEST_CANCELLED, now)
                : BigDecimal.ZERO;
        return MoneyLineResponse.builder()
                .id("pre-order-" + preOrder.getId())
                .kind("PRE_ORDER")
                .description("Food pre-order")
                .amount(preOrder.getPaidAmount())
                .currency(preOrder.getCurrency())
                .status(status)
                .refundDeadline(deadline)
                .refundedAmount(preOrder.getRefundedAmount())
                .refundIfCancelledNow(ifCancelled)
                .explanation(explain(reservation, "food pre-order", preOrder.getPaidAmount(), status, deadline, preOrder.getRefundedAmount(), ifCancelled))
                .build();
    }

    // e.g. "€49.10 back if cancelled now: food pre-order €50.00 minus the card fee (€0.90)".
    private String explain(Reservation reservation, String what, BigDecimal amount, String status, OffsetDateTime deadline, BigDecimal refunded, BigDecimal ifCancelled) {
        String name = what.substring(0, 1).toLowerCase(Locale.ROOT) + what.substring(1);
        return switch (status) {
            case "PENDING" -> "Waiting for payment" + (deadline == null ? "" : ", order before " + when(deadline, reservation));
            case "REFUNDED" -> money(refunded, reservation) + " refunded";
            case "KEPT" -> "Kept by the restaurant" + (refunded.signum() > 0 ? " (" + money(refunded, reservation) + " given back)" : "");
            case "CANCELLED" -> "Not needed anymore";
            default -> ifCancelled.signum() > 0
                    ? money(ifCancelled, reservation) + " back if cancelled now: " + name + " " + money(amount, reservation)
                        + " minus the card fee (" + money(amount.subtract(ifCancelled), reservation) + ")"
                    : "No refund if cancelled now: " + name + " (the deadline was " + when(deadline, reservation) + ")";
        };
    }

    private String when(OffsetDateTime moment, Reservation reservation) {
        return moment == null ? "the booking time" : WHEN.format(moment.atZoneSameInstant(reservationSupport.restaurantZone(reservation.getRestaurant())));
    }

    private java.util.Optional<PreOrder> preOrderOf(Reservation reservation) {
        return preOrderRepository.findForReservation(reservation.getId(), EnumSet.of(PreOrderStatus.SCHEDULED, PreOrderStatus.SENT)).stream()
                .filter(preOrder -> preOrder.getPaidAmount() != null && preOrder.getPaidAmount().signum() > 0)
                .findFirst();
    }

    private String currency(Reservation reservation) {
        String currency = reservation.getRestaurant() == null ? null : reservation.getRestaurant().getCurrency();
        return currency == null || currency.isBlank() ? "EUR" : currency.toUpperCase(Locale.ROOT);
    }

    private void record(Reservation reservation, ReservationEventType type, String detail, String reason, UUID actorId) {
        ReservationEvent event = new ReservationEvent();
        event.setReservation(reservation);
        event.setType(type);
        event.setDetail(detail);
        event.setReason(reason);
        event.setActorId(actorId);
        reservationEventRepository.save(event);
    }

    private static AuthException bad(String message) {
        return new AuthException(message, HttpStatus.BAD_REQUEST);
    }
}
