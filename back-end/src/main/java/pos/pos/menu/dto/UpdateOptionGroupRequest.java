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

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOptionGroupRequest {

    @NotNull(message = "typeId is required")
    private UUID typeId;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    private String description;

    @Min(value = 0, message = "minSelect must be greater than or equal to 0")
    private Integer minSelect;

    @Min(value = 0, message = "maxSelect must be greater than or equal to 0")
    private Integer maxSelect;

    @NotNull(message = "required is required")
    private Boolean required;

    @NotNull(message = "active is required")
    private Boolean active;

    @NotNull(message = "displayOrder is required")
    @Min(value = 0, message = "displayOrder must be greater than or equal to 0")
    private Integer displayOrder;
}
