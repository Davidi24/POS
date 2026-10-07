package pos.pos.fraud;

import java.util.Arrays;
import java.util.Optional;

/** The checks that flag staff actions for an owner to look at. Thresholds live in the restaurant settings. */
public enum FraudRule {
    LARGE_DISCOUNT(FraudSeverity.MEDIUM, "Big discount", "A discount at or above the set share of the bill"),
    ITEM_VOID_AFTER_KITCHEN(FraudSeverity.MEDIUM, "Item removed after cooking", "An item removed after it was sent to the kitchen"),
    MANY_VOIDS(FraudSeverity.HIGH, "Many removals", "One person removed more items or orders in a day than the set limit"),
    ORDER_VOIDED_AFTER_KITCHEN(FraudSeverity.HIGH, "Order voided after cooking", "A whole order voided after food went to the kitchen"),
    CANCELLED_AFTER_KITCHEN(FraudSeverity.MEDIUM, "Order cancelled after cooking", "An order cancelled after food went to the kitchen"),
    LARGE_REFUND(FraudSeverity.HIGH, "Big refund", "A refund at or above the set amount"),
    CASH_REFUNDS(FraudSeverity.HIGH, "Many cash refunds", "One person gave more cash refunds in a day than the set limit"),
    PAYMENT_VOIDED(FraudSeverity.MEDIUM, "Payment cancelled", "A payment taken and then cancelled"),
    REOPENED_PAID_ORDER(FraudSeverity.MEDIUM, "Paid order reopened", "A closed, paid order opened again"),
    CLOSED_WITHOUT_PAYMENT(FraudSeverity.HIGH, "Closed without payment", "An order closed with money still owed and no payment recorded"),
    MARKED_PAID_MANUALLY(FraudSeverity.HIGH, "Marked paid by hand", "An order marked paid while recorded payments don't cover it"),
    HIGH_TIP(FraudSeverity.LOW, "High tip", "A tip at or above the set share of the payment"),
    OFF_SHIFT_ACTION(FraudSeverity.LOW, "Action while clocked out", "A refund, removal or discount by someone not clocked in");

    private final FraudSeverity severity;
    private final String title;
    private final String description;

    FraudRule(FraudSeverity severity, String title, String description) {
        this.severity = severity;
        this.title = title;
        this.description = description;
    }

    public FraudSeverity severity() { return severity; }
    public String title() { return title; }
    public String description() { return description; }

    public static Optional<FraudRule> fromCode(String code) {
        return Arrays.stream(values()).filter(rule -> rule.name().equalsIgnoreCase(code)).findFirst();
    }
}
