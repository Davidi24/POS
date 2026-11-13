package pos.pos.restaurant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The owner's name, email and phone, changed by a super admin. The email can only change while the invitation is
 * still waiting (the new address gets a new invitation); after that the owner changes it themselves.
 */
public record UpdateRestaurantOwnerRequest(
        @NotBlank(message = "firstName is required") @Size(max = 50, message = "firstName must be at most 50 characters") String firstName,
        @NotBlank(message = "lastName is required") @Size(max = 50, message = "lastName must be at most 50 characters") String lastName,
        @NotBlank(message = "email is required") @Email(message = "email must be a valid email")
        @Size(max = 150, message = "email must be at most 150 characters") String email,
        @Size(max = 30, message = "phone must be at most 30 characters") String phone
) {
}
