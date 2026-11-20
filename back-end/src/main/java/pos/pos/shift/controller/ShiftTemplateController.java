package pos.pos.shift.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pos.pos.shift.dto.ShiftDtos.Template;
import pos.pos.shift.dto.ShiftDtos.TemplateRequest;
import pos.pos.shift.dto.ShiftDtos.Templates;
import pos.pos.shift.service.ShiftTemplateService;

import java.util.UUID;

/** The restaurant's usual shifts: read by anyone who sees shifts, changed by people who plan them. */
@RestController
@RequestMapping("/restaurants/{restaurantId}/shift-templates")
@RequiredArgsConstructor
public class ShiftTemplateController {
    private final ShiftTemplateService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SHIFT_SELF','SHIFT_READ','SHIFT_MANAGE')")
    public Templates list(Authentication a, @PathVariable UUID restaurantId, @RequestParam(required = false) UUID branchId) { return service.list(a, restaurantId, branchId); }

    @PostMapping
    @PreAuthorize("hasAuthority('SHIFT_MANAGE')")
    public Template create(Authentication a, @PathVariable UUID restaurantId, @Valid @RequestBody TemplateRequest r) { return service.save(a, restaurantId, null, r); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SHIFT_MANAGE')")
    public Template update(Authentication a, @PathVariable UUID restaurantId, @PathVariable UUID id, @Valid @RequestBody TemplateRequest r) {
        return service.save(a, restaurantId, id, r);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SHIFT_MANAGE')")
    public ResponseEntity<Void> delete(Authentication a, @PathVariable UUID restaurantId, @PathVariable UUID id) {
        service.delete(a, restaurantId, id);
        return ResponseEntity.noContent().build();
    }
}
