package pos.pos.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What went in and out of stock per day, at cost, for the Inventory overview: received (deliveries), used (taken by
 * sales) and wasted, plus how many changes were recorded. Days without changes are included as zeros.
 */
public record InventoryActivityResponse(
        String currency,
        LocalDate from,
        LocalDate to,
        BigDecimal received,
        BigDecimal used,
        BigDecimal wasted,
        long changes,
        List<Day> days
) {
    public record Day(LocalDate date, BigDecimal received, BigDecimal used, BigDecimal wasted, long changes) {
    }
}
