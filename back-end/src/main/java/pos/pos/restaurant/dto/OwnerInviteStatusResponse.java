package pos.pos.restaurant.dto;

import pos.pos.auth.enums.PasswordLinkStatus;
import pos.pos.mail.EmailOutboxStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * How the owner's invitation is doing: whether the email went out (or is waiting for a retry, or failed) and whether
 * its link still works. [delivery] is null when no invitation email is on record.
 */
public record OwnerInviteStatusResponse(
        UUID ownerUserId,
        String email,
        boolean signedUp,
        EmailOutboxStatus delivery,
        int attempts,
        OffsetDateTime queuedAt,
        OffsetDateTime sentAt,
        OffsetDateTime nextAttemptAt,
        String lastError,
        PasswordLinkStatus linkStatus,
        OffsetDateTime linkExpiresAt
) {
}
