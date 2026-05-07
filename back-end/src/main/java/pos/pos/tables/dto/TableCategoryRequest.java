package pos.pos.tables.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.tables.enums.TableLocationType;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableCategoryRequest {

    @NotBlank(message = "code is required")
    @Size(max = 50, message = "code must be at most 50 characters")
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    private String description;

    @JsonAlias("default_capacity")
    @NotNull(message = "defaultCapacity is required")
    @Min(value = 1, message = "defaultCapacity must be greater than 0")
    private Integer defaultCapacity;

    @JsonAlias("location_type")
    private TableLocationType locationType;

    @Size(max = 20, message = "color must be at most 20 characters")
    private String color;

    @JsonAlias("display_order")
    @NotNull(message = "displayOrder is required")
    @Min(value = 0, message = "displayOrder must not be negative")
    private Integer displayOrder;

    @NotNull(message = "active is required")
    private Boolean active;
}
