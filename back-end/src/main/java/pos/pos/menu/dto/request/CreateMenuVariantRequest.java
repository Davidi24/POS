package pos.pos.menu.dto.request;

import jakarta.validation.constraints.Digits;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMenuVariantRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name must be at most 120 characters")
    private String name;

    @Size(max = 80, message = "SKU must be at most 80 characters")
    private String sku;

    @Digits(integer = 12, fraction = 2, message = "priceDelta must have at most 12 digits and 2 decimals")
    private BigDecimal priceDelta;

    @JsonProperty("default")
    private Boolean isDefault;

    private Boolean active;

    @Min(value = 0, message = "displayOrder must be greater than or equal to 0")
    private Integer displayOrder;
}
