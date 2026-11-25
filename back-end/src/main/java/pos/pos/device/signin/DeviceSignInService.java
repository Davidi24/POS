package pos.pos.device.signin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.service.AccountAudit;
import pos.pos.device.entity.Device;
import pos.pos.device.network.DeviceNetworkService;
import pos.pos.device.repository.DeviceRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.device.DeviceNotFoundException;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.settings.service.SettingsAuditService;
import pos.pos.settings.service.SettingsDomainSupport;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Who can sign in on a till or tablet (user, 2026-11-25: "assign the users who can use it, if I want"). Nothing chosen =
 * everyone at its branch; otherwise people with one of the chosen roles, or the chosen people. Owners, co-owners, admins and
 * managers always can, so nobody is locked out of a till. Checked at PIN sign-in.
 */
@Service
@RequiredArgsConstructor
public class DeviceSignInService {

    /** Can always sign in on any device of their restaurant. */
    static final Set<String> ALWAYS = Set.of("SUPER_ADMIN", "CO_SUPER_ADMIN", "OWNER", "CO_OWNER", "ADMIN", "MANAGER");
    private static final List<String> ALWAYS_NAMES = List.of("Owner", "Co-Owner", "Admin", "Manager");

    private final DeviceAllowedRoleRepository allowedRoles;
    private final DeviceAllowedUserRepository allowedUsers;
    private final DeviceRepository deviceRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final SettingsDomainSupport settingsDomainSupport;
    private final SettingsAuditService settingsAuditService;
    private final pos.pos.security.rbac.RoleHierarchyService roleHierarchy;

    @Transactional(readOnly = true)
    public DeviceSignInDtos.DeviceSignInView view(Authentication authentication, UUID restaurantId, UUID deviceId) {
        settingsDomainSupport.requireManageableRestaurant(authentication, restaurantId);
        Device device = signInDevice(restaurantId, deviceId);
        return viewOf(device, restaurantId, authentication);
    }

