package pos.pos.restaurant.dto;

import pos.pos.restaurant.enums.RestaurantStatus;

/** Where an application stands, as the applicant and the reviewers see it. */
public enum RestaurantApplicationStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public static RestaurantApplicationStatus of(RestaurantStatus status) {
        return switch (status) {
            case PENDING -> PENDING;
            case REJECTED -> REJECTED;
            default -> APPROVED;
        };
    }
}
