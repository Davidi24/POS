package pos.pos.menu.dto;

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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOptionItemRequest {

    @Size(max = 50, message = "code must be at most 50 characters")
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    private BigDecimal priceDelta;

    @NotNull(message = "available is required")
    private Boolean available;

    @NotNull(message = "displayOrder is required")
    @Min(value = 0, message = "displayOrder must be greater than or equal to 0")
    private Integer displayOrder;
}
