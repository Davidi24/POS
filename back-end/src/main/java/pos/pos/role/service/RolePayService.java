package pos.pos.role.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.entity.Role;
import pos.pos.role.entity.RolePaySetting;
import pos.pos.role.mapper.RoleMapper;
import pos.pos.role.repository.RolePaySettingRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Who gets paid, per restaurant (user, 2026-10-31): a role is paid or not. A built-in role's people are paid in its
 * Salary team (Managers, Kitchen, …); a restaurant's own paid role is a Salary team of its own ("ROLE:<id>", shown with
 * its name and picture). Salary and the shift roster only show people with a paid role. A restaurant's choice is a
 * {@link RolePaySetting}; without one the role's {@link #defaults(Role)} apply.
 */
@Service
@RequiredArgsConstructor
public class RolePayService {

    public static final List<String> TEAMS = List.of("MANAGERS", "KITCHEN", "WAITERS", "CLEANING", "EXTRA");

    /** A role's pay: paid or not, and the Salary team (with the role's own name and picture when it's its own team). */
    public record Pay(boolean paid, String team, String teamName, String teamPicture) {
        public Pay(boolean paid, String team) {
            this(paid, team, null, null);
        }
    }

    /** A restaurant's own role is its own Salary team. */
    public static String ownTeam(Role role) {
        return "ROLE:" + role.getId();
    }

    private final RolePaySettingRepository settings;
    private final EntityManager em;

    /**
     * Managers, kitchen, waiters, cleaners and extra workers are paid; owners, co-owners, admins and viewers aren't. A
     * restaurant's own role is paid, as a team of its own.
     */
    public static Pay defaults(Role role) {
        String code = role.getCode() == null ? "" : role.getCode().toUpperCase(Locale.ROOT);
        if (role.isSystem()) {
            return switch (code) {
                case "MANAGER" -> new Pay(true, "MANAGERS");
                case "KITCHEN" -> new Pay(true, "KITCHEN");
                case "WAITER" -> new Pay(true, "WAITERS");
                case "CLEANER" -> new Pay(true, "CLEANING");
                case "EXTRA_WORKER" -> new Pay(true, "EXTRA");
                case "OWNER", "CO_OWNER", "ADMIN" -> new Pay(false, "MANAGERS");
                default -> new Pay(false, "EXTRA");
            };
        }
        return new Pay(true, ownTeam(role), role.getName(), role.getPicture());
    }

    /** Each role's pay in [restaurantId]; with no restaurant (a super admin outside one) the defaults. */
    @Transactional(readOnly = true)
    public Map<UUID, Pay> payOf(UUID restaurantId, Collection<Role> roles) {
        Map<UUID, RolePaySetting> chosen = restaurantId == null ? Map.of()
                : settings.findByRestaurantId(restaurantId).stream().collect(Collectors.toMap(RolePaySetting::getRoleId, s -> s, (a, b) -> a));
        Map<UUID, Pay> pay = new HashMap<>();
        for (Role role : roles) {
            RolePaySetting setting = chosen.get(role.getId());
            if (setting == null) pay.put(role.getId(), defaults(role));
            // A built-in role switched off here pays nobody.
            else if (role.isSystem()) pay.put(role.getId(), new Pay(setting.isPaid() && setting.isActive(), setting.getPayTeam()));
            else pay.put(role.getId(), new Pay(setting.isPaid(), ownTeam(role), role.getName(), role.getPicture()));
        }
        return pay;
    }

