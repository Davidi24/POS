package pos.pos.shift.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pos.pos.audit.entity.AuditLog;
import pos.pos.audit.enums.AuditSource;
import pos.pos.audit.repository.AuditLogRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.role.service.RolePayService;
import pos.pos.shift.dto.ShiftDtos.PlanPerson;
import pos.pos.shift.dto.ShiftDtos.PlanRequest;
import pos.pos.shift.entity.Shift;
import pos.pos.shift.entity.ShiftPlan;
import pos.pos.shift.entity.ShiftTemplate;
import pos.pos.shift.enums.ShiftStatus;
import pos.pos.shift.repository.ShiftPlanRepository;
import pos.pos.shift.repository.ShiftRepository;
import pos.pos.shift.repository.ShiftTemplateRepository;
import pos.pos.user.entity.User;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * How a usual shift runs at a branch (user, 2026-10-31): which days, for how long, who it applies to, and the shifts they take
 * turns with ("one week the first shift, one week the second"). The real shifts are normal shifts, made some weeks ahead and
 * topped up here, so one can be changed or removed on its own. Saving a plan again replaces its shifts that haven't started.
 * Days someone already works are skipped.
 */
@Service
@RequiredArgsConstructor
public class ShiftPlanService {

    private static final Logger log = LoggerFactory.getLogger(ShiftPlanService.class);

    private final ShiftPlanRepository plans;
    private final ShiftTemplateRepository templates;
    private final ShiftRepository shifts;
    private final RestaurantScopeService scope;
    private final RolePayService rolePay;
    private final EntityManager em;
    private final AuditLogRepository audits;
    private final TransactionTemplate transactions;

    @Value("${app.shifts.plan-weeks-ahead:12}")
    private int weeksAhead;

