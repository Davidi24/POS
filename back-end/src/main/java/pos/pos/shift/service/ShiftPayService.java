package pos.pos.shift.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pos.pos.audit.entity.AuditLog;
import pos.pos.audit.enums.AuditSource;
import pos.pos.audit.repository.AuditLogRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.shift.dto.ShiftDtos.*;
import pos.pos.shift.entity.Shift;
import pos.pos.shift.repository.ShiftRepository;
import pos.pos.shift.repository.StaffPayRateRepository;
import pos.pos.user.entity.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/**
 * Pay = hours worked × hourly wage + the tips recorded on the person's orders. Hours are the shift's worked time
 * (unpaid breaks excluded); a shift uses the wage it was clocked in at, or the current wage when it had none.
 * Still-open shifts count up to now.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ShiftPayService {
    private static final int MAX_DAYS = 62;
    private final ShiftRepository shifts;
    private final StaffPayRateRepository rates;
    private final RestaurantScopeService scope;
    private final EntityManager em;
    private final AuditLogRepository audits;

    @Transactional(readOnly = true)
    public PayReport pay(Authentication auth, UUID restaurantId, UUID branchId, LocalDate from, LocalDate to, boolean mine) {
        require(auth, mine ? "SHIFT_SELF" : "SHIFT_MANAGE");
        Branch branch = scope.requireAccessibleBranch(auth, restaurantId, branchId);
        if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusDays(MAX_DAYS - 1))) bad("Choose a date range of up to " + MAX_DAYS + " days.");
        UUID actor = scope.currentUserId(auth);
        ZoneId zone = ZoneId.of(branch.getRestaurant().getTimezone());
        String currency = branch.getRestaurant().getCurrency();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime start = from.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        List<Shift> worked = shifts.inWindow(restaurantId, branchId, mine ? actor : null, start, end, PageRequest.of(0, 5001)).stream()
                .filter(s -> s.getStartedAt() != null && !s.getStartedAt().isBefore(start) && s.getStartedAt().isBefore(end))
                .toList();
        if (worked.size() > 5000) bad("Too many shifts in this range. Choose a shorter date range.");

        List<User> people;
        if (mine) {
            User me = em.find(User.class, actor);
            people = me == null ? List.of() : List.of(me);
        } else {
            Map<UUID, User> byId = new LinkedHashMap<>();
            shifts.staff(restaurantId, branchId).forEach(u -> byId.put(u.getId(), u));
            // People who worked here in the period but have left the roster still get paid for it.
            worked.forEach(s -> byId.putIfAbsent(s.getUser().getId(), s.getUser()));
            people = new ArrayList<>(byId.values());
        }
        Map<UUID, List<String>> roleNames = new HashMap<>();
        if (!people.isEmpty()) shifts.roleNames(people.stream().map(User::getId).toList()).forEach(row ->
                roleNames.computeIfAbsent((UUID) row[0], ignored -> new ArrayList<>()).add((String) row[1]));
        Map<UUID, BigDecimal> current = rates.rates(restaurantId);
        Map<UUID, Map<LocalDate, BigDecimal>> tips = new HashMap<>();
        rates.tips(restaurantId, branchId, mine ? actor : null, start, end, zone.getId(), currency).forEach(row ->
                tips.computeIfAbsent(row.staffId(), ignored -> new TreeMap<>()).merge(LocalDate.parse(row.day()), row.tips(), BigDecimal::add));

        List<StaffPay> staff = people.stream().map(person -> {
            BigDecimal rate = current.get(person.getId());
            List<ShiftPay> shiftPays = worked.stream().filter(s -> s.getUser().getId().equals(person.getId()))
                    .sorted(Comparator.comparing(Shift::getStartedAt))
                    .map(s -> {
                        long minutes = ShiftService.workedMinutes(s, now);
                        BigDecimal shiftRate = s.getHourlyRate() != null ? s.getHourlyRate() : rate;
                        return new ShiftPay(s.getId(), s.getStartedAt().atZoneSameInstant(zone).toLocalDate(), s.getStatus(),
                                s.getStartedAt(), s.getEndedAt(), minutes, ShiftService.breakMinutes(s, now, false), shiftRate, wages(minutes, shiftRate));
                    }).toList();
            long minutes = shiftPays.stream().mapToLong(ShiftPay::workedMinutes).sum();
            long breaks = shiftPays.stream().mapToLong(ShiftPay::breakMinutes).sum();
            BigDecimal wages = shiftPays.stream().map(ShiftPay::wages).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<LocalDate, BigDecimal> personTips = tips.getOrDefault(person.getId(), Map.of());
            BigDecimal tipTotal = personTips.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            boolean missing = shiftPays.stream().anyMatch(p -> p.hourlyRate() == null && p.workedMinutes() > 0);
            return new StaffPay(person.getId(), name(person), roleNames.getOrDefault(person.getId(), List.of()), rate, shiftPays.size(),
                    minutes, breaks, wages.setScale(2, RoundingMode.HALF_UP), tipTotal, wages.add(tipTotal).setScale(2, RoundingMode.HALF_UP), missing, shiftPays,
                    personTips.entrySet().stream().map(e -> new DayTips(e.getKey(), e.getValue().setScale(2, RoundingMode.HALF_UP))).toList());
        }).toList();
        return new PayReport(zone.getId(), currency, from, to, now, staff);
    }

    public BigDecimal setRate(Authentication auth, UUID restaurantId, UUID branchId, UUID userId, PayRate request) {
        require(auth, "SHIFT_MANAGE");
        Branch branch = scope.requireAccessibleBranch(auth, restaurantId, branchId);
        User user = em.find(User.class, userId);
        if (user == null || user.getDeletedAt() != null || !Objects.equals(user.getRestaurantId(), restaurantId)) bad("Choose a staff member of this restaurant.");
        BigDecimal rate = request.hourlyRate().setScale(2, RoundingMode.HALF_UP);
        BigDecimal before = rates.rate(userId).orElse(null);
        UUID actor = scope.currentUserId(auth);
        rates.save(restaurantId, userId, rate, actor);
        int filled = rates.fillMissingShiftRates(userId, rate);
        AuditLog log = new AuditLog(); log.setRestaurant(branch.getRestaurant()); log.setBranch(branch); log.setActorUser(em.getReference(User.class, actor));
        log.setSource(AuditSource.API); log.setEntityType("STAFF_PAY_RATE"); log.setEntityId(userId); log.setAction("STAFF_PAY_RATE_SET");
        log.setSummary("Hourly wage set for " + name(user)); log.setBeforeState(before == null ? "none" : before.toPlainString());
        log.setAfterState(rate.toPlainString()); log.setMetadataPayload(filled > 0 ? "applied to " + filled + " earlier shifts without a wage" : null);
        audits.save(log);
        return rate;
    }

    private static BigDecimal wages(long minutes, BigDecimal rate) {
        return rate == null ? null : rate.multiply(BigDecimal.valueOf(minutes)).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
    private static String name(User u) { return (Objects.toString(u.getFirstName(), "") + " " + Objects.toString(u.getLastName(), "")).trim(); }
    private static boolean has(Authentication auth, String permission) { return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permission)); }
    private static void require(Authentication auth, String permission) {
        if (!has(auth, permission) && !has(auth, "SHIFT_MANAGE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to see this pay.");
    }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
