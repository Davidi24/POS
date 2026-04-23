package pos.pos.restaurant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRestaurantRequest {

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name must be at most 150 characters")
    private String name;

    @NotBlank(message = "legalName is required")
    @Size(max = 200, message = "legalName must be at most 200 characters")
    private String legalName;

    @Size(max = 100, message = "code must be at most 100 characters")
    private String code;

    @Size(max = 150, message = "slug must be at most 150 characters")
    private String slug;

    private String description;

    @Email(message = "email must be a valid email")
    @Size(max = 150, message = "email must be at most 150 characters")
    private String email;

    @Size(max = 50, message = "phone must be at most 50 characters")
    private String phone;

    @Size(max = 255, message = "website must be at most 255 characters")
    private String website;

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be exactly 3 characters")
    private String currency;

    @NotBlank(message = "timezone is required")
    @Size(max = 100, message = "timezone must be at most 100 characters")
    private String timezone;

    @Valid
    @NotNull(message = "owner is required")
    private CreateRestaurantOwnerRequest owner;
}
