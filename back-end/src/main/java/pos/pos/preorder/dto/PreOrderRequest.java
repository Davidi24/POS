package pos.pos.preorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// The full list of dishes; placing it again while still scheduled replaces the previous list.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreOrderRequest {

    @NotEmpty(message = "items must contain at least one dish")
    @Size(max = 50, message = "items must contain at most 50 dishes")
    @Valid
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") PreOrderItemRequest> items;

    @Size(max = 500, message = "notes must be at most 500 characters")
    private String notes;
}
