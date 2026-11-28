package pos.pos.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The applicant's private code (sent in the body, so it never ends up in URLs or logs). */
public record RestaurantApplicationStatusRequest(
        @NotBlank(message = "code is required")
        @Size(max = 64, message = "code must be at most 64 characters")
        String code
) {
}
