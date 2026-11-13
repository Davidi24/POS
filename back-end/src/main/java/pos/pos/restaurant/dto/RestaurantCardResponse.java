package pos.pos.restaurant.dto;

import pos.pos.restaurant.enums.RestaurantStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One restaurant on the super admins' restaurant list. */
public record RestaurantCardResponse(
        UUID id,
        String name,
        String code,
        RestaurantStatus status,
        boolean active,
        String currency,
        String timezone,
        String city,
        String country,
        long branches,
        long openBranches,
        long staff,
        UUID ownerUserId,
        String ownerName,
        String ownerEmail,
        // The owner hasn't set their password from the invitation yet.
        boolean ownerInvitePending,
        // The latest invitation email: PENDING/SENDING (on its way), SENT, FAILED or CANCELLED; null if none.
        String ownerInviteDelivery,
        // The newest invitation link has expired without being used.
        boolean ownerInviteExpired,
        String logoUrl,
        String coverUrl,
        OffsetDateTime createdAt
) {
}
