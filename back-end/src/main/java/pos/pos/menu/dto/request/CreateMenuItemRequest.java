package pos.pos.menu.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMenuItemRequest {

    @Size(max = 80, message = "SKU must be at most 80 characters")
    private String sku;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    private String description;

    @NotNull(message = "basePrice is required")
    @DecimalMin(value = "0.00", message = "basePrice must be greater than or equal to 0")
    @Digits(integer = 12, fraction = 2, message = "basePrice must have at most 12 digits and 2 decimals")
    private BigDecimal basePrice;

    @Size(max = 2048, message = "imageUrl must be at most 2048 characters")
    private String imageUrl;

    private Boolean available;

    /** Defaults to true when omitted. */
    private Boolean sendToKitchen;

    /** Defaults to false (staff menu only) when omitted. */
    private Boolean showOnline;

    /** Online section to place the dish in; wins over onlineSectionName. */
    private UUID onlineSectionId;

    /** Online section by name: reused if one exists (any case), created otherwise. Defaults to the dish's own section name. */
    @Size(max = 150, message = "onlineSectionName must be at most 150 characters")
    private String onlineSectionName;

    @Min(value = 0, message = "displayOrder must be greater than or equal to 0")
    private Integer displayOrder;

    @Size(max = 100, message = "ingredients can have at most 100 values")
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") String> ingredients;

    // Special-menu extras: order at least this many hours ahead, and the occasions they're offered for.
    @jakarta.validation.constraints.Min(value = 0, message = "orderBeforeHours must not be negative")
    @jakarta.validation.constraints.Max(value = 720, message = "orderBeforeHours must be at most 720")
    @Max(value = 720, message = "orderBeforeHours must be at most 720")
    private Integer orderBeforeHours;

    @Size(max = 50, message = "occasionCodes must be at most 50 values")
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") String> occasionCodes;
}
