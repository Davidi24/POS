package pos.pos.admins;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Support admins (the Super Admin's helpers) and restaurant admins, and what restaurants ask the Super Admin (user, 2026-11-19). */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record RestaurantRef(UUID id, String name) { }

    /** A support admin's restaurants: every one, or the listed ones; and whether they may remove owners and co-owners. */
    public record SupportAdminAccess(boolean allRestaurants, boolean mayRemoveOwners, List<RestaurantRef> restaurants) { }

    public record UpdateSupportAdminAccess(boolean allRestaurants, boolean mayRemoveOwners, @Size(max = 500) List<UUID> restaurantIds) { }

    /**
     * One admin for the Super Admin's Admins page: SUPPORT = made by the Super Admin, takes care of the restaurants given to
     * them; RESTAURANT = made by a restaurant's owner, only in that restaurant.
     */
    public record AdminCard(UUID id, String name, String email, String phone, String photoUrl, boolean active, String kind,
                            boolean allRestaurants, boolean mayRemoveOwners, List<RestaurantRef> restaurants) { }

    /** A support admin a restaurant can write to for help. */
    public record SupportContact(UUID id, String name, String email, String phone, String photoUrl) { }

    public record CreateRequest(@NotBlank @Pattern(regexp = "REMOVE_OWNER|NEED_ADMIN", message = "Choose what you ask for") String kind,
                                UUID targetUserId, @Size(max = 2000) String note) { }

    public record Decision(boolean approve, @Size(max = 2000) String note) { }

    public record PlatformRequest(UUID id, UUID restaurantId, String restaurantName, String kind, UUID targetUserId, String targetName,
                                  String note, String status, UUID createdBy, String createdByName, OffsetDateTime createdAt,
                                  String decidedByName, OffsetDateTime decidedAt, String decisionNote) { }
}
