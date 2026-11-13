package pos.pos.restaurant.dto;

import pos.pos.restaurant.enums.BranchStatus;

import java.util.UUID;

/** One branch on the "Choose a branch" page: its picture, where it is, and how many people and devices work there. */
public record BranchCardResponse(
        UUID id,
        String name,
        String code,
        BranchStatus status,
        boolean active,
        String coverUrl,
        String phone,
        String email,
        String street,
        String city,
        String country,
        long staff,
        long devices
) {
}
