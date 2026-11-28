package pos.pos.preorder.dto;

import jakarta.validation.Valid;
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
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreOrderItemRequest {

    @NotNull(message = "menuItemId is required")
    private UUID menuItemId;

    private UUID variantId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be greater than 0")
    @Max(value = 50, message = "quantity must be at most 50")
    private Integer quantity;

    @Size(max = 200, message = "notes must be at most 200 characters")
    private String notes;

    @Size(max = 30, message = "options must contain at most 30 choices")
    @Valid
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") PreOrderItemOptionRequest> options;
}
