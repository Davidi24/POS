package pos.pos.restaurant.currency;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Changing a restaurant's currency: the super admin gives the new currency and how much one unit of the old one is
 * worth in it, sees every price converted (and can correct any of them), then confirms.
 */
public final class CurrencyChangeDtos {

    private CurrencyChangeDtos() {
    }

    public record PreviewRequest(
            @NotBlank(message = "newCurrency is required")
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "newCurrency must be a 3-letter code such as EUR or ALL")
            String newCurrency,
            // 1 old = rate new (e.g. 1 EUR = 100.50 ALL).
            @NotNull(message = "rate is required")
            @DecimalMin(value = "0.00000001", message = "rate must be more than 0")
            @DecimalMax(value = "1000000", message = "rate must be at most 1000000")
            @Digits(integer = 7, fraction = 8, message = "rate can have at most 8 decimals")
            BigDecimal rate,
            // Converted prices are rounded to this step (0.01, 0.05, 0.10, 0.50, 1, 5, 10, 50 or 100).
            @NotNull(message = "roundingStep is required")
            BigDecimal roundingStep
    ) {
    }

    public record Override(
            @NotBlank(message = "kind is required") @Size(max = 40) String kind,
            @NotBlank(message = "id is required") @Size(max = 80) String id,
            @NotNull(message = "value is required")
            @Digits(integer = 13, fraction = 4, message = "value can have at most 4 decimals")
            BigDecimal value
    ) {
    }

    public record ApplyRequest(
            @NotBlank(message = "newCurrency is required")
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "newCurrency must be a 3-letter code such as EUR or ALL")
            String newCurrency,
            @NotNull(message = "rate is required")
            @DecimalMin(value = "0.00000001", message = "rate must be more than 0")
            @DecimalMax(value = "1000000", message = "rate must be at most 1000000")
            @Digits(integer = 7, fraction = 8, message = "rate can have at most 8 decimals")
            BigDecimal rate,
            @NotNull(message = "roundingStep is required")
            BigDecimal roundingStep,
            // Values the super admin corrected in the preview; everything else is converted at the rate.
            @Valid
            @Size(max = 20000, message = "at most 20000 corrections")
            List<Override> overrides,
            @Size(max = 64) String previewToken
    ) {
    }

    /** One amount that changes: [kind] + [id] identify it for corrections. */
    public record Item(String kind, String id, String name, String detail, BigDecimal oldValue, BigDecimal newValue,
                       String problem) {
    }

    public record Group(String key, String title, String hint, List<Item> items) {
    }

    public record Preview(
            String currentCurrency,
            String newCurrency,
            BigDecimal rate,
            BigDecimal roundingStep,
            boolean canChange,
            // What must be settled first ("2 open orders"); empty when the change can go ahead.
            List<String> blockers,
            List<Group> groups,
            int valueCount,
            // Converted without being listed: recipe costs follow the stock costs.
            long recipesFollowing,
            String previewToken
    ) {
    }

    public record ChangeRecord(UUID id, String oldCurrency, String newCurrency, BigDecimal rate, BigDecimal roundingStep,
                               int valuesConverted, OffsetDateTime changedAt, UUID changedBy, String changedByName) {
    }
}
