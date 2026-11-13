package pos.pos.restaurant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Gives the restaurant to a new owner: either someone already in the POS ([newOwnerUserId]) or a new person who gets
 * an invitation ([newOwner]), never both. [expectedVersion] is the restaurant's version the Super Admin looked at.
 */
public record TransferRestaurantOwnerRequest(
        UUID newOwnerUserId,
        @Valid CreateRestaurantOwnerRequest newOwner,
        @NotNull(message = "expectedVersion is required") Long expectedVersion,
        @Size(max = 500, message = "reason must be at most 500 characters") String reason
) {
}
