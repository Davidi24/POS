package pos.pos.payment.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pos.pos.payment.dto.PaymentResponse;
import pos.pos.payment.dto.PaymentTransactionResponse;
import pos.pos.payment.entity.Payment;
import pos.pos.payment.entity.PaymentTransaction;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentMapper {

    private final UserRepository userRepository;

    public PaymentResponse toResponse(Payment payment) {
        return toResponses(List.of(payment)).getFirst();
    }

    public List<PaymentResponse> toResponses(Collection<Payment> payments) {
        Set<UUID> people = new HashSet<>();
        for (Payment payment : payments) {
            people.add(payment.getCreatedBy());
            people.add(payment.getVoidedBy());
            payment.getTransactions().forEach(transaction -> people.add(transaction.getCreatedBy()));
        }
        people.remove(null);
        Map<UUID, String> names = names(people);
        return payments.stream().map(payment -> toResponse(payment, names)).toList();
    }

    public Map<UUID, String> names(Collection<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        for (User user : userRepository.findAllById(userIds)) {
            names.put(user.getId(), displayName(user));
        }
        return names;
    }

    public static String displayName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? user.getUsername() : full;
    }

    private PaymentResponse toResponse(Payment payment, Map<UUID, String> names) {
        BigDecimal net = payment.getAmount().add(payment.getTipAmount()).add(payment.getSurchargeAmount())
                .subtract(payment.getRefundedAmount()).setScale(2, RoundingMode.HALF_UP);
        boolean holdsMoney = payment.getStatus() == pos.pos.payment.enums.PaymentStatus.CAPTURED
                || payment.getStatus() == pos.pos.payment.enums.PaymentStatus.PARTIALLY_REFUNDED;
        List<PaymentTransactionResponse> transactions = payment.getTransactions().stream()
                .sorted(Comparator.comparing(PaymentTransaction::getProcessedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(transaction -> new PaymentTransactionResponse(
                        transaction.getId(),
                        transaction.getTransactionType(),
                        transaction.getStatus(),
                        transaction.getAmount(),
                        transaction.getCurrency(),
                        transaction.getReason(),
                        transaction.getCreatedBy(),
                        transaction.getCreatedBy() == null ? null : names.get(transaction.getCreatedBy()),
                        transaction.getProcessedAt()))
                .toList();
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder() == null ? null : payment.getOrder().getId(),
                payment.getOrder() == null ? null : payment.getOrder().getOrderNumber(),
                payment.getBranch() == null ? null : payment.getBranch().getId(),
                payment.getShift() == null ? null : payment.getShift().getId(),
                payment.getReferenceNumber(),
                payment.getReceiptNumber(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getTipAmount(),
                payment.getSurchargeAmount(),
                payment.getRefundedAmount(),
                holdsMoney || payment.getStatus() == pos.pos.payment.enums.PaymentStatus.REFUNDED ? net : BigDecimal.ZERO.setScale(2),
                holdsMoney ? net.max(BigDecimal.ZERO) : BigDecimal.ZERO.setScale(2),
                payment.getTenderedAmount(),
                payment.getChangeAmount(),
                payment.getCurrency(),
                payment.getCardBrand(),
                payment.getCardLast4(),
                payment.getExternalReference(),
                payment.getNotes(),
                payment.getPaidAt(),
                payment.getCreatedBy(),
                payment.getCreatedBy() == null ? null : names.get(payment.getCreatedBy()),
                payment.getVoidedAt(),
                payment.getVoidedBy(),
                payment.getVoidReason(),
                transactions
        );
    }
}
