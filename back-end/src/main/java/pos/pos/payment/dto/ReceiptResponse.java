package pos.pos.payment.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Everything a printed or emailed receipt shows, already filtered by the restaurant's receipt settings: a field the
 * restaurant chose not to show is null (or an empty list).
 */
public record ReceiptResponse(
        String restaurantName,
        String legalName,
        String branchName,
        List<String> addressLines,
        String phone,
        String taxNumber,
        String vatNumber,
        boolean showLogo,
        // The restaurant's logo when it has one and the receipt settings show it.
        String logoUrl,
        String orderNumber,
        String tableNumber,
        String serverName,
        Integer guestCount,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt,
        OffsetDateTime printedAt,
        String currency,
        List<Line> lines,
        BigDecimal subtotal,
        List<Discount> discounts,
        BigDecimal discountTotal,
        BigDecimal serviceChargeTotal,
        Tax tax,
        BigDecimal total,
        BigDecimal prepaidTotal,
        List<Payment> payments,
        BigDecimal paidTotal,
        BigDecimal tipTotal,
        BigDecimal balanceDue,
        String footerNote,
        boolean showQrCode,
        int copies
) {
    public record Line(UUID lineId, String name, String variant, int quantity, BigDecimal unitPrice, List<String> options,
                       BigDecimal lineTotal, boolean removed, String notes) {
    }

    public record Discount(String name, BigDecimal amount) {
    }

    public record Tax(BigDecimal rate, boolean inclusive, BigDecimal amount) {
    }

    public record Payment(String method, String receiptNumber, BigDecimal amount, BigDecimal tipAmount,
                          BigDecimal refundedAmount, BigDecimal tenderedAmount, BigDecimal changeAmount,
                          String cardBrand, String cardLast4, OffsetDateTime paidAt, String status) {
    }
}
