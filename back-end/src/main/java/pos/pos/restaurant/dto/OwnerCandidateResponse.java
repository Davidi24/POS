package pos.pos.restaurant.dto;

import java.util.List;
import java.util.UUID;

/** Someone already in the POS who can be given the restaurant: active staff of this restaurant. */
public record OwnerCandidateResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String username,
        List<String> roles
) {
}
