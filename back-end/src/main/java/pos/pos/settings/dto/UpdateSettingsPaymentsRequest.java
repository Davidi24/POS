package pos.pos.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsPaymentsRequest {

    @NotNull(message = "tipsEnabled is required")
    private Boolean tipsEnabled;

    // The tip buttons offered at the till, as percents of the bill. Send either the list or the same as text ("5,10,15").
    @Size(max = 6, message = "tipSuggestions can have at most 6 values")
    private List<@NotNull(message = "tipSuggestions must not contain empty values")
            @Min(value = 1, message = "tipSuggestions must be at least 1")
            @Max(value = 100, message = "tipSuggestions must be at most 100") Integer> tipSuggestions;

    @Size(max = 64, message = "tipSuggestionsText must be at most 64 characters")
    @jakarta.validation.constraints.Pattern(regexp = "^\\s*(\\d{1,3}(\\s*,\\s*\\d{1,3}){0,5})?\\s*$",
            message = "tipSuggestionsText must be up to 6 whole percents separated by commas, like 5,10,15")
    private String tipSuggestionsText;

    @NotNull(message = "maxTipPercent is required")
    @Min(value = 1, message = "maxTipPercent must be at least 1")
    @Max(value = 200, message = "maxTipPercent must be at most 200")
    private Integer maxTipPercent;

    @NotNull(message = "autoClosePaidOrders is required")
    private Boolean autoClosePaidOrders;

    @NotNull(message = "refundWindowDays is required")
    @Min(value = 0, message = "refundWindowDays must not be negative")
    @Max(value = 365, message = "refundWindowDays must be at most 365")
    private Integer refundWindowDays;
}
