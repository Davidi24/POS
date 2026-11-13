package pos.pos.restaurant.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One restaurant application, as the super admins review it. */
public record RestaurantApplicationResponse(
        UUID id,
        String name,
        String legalName,
        String description,
        String email,
        String phone,
        String website,
        String currency,
        String timezone,
        RestaurantApplicationStatus status,
        String rejectionReason,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewedAt,
        UUID reviewedBy,
        Applicant owner
) {
    /** The person who applied and will own the restaurant (their account is created on approval). */
    public record Applicant(String firstName, String lastName, String email, String username, String phone) {
    }
}
