package pos.pos.fraud.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.fraud.FraudReviewStatus;
import pos.pos.fraud.FraudRule;
import pos.pos.fraud.FraudSeverity;
import pos.pos.fraud.dto.FraudDtos.Activity;
import pos.pos.fraud.dto.FraudDtos.ActivityPage;
import pos.pos.fraud.dto.FraudDtos.Alert;
import pos.pos.fraud.dto.FraudDtos.AlertPage;
import pos.pos.fraud.dto.FraudDtos.DayCount;
import pos.pos.fraud.dto.FraudDtos.Overview;
import pos.pos.fraud.dto.FraudDtos.ReviewRequest;
import pos.pos.fraud.dto.FraudDtos.RuleCount;
import pos.pos.fraud.dto.FraudDtos.RuleSetting;
import pos.pos.fraud.dto.FraudDtos.Rules;
import pos.pos.fraud.dto.FraudDtos.StaffRisk;
import pos.pos.fraud.entity.FraudAlertReview;
import pos.pos.fraud.repository.FraudAlertReviewRepository;
import pos.pos.fraud.repository.FraudQueryRepository;
import pos.pos.fraud.repository.FraudQueryRepository.Finding;
import pos.pos.fraud.repository.FraudQueryRepository.Scope;
import pos.pos.payment.mapper.PaymentMapper;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.mapper.SettingsCodes;
import pos.pos.settings.repository.SettingsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Flags staff actions worth an owner's look. Alerts are worked out from orders and payments each time (so a changed
 * threshold applies to the past too); only the owner's verdict on each alert is stored.
 */
@Service
@RequiredArgsConstructor
public class FraudService {

    public static final int MAX_DAYS = 92;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ACTIVITY_TYPES = Set.of(
            "DISCOUNT", "ITEM_REMOVED", "ORDER_VOIDED", "ORDER_CANCELLED", "REFUND", "PAYMENT_CANCELLED", "ORDER_REOPENED");

    private final RestaurantScopeService restaurantScopeService;
    private final FraudQueryRepository queries;
    private final FraudAlertReviewRepository reviews;
    private final SettingsRepository settingsRepository;
    private final PaymentMapper paymentMapper;

    record Window(Restaurant restaurant, Settings settings, Scope scope, LocalDate from, LocalDate to, ZoneId zone) {
    }

    record Computed(List<Alert> alerts, boolean truncated) {
    }

