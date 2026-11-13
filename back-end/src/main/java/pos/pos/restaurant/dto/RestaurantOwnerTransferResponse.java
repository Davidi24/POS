package pos.pos.restaurant.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One change of a restaurant's owner. */
public record RestaurantOwnerTransferResponse(
        UUID id,
        UUID restaurantId,
        UUID previousOwnerId,
        String previousOwnerName,
        UUID newOwnerId,
        String newOwnerName,
        // The new owner was a new person, invited by email to set a password.
        boolean newOwnerInvited,
        // The former owner's account was switched off (it is kept for the history).
        boolean previousOwnerDeactivated,
        int sessionsRevoked,
        String reason,
        UUID transferredBy,
        String transferredByName,
        OffsetDateTime transferredAt
) {
}
