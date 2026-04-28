package pos.pos.restaurant.dto;

import pos.pos.restaurant.enums.RestaurantStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RestaurantRegistrationStatusResponse(
        UUID id,
        String name,
        RestaurantStatus status,
        String rejectionReason,
        OffsetDateTime createdAt
) {}
