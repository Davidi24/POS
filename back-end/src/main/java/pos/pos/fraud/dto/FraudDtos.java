package pos.pos.fraud.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pos.pos.fraud.FraudReviewStatus;
import pos.pos.fraud.FraudRule;
import pos.pos.fraud.FraudSeverity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class FraudDtos {

    private FraudDtos() {
    }

    /**
     * One flagged action. {@code key} is stable for the same action, so a review sticks to it. Money is in
     * {@code currency}; {@code amount} is what the action was worth (null when not about money).
     */
    public record Alert(String key, FraudRule rule, FraudSeverity severity, String title, String detail,
                        OffsetDateTime occurredAt, UUID staffId, String staffName, UUID orderId, String orderNumber,
                        UUID paymentId, BigDecimal amount, String currency, FraudReviewStatus status, String reviewNote,
                        UUID reviewedBy, String reviewedByName, OffsetDateTime reviewedAt) {
    }

    public record AlertPage(List<Alert> items, int page, int size, long totalElements, int totalPages, boolean hasNext,
                            boolean truncated) {
    }

    public record RuleCount(FraudRule rule, FraudSeverity severity, String title, long open, long total, BigDecimal amount) {
    }

    public record StaffRisk(UUID staffId, String staffName, long openAlerts, long highAlerts, long totalAlerts,
                            BigDecimal amount) {
    }

    public record DayCount(LocalDate date, long alerts, long high) {
    }

    public record Overview(LocalDate from, LocalDate to, String timezone, String currency, long openAlerts,
                           long highOpenAlerts, long totalAlerts, BigDecimal amountInvolved, List<RuleCount> rules,
                           List<StaffRisk> staff, List<DayCount> days, List<Alert> latest, boolean truncated) {
    }

    /** One sensitive action (whether flagged or not), newest first. */
    public record Activity(String type, OffsetDateTime at, UUID staffId, String staffName, UUID orderId,
                           String orderNumber, BigDecimal amount, String currency, String detail, boolean onShift) {
    }

    public record ActivityPage(List<Activity> items, int page, int size, long totalElements, int totalPages, boolean hasNext) {
    }

    public record RuleSetting(FraudRule rule, FraudSeverity severity, String title, String description, boolean enabled,
                              String threshold) {
    }

    public record Rules(int discountPercent, BigDecimal refundAmount, int voidsPerDay, int tipPercent,
                        int cashRefundsPerDay, List<RuleSetting> rules) {
    }

    public record ReviewRequest(
            @NotNull(message = "status is required") FraudReviewStatus status,
            @Size(max = 1000, message = "note must be at most 1000 characters") String note
    ) {
    }
}
