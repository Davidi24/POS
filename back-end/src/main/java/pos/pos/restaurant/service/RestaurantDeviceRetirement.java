package pos.pos.restaurant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Takes a removed branch's or archived restaurant's devices out of service for good: their assignments end, their
 * unused pairing tokens are revoked, and they become RETIRED with no secret, so they can never authenticate again.
 * The device rows stay, so sales, receipts and other history keep pointing at them.
 */
@Component
public class RestaurantDeviceRetirement {

    private final JdbcTemplate jdbc;
    private final String schema;

    public RestaurantDeviceRetirement(JdbcTemplate jdbc,
            @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid schema");
        this.jdbc = jdbc;
        this.schema = "\"" + schema + "\".";
    }

    /** Every device of the restaurant, in any branch or none. Returns how many were still in service. */
    public int retireRestaurant(UUID restaurantId, UUID actorId, OffsetDateTime now) {
        return retire("restaurant_id = ?", restaurantId, actorId, now);
    }

    /** The branch's devices. Returns how many were still in service. */
    public int retireBranch(UUID branchId, UUID actorId, OffsetDateTime now) {
        return retire("branch_id = ?", branchId, actorId, now);
    }

    private int retire(String devicesWhere, UUID ownerId, UUID actorId, OffsetDateTime now) {
        String devices = "select id from " + schema + "devices where " + devicesWhere;
        // An assignment of another branch's device to this place ends too (assignments carry their own branch).
        String assignmentPlace = devicesWhere.startsWith("branch_id")
                ? " or branch_id = ?"
                : " or branch_id in (select id from " + schema + "branches where restaurant_id = ?)";
        jdbc.update("update " + schema + "\"device-assignments\" set is_active = false,"
                        + " unassigned_at = greatest(?, assigned_at + interval '1 microsecond'), updated_at = ?"
                        + " where is_active = true and (device_id in (" + devices + ")" + assignmentPlace + ")",
                now, now, ownerId, ownerId);
        jdbc.update("update " + schema + "\"device-pairing-tokens\" set revoked_at = greatest(?, created_at)"
                        + " where used_at is null and revoked_at is null and device_id in (" + devices + ")",
                now, ownerId);
        return jdbc.update("update " + schema + "devices set status = 'RETIRED', is_active = false, is_online = false,"
                        + " auth_secret_hash = null, auth_secret_rotated_at = null, updated_at = ?, updated_by = ?"
                        + " where " + devicesWhere + " and (status <> 'RETIRED' or is_active or auth_secret_hash is not null)",
                now, actorId, ownerId);
    }
}
