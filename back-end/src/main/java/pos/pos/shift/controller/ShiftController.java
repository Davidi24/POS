package pos.pos.shift.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pos.pos.shift.dto.ShiftDtos.*;
import pos.pos.shift.service.ShiftService;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/shifts")
@RequiredArgsConstructor
public class ShiftController {
    private final ShiftService service;
    private final pos.pos.shift.service.ShiftPayService pay;
    @GetMapping
    @PreAuthorize("hasAnyAuthority('SHIFT_SELF','SHIFT_READ','SHIFT_MANAGE')")
    public Board board(Authentication a, @PathVariable UUID restaurantId, @PathVariable UUID branchId, @RequestParam LocalDate from, @RequestParam LocalDate to, @RequestParam(defaultValue="true") boolean mine) {
        return service.board(a, restaurantId, branchId, from, to, mine);
    }
    @PostMapping
    @PreAuthorize("hasAuthority('SHIFT_MANAGE')")
    public Item schedule(Authentication a, @PathVariable UUID restaurantId, @PathVariable UUID branchId, @Valid @RequestBody Schedule r) { return service.schedule(a, restaurantId, branchId, null, r); }
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SHIFT_MANAGE')")
    public Item edit(Authentication a, @PathVariable UUID restaurantId, @PathVariable UUID branchId, @PathVariable UUID id, @Valid @RequestBody Schedule r) { return service.schedule(a, restaurantId, branchId, id, r); }
    @PostMapping("/clock-in")
    @PreAuthorize("hasAuthority('SHIFT_SELF')")
    public Item clockIn(Authentication a, @PathVariable UUID restaurantId, @PathVariable UUID branchId, @Valid @RequestBody ClockIn r) { return service.clockIn(a, restaurantId, branchId, r); }
    @PostMapping("/{id}/break")
    @PreAuthorize("hasAuthority('SHIFT_SELF')")
    public Item startBreak(Authentication a, @PathVariable UUID restaurantId, @PathVariable UUID branchId, @PathVariable UUID id, @Valid @RequestBody StartBreak r) { return service.startBreak(a, restaurantId, branchId, id, r); }
    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('SHIFT_SELF')")
    public Item resume(Authentication a, @PathVariable UUID restaurantId, @PathVariable UUID branchId, @PathVariable UUID id, @Valid @RequestBody Action r) { return service.resume(a, restaurantId, branchId, id, r); }
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyAuthority('SHIFT_SELF','SHIFT_MANAGE')")
    public Item close(Authentication a, @PathVariable UUID restaurantId, @PathVariable UUID branchId, @PathVariable UUID id, @Valid @RequestBody Action r) { return service.close(a, restaurantId, branchId, id, r); }
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SHIFT_MANAGE')")
    public Item cancel(Authentication a, @PathVariable UUID restaurantId, @PathVariable UUID branchId, @PathVariable UUID id, @Valid @RequestBody Action r) { return service.finishSchedule(a, restaurantId, branchId, id, r, false); }
    @PostMapping("/{id}/missed")
    @PreAuthorize("hasAuthority('SHIFT_MANAGE')")
    public Item missed(Authentication a, @PathVariable UUID restaurantId, @PathVariable UUID branchId, @PathVariable UUID id, @Valid @RequestBody Action r) { return service.finishSchedule(a, restaurantId, branchId, id, r, true); }
    @PutMapping("/{id}/attendance")
    @PreAuthorize("hasAuthority('SHIFT_MANAGE')")
    public Item correct(Authentication a, @PathVariable UUID restaurantId, @PathVariable UUID branchId, @PathVariable UUID id, @Valid @RequestBody Correction r) { return service.correct(a, restaurantId, branchId, id, r); }
    @GetMapping("/pay")
    @PreAuthorize("hasAnyAuthority('SHIFT_SELF','SHIFT_MANAGE')")
    public PayReport pay(Authentication a, @PathVariable UUID restaurantId, @PathVariable UUID branchId, @RequestParam LocalDate from, @RequestParam LocalDate to, @RequestParam(defaultValue="true") boolean mine) {
        return pay.pay(a, restaurantId, branchId, from, to, mine);
    }
    @PutMapping("/pay-rates/{userId}")
    @PreAuthorize("hasAuthority('SHIFT_MANAGE')")
    public PayRate setPayRate(Authentication a, @PathVariable UUID restaurantId, @PathVariable UUID branchId, @PathVariable UUID userId, @Valid @RequestBody PayRate r) {
        return new PayRate(pay.setRate(a, restaurantId, branchId, userId, r));
    }
}
