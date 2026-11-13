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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

// checked
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

    //    The reservationSlotMinutes doesn't mean the customer only sits for 15 minutes. It’s the duration of the reservation slot.
    //    For example, if you set it to 15 minutes, it means:
    //    A reservation starts at a specific time (e.g., 6:00 PM).
    //    The table is reserved for 15 minutes (so, from 6:00 PM to 6:15 PM).
    //    After 15 minutes, the slot is considered free for another reservation or customer.
    @Column(name = "reservation_slot_minutes", nullable = false)
    private int reservationSlotMinutes = 15;

    // how much a costumer is supposed to stay before another costumer comes
    @Column(name = "default_table_turn_time_minutes", nullable = false)
    private int defaultTableTurnTimeMinutes = 90;

    // if you want to charge for service like 10% go for the stuff for serving the food
    @Column(name = "service_charge_enabled", nullable = false)
    private boolean serviceChargeEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_charge_type", length = 20)
    private ServiceChargeType serviceChargeType;

    @Column(name = "service_charge_value", precision = 12, scale = 2)
    private BigDecimal serviceChargeValue;

    @Column(name = "order_tax_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal orderTaxRate = BigDecimal.ZERO;

    @Column(name = "order_tax_inclusive", nullable = false)
    private boolean orderTaxInclusive = false;

    // if you want to round the amount like 10.291 dollar to make 290 dollar
    @Column(name = "cash_rounding_enabled", nullable = false)
    private boolean cashRoundingEnabled = false;

    @Column(name = "cash_rounding_increment", precision = 12, scale = 2)
    private BigDecimal cashRoundingIncrement;

    @Column(name = "allow_split_bills", nullable = false)
    private boolean allowSplitBills = true;

    // An open ticket is a bill or order that is created but not yet paid.
    // It’s like a temporary reservation of the customer’s items or services until they settle the payment.
    @Column(name = "allow_open_tickets", nullable = false)
    private boolean allowOpenTickets = true;

    // The requireCustomerForInvoice field determines whether the system requires a customer's information to generate an invoice.
    @Column(name = "require_customer_for_invoice", nullable = false)
    private boolean requireCustomerForInvoice = false;

    @Column(name = "enable_qr_ordering", nullable = false)
    private boolean enableQrOrdering = false;

    // Off until the restaurant switches it on (user rule, 2026-10-29): no takeaway tabs or orders unless it's enabled.
    @Column(name = "enable_takeaway", nullable = false)
    private boolean enableTakeaway = false;

    @Column(name = "enable_delivery", nullable = false)
    private boolean enableDelivery = false;

    // Off: only Owner and Co-Owner create, edit and delete Managers. On: Admins can too. Only Owner and Co-Owner flip it.
    @Column(name = "admins_can_manage_managers", nullable = false)
    private boolean adminsCanManageManagers = false;

    // Guests may order dishes with their reservation; off until the restaurant opts in.
    @Column(name = "pre_orders_enabled", nullable = false)
    private boolean preOrdersEnabled = false;

    // How many minutes before the booking a pre-order goes to the kitchen; until then it can still be changed or refunded.
    @Column(name = "pre_order_lead_minutes", nullable = false)
    private int preOrderLeadMinutes = 30;

    // Reservation policy (Admin Hub → Settings → Reservations). Groups of largeGroupFrom+ get extra table time.
    @Column(name = "large_group_from", nullable = false)
    private int largeGroupFrom = 5;

    @Column(name = "large_group_extra_minutes", nullable = false)
    private int largeGroupExtraMinutes = 15;

    // Bookings of this many guests or more need staff approval.
    @Column(name = "approval_group_size", nullable = false)
    private int approvalGroupSize = 7;

    // A late guest's table is held this long after the start; the "hold ends" warning shows at holdWarningMinutes.
    @Column(name = "hold_minutes", nullable = false)
    private int holdMinutes = 30;

    @Column(name = "hold_warning_minutes", nullable = false)
    private int holdWarningMinutes = 20;

    @Column(name = "check_in_opens_minutes", nullable = false)
    private int checkInOpensMinutes = 120;

    // When managers are told the day before about bookings still waiting for attendance confirmation.
    @Column(name = "confirm_reminder_time", nullable = false)
    private LocalTime confirmReminderTime = LocalTime.of(15, 0);

    // Same-day bookings: attendance should be confirmed this long before the start.
    @Column(name = "same_day_confirm_minutes", nullable = false)
    private int sameDayConfirmMinutes = 120;

    // No attendance reply this long before the start: staff are asked to call.
    @Column(name = "attendance_call_minutes", nullable = false)
    private int attendanceCallMinutes = 120;

    // Staff may reopen a cancelled / no-show booking within this window; after it, a manager with a reason.
    @Column(name = "reopen_window_minutes", nullable = false)
    private int reopenWindowMinutes = 60;

    @Column(name = "undo_seat_minutes", nullable = false)
    private int undoSeatMinutes = 15;

    // Staff bookings confirmed at once when a table fits; off = every booking waits for approval (user, 2026-11-02).
    @Column(name = "auto_confirm_bookings", nullable = false)
    private boolean autoConfirmBookings = true;

    // No answer to "Still coming?": asked again this many hours before (0 = never).
    @Column(name = "guest_second_reminder_hours", nullable = false)
    private int guestSecondReminderHours = 6;

    // A guest who never confirmed: their table waits this long (0 = the usual hold).
    @Column(name = "unconfirmed_hold_minutes", nullable = false)
    private int unconfirmedHoldMinutes = 15;

    // Told about tomorrow's bookings and about bookings staff must look at.
    @Column(name = "booking_alert_roles", nullable = false, length = 200)
    private String bookingAlertRoles = "OWNER,MANAGER";

    // How much extra hold a guest can ask for with "I'm running late".
    @Column(name = "running_late_max_minutes", nullable = false)
    private int runningLateMaxMinutes = 30;

    // A guest counts as late this long after the booking time.
    @Column(name = "late_after_minutes", nullable = false)
    private int lateAfterMinutes = 15;

    // How long before the booking the guest gets "Still coming? [Confirm] [Cancel]".
    @Column(name = "guest_reminder_hours", nullable = false)
    private int guestReminderHours = 24;

    // Staff are warned about a guest's new booking from this many no-shows.
    @Column(name = "no_show_warning_from", nullable = false)
    private int noShowWarningFrom = 1;

    // When deposits are on, bookings from this many guests pay one.
    @Column(name = "deposit_from_guests", nullable = false)
    private int depositFromGuests = 7;

    // The payment provider's fee, kept back on refunds: percent of the amount plus a fixed part.
    @Column(name = "card_fee_percent", nullable = false, precision = 5, scale = 2)
    private java.math.BigDecimal cardFeePercent = new java.math.BigDecimal("1.50");

    @Column(name = "card_fee_fixed", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal cardFeeFixed = new java.math.BigDecimal("0.25");

    // The waiters' Kitchen Status: an order counts as taking long after this, and ready food as waiting too long.
    @Column(name = "kitchen_slow_after_minutes", nullable = false)
    private int kitchenSlowAfterMinutes = 20;

    @Column(name = "kitchen_ready_waiting_minutes", nullable = false)
    private int kitchenReadyWaitingMinutes = 5;

    // Inventory → Overview: stock worth at least this much (at cost) counts as valuable.
    @Column(name = "valuable_stock_from", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal valuableStockFrom = new java.math.BigDecimal("100.00");

    // How often each active inventory place should be physically counted.
    @Column(name = "inventory_count_frequency", nullable = false)
    private int inventoryCountFrequency = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_count_period", nullable = false, length = 10)
    private pos.pos.settings.enums.InventoryCountPeriod inventoryCountPeriod = pos.pos.settings.enums.InventoryCountPeriod.WEEKLY;

    // Schedule changes start a fresh run so enabling the schedule doesn't create old missed counts.
    @Column(name = "inventory_count_schedule_started_at", nullable = false)
    private java.time.OffsetDateTime inventoryCountScheduleStartedAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);

    // Who is told when an order is closed as unpaid: role codes separated by commas.
    @Column(name = "unpaid_alert_roles", nullable = false, length = 500)
    private String unpaidAlertRoles = "OWNER,MANAGER";

    // Seating: up to this many guests over a table's seats is normal.
    @Column(name = "seating_extra_guests", nullable = false)
    private int seatingExtraGuests = 2;

    // Seating: a table can't take more than this many guests over its seats. Above the normal number it's unusual, and the
    // people below are told right away.
    @Column(name = "seating_most_extra_guests", nullable = false)
    private int seatingMostExtraGuests = 4;

    // Seating: who is told, role codes separated by commas.
    @Column(name = "seating_alert_roles", nullable = false, length = 500)
    private String seatingAlertRoles = "OWNER,MANAGER";

    // Guests still at a table this long before its next booking: the table warns.
    @Column(name = "next_booking_warning_minutes", nullable = false)
    private int nextBookingWarningMinutes = 15;

    @Column(name = "join_booking_warning_minutes", nullable = false)
    private int joinBookingWarningMinutes = 120;

    @Column(name = "booking_coming_minutes", nullable = false)
    private int bookingComingMinutes = 30;

    // Special cases (Settings → Tables): minutes without any action on a table or its order before the system steps in; 0 = never.
    @Column(name = "special_case_paid_minutes", nullable = false)
    private int specialCasePaidMinutes = 60;

    @Column(name = "special_case_seated_minutes", nullable = false)
    private int specialCaseSeatedMinutes = 240;

    @Column(name = "special_case_order_minutes", nullable = false)
    private int specialCaseOrderMinutes = 240;

    @Column(name = "special_case_warning_minutes", nullable = false)
    private int specialCaseWarningMinutes = 30;

    @Column(name = "special_case_auto_move", nullable = false)
    private boolean specialCaseAutoMove = true;

    @Column(name = "special_case_booking_no_action", nullable = false)
    private boolean specialCaseBookingNoAction = true;

    // The "Give more time" buttons, minutes separated by commas.
    @Column(name = "special_case_more_time", nullable = false, length = 30)
    private String specialCaseMoreTime = "10,15,30";

    @Column(name = "special_case_alert_roles", nullable = false, length = 500)
    private String specialCaseAlertRoles = "OWNER,MANAGER";

    // Staff can clock in for a scheduled shift from this long before it starts.
    @Column(name = "clock_in_early_minutes", nullable = false)
    private int clockInEarlyMinutes = 120;

    // POS → My shift → Team: staff see everyone working; off, only people in their own role.
    @Column(name = "staff_see_whole_team", nullable = false)
    private boolean staffSeeWholeTeam = true;

    // Payments: tips offered at the till, the biggest tip accepted (as a share of the bill), closing paid orders,
    // and how many days after payment a refund can still be given.
    @Column(name = "tips_enabled", nullable = false)
    private boolean tipsEnabled = true;

    @Column(name = "tip_suggestions", nullable = false, length = 50)
    private String tipSuggestions = "5,10,15";

    @Column(name = "max_tip_percent", nullable = false)
    private int maxTipPercent = 50;

    @Column(name = "auto_close_paid_orders", nullable = false)
    private boolean autoClosePaidOrders = true;

    @Column(name = "refund_window_days", nullable = false)
    private int refundWindowDays = 30;

    // Fraud checks: when a discount, refund, void or tip is flagged for an owner to look at.
    @Column(name = "fraud_discount_percent", nullable = false)
    private int fraudDiscountPercent = 30;

    @Column(name = "fraud_refund_amount", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal fraudRefundAmount = new java.math.BigDecimal("50.00");

    @Column(name = "fraud_voids_per_day", nullable = false)
    private int fraudVoidsPerDay = 5;

    @Column(name = "fraud_tip_percent", nullable = false)
    private int fraudTipPercent = 30;

    @Column(name = "fraud_cash_refunds_per_day", nullable = false)
    private int fraudCashRefundsPerDay = 2;

    // Rule codes the owner switched off, comma separated.
    @Column(name = "fraud_disabled_rules", nullable = false, length = 500)
    private String fraudDisabledRules = "";

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

        // The second if is there to establish the relationship between Settings and SettingsReceipt
        // objects, ensuring that the receiptSettings is linked back to the current Settings object.
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
        return NormalizationUtils.normalizeCode(value);
    }
}