    @Transactional
    public DeviceSignInDtos.DeviceSignInView set(Authentication authentication, UUID restaurantId, UUID deviceId,
                                                 DeviceSignInDtos.SetDeviceSignInRequest request) {
        settingsDomainSupport.requireManageableRestaurant(authentication, restaurantId);
        Device device = signInDevice(restaurantId, deviceId);
        Set<UUID> roleIds = new LinkedHashSet<>(request.roleIds());
        Set<UUID> userIds = new LinkedHashSet<>(request.userIds());
        Map<UUID, Role> choices = roleChoices(restaurantId).stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        if (!choices.keySet().containsAll(roleIds)) {
            throw new AuthException("One of those roles can't be chosen here.", HttpStatus.BAD_REQUEST);
        }
        List<User> people = userIds.isEmpty() ? List.of() : userRepository.findAllById(userIds);
        if (people.size() != userIds.size() || people.stream().anyMatch(u -> u.getDeletedAt() != null || !restaurantId.equals(u.getRestaurantId()))) {
            throw new AuthException("One of those people doesn't work here.", HttpStatus.BAD_REQUEST);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        allowedRoles.deleteByDevice(device.getId());
        allowedUsers.deleteByDevice(device.getId());
        allowedRoles.flush();
        allowedUsers.flush();
        roleIds.forEach(id -> allowedRoles.save(new DeviceAllowedRole(device.getId(), id, now)));
        userIds.forEach(id -> allowedUsers.save(new DeviceAllowedUser(device.getId(), id, now)));
        String who = roleIds.isEmpty() && userIds.isEmpty() ? "everyone at its branch"
                : java.util.stream.Stream.concat(roleIds.stream().map(id -> choices.get(id).getName()), people.stream().map(AccountAudit::nameOf))
                .collect(Collectors.joining(", "));
        settingsAuditService.log(device.getRestaurant(), device.getBranch(), "DEVICE_SIGN_IN", device.getId(), "UPDATE",
                "Who can sign in on " + device.getName() + ": " + who, settingsDomainSupport.currentActorId(authentication));
        return viewOf(device, restaurantId, authentication);
    }

    /** Whether [userId], with their active [roles], may sign in on [deviceId]. */
    @Transactional(readOnly = true)
    public boolean allows(UUID deviceId, UUID userId, Collection<Role> roles) {
        if (roles.stream().anyMatch(r -> ALWAYS.contains(r.getCode()))) return true;
        List<DeviceAllowedRole> byRole = allowedRoles.findByDeviceId(deviceId);
        List<DeviceAllowedUser> byPerson = allowedUsers.findByDeviceId(deviceId);
        if (byRole.isEmpty() && byPerson.isEmpty()) return true;
        if (byPerson.stream().anyMatch(p -> p.getUserId().equals(userId))) return true;
        Set<UUID> mine = roles.stream().map(Role::getId).collect(Collectors.toSet());
        return byRole.stream().anyMatch(r -> mine.contains(r.getRoleId()));
    }

    private Device signInDevice(UUID restaurantId, UUID deviceId) {
        Device device = deviceRepository.findRestaurantNonPrinterById(deviceId, restaurantId).orElseThrow(DeviceNotFoundException::new);
        if (!DeviceNetworkService.RULED.contains(device.getDeviceType())) {
            throw new AuthException("Only tills, tablets and screens have people who sign in.", HttpStatus.BAD_REQUEST);
        }
        return device;
    }

    /** The restaurant's roles that can be chosen: built-in and its own, switched on, not the ones that always can. */
    private List<Role> roleChoices(UUID restaurantId) {
        return roleRepository.findByDeletedAtIsNullOrderByRankDescNameAsc().stream()
                .filter(r -> r.isActive() && (r.getRestaurantId() == null || r.getRestaurantId().equals(restaurantId)))
                .filter(r -> !ALWAYS.contains(r.getCode()))
                .toList();
    }

    private DeviceSignInDtos.DeviceSignInView viewOf(Device device, UUID restaurantId, Authentication authentication) {
        List<Role> choices = roleChoices(restaurantId);
        Map<UUID, Role> byId = choices.stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        List<DeviceSignInDtos.RoleLine> roles = allowedRoles.findByDeviceId(device.getId()).stream()
                .map(r -> byId.get(r.getRoleId())).filter(java.util.Objects::nonNull).map(DeviceSignInService::line).toList();
        List<UUID> userIds = allowedUsers.findByDeviceId(device.getId()).stream().map(DeviceAllowedUser::getUserId).toList();
        List<DeviceSignInDtos.PersonLine> people = userRepository.findAllById(userIds).stream()
                .filter(u -> u.getDeletedAt() == null)
                .sorted(Comparator.comparing(AccountAudit::nameOf, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new DeviceSignInDtos.PersonLine(u.getId(), AccountAudit.nameOf(u), u.getAvatarUrl())).toList();
        return new DeviceSignInDtos.DeviceSignInView(device.getId(), roles.isEmpty() && people.isEmpty(), roles, people,
                choices.stream().map(DeviceSignInService::line).toList(), ALWAYS_NAMES, tabs(restaurantId, authentication));
    }

    /** The roles the person editing sees, highest first, each with the people of it they see (the Users rule). */
    private List<DeviceSignInDtos.RoleTab> tabs(UUID restaurantId, Authentication authentication) {
        boolean platformAdmin = roleHierarchy.isSuperAdmin(authentication) || roleHierarchy.isCoSuperAdmin(authentication);
        long rank = roleHierarchy.actorRank(authentication);
        UUID actorId = roleHierarchy.currentUserId(authentication);
        org.springframework.data.domain.Pageable all = org.springframework.data.domain.PageRequest.of(0, 500,
                org.springframework.data.domain.Sort.by("firstName").and(org.springframework.data.domain.Sort.by("lastName")));
        return roleRepository.findByDeletedAtIsNullOrderByRankDescNameAsc().stream()
                .filter(r -> r.isActive() && (r.getRestaurantId() == null || r.getRestaurantId().equals(restaurantId)))
                .filter(r -> !"SUPER_ADMIN".equals(r.getCode()) && !"CO_SUPER_ADMIN".equals(r.getCode()))
                .filter(r -> platformAdmin || r.getRank() <= rank)
                .map(r -> new DeviceSignInDtos.RoleTab(r.getId(), r.getCode(), r.getName(), r.getPicture(), ALWAYS.contains(r.getCode()),
                        userRepository.searchVisibleUsers(true, null, null, r.getCode(), platformAdmin, rank, actorId, restaurantId, all)
                                .stream()
                                .filter(u -> u.getDeletedAt() == null && restaurantId.equals(u.getRestaurantId()))
                                .map(u -> new DeviceSignInDtos.PersonLine(u.getId(), AccountAudit.nameOf(u), u.getAvatarUrl()))
                                .toList()))
                .toList();
    }

    private static DeviceSignInDtos.RoleLine line(Role role) {
        return new DeviceSignInDtos.RoleLine(role.getId(), role.getCode(), role.getName(), role.getPicture());
    }
}