    @Transactional(readOnly = true)
    public Overview overview(Authentication authentication, UUID restaurantId, UUID branchId, LocalDate from, LocalDate to) {
        Window window = window(authentication, restaurantId, branchId, from, to);
        Computed computed = compute(window);
        List<Alert> alerts = computed.alerts();

        Map<FraudRule, List<Alert>> byRule = new EnumMap<>(FraudRule.class);
        alerts.forEach(alert -> byRule.computeIfAbsent(alert.rule(), rule -> new ArrayList<>()).add(alert));
        List<RuleCount> rules = new ArrayList<>();
        for (FraudRule rule : FraudRule.values()) {
            List<Alert> ruleAlerts = byRule.getOrDefault(rule, List.of());
            rules.add(new RuleCount(rule, rule.severity(), rule.title(),
                    ruleAlerts.stream().filter(this::isOpen).count(), ruleAlerts.size(), sum(ruleAlerts)));
        }

        Map<UUID, List<Alert>> byStaff = alerts.stream().filter(alert -> alert.staffId() != null)
                .collect(Collectors.groupingBy(Alert::staffId));
        List<StaffRisk> staff = byStaff.entrySet().stream()
                .map(entry -> new StaffRisk(entry.getKey(), entry.getValue().getFirst().staffName(),
                        entry.getValue().stream().filter(this::isOpen).count(),
                        entry.getValue().stream().filter(alert -> isOpen(alert) && alert.severity() == FraudSeverity.HIGH).count(),
                        entry.getValue().size(), sum(entry.getValue())))
                .sorted(Comparator.comparingLong(StaffRisk::highAlerts).reversed()
                        .thenComparing(Comparator.comparingLong(StaffRisk::openAlerts).reversed())
                        .thenComparing(StaffRisk::staffName, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(10)
                .toList();

        Map<LocalDate, long[]> perDay = new LinkedHashMap<>();
        for (LocalDate day = window.from(); !day.isAfter(window.to()); day = day.plusDays(1)) {
            perDay.put(day, new long[2]);
        }
        for (Alert alert : alerts) {
            if (alert.occurredAt() == null) continue;
            long[] counts = perDay.get(alert.occurredAt().atZoneSameInstant(window.zone()).toLocalDate());
            if (counts != null) {
                counts[0]++;
                if (alert.severity() == FraudSeverity.HIGH) counts[1]++;
            }
        }
        List<DayCount> days = perDay.entrySet().stream().map(entry -> new DayCount(entry.getKey(), entry.getValue()[0], entry.getValue()[1])).toList();

        return new Overview(
                window.from(), window.to(), window.zone().getId(), currency(window),
                alerts.stream().filter(this::isOpen).count(),
                alerts.stream().filter(alert -> isOpen(alert) && alert.severity() == FraudSeverity.HIGH).count(),
                alerts.size(),
                sum(alerts),
                rules,
                staff,
                days,
                alerts.stream().filter(this::isOpen).limit(5).toList(),
                computed.truncated()
        );
    }

    @Transactional(readOnly = true)
    public AlertPage alerts(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            LocalDate from,
            LocalDate to,
            FraudRule rule,
            FraudSeverity severity,
            FraudReviewStatus status,
            UUID staffId,
            int page,
            int size
    ) {
        checkPage(page, size);
        Window window = window(authentication, restaurantId, branchId, from, to);
        Computed computed = compute(window);
        List<Alert> filtered = computed.alerts().stream()
                .filter(alert -> rule == null || alert.rule() == rule)
                .filter(alert -> severity == null || alert.severity() == severity)
                .filter(alert -> status == null || alert.status() == status)
                .filter(alert -> staffId == null || staffId.equals(alert.staffId()))
                .toList();
        int start = (int) Math.min((long) page * size, filtered.size());
        int end = Math.min(start + size, filtered.size());
        int totalPages = (filtered.size() + size - 1) / size;
        return new AlertPage(filtered.subList(start, end), page, size, filtered.size(), totalPages, end < filtered.size(),
                computed.truncated());
    }

    @Transactional(readOnly = true)
    public ActivityPage activity(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            LocalDate from,
            LocalDate to,
            String type,
            UUID staffId,
            int page,
            int size
    ) {
        checkPage(page, size);
        String kind = type == null || type.isBlank() ? null : type.trim().toUpperCase(Locale.ROOT);
        if (kind != null && !ACTIVITY_TYPES.contains(kind)) {
            throw bad("type must be one of " + String.join(", ", ACTIVITY_TYPES.stream().sorted().toList()));
        }
        Window window = window(authentication, restaurantId, branchId, from, to);
        long total = queries.activityCount(window.scope(), kind, staffId);
        var rows = queries.activity(window.scope(), kind, staffId, page * size, size);
        Set<UUID> people = new HashSet<>();
        rows.forEach(row -> people.add(row.staffId()));
        people.remove(null);
        Map<UUID, String> names = paymentMapper.names(people);
        List<Activity> items = rows.stream()
                .map(row -> new Activity(row.type(), row.at(), row.staffId(),
                        row.staffId() == null ? null : names.getOrDefault(row.staffId(), "Former staff"),
                        row.orderId(), row.orderNumber(), money(row.amount()), row.currency(), row.detail(), row.onShift()))
                .toList();
        int totalPages = (int) ((total + size - 1) / size);
        return new ActivityPage(items, page, size, total, totalPages, (long) (page + 1) * size < total);
    }

    @Transactional(readOnly = true)
    public Rules rules(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        Settings settings = settings(restaurant);
        Set<String> disabled = new HashSet<>(SettingsCodes.parseCodes(settings.getFraudDisabledRules()));
        List<RuleSetting> rules = new ArrayList<>();
        for (FraudRule rule : FraudRule.values()) {
            rules.add(new RuleSetting(rule, rule.severity(), rule.title(), rule.description(), !disabled.contains(rule.name()),
                    threshold(rule, settings)));
        }
        return new Rules(settings.getFraudDiscountPercent(), settings.getFraudRefundAmount(), settings.getFraudVoidsPerDay(),
                settings.getFraudTipPercent(), settings.getFraudCashRefundsPerDay(), rules);
    }

    @Transactional
    public Alert review(Authentication authentication, UUID restaurantId, String alertKey, ReviewRequest request) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        String key = alertKey == null ? "" : alertKey.trim();
        if (key.isEmpty() || key.length() > 200 || !key.matches("[A-Z_]+:[A-Za-z0-9:_-]+")) {
            throw bad("Invalid alert key");
        }
        FraudRule rule = FraudRule.fromCode(key.substring(0, key.indexOf(':')))
                .orElseThrow(() -> bad("Invalid alert key"));
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        FraudAlertReview review = reviews.findByRestaurantIdAndAlertKey(restaurant.getId(), key).orElseGet(() -> {
            FraudAlertReview created = new FraudAlertReview();
            created.setRestaurantId(restaurant.getId());
            created.setAlertKey(key);
            return created;
        });
        review.setStatus(request.status());
        review.setNote(request.note());
        review.setReviewedBy(actorId);
        review.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));
        FraudAlertReview saved = reviews.saveAndFlush(review);
        String reviewer = paymentMapper.names(Set.of(actorId)).get(actorId);
        return new Alert(key, rule, rule.severity(), rule.title(), null, null, null, null, null, null, null, null, null,
                saved.getStatus(), saved.getNote(), saved.getReviewedBy(), reviewer, saved.getReviewedAt());
    }

    // ---- Computing alerts ----

    Computed compute(Window window) {
        Settings settings = window.settings();
        Set<String> disabled = new HashSet<>(SettingsCodes.parseCodes(settings.getFraudDisabledRules()));
        Scope scope = window.scope();
        List<Finding> findings = new ArrayList<>();
        boolean truncated = false;
        for (FraudRule rule : FraudRule.values()) {
            if (disabled.contains(rule.name())) {
                continue;
            }
            List<Finding> found = switch (rule) {
                case LARGE_DISCOUNT -> queries.largeDiscounts(scope, settings.getFraudDiscountPercent());
                case ITEM_VOID_AFTER_KITCHEN -> queries.itemsRemovedAfterKitchen(scope);
                case MANY_VOIDS -> queries.manyVoids(scope, settings.getFraudVoidsPerDay());
                case ORDER_VOIDED_AFTER_KITCHEN -> queries.ordersEndedAfterKitchen(scope, "VOIDED", rule);
                case CANCELLED_AFTER_KITCHEN -> queries.ordersEndedAfterKitchen(scope, "CANCELLED", rule);
                case LARGE_REFUND -> queries.largeRefunds(scope, settings.getFraudRefundAmount());
                case CASH_REFUNDS -> queries.manyCashRefunds(scope, settings.getFraudCashRefundsPerDay());
                case PAYMENT_VOIDED -> queries.voidedPayments(scope);
                case REOPENED_PAID_ORDER -> queries.reopenedPaidOrders(scope);
                case CLOSED_WITHOUT_PAYMENT -> queries.closedWithoutPayment(scope);
                case MARKED_PAID_MANUALLY -> queries.markedPaidManually(scope);
                case HIGH_TIP -> queries.highTips(scope, settings.getFraudTipPercent());
                case OFF_SHIFT_ACTION -> queries.offShiftActions(scope);
            };
            truncated |= found.size() >= FraudQueryRepository.LIMIT;
            findings.addAll(found);
        }

        Set<UUID> people = new HashSet<>();
        findings.forEach(finding -> people.add(finding.staffId()));
        List<String> keys = findings.stream().map(this::key).toList();
        Map<String, FraudAlertReview> verdicts = keys.isEmpty() ? Map.of()
                : reviews.findAllByRestaurantIdAndAlertKeyIn(window.restaurant().getId(), new HashSet<>(keys)).stream()
                .collect(Collectors.toMap(FraudAlertReview::getAlertKey, Function.identity(), (a, b) -> a));
        verdicts.values().forEach(review -> people.add(review.getReviewedBy()));
        people.remove(null);
        Map<UUID, String> names = paymentMapper.names(people);

        List<Alert> alerts = new ArrayList<>(findings.size());
        Set<String> seen = new HashSet<>();
        for (Finding finding : findings) {
            String key = key(finding);
            if (!seen.add(key)) {
                continue;
            }
            FraudAlertReview verdict = verdicts.get(key);
            String staffName = finding.staffId() == null ? null : names.getOrDefault(finding.staffId(), "Former staff");
            alerts.add(new Alert(
                    key,
                    finding.rule(),
                    finding.rule().severity(),
                    finding.rule().title(),
                    detail(finding, staffName, settings),
                    finding.at(),
                    finding.staffId(),
                    staffName,
                    finding.orderId(),
                    finding.orderNumber(),
                    finding.paymentId(),
                    money(finding.amount()),
                    finding.currency(),
                    verdict == null ? FraudReviewStatus.OPEN : verdict.getStatus(),
                    verdict == null ? null : verdict.getNote(),
                    verdict == null ? null : verdict.getReviewedBy(),
                    verdict == null || verdict.getReviewedBy() == null ? null : names.get(verdict.getReviewedBy()),
                    verdict == null ? null : verdict.getReviewedAt()
            ));
        }
        alerts.sort(Comparator.comparing(Alert::occurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Alert::key));
        return new Computed(alerts, truncated);
    }

    public String key(Finding finding) {
        return finding.rule().name() + ":" + finding.refId();
    }

    public static String detail(Finding finding, String staffName, Settings settings) {
        String who = staffName == null ? "Someone" : staffName;
        String money = finding.amount() == null ? "" : money(finding.amount()).toPlainString() + " " + (finding.currency() == null ? "" : finding.currency());
        String order = finding.orderNumber() == null ? "" : " on " + finding.orderNumber();
        String extra = finding.extra() == null || finding.extra().isBlank() ? "" : finding.extra();
        return switch (finding.rule()) {
            case LARGE_DISCOUNT -> who + " gave a " + ratio(finding) + "% discount (" + money + ")" + order + suffix(extra);
            case ITEM_VOID_AFTER_KITCHEN -> who + " removed " + extra + " (" + money + ") after it went to the kitchen" + order;
            case MANY_VOIDS -> who + " removed " + finding.count() + " items or orders on " + extra + " (" + money + ")";
            case ORDER_VOIDED_AFTER_KITCHEN -> who + " voided" + order + " (" + money + ") after food went to the kitchen";
            case CANCELLED_AFTER_KITCHEN -> who + " cancelled" + order + " (" + money + ") after food went to the kitchen";
            case LARGE_REFUND -> who + " refunded " + money + order + suffix(extra);
            case CASH_REFUNDS -> who + " gave " + finding.count() + " cash refunds on " + extra + " (" + money + ")";
            case PAYMENT_VOIDED -> who + " cancelled a payment of " + money + order + suffix(extra);
            case REOPENED_PAID_ORDER -> who + " reopened paid order" + (finding.orderNumber() == null ? "" : " " + finding.orderNumber()) + " (" + money + ")";
            case CLOSED_WITHOUT_PAYMENT -> who + " closed" + order + " with " + money + " not paid";
            case MARKED_PAID_MANUALLY -> who + " marked" + order + " paid with " + money + " not covered by payments";
            case HIGH_TIP -> "A " + ratio(finding) + "% tip (" + money + ") was taken by " + who + order;
            case OFF_SHIFT_ACTION -> who + " made a " + humanKind(extra) + " (" + money + ")" + order + " while not clocked in";
        };
    }

    private static String suffix(String extra) {
        return extra == null || extra.isBlank() ? "" : " · " + extra;
    }

    private static String ratio(Finding finding) {
        return finding.ratio() == null ? "?" : finding.ratio().stripTrailingZeros().toPlainString();
    }

    private static String humanKind(String kind) {
        if (kind == null) return "change";
        return switch (kind) {
            case "DISCOUNT" -> "discount";
            case "ITEM_REMOVED" -> "removal";
            case "REFUND" -> "refund";
            case "PAYMENT_CANCELLED" -> "payment cancellation";
            case "ORDER_REOPENED" -> "reopen";
            default -> kind.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private static String threshold(FraudRule rule, Settings settings) {
        return switch (rule) {
            case LARGE_DISCOUNT -> settings.getFraudDiscountPercent() + "% of the bill or more";
            case MANY_VOIDS -> settings.getFraudVoidsPerDay() + " or more removals a day";
            case LARGE_REFUND -> settings.getFraudRefundAmount().toPlainString() + " or more";
            case CASH_REFUNDS -> settings.getFraudCashRefundsPerDay() + " or more cash refunds a day";
            case HIGH_TIP -> settings.getFraudTipPercent() + "% of the payment or more";
            default -> null;
        };
    }

    Window window(Authentication authentication, UUID restaurantId, UUID branchId, LocalDate from, LocalDate to) {
        Restaurant restaurant = restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        if (branchId != null) {
            restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        }
        if (from == null || to == null) {
            throw bad("from and to are required");
        }
        if (to.isBefore(from)) {
            throw bad("to must not be before from");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw bad("The period can be at most " + MAX_DAYS + " days");
        }
        ZoneId zone;
        try {
            zone = restaurant.getTimezone() == null ? ZoneId.of("UTC") : ZoneId.of(restaurant.getTimezone());
        } catch (Exception ignored) {
            zone = ZoneId.of("UTC");
        }
        Scope scope = new Scope(restaurant.getId(), branchId, from.atStartOfDay(zone).toOffsetDateTime(),
                to.plusDays(1).atStartOfDay(zone).toOffsetDateTime(), zone.getId());
        return new Window(restaurant, settings(restaurant), scope, from, to, zone);
    }

    private Settings settings(Restaurant restaurant) {
        return settingsRepository.findByRestaurant_Id(restaurant.getId()).orElseGet(Settings::new);
    }

    private boolean isOpen(Alert alert) {
        return alert.status() == FraudReviewStatus.OPEN;
    }

    private static BigDecimal sum(List<Alert> alerts) {
        return alerts.stream().map(Alert::amount).filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String currency(Window window) {
        return window.restaurant().getCurrency();
    }

    private static void checkPage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw bad("page must not be negative and size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private static AuthException bad(String message) {
        return new AuthException(message, HttpStatus.BAD_REQUEST);
    }
}
