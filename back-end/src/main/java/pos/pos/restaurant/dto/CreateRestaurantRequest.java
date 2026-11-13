package pos.pos.restaurant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @Size(max = 2000, message = "description must be at most 2000 characters")
    private String description;

    // Shown to guests on the booking pages and in their emails, so a restaurant always has both.
    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email")
    @Size(max = 150, message = "email must be at most 150 characters")
    private String email;

    @NotBlank(message = "phone is required")
    @Pattern(regexp = "^\\+?[0-9\\s\\-().]{7,50}$", message = "phone must be a valid phone number")
    private String phone;

    @Size(max = 255, message = "website must be at most 255 characters")
    @pos.pos.restaurant.validation.HttpWebsite
    private String website;

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be exactly 3 characters")
    private String currency;

    @NotBlank(message = "timezone is required")
    @Size(max = 100, message = "timezone must be at most 100 characters")
    private String timezone;

    // Optional: the owner can also be added in a second step (POST /restaurants/{id}/owner), which is what the app does.
    @Valid
    private CreateRestaurantOwnerRequest owner;

    // Every restaurant starts with one branch so the POS can be used at once; "Main" when left empty.
    @Size(max = 150, message = "firstBranchName must be at most 150 characters")
    private String firstBranchName;
}
