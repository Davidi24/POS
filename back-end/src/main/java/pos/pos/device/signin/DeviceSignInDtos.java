package pos.pos.device.signin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Who can sign in on a till or tablet (user, 2026-11-25). */
public final class DeviceSignInDtos {

    private DeviceSignInDtos() {
    }

    /**
     * [everyone] when nothing is chosen; else the chosen [roles] and [people]. [roleChoices]: the restaurant's roles that can
     * be chosen; [alwaysAllowed]: the roles that can always sign in (owners, admins, managers).
     */
    public record DeviceSignInView(UUID deviceId, boolean everyone, List<RoleLine> roles, List<PersonLine> people,
                                   List<RoleLine> roleChoices, List<String> alwaysAllowed, List<RoleTab> tabs) {
    }

    /**
     * A role as a tab of the form (user, 2026-11-25: "an icon for each role, a nav bar; by the role I choose, its people ready
     * to pick"): the roles the person editing sees (theirs and below, never the platform's), each with its people they see
     * (the Users rule: below them, and those at their level they added). [always]: they sign in on any device anyway.
     */
    public record RoleTab(UUID id, String code, String name, String picture, boolean always, List<PersonLine> people) {
    }

    public record RoleLine(UUID id, String code, String name, String picture) {
    }

    public record PersonLine(UUID id, String name, String photoUrl) {
    }

    /** Both empty = everyone at the branch. */
    public record SetDeviceSignInRequest(
            @NotNull @Size(max = 50, message = "At most 50 roles") List<UUID> roleIds,
            @NotNull @Size(max = 200, message = "At most 200 people") List<UUID> userIds
    ) {
    }
}
