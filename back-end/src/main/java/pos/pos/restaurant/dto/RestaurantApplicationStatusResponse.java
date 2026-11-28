package pos.pos.restaurant.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** What an applicant sees when they check on their application with its code. */
public record RestaurantApplicationStatusResponse(
        UUID id,
        String name,
        RestaurantApplicationStatus status,
        // Only when it was rejected: the reason the reviewer gave.
        String rejectionReason,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewedAt,
        // One sentence on what happens next.
        String message
) {
}
