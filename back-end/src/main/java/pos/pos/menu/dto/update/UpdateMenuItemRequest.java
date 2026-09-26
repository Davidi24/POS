package pos.pos.menu.dto.update;

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
public class UpdateMenuItemRequest {

    @Size(max = 80, message = "SKU must be at most 80 characters")
    private String sku;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    private String description;

    @NotNull(message = "basePrice is required")
    @DecimalMin(value = "0.00", message = "basePrice must be greater than or equal to 0")
    private BigDecimal basePrice;

    private String imageUrl;

    @NotNull(message = "available is required")
    private Boolean available;

    /** Keeps the current value when omitted, so older clients don't reset it. */
    private Boolean sendToKitchen;

    /** Keeps the current value when omitted (unless an online section is given, which also switches it on). */
    private Boolean showOnline;

    /** Online section to place the dish in; wins over onlineSectionName. */
    private UUID onlineSectionId;

    /** Online section by name: reused if one exists (any case), created otherwise. */
    @Size(max = 150, message = "onlineSectionName must be at most 150 characters")
    private String onlineSectionName;

    @NotNull(message = "displayOrder is required")
    @Min(value = 0, message = "displayOrder must be greater than or equal to 0")
    private Integer displayOrder;

    private List<String> ingredients;

    /** When set to a section other than the current one, moves the item there. */
    private UUID sectionId;
}
