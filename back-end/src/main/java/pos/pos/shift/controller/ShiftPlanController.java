package pos.pos.shift.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pos.pos.shift.dto.ShiftDtos.PlanRequest;
import pos.pos.shift.dto.ShiftDtos.PlanSaved;
import pos.pos.shift.service.ShiftTemplateService;

import java.util.UUID;

/** How a usual shift runs at a branch: set by people who plan shifts. */
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/shift-templates/{id}/plan")
@RequiredArgsConstructor
public class ShiftPlanController {
    private final ShiftTemplateService service;

    @PutMapping
    @PreAuthorize("hasAuthority('SHIFT_MANAGE')")
    public PlanSaved save(Authentication a, @PathVariable UUID restaurantId, @PathVariable UUID branchId, @PathVariable UUID id, @Valid @RequestBody PlanRequest r) {
        return service.savePlan(a, restaurantId, branchId, id, r);
    }
}
