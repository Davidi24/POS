package pos.pos.restaurant.dto;

import java.util.UUID;

public record RestaurantSummaryResponse(
        UUID restaurantId,
        long totalBranches,
        long activeBranches,
        long inactiveBranches,
        boolean hasAddress,
        boolean hasBranding,
        boolean hasPrimaryContact,
        boolean hasDefaultTaxProfile
) {}
