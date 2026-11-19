package pos.pos.auth.dto;

import pos.pos.auth.enums.PasswordLinkPurpose;
import pos.pos.auth.enums.PasswordLinkStatus;

import java.time.OffsetDateTime;

/**
 * What an emailed password link is for and whether it still works. Who it belongs to is only filled in while the link
 * works; otherwise [message] says what to do instead.
 */
public record PasswordLinkView(
        PasswordLinkStatus status,
        PasswordLinkPurpose purpose,
        String message,
        String firstName,
        String username,
        String email,
        String restaurantName,
        OffsetDateTime expiresAt,
        // The person's own language from Profile (null when unknown), for the page.
        String language
) {
}
