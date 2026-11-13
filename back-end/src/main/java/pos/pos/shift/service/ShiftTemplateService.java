package pos.pos.shift.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.role.service.RolePayService;
import pos.pos.shift.dto.ShiftDtos.PlanPerson;
import pos.pos.shift.dto.ShiftDtos.PlanRequest;
import pos.pos.shift.dto.ShiftDtos.PlanSaved;
import pos.pos.shift.dto.ShiftDtos.Template;
import pos.pos.shift.dto.ShiftDtos.TemplateRequest;
import pos.pos.shift.dto.ShiftDtos.TemplateRole;
import pos.pos.shift.dto.ShiftDtos.Templates;
import pos.pos.shift.entity.ShiftPlan;
import pos.pos.shift.entity.ShiftTemplate;
import pos.pos.shift.repository.ShiftTemplateRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The restaurant's usual shifts (user, 2026-10-31): each has a name, times and the roles it's for (none = everyone).
 * A shift can only be for roles that get paid there, since only paid people work shifts.
 */
@Service
@RequiredArgsConstructor
public class ShiftTemplateService {

    private static final int MAX_TEMPLATES = 50;
    private static final Set<String> PLATFORM_ROLES = Set.of("SUPER_ADMIN", "CO_SUPER_ADMIN");

    private final ShiftTemplateRepository templates;
    private final RoleRepository roles;
    private final RolePayService rolePay;
    private final RestaurantScopeService scope;
    private final ShiftPlanService plans;
    private final pos.pos.shift.repository.ShiftPlanRepository planRepository;

    @Transactional(readOnly = true)
    public Templates list(Authentication auth, UUID restaurantId, UUID branchId) {
        scope.requireAccessibleRestaurant(auth, restaurantId);
        // How each runs is per branch.
        Map<UUID, ShiftPlan> planned = branchId == null ? Map.of() : plansOf(auth, restaurantId, branchId);
        List<Role> paid = paidRoles(restaurantId);
        Map<UUID, Role> byId = paid.stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        return new Templates(
                templates.findByRestaurantIdOrderBySortOrderAscNameAsc(restaurantId).stream().map(t -> view(t, byId, planned.get(t.getId()))).toList(),
                paid.stream().map(ShiftTemplateService::role).toList());
    }

    @Transactional
    public Template save(Authentication auth, UUID restaurantId, UUID id, TemplateRequest request) {
        scope.requireAccessibleRestaurant(auth, restaurantId);
        if (request.start().equals(request.end())) bad("A shift needs different start and end times.");
        Map<UUID, Role> paid = paidRoles(restaurantId).stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        Set<UUID> roleIds = new LinkedHashSet<>(request.roleIds() == null ? List.of() : request.roleIds());
        if (!paid.keySet().containsAll(roleIds)) bad("A shift can only be for roles that get paid here.");
        ShiftTemplate template;
        if (id == null) {
            List<ShiftTemplate> existing = templates.findByRestaurantIdOrderBySortOrderAscNameAsc(restaurantId);
            if (existing.size() >= MAX_TEMPLATES) bad("A restaurant can keep up to " + MAX_TEMPLATES + " usual shifts.");
            template = new ShiftTemplate();
            template.setRestaurantId(restaurantId);
            template.setSortOrder(existing.stream().mapToInt(ShiftTemplate::getSortOrder).max().orElse(0) + 1);
        } else {
            template = templates.findByIdAndRestaurantId(id, restaurantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This shift was removed. Refresh and try again."));
        }
        template.setName(request.name().trim());
        template.setStartTime(request.start());
        template.setEndTime(request.end());
        template.setIcon(request.icon());
        template.getRoleIds().clear();
        template.getRoleIds().addAll(roleIds);
        ShiftTemplate saved = templates.saveAndFlush(template);
        // Shifts its plans made follow new times.
        if (id != null) plans.remake(saved);
        return view(saved, paid, null);
    }

    @Transactional
    public void delete(Authentication auth, UUID restaurantId, UUID id) {
        scope.requireAccessibleRestaurant(auth, restaurantId);
        templates.findByIdAndRestaurantId(id, restaurantId).ifPresent(template -> {
            plans.removeFor(template.getId());
            templates.delete(template);
        });
    }

    /** Saves how a shift runs at a branch, and makes its shifts. */
    @Transactional
    public PlanSaved savePlan(Authentication auth, UUID restaurantId, UUID branchId, UUID id, PlanRequest request) {
        ShiftPlanService.Saved saved = plans.save(auth, restaurantId, branchId, id, request);
        Map<UUID, Role> paid = paidRoles(restaurantId).stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        return new PlanSaved(view(templates.findById(id).orElseThrow(), paid, saved.plan()), saved.added(), saved.skipped());
    }

    private Map<UUID, ShiftPlan> plansOf(Authentication auth, UUID restaurantId, UUID branchId) {
        scope.requireAccessibleBranch(auth, restaurantId, branchId);
        return planRepository.findByBranchId(branchId).stream().collect(Collectors.toMap(ShiftPlan::getTemplateId, Function.identity()));
    }

    /** The roles a shift can be for: the restaurant's roles (built-in and its own) that are on and paid there. */
    private List<Role> paidRoles(UUID restaurantId) {
        List<Role> candidates = roles.findByDeletedAtIsNullOrderByRankDescNameAsc().stream()
                .filter(Role::isActive)
                .filter(r -> r.isSystem() ? !PLATFORM_ROLES.contains(r.getCode()) : restaurantId.equals(r.getRestaurantId()))
                .toList();
        Map<UUID, RolePayService.Pay> pay = rolePay.payOf(restaurantId, candidates);
        Set<UUID> off = rolePay.switchedOff(restaurantId);
        return candidates.stream().filter(r -> pay.get(r.getId()).paid() && !off.contains(r.getId())).toList();
    }

    private static Template view(ShiftTemplate t, Map<UUID, Role> roles, ShiftPlan plan) {
        return new Template(t.getId(), t.getName(), t.getStartTime(), t.getEndTime(),
                t.getRoleIds().stream().map(roles::get).filter(java.util.Objects::nonNull).map(ShiftTemplateService::role).toList(), t.getIcon(),
                plan == null ? List.of() : plan.weekdaySet().stream().sorted().toList(), plan == null ? null : plan.getStartsOn(), plan == null ? null : plan.getLengthUnit(),
                plan == null ? null : plan.getLengthCount(),
                plan == null ? List.of() : plan.getPeople().entrySet().stream().map(e -> new PlanPerson(e.getKey(), e.getValue())).toList(),
                plan == null ? null : plan.getTurnEvery(), plan == null ? List.of() : plan.turnList());
    }

    private static TemplateRole role(Role r) {
        return new TemplateRole(r.getId(), r.getName(), r.isSystem() ? r.getCode() : null, r.getPicture());
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
