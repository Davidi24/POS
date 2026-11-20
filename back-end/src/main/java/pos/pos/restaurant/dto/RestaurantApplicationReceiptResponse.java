package pos.pos.restaurant.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * What an applicant gets back right after applying. [statusCode] is shown only here (and in the confirmation email):
 * it is the private code for checking on the application later.
 */
public record RestaurantApplicationReceiptResponse(
        UUID id,
        String name,
        RestaurantApplicationStatus status,
        String statusCode,
        String ownerEmail,
        OffsetDateTime submittedAt
) {
}
