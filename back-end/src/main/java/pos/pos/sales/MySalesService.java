package pos.pos.sales;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.shift.entity.Shift;
import pos.pos.shift.repository.ShiftRepository;
import pos.pos.user.repository.UserRepository;
import java.time.*;
import java.util.*;
import static pos.pos.sales.MySalesResponse.*;

@Service
@RequiredArgsConstructor
public class MySalesService {
    private final RestaurantScopeService scope;
    private final ShiftRepository shifts;
    private final UserRepository users;
    private final MySalesRepository reports;

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public MySalesResponse report(Authentication auth, UUID restaurantId, UUID branchId, LocalDate date, UUID staffId, UUID shiftId) {
        require(auth,"ORDER_READ");
        var branch = scope.requireAccessibleBranch(auth,restaurantId,branchId);
        UUID actor = scope.currentUserId(auth);
        UUID selected = staffId == null ? actor : staffId;
        if (!selected.equals(actor)) { require(auth,"ORDER_AUDIT"); require(auth,"SHIFT_READ"); }
        var user = selected.equals(actor) ? scope.currentActor(auth) : users.findById(selected).orElseThrow(() -> bad("Staff member not found"));
        if (!selected.equals(actor) && (!restaurantId.equals(user.getRestaurantId()) ||
                (user.getDefaultBranchId()!=null && !branchId.equals(user.getDefaultBranchId())))) throw new AuthException("Staff member belongs to another branch", HttpStatus.FORBIDDEN);
        ZoneId zone = ZoneId.of(branch.getRestaurant().getTimezone());
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate day = date == null ? now.atZoneSameInstant(zone).toLocalDate() : date;
        var from = day.atStartOfDay(zone).toOffsetDateTime();
        var to = day.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        var dayStart = from;
        var dayEnd = to;
        var options = shifts.inWindow(restaurantId,branchId,selected,from,to,PageRequest.of(0,101));
        if (options.size()>100) throw bad("Too many shifts for this date");
        List<ShiftOption> shiftOptions = options.stream().filter(s -> s.getStartedAt()!=null)
                .filter(s -> s.getStartedAt().isBefore(day.plusDays(1).atStartOfDay(zone).toOffsetDateTime()) &&
                        (s.getEndedAt()==null ? now : s.getEndedAt()).isAfter(day.atStartOfDay(zone).toOffsetDateTime()))
                .map(s -> option(s,now)).toList();
        if (shiftId != null) {
            Shift shift = shifts.findById(shiftId).orElseThrow(() -> bad("Shift not found"));
            if (!shift.getRestaurant().getId().equals(restaurantId) || !shift.getBranch().getId().equals(branchId) || !shift.getUser().getId().equals(selected))
                throw new AuthException("Shift does not belong to this staff member and branch",HttpStatus.FORBIDDEN);
            if (shift.getStartedAt()==null) throw bad("This shift has not started");
            from = shift.getStartedAt(); to = shift.getEndedAt()==null ? now : shift.getEndedAt();
            if (!from.isBefore(dayEnd) || !to.isAfter(dayStart)) throw bad("Shift does not overlap the selected date");
            if (to.isBefore(from) || Duration.between(from,to).toDays()>31) throw bad("This shift needs an attendance correction before reporting");
            if (to.equals(from)) to = to.plusNanos(1);
        }
        String name = (Objects.toString(user.getFirstName(),"") + " " + Objects.toString(user.getLastName(),"")).trim();
        return new MySalesResponse(restaurantId,branchId,selected,name,zone.getId(),day,from,to,shiftId,now,shiftOptions,
            reports.totals(restaurantId,branchId,selected,from,to,shiftId,zone.getId(),branch.getRestaurant().getCurrency()),
            "Sales are closed orders opened by this staff member. Collections and tips use recorded payments on those orders; shift collections require the payment's shift ID. Tips are recorded before refunds. Refunds are attributed to the original payment date; no estimated refund allocation to tips. Dates use the restaurant's local calendar day.");
    }
    private ShiftOption option(Shift s, OffsetDateTime now) {
        var end = s.getEndedAt()==null ? now : s.getEndedAt();
        long breaks = s.getBreaks().stream().mapToLong(b -> {
            var e = b.getEndedAt()==null ? end : b.getEndedAt();
            return Math.max(0,Duration.between(b.getStartedAt(),e).toMinutes());
        }).sum();
        return new ShiftOption(s.getId(),s.getStatus().name(),s.getStartedAt(),s.getEndedAt(),Math.max(0,Duration.between(s.getStartedAt(),end).toMinutes()-breaks));
    }
    private void require(Authentication auth,String permission) {
        if (auth==null || auth.getAuthorities().stream().noneMatch(a -> permission.equals(a.getAuthority())))
            throw new AuthException("You do not have permission to view these sales",HttpStatus.FORBIDDEN);
    }
    private AuthException bad(String message) { return new AuthException(message,HttpStatus.BAD_REQUEST); }
}