    /** Saves how [templateId] runs at the branch; no weekdays takes it off the calendar. Answers {plan or null, added, skipped}. */
    @Transactional
    public Saved save(Authentication auth, UUID restaurantId, UUID branchId, UUID templateId, PlanRequest request) {
        Branch branch = scope.requireAccessibleBranch(auth, restaurantId, branchId);
        ShiftTemplate template = templates.findByIdAndRestaurantId(templateId, restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This shift was removed. Refresh and try again."));
        UUID actor = scope.currentUserId(auth);
        OffsetDateTime now = now();
        ShiftPlan existing = plans.findByTemplateIdAndBranchId(templateId, branchId).orElse(null);
        Set<Integer> weekdays = request.weekdays() == null ? Set.of() : new TreeSet<>(request.weekdays());
        if (weekdays.isEmpty()) {
            if (existing != null) {
                shifts.deletePlannedOfPlan(existing.getId(), now);
                plans.delete(existing);
            }
            audit(branch, actor, templateId, "SHIFT_PLAN_REMOVED", template.getName());
            return new Saved(null, 0, 0);
        }
        if (request.startsOn() == null) bad("Choose when it starts.");
        if (request.lengthUnit() != null && request.lengthCount() == null) bad("Choose how long it lasts.");
        List<UUID> turns = new ArrayList<>(new LinkedHashSet<>(request.turnShifts() == null ? List.<UUID>of() : request.turnShifts()));
        for (UUID turn : turns) {
            if (turn.equals(templateId) || templates.findByIdAndRestaurantId(turn, restaurantId).isEmpty()) bad("Choose shifts of this restaurant to take turns with.");
        }
        Map<UUID, Integer> people = new HashMap<>();
        for (PlanPerson person : request.people() == null ? List.<PlanPerson>of() : request.people()) {
            if (person.startTurn() > turns.size()) bad("Choose a turn for everyone.");
            people.put(person.userId(), person.startTurn());
        }
        Map<UUID, RolePayService.Pay> pay = rolePay.peoplePay(restaurantId, people.keySet());
        for (UUID userId : people.keySet()) {
            User user = em.find(User.class, userId);
            if (!canWork(user, restaurantId, branchId)) bad("Choose people who belong to this branch.");
            RolePayService.Pay theirs = pay.get(userId);
            if (theirs == null || !theirs.paid()) bad("Only people who get paid can be put on shifts. Make one of their roles paid in Admin Hub → Users.");
        }
        ShiftPlan plan = existing;
        if (plan == null) {
            plan = new ShiftPlan();
            plan.setTemplateId(templateId);
            plan.setBranchId(branchId);
        }
        plan.setWeekdays(weekdays.stream().map(String::valueOf).collect(Collectors.joining(",")));
        plan.setStartsOn(request.startsOn());
        plan.setLengthUnit(request.lengthUnit());
        plan.setLengthCount(request.lengthUnit() == null ? null : request.lengthCount());
        plan.setTurnEvery(request.turnEvery() == null ? 1 : request.turnEvery());
        plan.setTurnShifts(turns.isEmpty() ? null : turns.stream().map(UUID::toString).collect(Collectors.joining(",")));
        plan.getPeople().keySet().retainAll(people.keySet());
        plan.getPeople().putAll(people);
        plan.setPlannedUntil(null);
        plan.setUpdatedBy(actor);
        plan = plans.saveAndFlush(plan);
        shifts.deletePlannedOfPlan(plan.getId(), now);
        int[] made = fill(plan, branch, template, now);
        audit(branch, actor, templateId, "SHIFT_PLAN_SAVED", template.getName() + ", weekdays " + plan.getWeekdays() + ", " + people.size() + " people");
        return new Saved(plan, made[0], made[1]);
    }

    public record Saved(ShiftPlan plan, int added, int skipped) {}

    /** After a shift's times changed: its plans make their shifts again. */
    @Transactional
    public void remake(ShiftTemplate template) {
        OffsetDateTime now = now();
        for (ShiftPlan plan : plans.findByTemplateId(template.getId())) {
            Branch branch = em.find(Branch.class, plan.getBranchId());
            if (branch == null) continue;
            shifts.deletePlannedOfPlan(plan.getId(), now);
            plan.setPlannedUntil(null);
            fill(plan, branch, template, now);
        }
    }

    /** Before a shift is removed: its plans go, with their shifts that haven't started. */
    @Transactional
    public void removeFor(UUID templateId) {
        OffsetDateTime now = now();
        for (ShiftPlan plan : plans.findByTemplateId(templateId)) {
            shifts.deletePlannedOfPlan(plan.getId(), now);
            plans.delete(plan);
        }
    }

    /** Keeps every plan made ahead, also soon after a start, in case the server was off. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 120_000)
    public void topUp() {
        for (UUID id : plans.allIds()) {
            try {
                transactions.executeWithoutResult(status -> plans.lock(id).ifPresent(plan -> {
                    Branch branch = em.find(Branch.class, plan.getBranchId());
                    ShiftTemplate template = em.find(ShiftTemplate.class, plan.getTemplateId());
                    if (branch != null && template != null && branch.getDeletedAt() == null && branch.getRestaurant().getDeletedAt() == null) fill(plan, branch, template, now());
                }));
            } catch (RuntimeException e) {
                log.warn("Couldn't top up shift plan {}", id, e);
            }
        }
    }

    /** Makes the shifts after what's already made, up to the weeks ahead; answers {added, skipped}. */
    private int[] fill(ShiftPlan plan, Branch branch, ShiftTemplate template, OffsetDateTime now) {
        ZoneId zone = ZoneId.of(branch.getRestaurant().getTimezone());
        LocalDate today = LocalDate.now(zone);
        LocalDate until = today.plusWeeks(weeksAhead);
        LocalDate last = plan.lastDay();
        if (last != null && last.isBefore(until)) until = last;
        LocalDate from = plan.getPlannedUntil() != null ? plan.getPlannedUntil().plusDays(1) : plan.getStartsOn();
        if (from.isBefore(today)) from = today;
        List<ShiftTemplate> turns = new ArrayList<>(List.of(template));
        plan.turnList().forEach(id -> templates.findById(id).ifPresent(turns::add));
        UUID restaurantId = branch.getRestaurant().getId();
        Map<UUID, RolePayService.Pay> pay = rolePay.peoplePay(restaurantId, plan.getPeople().keySet());
        Map<UUID, User> users = new HashMap<>();
        plan.getPeople().keySet().forEach(id -> {
            User user = em.find(User.class, id);
            RolePayService.Pay theirs = pay.get(id);
            if (canWork(user, restaurantId, plan.getBranchId()) && theirs != null && theirs.paid()) users.put(id, user);
        });
        Set<Integer> weekdays = plan.weekdaySet();
        LocalDate firstMonday = plan.getStartsOn().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int every = Math.max(1, plan.getTurnEvery());
        int added = 0;
        int skipped = 0;
        for (LocalDate date = from; !date.isAfter(until); date = date.plusDays(1)) {
            if (!weekdays.contains(date.getDayOfWeek().getValue() - 1)) continue;
            long week = ChronoUnit.WEEKS.between(firstMonday, date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            for (Map.Entry<UUID, User> person : users.entrySet()) {
                ShiftTemplate turn = turns.get((int) Math.floorMod(week / every + plan.getPeople().getOrDefault(person.getKey(), 0), (long) turns.size()));
                OffsetDateTime start = date.atTime(turn.getStartTime()).atZone(zone).toOffsetDateTime();
                OffsetDateTime end = (turn.getEndTime().isAfter(turn.getStartTime()) ? date : date.plusDays(1)).atTime(turn.getEndTime()).atZone(zone).toOffsetDateTime();
                if (!end.isAfter(now)) continue;
                if (shifts.plannedOverlaps(person.getKey(), start, end) > 0) {
                    skipped++;
                    continue;
                }
                Shift shift = new Shift();
                shift.setId(UUID.randomUUID());
                shift.setRestaurant(branch.getRestaurant());
                shift.setBranch(branch);
                shift.setUser(person.getValue());
                shift.setStatus(ShiftStatus.SCHEDULED);
                shift.setScheduledStart(start);
                shift.setScheduledEnd(end);
                shift.setPlanId(plan.getId());
                shift.setCreatedBy(plan.getUpdatedBy());
                shift.setUpdatedBy(plan.getUpdatedBy());
                shifts.saveAndFlush(shift);
                added++;
            }
        }
        if (plan.getPlannedUntil() == null || until.isAfter(plan.getPlannedUntil())) plan.setPlannedUntil(until);
        return new int[]{added, skipped};
    }

    private static boolean canWork(User user, UUID restaurantId, UUID branchId) {
        return user != null && user.getDeletedAt() == null && user.isActive() && Objects.equals(user.getRestaurantId(), restaurantId)
                && (user.getDefaultBranchId() == null || user.getDefaultBranchId().equals(branchId));
    }

    private void audit(Branch branch, UUID actor, UUID templateId, String action, String details) {
        AuditLog entry = new AuditLog();
        entry.setRestaurant(branch.getRestaurant());
        entry.setBranch(branch);
        entry.setActorUser(em.getReference(User.class, actor));
        entry.setSource(AuditSource.API);
        entry.setEntityType("SHIFT");
        entry.setEntityId(templateId);
        entry.setAction(action);
        entry.setSummary(action.replace('_', ' '));
        entry.setMetadataPayload(details);
        audits.save(entry);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
