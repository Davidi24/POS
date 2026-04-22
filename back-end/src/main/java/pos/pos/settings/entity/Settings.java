package pos.pos.settings.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.settings.enums.ServiceChargeType;
import pos.pos.settings.enums.WeekStartDay;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(
        name = "settings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_settings_restaurant_id", columnNames = "restaurant_id")
        },
        indexes = {
                @Index(name = "idx_settings_default_branch_id", columnList = "default_branch_id"),
                @Index(name = "idx_settings_created_by", columnList = "created_by"),
                @Index(name = "idx_settings_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        char_length(btrim(default_language)) > 0
        AND char_length(btrim(date_format)) > 0
        AND char_length(btrim(time_format)) > 0
        AND reservation_slot_minutes > 0
        AND default_table_turn_time_minutes > 0
        AND (service_charge_value IS NULL OR service_charge_value >= 0)
        AND (cash_rounding_increment IS NULL OR cash_rounding_increment > 0)
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Settings extends AbstractAuditedEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            unique = true,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_settings_restaurant")
    )
    private Restaurant restaurant;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "default_branch_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_settings_default_branch")
    )
    private Branch defaultBranch;

    @Column(name = "default_language", nullable = false, length = 20)
    private String defaultLanguage = "en";

    @Column(name = "date_format", nullable = false, length = 30)
    private String dateFormat = "yyyy-MM-dd";

    @Column(name = "time_format", nullable = false, length = 30)
    private String timeFormat = "HH:mm";

    @Enumerated(EnumType.STRING)
    @Column(name = "week_start_day", nullable = false, length = 15)
    private WeekStartDay weekStartDay = WeekStartDay.MONDAY;

    @Column(name = "order_sequence_prefix", length = 20)
    private String orderSequencePrefix = "ORD";

    @Column(name = "invoice_sequence_prefix", length = 20)
    private String invoiceSequencePrefix = "INV";

    @Column(name = "reservation_slot_minutes", nullable = false)
    private int reservationSlotMinutes = 15;

    @Column(name = "default_table_turn_time_minutes", nullable = false)
    private int defaultTableTurnTimeMinutes = 90;

    @Column(name = "service_charge_enabled", nullable = false)
    private boolean serviceChargeEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_charge_type", length = 20)
    private ServiceChargeType serviceChargeType;

    @Column(name = "service_charge_value", precision = 12, scale = 2)
    private BigDecimal serviceChargeValue;

    @Column(name = "cash_rounding_enabled", nullable = false)
    private boolean cashRoundingEnabled = false;

    @Column(name = "cash_rounding_increment", precision = 12, scale = 2)
    private BigDecimal cashRoundingIncrement;

    @Column(name = "allow_split_bills", nullable = false)
    private boolean allowSplitBills = true;

    @Column(name = "allow_open_tickets", nullable = false)
    private boolean allowOpenTickets = true;

    @Column(name = "require_customer_for_invoice", nullable = false)
    private boolean requireCustomerForInvoice = false;

    @Column(name = "enable_qr_ordering", nullable = false)
    private boolean enableQrOrdering = false;

    @Column(name = "enable_takeaway", nullable = false)
    private boolean enableTakeaway = true;

    @Column(name = "enable_delivery", nullable = false)
    private boolean enableDelivery = false;

    @OneToOne(mappedBy = "settings", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private SettingsReceipt receiptSettings;

    @OneToOne(mappedBy = "settings", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private SettingsOrderRule orderRuleSettings;

    @OneToMany(mappedBy = "settings", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("priority ASC, createdAt ASC")
    private List<SettingsReservationRule> reservationRules = new ArrayList<>();

    public void setReceiptSettings(SettingsReceipt receiptSettings) {
        if (this.receiptSettings != null) {
            this.receiptSettings.setSettings(null);
        }

        this.receiptSettings = receiptSettings;

        if (receiptSettings != null) {
            receiptSettings.setSettings(this);
        }
    }

    public void setOrderRuleSettings(SettingsOrderRule orderRuleSettings) {
        if (this.orderRuleSettings != null) {
            this.orderRuleSettings.setSettings(null);
        }

        this.orderRuleSettings = orderRuleSettings;

        if (orderRuleSettings != null) {
            orderRuleSettings.setSettings(this);
        }
    }

    public void addReservationRule(SettingsReservationRule rule) {
        if (rule == null) {
            return;
        }

        reservationRules.add(rule);
        rule.setSettings(this);
    }

    public void removeReservationRule(SettingsReservationRule rule) {
        if (rule == null) {
            return;
        }

        reservationRules.remove(rule);
        rule.setSettings(null);
    }

    @Override
    protected void normalizeFields() {
        defaultLanguage = normalizeLanguageTag(defaultLanguage);
        dateFormat = NormalizationUtils.normalize(dateFormat);
        timeFormat = NormalizationUtils.normalize(timeFormat);
        orderSequencePrefix = normalizePrefix(orderSequencePrefix);
        invoiceSequencePrefix = normalizePrefix(invoiceSequencePrefix);
    }

    @Override
    protected void validateState() {
        if (reservationSlotMinutes <= 0) {
            throw new IllegalStateException("reservationSlotMinutes must be greater than zero");
        }

        if (defaultTableTurnTimeMinutes <= 0) {
            throw new IllegalStateException("defaultTableTurnTimeMinutes must be greater than zero");
        }

        if (serviceChargeEnabled && (serviceChargeType == null || serviceChargeValue == null)) {
            throw new IllegalStateException("service charge configuration is incomplete");
        }

        if (serviceChargeValue != null && serviceChargeValue.signum() < 0) {
            throw new IllegalStateException("serviceChargeValue must not be negative");
        }

        if (cashRoundingIncrement != null && cashRoundingIncrement.signum() <= 0) {
            throw new IllegalStateException("cashRoundingIncrement must be greater than zero");
        }

        if (restaurant != null && defaultBranch != null && defaultBranch.getRestaurant() != null) {
            if (!Objects.equals(defaultBranch.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("default branch must belong to the same restaurant");
            }
        }
    }

    private String normalizeLanguageTag(String value) {
        String normalized = NormalizationUtils.normalize(value);
        if (normalized == null) {
            return null;
        }

        String candidate = normalized.replace('_', '-');
        Locale locale = Locale.forLanguageTag(candidate);
        if (locale.getLanguage().isBlank()) {
            throw new IllegalStateException("defaultLanguage must be a valid BCP 47 language tag");
        }

        return locale.toLanguageTag();
    }

    private String normalizePrefix(String value) {
        String normalized = NormalizationUtils.normalizeUpper(value);
        if (normalized == null) {
            return null;
        }

        String sanitized = normalized
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        return sanitized.isEmpty() ? null : sanitized;
    }
}