    /** The role as the app sees it inside [restaurantId], with its pay. */
    @Transactional(readOnly = true)
    public RoleResponse response(Role role, UUID restaurantId) {
        return responses(List.of(role), restaurantId).getFirst();
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> responses(List<Role> roles, UUID restaurantId) {
        Map<UUID, Pay> pay = payOf(restaurantId, roles);
        Set<UUID> off = switchedOff(restaurantId);
        return roles.stream().map(role -> {
            RoleResponse response = RoleMapper.toResponse(role);
            Pay p = pay.get(role.getId());
            response.setPaid(p.paid());
            response.setPayTeam(p.team());
            // A built-in role is on or off per restaurant.
            if (role.isSystem() && off.contains(role.getId())) response.setIsActive(false);
            return response;
        }).toList();
    }

    /** The built-in roles switched off in [restaurantId]. */
    @Transactional(readOnly = true)
    public Set<UUID> switchedOff(UUID restaurantId) {
        if (restaurantId == null) return Set.of();
        return settings.findByRestaurantId(restaurantId).stream().filter(s -> !s.isActive()).map(RolePaySetting::getRoleId).collect(Collectors.toSet());
    }

    /** Switches a built-in role on or off in [restaurantId]; its pay stays as it was. */
    @Transactional
    public void setActive(UUID restaurantId, Role role, boolean active, UUID actor) {
        Pay current = payOf(restaurantId, List.of(role)).get(role.getId());
        RolePaySetting setting = settings.findByRestaurantIdAndRoleId(restaurantId, role.getId()).orElseGet(() -> {
            RolePaySetting created = new RolePaySetting();
            created.setRestaurantId(restaurantId);
            created.setRoleId(role.getId());
            created.setPaid(current.paid());
            created.setPayTeam(role.isSystem() ? current.team() : "EXTRA");
            return created;
        });
        setting.setActive(active);
        setting.setUpdatedBy(actor);
        settings.save(setting);
    }

    /**
     * Each person's pay in [restaurantId]: paid when any of their active roles is, in the team of their highest paid role
     * (a manager who also waits tables is paid as a manager). People without a role aren't paid.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Pay> peoplePay(UUID restaurantId, Collection<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        List<Object[]> rows = em.createQuery(
                        "select ur.userId, r from UserRole ur, Role r where ur.roleId = r.id and ur.userId in :ids "
                                + "and r.isActive = true and r.deletedAt is null order by r.rank desc", Object[].class)
                .setParameter("ids", userIds)
                .getResultList();
        Map<UUID, Pay> rolePay = payOf(restaurantId, rows.stream().map(row -> (Role) row[1]).distinct().toList());
        Map<UUID, Pay> people = new HashMap<>();
        for (Object[] row : rows) {
            UUID userId = (UUID) row[0];
            Pay pay = rolePay.get(((Role) row[1]).getId());
            // Rows come highest rank first: the first paid role decides the team.
            if (pay.paid() && (!people.containsKey(userId) || !people.get(userId).paid())) people.put(userId, pay);
            else people.putIfAbsent(userId, new Pay(false, pay.team()));
        }
        return people;
    }

    /** A restaurant's own roles that are paid: each is a Salary team, also before anyone has it. */
    @Transactional(readOnly = true)
    public List<Pay> ownTeams(UUID restaurantId) {
        List<Role> own = em.createQuery("select r from Role r where r.restaurantId = :restaurantId and r.isSystem = false "
                        + "and r.isActive = true and r.deletedAt is null order by r.name", Role.class)
                .setParameter("restaurantId", restaurantId)
                .getResultList();
        Map<UUID, Pay> pay = payOf(restaurantId, own);
        return own.stream().map(role -> pay.get(role.getId())).filter(Pay::paid).toList();
    }

    /** Saves [restaurantId]'s choice for [role] (a built-in role may also change its team). */
    @Transactional
    public Pay set(UUID restaurantId, Role role, boolean paid, String team, UUID actor) {
        // The column holds a built-in team; a restaurant's own role is its own team whatever is kept there.
        String chosenTeam = !role.isSystem() ? "EXTRA" : team != null && TEAMS.contains(team) ? team : payOf(restaurantId, List.of(role)).get(role.getId()).team();
        RolePaySetting setting = settings.findByRestaurantIdAndRoleId(restaurantId, role.getId()).orElseGet(() -> {
            RolePaySetting created = new RolePaySetting();
            created.setRestaurantId(restaurantId);
            created.setRoleId(role.getId());
            return created;
        });
        setting.setPaid(paid);
        setting.setPayTeam(chosenTeam);
        setting.setUpdatedBy(actor);
        settings.save(setting);
        return payOf(restaurantId, List.of(role)).get(role.getId());
    }
}
