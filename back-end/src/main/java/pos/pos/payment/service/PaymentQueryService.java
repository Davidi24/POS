package pos.pos.payment.service;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderItemOption;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.service.OrderSupport;
import pos.pos.payment.dto.PaymentPageResponse;
import pos.pos.payment.dto.ReceiptResponse;
import pos.pos.payment.entity.Payment;
import pos.pos.payment.enums.PaymentMethod;
import pos.pos.payment.enums.PaymentStatus;
import pos.pos.payment.mapper.PaymentMapper;
import pos.pos.payment.repository.PaymentRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.BranchAddressRepository;
import pos.pos.restaurant.repository.RestaurantAddressRepository;
import pos.pos.restaurant.repository.RestaurantTaxProfileRepository;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.entity.SettingsReceipt;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_RANGE_DAYS = 366;

    private final RestaurantScopeService restaurantScopeService;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentCalculator calculator;
    private final OrderSupport orderSupport;
    private final RestaurantAddressRepository restaurantAddressRepository;
    private final BranchAddressRepository branchAddressRepository;
    private final RestaurantTaxProfileRepository restaurantTaxProfileRepository;

    /**
     * Payments taken at a branch, newest first. Without ORDER_AUDIT a person only sees the payments they took.
     */
    @Transactional(readOnly = true)
    public PaymentPageResponse branchPayments(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to,
            PaymentMethod method,
            PaymentStatus status,
            UUID staffId,
            String search,
            int page,
            int size
    ) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw bad("page must not be negative and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw bad("from must be before to");
        }
        if (from != null && to != null && from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw bad("The period can be at most " + MAX_RANGE_DAYS + " days");
        }
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        if (term.length() > 100) {
            throw bad("search must be at most 100 characters");
        }
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        UUID onlyStaff = staffId;
        if (!hasAuthority(authentication, "ORDER_AUDIT")) {
            if (staffId != null && !staffId.equals(actorId)) {
                throw new AuthException("You can only see the payments you took", HttpStatus.FORBIDDEN);
            }
            onlyStaff = actorId;
        }
        UUID staffFilter = onlyStaff;
        Specification<Payment> filter = (root, query, cb) -> {
            List<Predicate> clauses = new ArrayList<>();
            clauses.add(cb.equal(root.get("restaurant").get("id"), restaurantId));
            clauses.add(cb.equal(root.get("branch").get("id"), branchId));
            if (from != null) clauses.add(cb.greaterThanOrEqualTo(root.get("paidAt"), from));
            if (to != null) clauses.add(cb.lessThan(root.get("paidAt"), to));
            if (method != null) clauses.add(cb.equal(root.get("method"), method));
            if (status != null) clauses.add(cb.equal(root.get("status"), status));
            if (staffFilter != null) clauses.add(cb.equal(root.get("createdBy"), staffFilter));
            if (!term.isEmpty()) {
                String like = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                var order = root.join("order", JoinType.INNER);
                clauses.add(cb.or(
                        cb.like(cb.lower(root.get("referenceNumber")), like, '!'),
                        cb.like(cb.lower(root.get("receiptNumber")), like, '!'),
                        cb.like(cb.lower(order.get("orderNumber")), like, '!'),
                        cb.like(cb.lower(root.get("cardLast4")), like, '!')
                ));
            }
            return cb.and(clauses.toArray(Predicate[]::new));
        };
        var result = paymentRepository.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "paidAt", "id")));
        return new PaymentPageResponse(
                paymentMapper.toResponses(result.getContent()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public ReceiptResponse receipt(Authentication authentication, UUID restaurantId, UUID orderId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        Order order = orderSupport.requireOrder(restaurantId, orderId);
        Restaurant restaurant = order.getRestaurant();
        Settings settings = orderSupport.loadSettings(restaurant);
        SettingsReceipt receipt = settings.getReceiptSettings() == null ? new SettingsReceipt() : settings.getReceiptSettings();

        List<String> addressLines = new ArrayList<>();
        branchAddressRepository.findAllByBranchIdAndDeletedAtIsNullOrderByIsPrimaryDescCreatedAtAsc(order.getBranch().getId())
                .stream()
                .findFirst()
                .ifPresentOrElse(
                        address -> addAddress(addressLines, address.getStreetLine1(), address.getStreetLine2(), address.getPostalCode(), address.getCity(), address.getCountry()),
                        () -> restaurantAddressRepository.findByRestaurantIdAndIsPrimaryTrueAndDeletedAtIsNull(restaurant.getId())
                                .ifPresent(address -> addAddress(addressLines, address.getStreetLine1(), address.getStreetLine2(),
                                        address.getPostalCode(), address.getCity(), address.getCountry())));
        var taxProfile = restaurantTaxProfileRepository.findByRestaurantIdAndIsDefaultTrueAndDeletedAtIsNull(restaurant.getId());

        String serverName = null;
        if (receipt.isShowServerName() && order.getCreatedBy() != null) {
            serverName = paymentMapper.names(List.of(order.getCreatedBy())).get(order.getCreatedBy());
        }

        List<ReceiptResponse.Line> lines = order.getLineItems().stream()
                .filter(line -> receipt.isPrintVoidedItems() || isCounted(line))
                .sorted(Comparator.comparing(OrderLineItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(line -> new ReceiptResponse.Line(
                        line.getItemNameSnapshot(),
                        line.getVariantNameSnapshot(),
                        line.getQuantity(),
                        line.getUnitPriceSnapshot().add(line.getVariantPriceDeltaSnapshot()),
                        line.getOptions().stream().map(this::optionText).toList(),
                        isCounted(line) ? line.getLineTotal() : BigDecimal.ZERO.setScale(2),
                        !isCounted(line),
                        line.getNotes()))
                .toList();

        List<Payment> payments = order.getPayments().stream()
                .filter(payment -> payment.getStatus() != PaymentStatus.VOIDED && payment.getStatus() != PaymentStatus.FAILED)
                .sorted(Comparator.comparing(Payment::getPaidAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return new ReceiptResponse(
                restaurant.getName(),
                restaurant.getLegalName(),
                order.getBranch().getName(),
                addressLines,
                order.getBranch().getPhone(),
                taxProfile.map(profile -> profile.getTaxNumber()).orElse(null),
                taxProfile.map(profile -> profile.getVatNumber()).orElse(null),
                receipt.isShowLogo(),
                receipt.isShowOrderNumber() ? order.getOrderNumber() : null,
                receipt.isShowTableName() && order.getRestaurantTable() != null ? order.getRestaurantTable().getTableNumber() : null,
                serverName,
                order.getGuestCount(),
                order.getOpenedAt(),
                order.getClosedAt(),
                OffsetDateTime.now(ZoneOffset.UTC),
                order.getCurrency(),
                lines,
                order.getSubtotal(),
                order.getDiscounts().stream()
                        .filter(discount -> discount.getAmountApplied().signum() > 0)
                        .map(discount -> new ReceiptResponse.Discount(discount.getName(), discount.getAmountApplied()))
                        .toList(),
                order.getDiscountTotal(),
                order.getServiceChargeTotal(),
                receipt.isShowTaxBreakdown()
                        ? new ReceiptResponse.Tax(order.getTaxRateSnapshot(), order.isTaxInclusiveSnapshot(), order.getTaxTotal())
                        : null,
                order.getTotal(),
                order.getPrepaidTotal(),
                payments.stream().map(payment -> new ReceiptResponse.Payment(
                        payment.getMethod().name(),
                        payment.getReceiptNumber(),
                        payment.getAmount(),
                        payment.getTipAmount(),
                        payment.getRefundedAmount(),
                        payment.getTenderedAmount(),
                        payment.getChangeAmount(),
                        payment.getCardBrand(),
                        payment.getCardLast4(),
                        payment.getPaidAt(),
                        payment.getStatus().name())).toList(),
                calculator.paidTotal(order),
                calculator.tipTotal(order),
                calculator.balanceDue(order, settings),
                receipt.getFooterNote(),
                receipt.isShowQrCode(),
                receipt.getReceiptCopies()
        );
    }

    private boolean isCounted(OrderLineItem line) {
        return line.getStatus() != OrderLineItemStatus.VOIDED && line.getStatus() != OrderLineItemStatus.CANCELLED;
    }

    private String optionText(OrderItemOption option) {
        String name = option.getOptionNameSnapshot() == null ? "" : option.getOptionNameSnapshot();
        return option.getQuantity() > 1 ? option.getQuantity() + " × " + name : name;
    }

    private static void addAddress(List<String> lines, String street1, String street2, String postalCode, String city, String country) {
        if (street1 != null && !street1.isBlank()) lines.add(street1);
        if (street2 != null && !street2.isBlank()) lines.add(street2);
        String cityLine = ((postalCode == null ? "" : postalCode + " ") + (city == null ? "" : city)).trim();
        if (!cityLine.isEmpty()) lines.add(cityLine);
        if (country != null && !country.isBlank()) lines.add(country);
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private static AuthException bad(String message) {
        return new AuthException(message, HttpStatus.BAD_REQUEST);
    }
}
