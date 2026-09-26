package pos.pos.preorder.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreOrderItemOptionRequest {

    @NotNull(message = "optionItemId is required")
    private UUID optionItemId;

    @Min(value = 1, message = "quantity must be greater than 0")
    @Max(value = 20, message = "quantity must be at most 20")
    private Integer quantity;

    @Size(max = 200, message = "notes must be at most 200 characters")
    private String notes;
}
