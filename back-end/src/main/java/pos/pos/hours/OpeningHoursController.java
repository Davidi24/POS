package pos.pos.hours;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * A branch's opening hours (user, 2026-11-20, step 4). Everyone working there may read them (the POS, bookings and
 * Statistics use them); changing them needs "Change opening hours", opening outside them "Open outside the hours". The branch
 * check reads lazy relations, so each call runs in a transaction (open-in-view is off).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/opening-hours")
public class OpeningHoursController {

    private final OpeningHoursService hours;
    private final RestaurantScopeService scope;

    public record WeekRequest(@NotNull @Size(max = 7) List<OpeningHoursService.Day> days) {
    }

    public record SpecialDayRequest(@NotNull LocalDate startsOn, LocalDate endsOn, boolean closed,
                                    @Size(max = OpeningHoursService.MAX_OPENINGS_A_DAY) List<OpeningHoursService.Span> spans,
                                    @Size(max = 255) String note) {
    }

    public record OpenNowRequest(@NotNull OffsetDateTime until) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public OpeningHoursService.Hours get(Authentication auth, @PathVariable UUID restaurantId, @PathVariable UUID branchId) {
        scope.requireAccessibleBranch(auth, restaurantId, branchId);
        return hours.hours(branchId);
    }

    @GetMapping("/now")
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public OpeningHoursService.OpenNow now(Authentication auth, @PathVariable UUID restaurantId, @PathVariable UUID branchId) {
        scope.requireAccessibleBranch(auth, restaurantId, branchId);
        return hours.now(branchId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    /** Whether it's open at a moment by its hours (a booking's time). */
    @GetMapping("/at")
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public boolean openAt(Authentication auth, @PathVariable UUID restaurantId, @PathVariable UUID branchId,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime time) {
        scope.requireAccessibleBranch(auth, restaurantId, branchId);
        return hours.isOpenAt(branchId, time);
    }

    @GetMapping("/calendar")
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<OpeningHoursService.CalendarDay> calendar(Authentication auth, @PathVariable UUID restaurantId, @PathVariable UUID branchId,
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        scope.requireAccessibleBranch(auth, restaurantId, branchId);
        return hours.calendar(branchId, from, to);
    }

    @PutMapping("/week")
    @Transactional
    @PreAuthorize("hasAuthority('HOURS_UPDATE')")
    public OpeningHoursService.Hours saveWeek(Authentication auth, @PathVariable UUID restaurantId, @PathVariable UUID branchId,
                                              @Valid @RequestBody WeekRequest request) {
        scope.requireAccessibleBranch(auth, restaurantId, branchId);
        return hours.saveWeek(branchId, request.days());
    }

    @PostMapping("/special-days")
    @Transactional
    @PreAuthorize("hasAuthority('HOURS_UPDATE')")
    public OpeningHoursService.SpecialDay addSpecialDay(Authentication auth, @PathVariable UUID restaurantId, @PathVariable UUID branchId,
                                                        @Valid @RequestBody SpecialDayRequest request) {
        scope.requireAccessibleBranch(auth, restaurantId, branchId);
        return hours.saveSpecialDay(branchId, null, request.startsOn(), request.endsOn(), request.closed(), request.spans(), request.note(),
                scope.currentUserId(auth));
    }

    @PutMapping("/special-days/{dayId}")
    @Transactional
    @PreAuthorize("hasAuthority('HOURS_UPDATE')")
    public OpeningHoursService.SpecialDay changeSpecialDay(Authentication auth, @PathVariable UUID restaurantId, @PathVariable UUID branchId,
                                                           @PathVariable UUID dayId, @Valid @RequestBody SpecialDayRequest request) {
        scope.requireAccessibleBranch(auth, restaurantId, branchId);
        return hours.saveSpecialDay(branchId, dayId, request.startsOn(), request.endsOn(), request.closed(), request.spans(), request.note(),
                scope.currentUserId(auth));
    }

    @DeleteMapping("/special-days/{dayId}")
    @Transactional
    @PreAuthorize("hasAuthority('HOURS_UPDATE')")
    public ResponseEntity<Void> deleteSpecialDay(Authentication auth, @PathVariable UUID restaurantId, @PathVariable UUID branchId,
                                                 @PathVariable UUID dayId) {
        scope.requireAccessibleBranch(auth, restaurantId, branchId);
        hours.deleteSpecialDay(branchId, dayId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/open-now")
    @Transactional
    @PreAuthorize("hasAuthority('OPEN_OUTSIDE_HOURS')")
    public OpeningHoursService.OpenNow openNow(Authentication auth, @PathVariable UUID restaurantId, @PathVariable UUID branchId,
                                               @Valid @RequestBody OpenNowRequest request) {
        scope.requireAccessibleBranch(auth, restaurantId, branchId);
        return hours.openNow(branchId, request.until(), scope.currentUserId(auth));
    }

    @DeleteMapping("/open-now")
    @Transactional
    @PreAuthorize("hasAuthority('OPEN_OUTSIDE_HOURS')")
    public OpeningHoursService.OpenNow closeNow(Authentication auth, @PathVariable UUID restaurantId, @PathVariable UUID branchId) {
        scope.requireAccessibleBranch(auth, restaurantId, branchId);
        return hours.closeNow(branchId);
    }
}
